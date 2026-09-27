package app.relay.chat.runtime

import app.relay.chat.data.Plan
import app.relay.chat.net.ApiException
import app.relay.chat.net.ChatTurn
import app.relay.chat.net.Role
import app.relay.chat.net.StreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs the official Claude Code / Codex CLIs inside [LinuxRuntime] and exposes them as a
 * chat backend. Sign-in is the CLI's own login flow; Relay never reads or stores the
 * resulting credentials — they stay in the CLI's config inside the rootfs.
 */
class PlanService(private val runtime: LinuxRuntime) {

    // NOFOLLOW_LINKS: the Claude installer makes ~/.local/bin/claude an absolute symlink
    // (/root/.local/share/claude/versions/…), which only resolves inside the rootfs.
    fun isInstalled(plan: Plan) = runtime.isBaseReady &&
        Files.exists(runtime.hostPath(plan.binPath).toPath(), LinkOption.NOFOLLOW_LINKS)

    suspend fun install(plan: Plan, log: (String) -> Unit) {
        if (isInstalled(plan)) return
        val arch = runtime.arch ?: throw IOException("Unsupported CPU")
        val cmd = when (plan) {
            // Anthropic's own installer; it detects musl (Alpine) and fetches the matching build.
            Plan.Claude -> "curl -fsSL https://claude.ai/install.sh | bash"
            Plan.Codex -> "set -e; cd /tmp; " +
                "curl -fL --retry 3 -o codex.tgz https://github.com/openai/codex/releases/latest/download/codex-$arch-unknown-linux-musl.tar.gz; " +
                "tar xzf codex.tgz; install -m 755 codex-$arch-unknown-linux-musl /usr/local/bin/codex; " +
                "rm -f codex.tgz codex-$arch-unknown-linux-musl; codex --version"
        }
        log("Installing ${plan.cli}…")
        val code = runtime.run(cmd, log)
        if (code != 0 || !isInstalled(plan)) throw IOException("${plan.cli} install failed (exit $code).")
    }

    /** Updates the CLI to its latest release (the in-sandbox auto-updater is off). */
    suspend fun update(plan: Plan, log: (String) -> Unit) {
        when (plan) {
            Plan.Claude -> {
                val code = runtime.run("claude update", log)
                if (code != 0) throw IOException("claude update failed (exit $code).")
            }
            Plan.Codex -> {
                runtime.hostPath(plan.binPath).delete()
                install(plan, log)
            }
        }
    }

    suspend fun isSignedIn(plan: Plan): Boolean = withContext(Dispatchers.IO) {
        if (!isInstalled(plan)) return@withContext false
        val out = StringBuilder()
        val code = runtime.run(
            when (plan) {
                Plan.Claude -> "claude auth status --json"
                Plan.Codex -> "codex login status"
            },
            { out.appendLine(it) },
        )
        when (plan) {
            Plan.Claude -> Regex(""""loggedIn"\s*:\s*true""").containsMatchIn(out)
            Plan.Codex -> code == 0 && out.contains("Logged in", ignoreCase = true)
        }
    }

    /**
     * Models the plan offers. Codex publishes its catalog (`codex debug models`: the entries
     * it lists in its own picker, by priority); Claude Code has no such command, so it's the
     * curated [Plan.models].
     */
    suspend fun listModels(plan: Plan): List<String> = withContext(Dispatchers.IO) {
        if (plan != Plan.Codex || !isInstalled(plan)) return@withContext plan.models
        val p = runtime.start(listOf("codex", "debug", "models"))
        val json = try {
            p.inputStream.bufferedReader().readText().also { p.waitFor() }
        } finally {
            p.destroy()
        }
        val live = runCatching {
            val arr = JSONObject(json).getJSONArray("models")
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .filter { it.optString("visibility") == "list" }
                .sortedBy { it.optInt("priority", Int.MAX_VALUE) }
                .map { it.getString("slug") }
        }.getOrDefault(emptyList())
        live.ifEmpty { plan.models }
    }

    suspend fun signOut(plan: Plan) {
        if (!isInstalled(plan)) return
        runtime.run(if (plan == Plan.Claude) "claude auth logout" else "codex logout", {})
    }

    /** A running `login` process; [send] types into it (for "paste this code" prompts). */
    class LoginSession internal constructor(private val stdin: OutputStream) {
        fun send(text: String) = runCatching {
            stdin.write((text.trim() + "\n").toByteArray())
            stdin.flush()
        }.isSuccess
    }

    /**
     * Runs the CLI's login. Browser launches (via the xdg-open shim) and URLs printed to the
     * terminal are reported through [onUrl]; Codex's device code through [onCode].
     */
    suspend fun login(
        plan: Plan,
        log: (String) -> Unit,
        onUrl: (String) -> Unit,
        onCode: (String) -> Unit,
        onSession: (LoginSession) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val openFile = File(runtime.tmp, "relay-open-url").apply { delete() }
        val argv = when (plan) {
            Plan.Claude -> listOf("claude", "auth", "login", "--claudeai")
            Plan.Codex -> listOf("codex", "login", "--device-auth")
        }
        val p = runtime.start(argv, mergeStderr = true)
        onSession(LoginSession(p.outputStream))
        val seen = mutableSetOf<String>()
        fun report(url: String) {
            val clean = url.trimEnd('.', ',', ')', '"', '\'')
            if (seen.add(clean)) onUrl(clean)
        }
        val watcher = launch {
            while (isActive) {
                if (openFile.exists()) openFile.readLines().filter { it.isNotBlank() }.forEach(::report)
                delay(400)
            }
        }
        try {
            p.inputStream.forEachLine { raw ->
                val line = LinuxRuntime.stripAnsi(raw)
                if (line.isBlank()) return@forEachLine
                log(line)
                URL.findAll(line).forEach { report(it.value) }
                if (plan == Plan.Codex) DEVICE_CODE.find(line.trim())?.let { onCode(it.value) }
            }
            p.waitFor()
        } finally {
            watcher.cancel()
            p.destroy()
        }
    }

    // ── Chat ───────────────────────────────────────────────────────────

    /** Conversation (hash of turns so far, incl. the reply) → CLI session to resume. */
    private val sessions = ConcurrentHashMap<String, String>()

    private fun key(turns: List<ChatTurn>): String {
        val md = MessageDigest.getInstance("SHA-256")
        turns.forEach { md.update("${it.role}:${it.text}\u0000".toByteArray()) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun transcript(turns: List<ChatTurn>): String {
        if (turns.size == 1) return turns[0].text
        return buildString {
            append("Here is our conversation so far:\n\n")
            turns.dropLast(1).forEach {
                append(if (it.role == Role.User) "<user>\n" else "<assistant>\n")
                append(it.text)
                append(if (it.role == Role.User) "\n</user>\n\n" else "\n</assistant>\n\n")
            }
            append("Now reply to my latest message:\n\n")
            append(turns.last().text)
        }
    }

    /** Python for the tools (older installs predate it in the base packages). */
    private suspend fun ensureToolDeps(emit: (StreamEvent) -> Unit) {
        if (Files.exists(runtime.hostPath("/usr/bin/python3").toPath(), LinkOption.NOFOLLOW_LINKS)) return
        emit(StreamEvent.ToolStart("setup", "Setup", "apk add python3 py3-pip"))
        val out = StringBuilder()
        val code = runtime.run("apk add --no-progress python3 py3-pip", { out.appendLine(it) })
        emit(StreamEvent.ToolEnd("setup", out.toString().takeLast(1500), code != 0))
    }

    /**
     * [chatKey] picks the working directory (one per chat, so files a tool writes stay with
     * that conversation). With [tools], Claude Code gets Bash/web/file tools pre-approved and
     * Codex runs unsandboxed with live web search — Relay's proot sandbox is the boundary.
     */
    fun streamChat(plan: Plan, model: String, turns: List<ChatTurn>, chatKey: String?, tools: Boolean): Flow<StreamEvent> = callbackFlow {
        var process: Process? = null
        val job = launch(Dispatchers.IO) {
            try {
                if (tools) ensureToolDeps { trySendBlocking(it) }
                val resumeId = if (turns.size > 1) sessions[key(turns.dropLast(1))] else null
                val prompt = if (resumeId != null) turns.last().text else transcript(turns)
                val argv = when (plan) {
                    Plan.Claude -> buildList {
                        addAll(listOf("claude", "-p", "--output-format", "stream-json", "--verbose", "--include-partial-messages"))
                        // A chat, not a coding agent: chosen tools only, no project settings or MCP.
                        if (tools) {
                            add("--tools"); addAll(CLAUDE_TOOLS)
                            add("--allowedTools"); addAll(CLAUDE_TOOLS)
                        } else addAll(listOf("--tools", ""))
                        addAll(listOf("--setting-sources", "", "--strict-mcp-config"))
                        addAll(listOf("--system-prompt", if (tools) TOOLS_SYSTEM_PROMPT else CHAT_SYSTEM_PROMPT))
                        if (model != "default") addAll(listOf("--model", model))
                        if (resumeId != null) addAll(listOf("--resume", resumeId))
                    }
                    Plan.Codex -> buildList {
                        addAll(listOf("codex", "exec"))
                        if (resumeId != null) addAll(listOf("resume", resumeId))
                        addAll(listOf("--json", "--skip-git-repo-check"))
                        if (tools) {
                            // Codex's own sandbox (Landlock/bwrap) can't nest inside proot.
                            add("--dangerously-bypass-approvals-and-sandbox")
                            addAll(listOf("-c", "web_search=\"live\""))
                        } else {
                            if (resumeId == null) addAll(listOf("--sandbox", "read-only"))
                            addAll(listOf("-c", "web_search=\"disabled\""))
                        }
                        if (model != "default") addAll(listOf("-m", model))
                        add("-")
                    }
                }
                val p = runtime.start(argv, workdir = "/root/chats/${chatKey ?: "scratch"}")
                process = p
                p.outputStream.use { it.write(prompt.toByteArray()) }
                val stderr = StringBuilder()
                val errReader = launch { p.errorStream.bufferedReader().forEachLine { stderr.appendLine(it) } }

                val reply = StringBuilder()
                var sessionId: String? = null
                var failure: String? = null
                val emittedPerItem = HashMap<String, Int>()
                val startedTools = HashSet<String>()

                p.inputStream.bufferedReader().forEachLine { line ->
                    val o = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachLine
                    when (plan) {
                        Plan.Claude -> when (o.optString("type")) {
                            "system" -> if (o.optString("subtype") == "init") sessionId = o.optString("session_id").ifEmpty { null }
                            "stream_event" -> {
                                val ev = o.optJSONObject("event") ?: return@forEachLine
                                when (ev.optString("type")) {
                                    "content_block_delta" -> ev.optJSONObject("delta")?.takeIf { it.optString("type") == "text_delta" }
                                        ?.optString("text")?.takeIf { it.isNotEmpty() }?.let {
                                            reply.append(it); trySendBlocking(StreamEvent.Text(it))
                                        }
                                    "message_delta" -> ev.optJSONObject("usage")?.optInt("output_tokens", -1)
                                        ?.takeIf { it >= 0 }?.let { trySendBlocking(StreamEvent.Usage(it)) }
                                }
                            }
                            "assistant" -> o.optJSONObject("message")?.optJSONArray("content")?.let { blocks ->
                                for (i in 0 until blocks.length()) {
                                    val b = blocks.optJSONObject(i) ?: continue
                                    if (b.optString("type") == "tool_use") {
                                        val name = b.optString("name")
                                        trySendBlocking(StreamEvent.ToolStart(b.optString("id"), name, summarize(name, b.optJSONObject("input"))))
                                    }
                                }
                            }
                            "user" -> o.optJSONObject("message")?.optJSONArray("content")?.let { blocks ->
                                for (i in 0 until blocks.length()) {
                                    val b = blocks.optJSONObject(i) ?: continue
                                    if (b.optString("type") == "tool_result") {
                                        trySendBlocking(StreamEvent.ToolEnd(b.optString("tool_use_id"), resultText(b.opt("content")), b.optBoolean("is_error")))
                                    }
                                }
                            }
                            "result" -> {
                                if (o.optBoolean("is_error") || o.optString("subtype").startsWith("error")) {
                                    failure = o.optString("result").ifBlank { o.optString("subtype") }
                                }
                                o.optJSONObject("usage")?.optInt("output_tokens", -1)?.takeIf { it >= 0 }
                                    ?.let { trySendBlocking(StreamEvent.Usage(it)) }
                            }
                        }
                        Plan.Codex -> when (o.optString("type")) {
                            "thread.started" -> sessionId = o.optString("thread_id").ifEmpty { null }
                            "item.started", "item.updated", "item.completed" -> {
                                val item = o.optJSONObject("item") ?: return@forEachLine
                                val completed = o.optString("type") == "item.completed"
                                codexTool(item)?.let { (name, input) ->
                                    val id = item.optString("id")
                                    if (startedTools.add(id)) trySendBlocking(StreamEvent.ToolStart(id, name, input))
                                    if (completed) {
                                        val out = item.optString("aggregated_output").ifEmpty { resultText(item.opt("result")) }
                                        val failed = item.optString("status") == "failed" ||
                                            (item.has("exit_code") && !item.isNull("exit_code") && item.optInt("exit_code") != 0)
                                        trySendBlocking(StreamEvent.ToolEnd(id, out, failed))
                                    }
                                    return@forEachLine
                                }
                                if (item.optString("type") != "agent_message") return@forEachLine
                                val text = item.optString("text")
                                val id = item.optString("id")
                                val done = emittedPerItem[id] ?: 0
                                if (text.length > done) {
                                    // Separate consecutive messages with a blank line.
                                    val prefix = if (done == 0 && reply.isNotEmpty()) "\n\n" else ""
                                    val delta = prefix + text.substring(done)
                                    reply.append(delta)
                                    emittedPerItem[id] = text.length
                                    trySendBlocking(StreamEvent.Text(delta))
                                }
                            }
                            "turn.completed" -> o.optJSONObject("usage")?.optInt("output_tokens", -1)
                                ?.takeIf { it >= 0 }?.let { trySendBlocking(StreamEvent.Usage(it)) }
                            "turn.failed" -> failure = o.optJSONObject("error")?.optString("message") ?: "Turn failed"
                            "error" -> failure = o.optString("message").ifBlank { "Codex error" }
                        }
                    }
                }
                val code = p.waitFor()
                errReader.join()

                if (failure == null && code != 0 && reply.isEmpty()) {
                    failure = stderr.lines().map { LinuxRuntime.stripAnsi(it).trim() }.lastOrNull { it.isNotEmpty() }
                        ?: "${plan.cli} exited with code $code"
                }
                failure?.let { msg ->
                    val signedOut = Regex("log ?in|logged in|authenticat|unauthori|401|token", RegexOption.IGNORE_CASE).containsMatchIn(msg)
                    throw ApiException(if (signedOut) "Signed out" else "${plan.cli} error", if (signedOut) 401 else null, msg.take(300))
                }
                sessionId?.let { sessions[key(turns + ChatTurn(Role.Assistant, reply.toString()))] = it }
                close()
            } catch (e: Throwable) {
                close(e)
            }
        }
        awaitClose {
            process?.destroy()
            job.cancel()
        }
    }

    /** One-line summary of a Claude Code tool call's input. */
    private fun summarize(name: String, input: JSONObject?): String {
        if (input == null) return ""
        val key = when (name) {
            "Bash" -> "command"
            "WebSearch" -> "query"
            "WebFetch" -> "url"
            "Read", "Write", "Edit", "MultiEdit" -> "file_path"
            "Glob", "Grep" -> "pattern"
            else -> null
        }
        return key?.let { input.optString(it) }?.takeIf { it.isNotEmpty() } ?: input.toString().take(300)
    }

    /** Tool-result content as text: a string, or a list of {type:text,text} blocks. */
    private fun resultText(content: Any?): String = when (content) {
        null, JSONObject.NULL -> ""
        is String -> content
        is org.json.JSONArray -> (0 until content.length()).joinToString("\n") { i ->
            content.optJSONObject(i)?.let { it.optString("text").ifEmpty { it.toString() } } ?: content.optString(i)
        }
        is JSONObject -> content.optJSONArray("content")?.let { resultText(it) } ?: content.toString()
        else -> content.toString()
    }.take(4000)

    /** Maps a Codex exec item to (tool name, input summary), or null if it isn't a tool call. */
    private fun codexTool(item: JSONObject): Pair<String, String>? = when (item.optString("type")) {
        "command_execution" -> "Bash" to cleanCommand(item.optString("command"))
        "web_search" -> "WebSearch" to item.optString("query")
        "mcp_tool_call" -> "mcp:${item.optString("server")}.${item.optString("tool")}" to (item.optJSONObject("arguments")?.toString()?.take(300) ?: "")
        "file_change" -> "Edit" to (item.optJSONArray("changes")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("path") }.joinToString(", ")
        } ?: "")
        else -> null
    }

    /** `/bin/bash -lc 'python3 x.py'` → `python3 x.py`. */
    private fun cleanCommand(cmd: String): String {
        val m = Regex("""^(?:/usr)?(?:/bin/)?(?:ba)?sh\s+-l?c\s+(['"])([\s\S]*)\1$""").find(cmd.trim())
        return m?.groupValues?.get(2) ?: cmd
    }

    companion object {
        private val CLAUDE_TOOLS = listOf("Bash", "WebSearch", "WebFetch", "Read", "Write", "Edit", "Glob", "Grep")
        const val TOOLS_SYSTEM_PROMPT =
            "You are a helpful, friendly assistant chatting with the user in Relay, a mobile chat app. " +
                "You have tools: web search and web fetch for current information, and a Bash shell in a private " +
                "Alpine Linux sandbox on the user's phone with Python 3 (pip install needs --break-system-packages). " +
                "Use them when they genuinely help — calculations, running code, checking current facts — not for " +
                "things you already know. Files you create stay in this chat's working directory. " +
                "Answer conversationally and concisely. Use Markdown; put code in fenced code blocks."
        private val URL = Regex("""https?://[^\s"'<>]+""")
        private val DEVICE_CODE = Regex("""^[A-Z0-9]{4,5}-[A-Z0-9]{4,6}$""")
        const val CHAT_SYSTEM_PROMPT =
            "You are a helpful, friendly assistant chatting with the user in Relay, a mobile chat app. " +
                "Answer conversationally and concisely. Use Markdown; put code in fenced code blocks."
    }
}
