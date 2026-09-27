package app.relay.chat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.relay.chat.data.Connection
import app.relay.chat.data.ChatStore
import app.relay.chat.data.ConnectionStore
import app.relay.chat.data.StoredMessage
import app.relay.chat.data.StoredTool
import app.relay.chat.data.ModelRef
import app.relay.chat.data.Plan
import app.relay.chat.runtime.LinuxRuntime
import app.relay.chat.runtime.PlanService
import app.relay.chat.runtime.RuntimeService
import app.relay.chat.net.ApiException
import app.relay.chat.net.ChatTurn
import app.relay.chat.net.LlmClient
import app.relay.chat.net.ModelsResult
import app.relay.chat.net.Role
import app.relay.chat.net.StreamEvent
import app.relay.chat.ui.theme.ThemeId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlinx.coroutines.withContext

data class Attachment(val name: String, val text: String)

/**
 * Smooths streamed text. Backends deliver bursts (Claude Code flushes several words at a
 * time), so deltas queue here and are released each frame: a few characters when caught up,
 * more when behind, so the text never trails the stream by more than ~1/4 s.
 */
private class Typewriter(private val target: UiMessage) {
    private val pending = StringBuilder()
    private var finished = false
    /** Tool calls waiting for the text before them to be revealed. */
    private val queuedTools = ArrayDeque<ToolCall>()
    private val byId = HashMap<String, ToolCall>()

    fun push(delta: String) { pending.append(delta) }

    fun pushTool(id: String, name: String, input: String) {
        val t = ToolCall(id, name, input, anchor = target.text.length + pending.length)
        byId[id] = t
        queuedTools.addLast(t)
        attachReady()
    }

    fun endTool(id: String, output: String, isError: Boolean) {
        byId[id]?.let { it.output = output; it.isError = isError; it.done = true }
    }

    private fun attachReady() {
        while (queuedTools.isNotEmpty() && queuedTools.first().anchor <= target.text.length) {
            target.tools += queuedTools.removeFirst()
        }
    }
    fun finish() { finished = true }

    /** Shows everything at once (stop / error / cancel). */
    fun flush() {
        if (pending.isNotEmpty()) {
            target.reveal(pending.toString())
            pending.clear()
        }
        attachReady()
        // A reply that stopped mid-call leaves its tools unfinished; don't spin forever.
        target.tools.forEach { if (!it.done) it.done = true }
    }

    // AndroidUiDispatcher.Main carries Compose's frame clock (viewModelScope doesn't), so
    // each release lands exactly once per drawn frame.
    suspend fun run() = withContext(AndroidUiDispatcher.Main) {
        while (true) {
            withFrameNanos { }
            attachReady()
            val backlog = pending.length
            if (backlog == 0) {
                if (finished) break
                continue
            }
            // ~15 frames to drain any backlog; at least 2 chars/frame (~120 chars/s).
            var n = maxOf(2, (backlog + 14) / 15).coerceAtMost(backlog)
            // Don't split a surrogate pair (emoji).
            if (n < backlog && Character.isHighSurrogate(pending[n - 1])) n++
            target.reveal(pending.substring(0, n))
            pending.delete(0, n)
        }
    }
}

/** Live state of a plan's setup / sign-in, observed by the plan screen. */
class PlanSetup(val plan: Plan) {
    enum class Step { Idle, Runtime, Install, SignIn, Done, Failed }

    var step by mutableStateOf(Step.Idle)
    val log = mutableStateListOf<String>()
    var url by mutableStateOf<String?>(null)
    var deviceCode by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var updating by mutableStateOf(false)
    internal var session: PlanService.LoginSession? = null
    internal var job: Job? = null

    val running get() = step == Step.Runtime || step == Step.Install || step == Step.SignIn

    internal fun addLog(line: String) {
        android.util.Log.i("RelayRuntime", line)
        // Collapse progress lines ("  42% of 100 MB", curl meters) into one updating row.
        val progress = Regex("""^\s*(\d{1,3}(\.\d)?%|#+\s|[\d.]+[kMG]?\s+\d+\s)""")
        if (log.isNotEmpty() && progress.containsMatchIn(line) && progress.containsMatchIn(log.last())) log[log.lastIndex] = line
        else log += line
        if (log.size > 200) log.removeRange(0, log.size - 200)
    }
}

/** One tool call inside a reply, placed after the first [anchor] characters of its text. */
class ToolCall(val id: String, val name: String, val detail: String, val anchor: Int) {
    var output by mutableStateOf<String?>(null)
    var isError by mutableStateOf(false)
    var done by mutableStateOf(false)
}

class UiMessage(
    val id: Long,
    val role: Role,
    text: String,
    /** Shown to the user; for user turns with an attachment this omits the file body. */
    val display: String = text,
) {
    var text by mutableStateOf(text)
    var tokens by mutableStateOf<Int?>(null)
    var seconds by mutableStateOf<Double?>(null)
    var connectionName by mutableStateOf<String?>(null)
    var model by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var done by mutableStateOf(role == Role.User)
    val tools = mutableStateListOf<ToolCall>()

    /**
     * Reveal history for the fade-in: [revealEnds] are text lengths after each release,
     * [revealTimes] the System.nanoTime() of that release. Plain lists: the frame loop that
     * draws the fade polls them, so they don't need to be snapshot state.
     */
    internal val revealEnds = ArrayList<Int>()
    internal val revealTimes = ArrayList<Long>()

    internal fun reveal(chunk: String) {
        text += chunk
        revealEnds += text.length
        revealTimes += System.nanoTime()
    }

    /** When the character at [index] appeared, or 0 if it predates the history. */
    fun revealedAt(index: Int): Long {
        var lo = 0
        var hi = revealEnds.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (revealEnds[mid] > index) { ans = mid; hi = mid - 1 } else lo = mid + 1
        }
        return if (ans < 0) 0L else revealTimes[ans]
    }

    val lastRevealAt get() = revealTimes.lastOrNull() ?: 0L
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ConnectionStore(app)

    /** A string in the app's chosen language (the Application context isn't wrapped). */
    private fun str(id: Int, vararg args: Any): String = Lang.wrap(getApplication()).getString(id, *args)
    private val chatStore = ChatStore(app)
    val client = LlmClient()
    private val runtime = LinuxRuntime(app)
    val plans = PlanService(runtime)
    val runtimeSupported get() = runtime.arch != null

    var connections by mutableStateOf(store.loadConnections())
        private set
    var defaultId by mutableStateOf(store.defaultConnectionId)
        private set
    private var storedSelection by mutableStateOf(store.selected)

    var theme by mutableStateOf(store.theme)
        private set

    /** Lets subscription chats use the CLI's tools (web search, code, files). */
    var toolsEnabled by mutableStateOf(store.toolsEnabled)
        private set

    fun chooseTools(on: Boolean) {
        toolsEnabled = on
        store.toolsEnabled = on
    }

    fun chooseTheme(id: ThemeId) {
        theme = id
        store.theme = id
    }

    val messages = mutableStateListOf<UiMessage>()

    /** Saved chats, newest first. */
    var chats by mutableStateOf(chatStore.list())
        private set

    /** The open chat's id; null until its first message is sent. */
    var chatId by mutableStateOf<String?>(null)
        private set
    var streaming by mutableStateOf(false)
        private set
    var attachment by mutableStateOf<Attachment?>(null)

    private var streamJob: Job? = null
    private var nextId = 0L

    /** The selected model if it still exists, else the default connection's first model. */
    val selection: ModelRef?
        get() {
            storedSelection?.let { s ->
                val c = connections.find { it.id == s.connectionId }
                if (c != null && (s.model in c.models || c.models.isEmpty())) return s
            }
            val c = connections.find { it.id == defaultId } ?: connections.firstOrNull() ?: return null
            return c.models.firstOrNull()?.let { ModelRef(c.id, it) }
        }

    val selectedConnection: Connection?
        get() = selection?.let { s -> connections.find { it.id == s.connectionId } }
            ?: connections.find { it.id == defaultId } ?: connections.firstOrNull()

    fun connection(id: String?) = connections.find { it.id == id }

    fun select(ref: ModelRef) {
        storedSelection = ref
        store.selected = ref
    }

    fun newChat() {
        stop()
        messages.clear()
        attachment = null
        chatId = null
        chatStore.lastChatId = null
    }

    init {
        // Plan model lists change between app versions (and Codex's catalog over time).
        connections.filter { it.plan != null }.forEach { refreshModels(it.id) }
        chatStore.lastChatId?.let { openChat(it) }
    }

    fun openChat(id: String) {
        stop()
        val stored = chatStore.load(id) ?: return
        messages.clear()
        attachment = null
        stored.forEach { s ->
            messages += UiMessage(nextId++, if (s.user) Role.User else Role.Assistant, s.text, s.display).also {
                it.tokens = s.tokens
                it.seconds = s.seconds
                it.connectionName = s.connectionName
                it.model = s.model
                it.error = s.error
                it.done = true
                s.tools.forEach { t ->
                    it.tools += ToolCall(t.id, t.name, t.detail, t.anchor).apply {
                        output = t.output; isError = t.isError; done = true
                    }
                }
            }
        }
        chatId = id
        chatStore.lastChatId = id
    }

    fun deleteChat(id: String) {
        chatStore.delete(id)
        chats = chatStore.list()
        if (chatId == id) newChat()
    }

    /** Writes the open chat (creating it on its first message). */
    private fun saveChat() {
        if (messages.isEmpty()) return
        val id = chatId ?: java.util.UUID.randomUUID().toString().also { chatId = it }
        val title = messages.firstOrNull { it.role == Role.User }?.display
            ?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(80) ?: "New chat"
        chatStore.save(
            id, title,
            messages.map {
                StoredMessage(
                    user = it.role == Role.User, text = it.text, display = it.display, tokens = it.tokens,
                    seconds = it.seconds, connectionName = it.connectionName, model = it.model, error = it.error,
                    tools = it.tools.map { t -> StoredTool(t.id, t.name, t.detail, t.anchor, t.output, t.isError) },
                )
            },
        )
        chatStore.lastChatId = id
        chats = chatStore.list()
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
        streaming = false
    }

    fun send(input: String) {
        val text = input.trim()
        val att = attachment
        if ((text.isEmpty() && att == null) || streaming) return
        val sel = selection ?: return
        val conn = connection(sel.connectionId) ?: return

        val full = buildString {
            append(text)
            if (att != null) {
                if (isNotEmpty()) append("\n\n")
                append("File: ${att.name}\n```\n${att.text}\n```")
            }
        }
        val shown = if (att == null) text else listOf(text, "[${att.name}]").filter { it.isNotEmpty() }.joinToString("\n")
        messages += UiMessage(nextId++, Role.User, full, shown)
        attachment = null

        val history = messages.filter { it.error == null && it.text.isNotEmpty() }.map { ChatTurn(it.role, it.text) }
        val reply = UiMessage(nextId++, Role.Assistant, "").also {
            it.connectionName = conn.name
            it.model = sel.model
        }
        messages += reply
        streaming = true
        saveChat()

        val started = System.nanoTime()
        val typer = Typewriter(reply)
        streamJob = viewModelScope.launch {
            val typing = launch { typer.run() }
            if (conn.plan != null) RuntimeService.acquire(getApplication(), str(R.string.notif_replying, conn.plan.cli))
            try {
                val stream = conn.plan?.let { plans.streamChat(it, sel.model, history, chatId, toolsEnabled) }
                    ?: client.streamChat(conn, sel.model, history)
                stream.collect { ev ->
                    when (ev) {
                        is StreamEvent.Text -> typer.push(ev.delta)
                        is StreamEvent.Usage -> reply.tokens = ev.outputTokens
                        is StreamEvent.ToolStart -> typer.pushTool(ev.id, ev.name, ev.input)
                        is StreamEvent.ToolEnd -> typer.endTool(ev.id, ev.output, ev.isError)
                    }
                }
                typer.finish()
                typing.join()
                if (conn.lastError != null) updateConnection(conn.id) { it.copy(lastError = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                typer.flush()
                reply.error = e.message ?: e.javaClass.simpleName
                if (e is ApiException && (e.code == 401 || e.code == 403)) {
                    updateConnection(conn.id) { it.copy(lastError = e.short) }
                }
            } finally {
                typer.flush()
                typing.cancel()
                if (conn.plan != null) RuntimeService.release(getApplication())
                reply.seconds = (System.nanoTime() - started) / 1e9
                reply.done = true
                streaming = false
                saveChat()
            }
        }
    }

    suspend fun test(draft: Connection): Result<ModelsResult> = try {
        Result.success(client.listModels(draft))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

    /** Saves the draft; fetches its model list unless [known] already came from a test. */
    fun save(draft: Connection, known: ModelsResult?) {
        val withModels = when {
            known != null -> draft.copy(models = known.models, lastError = null)
            draft.manualModelList.isNotEmpty() && draft.models.isEmpty() -> draft.copy(models = draft.manualModelList)
            else -> draft
        }
        val list = connections.toMutableList()
        val i = list.indexOfFirst { it.id == draft.id }
        if (i >= 0) list[i] = withModels else list += withModels
        persist(list)
        if (defaultId == null || connections.none { it.id == defaultId }) setDefault(draft.id)
        if (known == null) refreshModels(draft.id)
    }

    fun refreshModels(id: String) {
        val c = connection(id) ?: return
        c.plan?.let { plan ->
            viewModelScope.launch {
                val listed = runCatching { plans.listModels(plan) }.getOrDefault(plan.models)
                updateConnection(id) { it.copy(models = (listed + it.manualModelList).distinct()) }
            }
            return
        }
        viewModelScope.launch {
            test(c).onSuccess { r ->
                updateConnection(id) { it.copy(models = r.models.ifEmpty { it.manualModelList }, lastError = null) }
            }.onFailure { e ->
                val short = (e as? ApiException)?.short ?: "Couldn't load models"
                updateConnection(id) { it.copy(lastError = short, models = it.models.ifEmpty { it.manualModelList }) }
            }
        }
    }

    fun delete(id: String) {
        persist(connections.filterNot { it.id == id })
        if (defaultId == id) setDefault(connections.firstOrNull()?.id)
        if (storedSelection?.connectionId == id) {
            storedSelection = null
            store.selected = null
        }
    }

    // ── Subscriptions through the on-device CLI ─────────────────────────

    private val setups = mutableMapOf<Plan, PlanSetup>()
    fun planSetup(plan: Plan) = setups.getOrPut(plan) { PlanSetup(plan) }
    fun planConnection(plan: Plan) = connections.find { it.plan == plan }

    /** Downloads the runtime + CLI if needed, then runs the CLI's own sign-in. */
    fun startPlanSetup(plan: Plan) {
        val s = planSetup(plan)
        if (s.running) return
        s.log.clear(); s.url = null; s.deviceCode = null; s.error = null
        s.job = viewModelScope.launch {
            RuntimeService.acquire(getApplication(), str(R.string.notif_setting_up, plan.cli))
            try {
                s.step = PlanSetup.Step.Runtime
                runtime.ensureBase(s::addLog)
                s.step = PlanSetup.Step.Install
                plans.install(plan, s::addLog)
                s.step = PlanSetup.Step.SignIn
                if (!plans.isSignedIn(plan)) {
                    s.addLog("Starting ${plan.cli} sign-in…")
                    plans.login(
                        plan,
                        log = s::addLog,
                        onUrl = { url -> if (s.url == null || url.contains("oauth") || url.contains("device")) s.url = url },
                        onCode = { s.deviceCode = it },
                        onSession = { s.session = it },
                    )
                    s.session = null
                    if (!plans.isSignedIn(plan)) throw IllegalStateException("Sign-in didn't complete.")
                }
                val existing = planConnection(plan)
                val custom = existing?.manualModels ?: ""
                val conn = Connection(
                    id = plan.connectionId, name = plan.cli, format = app.relay.chat.data.ApiFormat.OpenAI,
                    baseUrl = "", apiKey = "", plan = plan, manualModels = custom,
                    models = (plans.listModels(plan) + Connection(id = "", name = "", format = app.relay.chat.data.ApiFormat.OpenAI, baseUrl = "", apiKey = "", manualModels = custom).manualModelList).distinct(),
                )
                persist(if (existing != null) connections.map { if (it.plan == plan) conn else it } else connections + conn)
                if (defaultId == null || connections.none { it.id == defaultId }) setDefault(conn.id)
                if (existing == null) select(ModelRef(conn.id, plan.models.first()))
                s.step = PlanSetup.Step.Done
            } catch (e: CancellationException) {
                s.step = PlanSetup.Step.Idle
                throw e
            } catch (e: Throwable) {
                s.error = e.message ?: e.javaClass.simpleName
                s.step = PlanSetup.Step.Failed
            } finally {
                RuntimeService.release(getApplication())
            }
        }
    }

    /** Extra model IDs the user typed for a plan (comma/newline separated). */
    fun setPlanCustomModels(plan: Plan, text: String) {
        val c = planConnection(plan) ?: return
        updateConnection(c.id) { it.copy(manualModels = text) }
        refreshModels(c.id)
    }

    fun updatePlanCli(plan: Plan) {
        val s = planSetup(plan)
        if (s.running || s.updating) return
        s.log.clear(); s.error = null
        s.updating = true
        viewModelScope.launch {
            RuntimeService.acquire(getApplication(), str(R.string.notif_updating, plan.cli))
            try {
                plans.update(plan, s::addLog)
                planConnection(plan)?.let { refreshModels(it.id) }
                s.addLog("${plan.cli} is up to date.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                s.error = e.message ?: e.javaClass.simpleName
            } finally {
                s.updating = false
                RuntimeService.release(getApplication())
            }
        }
    }

    fun cancelPlanSetup(plan: Plan) {
        planSetup(plan).job?.cancel()
    }

    /** Types into the running login (for "paste the code from the browser" prompts). */
    fun sendPlanCode(plan: Plan, code: String) = planSetup(plan).session?.send(code) ?: false

    fun signOutPlan(plan: Plan) {
        viewModelScope.launch {
            runCatching { plans.signOut(plan) }
            planConnection(plan)?.let { delete(it.id) }
            planSetup(plan).step = PlanSetup.Step.Idle
        }
    }

    fun setDefault(id: String?) {
        defaultId = id
        store.defaultConnectionId = id
    }

    private fun updateConnection(id: String, f: (Connection) -> Connection) {
        persist(connections.map { if (it.id == id) f(it) else it })
    }

    private fun persist(list: List<Connection>) {
        connections = list
        store.saveConnections(list)
    }
}
