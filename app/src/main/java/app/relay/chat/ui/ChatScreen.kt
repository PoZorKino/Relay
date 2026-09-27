package app.relay.chat.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.relay.chat.AppViewModel
import app.relay.chat.Attachment
import app.relay.chat.UiMessage
import app.relay.chat.R
import app.relay.chat.plural
import app.relay.chat.localizeError
import android.content.res.Resources
import androidx.compose.ui.res.stringResource
import app.relay.chat.ToolCall
import androidx.compose.ui.graphics.graphicsLayer
import app.relay.chat.data.ApiFormat
import app.relay.chat.data.Connection
import app.relay.chat.data.Plan
import app.relay.chat.net.Role as ChatRole
import app.relay.chat.ui.theme.Relay
import app.relay.chat.ui.theme.ThemeId
import java.util.Locale

/** "Home Lab" → "home-lab" (Terminal theme naming). */
fun slug(name: String) = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "conn" }

/** "qwen2.5-coder-32b" → "qwen" (Terminal theme prompt label). */
private fun shortModel(model: String?) = model?.takeWhile { it.isLetter() }?.lowercase()?.ifEmpty { null } ?: "ai"

/** The CLI invocation a plan chat runs. */
private fun planCommand(plan: Plan, model: String?) = when (plan) {
    Plan.Claude -> "claude -p" + (model?.let { " --model $it" } ?: "")
    Plan.Codex -> "codex exec" + (model?.takeIf { it != "default" }?.let { " -m $it" } ?: "")
}

/** What the endpoint line under the header shows: the URL, or the CLI for a plan. */
private fun endpointLabel(res: Resources, c: Connection, model: String?) =
    c.plan?.let { res.getString(R.string.on_this_phone, planCommand(it, model)) } ?: c.baseUrl

private fun chatPath(c: Connection, model: String?) = c.plan?.let { "exec ${planCommand(it, model)}" } ?: "POST " + c.baseUrl.trimEnd('/') + when (c.format) {
    ApiFormat.OpenAI -> "/chat/completions"
    ApiFormat.Anthropic -> "/messages"
    ApiFormat.Gemini -> "/models/${model ?: "{model}"}:streamGenerateContent"
}

@Composable
fun ChatScreen(
    vm: AppViewModel,
    onMenu: () -> Unit,
    onPickModel: () -> Unit,
    onAddConnection: () -> Unit,
    toast: (String) -> Unit,
) {
    val conn = vm.selectedConnection
    val sel = vm.selection

    Column(Modifier.fillMaxSize().background(Relay.Ground)) {
        Header(
            model = sel?.model ?: stringResource(if (conn == null) R.string.no_model else R.string.choose_model),
            connName = conn?.name,
            isPlan = conn?.plan != null,
            onMenu = onMenu,
            onPill = if (conn == null) onAddConnection else onPickModel,
            onNewChat = { vm.newChat() },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (conn == null) EmptyState(onAddConnection)
            else MessageList(vm, conn, sel?.model, onPickModel)
        }

        val placeholder = when {
            conn == null -> stringResource(if (Relay.isTerminal) R.string.term_message_empty else R.string.message_empty)
            Relay.isTerminal -> stringResource(R.string.term_message_to, slug(conn.name))
            else -> stringResource(R.string.message_to, conn.name)
        }
        Composer(vm, placeholder, enabled = sel != null, toast = toast)
    }
}

// ── Header ─────────────────────────────────────────────────────────────

@Composable
private fun Header(model: String, connName: String?, isPlan: Boolean, onMenu: () -> Unit, onPill: () -> Unit, onNewChat: () -> Unit) {
    val theme = Relay.theme
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (theme) {
            ThemeId.Lilac -> IconBtn(RelayIcons.Menu, stringResource(R.string.menu), onMenu, iconSize = 20.dp, background = Relay.Surface, shape = CircleShape)
            ThemeId.Terminal -> IconBtn(RelayIcons.Menu, stringResource(R.string.menu), onMenu, iconSize = 20.dp, border = Relay.Line)
            else -> IconBtn(RelayIcons.Menu, stringResource(R.string.menu), onMenu)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            ModelPill(model, connName, isPlan, onPill)
        }
        when (theme) {
            ThemeId.Lilac -> IconBtn(RelayIcons.NewChat, stringResource(R.string.new_chat), onNewChat, iconSize = 20.dp, background = Relay.Surface, shape = CircleShape)
            ThemeId.Terminal -> IconBtn(RelayIcons.Plus, stringResource(R.string.new_chat), onNewChat, iconSize = 20.dp, border = Relay.Line)
            else -> IconBtn(RelayIcons.NewChat, stringResource(R.string.new_chat), onNewChat)
        }
    }
    ThemeRule()
}

/** Header/footer rule: Classic hairline, Midnight darker hairline, Terminal dashed, Lilac none. */
@Composable
private fun ThemeRule() {
    when (Relay.theme) {
        ThemeId.Lilac -> Unit
        ThemeId.Terminal -> DashedDivider()
        ThemeId.Midnight -> HorizontalDivider(thickness = 1.dp, color = Relay.RowLine)
        ThemeId.Classic -> HorizontalDivider(thickness = 1.dp, color = Relay.Line)
    }
}

@Composable
private fun ModelPill(model: String, connName: String?, isPlan: Boolean, onClick: () -> Unit) {
    if (Relay.isTerminal) {
        val shape = Relay.shape(4.dp)
        Column(
            Modifier
                .clip(shape)
                .border(1.dp, Relay.Line, shape)
                .clickable(role = Role.Button, onClickLabel = "Choose a model", onClick = onClick)
                .heightIn(min = 44.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$model ▾", style = ts(13f, 700, lineHeight = 1.3f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                connName?.let { stringResource(if (isPlan) R.string.term_pill_plan else R.string.term_pill_key, slug(it)) }
                    ?: stringResource(R.string.term_pill_none),
                style = ts(10.5f, 400, Relay.Muted, lineHeight = 1.3f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    val lilac = Relay.isLilac
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .then(if (lilac) Modifier.lilacShadow(shape, 4.dp) else Modifier)
            .clip(shape)
            .background(Relay.Surface)
            .then(if (lilac) Modifier else Modifier.border(1.dp, Relay.Line, shape))
            .clickable(role = Role.Button, onClickLabel = "Choose a model", onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = if (lilac) 14.dp else 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!lilac) {
            val glow = Relay.theme == ThemeId.Midnight
            val accent = Relay.Accent
            Box(
                Modifier
                    .size(8.dp)
                    .drawBehind {
                        if (glow) drawCircle(
                            Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0f)), radius = 8.dp.toPx()),
                            radius = 8.dp.toPx(),
                        )
                    }
                    .clip(CircleShape)
                    .background(accent)
            )
        }
        Column(Modifier.weight(1f, fill = false), horizontalAlignment = if (lilac) Alignment.CenterHorizontally else Alignment.Start) {
            Text(model, style = ts(14f, if (lilac) 700 else 600, lineHeight = 1.15f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                connName?.let { stringResource(if (isPlan) R.string.pill_plan else R.string.pill_api, it) }
                    ?: stringResource(R.string.pill_add),
                style = ts(11f, 400, Relay.Muted, lineHeight = 1.15f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(RelayIcons.ChevronDown, null, Modifier.size(14.dp), tint = Relay.Ink)
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (Relay.isTerminal) {
            Text("~/relay", style = ts(22f, 700, Relay.Accent))
            Text(
                stringResource(R.string.term_empty_body),
                style = ts(12.5f, 400, Relay.Muted, lineHeight = 1.5f), textAlign = TextAlign.Center,
            )
        } else {
            Text(stringResource(R.string.empty_title), style = ts(24f, 700, family = Relay.Display, letterSpacing = (-0.01).em))
            Text(
                stringResource(R.string.empty_body),
                style = ts(14f, 400, Relay.Muted, lineHeight = 1.45f),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(6.dp)
        AddButton(onAdd)
    }
}

// ── Messages ───────────────────────────────────────────────────────────

@Composable
private fun MessageList(vm: AppViewModel, conn: Connection, model: String?, onPickModel: () -> Unit) {
    val state = rememberLazyListState()
    val msgs = vm.messages
    // Newest first + reverseLayout keeps the view pinned to the bottom while text streams in.
    val reversed = msgs.asReversed()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) state.animateScrollToItem(0) }

    val theme = Relay.theme
    val padding = when (theme) {
        ThemeId.Lilac -> PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)
        ThemeId.Terminal -> PaddingValues(horizontal = 16.dp, vertical = 18.dp)
        else -> PaddingValues(horizontal = 16.dp, vertical = 20.dp)
    }
    val gap = if (theme == ThemeId.Classic || theme == ThemeId.Midnight) 18.dp else 16.dp

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val contentWidth = maxWidth - 32.dp
        LazyColumn(
            state = state,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(gap, Alignment.Top),
        ) {
            items(reversed, key = { it.id }) { m ->
                when {
                    theme == ThemeId.Terminal -> TerminalMessage(m)
                    m.role == ChatRole.User -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        UserBubble(m, Modifier.widthIn(max = contentWidth * 0.78f))
                    }
                    else -> AssistantMessage(m, contentWidth * 0.92f)
                }
            }
            item(key = "endpoint") {
                Column(
                    horizontalAlignment = if (theme == ThemeId.Terminal) Alignment.Start else Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (theme == ThemeId.Terminal) {
                        Text("[conn] ${chatPath(conn, model)}", style = ts(11.5f, 400, Relay.Muted, lineHeight = 1.55f))
                    } else {
                        val lilac = theme == ThemeId.Lilac
                        Text(
                            "→ ${endpointLabel(LocalContext.current.resources, conn, model)}",
                            Modifier
                                .clip(if (lilac) RoundedCornerShape(50) else RoundedCornerShape(8.dp))
                                .background(if (lilac) Relay.InlineCode else Relay.Chip)
                                .padding(horizontal = if (lilac) 12.dp else 10.dp, vertical = 6.dp),
                            style = ts(12f, 400, Relay.Muted, family = Relay.Mono),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (model == null) {
                        Spacer(14.dp)
                        Text(stringResource(R.string.no_models_loaded_from, conn.name), style = ts(14f, 400, Relay.Muted))
                        Text(
                            stringResource(R.string.choose_model),
                            Modifier.clickable(role = Role.Button, onClick = onPickModel).padding(12.dp),
                            style = ts(14f, 600, Relay.AccentText),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserBubble(m: UiMessage, modifier: Modifier) {
    val lilac = Relay.isLilac
    val shape = if (lilac) RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp) else RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    SelectionContainer(modifier) {
        Text(
            m.display,
            Modifier
                .clip(shape)
                .background(Relay.Accent)
                .padding(horizontal = if (lilac) 16.dp else 14.dp, vertical = if (lilac) 13.dp else 12.dp),
            style = ts(15f, if (Relay.theme == ThemeId.Midnight) 500 else 400, Relay.OnAccent, lineHeight = 1.45f),
        )
    }
}

@Composable
private fun AssistantMessage(m: UiMessage, maxWidth: Dp) {
    val lilac = Relay.isLilac
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val content = @Composable {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (m.text.isEmpty() && m.tools.isEmpty() && !m.done) TypingDots()
                else if (m.text.isNotEmpty() || m.tools.isNotEmpty()) MessageBody(m, 15f, 1.5f)
                m.error?.let { ErrorNote(it) }
                if (!lilac) MetaRow(m)
            }
        }
        if (lilac && (m.text.isNotEmpty() || m.tools.isNotEmpty() || !m.done)) {
            // Lilac wraps replies in a white bubble with a tucked bottom-left corner.
            Box(
                Modifier
                    .widthIn(max = maxWidth)
                    .clip(RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp))
                    .background(Relay.Surface)
                    .padding(horizontal = 16.dp, vertical = if (m.text.isEmpty()) 12.dp else 14.dp)
            ) { content() }
        } else {
            Box(Modifier.widthIn(max = maxWidth)) { content() }
        }
        if (lilac) Box(Modifier.padding(start = 6.dp)) { MetaRow(m) }
    }
}

@Composable
private fun MetaRow(m: UiMessage) {
    if (!m.done || m.error != null) return
    val res = LocalContext.current.resources
    val parts = buildList {
        m.tokens?.let { add(res.plural(R.plurals.tokens, it)) }
        m.seconds?.let { add(res.getString(R.string.seconds, it)) }
        m.connectionName?.let { add(it) }
    }
    if (parts.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { i, p ->
            if (i > 0) Text("·", style = ts(12f, 400, Relay.Muted))
            Text(p, style = ts(12f, 400, Relay.Muted), maxLines = 1)
        }
    }
}

@Composable
private fun TerminalMessage(m: UiMessage) {
    val body = ts(13.5f, 400, lineHeight = 1.55f)
    if (m.role == ChatRole.User) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("you>", style = ts(13.5f, 700, Relay.Accent, lineHeight = 1.55f))
            SelectionContainer { Text(m.display, style = body) }
        }
        return
    }
    val label = "${shortModel(m.model)}>"
    if (m.text.isEmpty() && m.tools.isEmpty() && !m.done) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = ts(13.5f, 700, Relay.Amber, lineHeight = 1.55f))
            BlockCursor()
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = ts(13.5f, 700, Relay.Amber, lineHeight = 1.55f))
        if (m.text.isNotEmpty() || m.tools.isNotEmpty()) MessageBody(m, 13.5f, 1.55f)
        m.error?.let { ErrorNote(it) }
        if (m.done && m.error == null) {
            val parts = buildList {
                m.tokens?.let { add("$it tok") }
                m.seconds?.let { add(String.format(Locale.US, "%.1f s", it)) }
                m.connectionName?.let { add(slug(it)) }
            }
            if (parts.isNotEmpty()) Text("-- ${parts.joinToString(" · ")} --", style = ts(11f, 400, Relay.Muted))
        }
    }
}

private const val FADE_NS = 350_000_000L

/**
 * Frame clock for the fade-in: ticks every frame while [m] is streaming or still has
 * characters mid-fade, then settles (returning 0 = "nothing fading").
 */
@Composable
private fun fadeClock(m: UiMessage): Long {
    val now by produceState(0L, m, m.done) {
        while (true) {
            withFrameNanos { }
            val t = System.nanoTime()
            value = t
            if (m.done && t - m.lastRevealAt > FADE_NS) {
                value = 0L
                break
            }
        }
    }
    return now
}

/** Number of trailing characters of [m] that are still fading in at time [now]. */
private fun fadingTail(m: UiMessage, now: Long): Int {
    if (now == 0L) return 0
    val len = m.text.length
    var i = len
    while (i > 0 && now - m.revealedAt(i - 1) < FADE_NS) i--
    return len - i
}

/**
 * Applies the fade to the last [tail] characters of [s]: each gets alpha by its age.
 * Alphas are bucketed so a long tail is only a handful of spans.
 */
private fun withFade(s: AnnotatedString, m: UiMessage, tail: Int, now: Long, color: Color): AnnotatedString {
    if (tail <= 0 || s.isEmpty()) return s
    val n = minOf(tail, s.length)
    val start = s.length - n
    val textEnd = m.text.length
    return buildAnnotatedString {
        append(s)
        var runStart = start
        var runBucket = -1
        for (k in start until s.length) {
            // Map the rendered char to its source char by distance from the end.
            val src = textEnd - (s.length - k)
            val age = (now - m.revealedAt(src)).coerceAtLeast(0)
            val bucket = ((age * 10) / FADE_NS).toInt().coerceIn(0, 10)
            if (bucket != runBucket) {
                if (runBucket in 0..9) addStyle(SpanStyle(color = color.copy(alpha = (runBucket + 0.5f) / 10f)), runStart, k)
                runStart = k
                runBucket = bucket
            }
        }
        if (runBucket in 0..9) addStyle(SpanStyle(color = color.copy(alpha = (runBucket + 0.5f) / 10f)), runStart, s.length)
    }
}

/** Reply text with its tool-call cards placed where they happened. */
@Composable
private fun MessageBody(m: UiMessage, size: Float, lineHeight: Float) {
    val now = fadeClock(m)
    val tail = fadingTail(m, now)
    val text = m.text
    Column(verticalArrangement = Arrangement.spacedBy(if (Relay.isTerminal) 8.dp else 10.dp)) {
        var start = 0
        m.tools.sortedBy { it.anchor }.forEach { t ->
            val end = t.anchor.coerceIn(start, text.length)
            text.substring(start, end).takeIf { it.isNotBlank() }?.let { TextSegment(it, m, 0, now, size, lineHeight) }
            ToolCard(t)
            start = end
        }
        text.substring(start).takeIf { it.isNotBlank() }?.let { TextSegment(it, m, tail, now, size, lineHeight) }
    }
}

@Composable
private fun TextSegment(segment: String, m: UiMessage, tail: Int, now: Long, size: Float, lineHeight: Float) {
    val blocks = parseBlocks(segment)
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(if (Relay.isTerminal) 8.dp else 10.dp)) {
            blocks.forEachIndexed { i, b ->
                // New text only ever lands in the last block.
                val t = if (i == blocks.lastIndex) tail else 0
                when (b) {
                    is Block.Code -> CodeBlock(b.code, m, t, now)
                    is Block.Para -> Text(
                        withFade(paraMarkdown(b.text), m, t, now, Relay.Ink),
                        style = ts(size, 400, lineHeight = lineHeight),
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorNote(raw: String) {
    val err = localizeError(LocalContext.current.resources, raw)
    Row(
        Modifier.clip(Relay.shape(12.dp)).background(Relay.ErrorTint).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(RelayIcons.Alert, null, Modifier.size(18.dp), tint = Relay.Error)
        Text(err, style = ts(14f, 500, Relay.Error, lineHeight = 1.4f))
    }
}

@Composable
private fun CodeBlock(code: String, m: UiMessage, tail: Int, now: Long) {
    val terminal = Relay.isTerminal
    val shape = Relay.shape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Relay.CodeBg)
            .then(Relay.CodeBorder?.let { Modifier.border(1.dp, it, shape) } ?: Modifier)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = if (terminal) 12.dp else 14.dp, vertical = if (terminal) 10.dp else 12.dp)
    ) {
        Text(
            withFade(AnnotatedString(code), m, tail, now, Relay.CodeText),
            style = ts(if (terminal) 13.5f else 12.5f, 400, Relay.CodeText, family = Relay.Mono, lineHeight = if (terminal) 1.55f else 1.5f),
            softWrap = false,
        )
    }
}

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransition(label = "typing")
    val phase by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "phase")
    Row(Modifier.padding(vertical = if (Relay.isLilac) 0.dp else 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Static frame matches the design (1 / .6 / .3); the lead dot walks left→right.
        val alphas = listOf(1f, 0.6f, 0.3f)
        val lead = phase.toInt().coerceIn(0, 2)
        repeat(3) { i ->
            Box(
                Modifier
                    .size(7.dp)
                    .alpha(alphas[(i - lead + 3) % 3])
                    .clip(CircleShape)
                    .background(Relay.Accent)
            )
        }
    }
}

/** Terminal's blinking block cursor (9×17) standing in for the typing dots. */
@Composable
private fun BlockCursor() {
    val t = rememberInfiniteTransition(label = "cursor")
    val phase by t.animateFloat(0f, 2f, infiniteRepeatable(tween(1000), RepeatMode.Restart), label = "blink")
    Box(
        Modifier
            .size(width = 9.dp, height = 17.dp)
            .alpha(if (phase < 1f) 1f else 0f)
            .background(Relay.Accent)
    )
}

// ── Tool calls ─────────────────────────────────────────────────────────

private fun toolTitle(res: Resources, t: ToolCall): String {
    val title = when (t.name) {
        "Bash" -> res.getString(
            if (Regex("""\bpython3?\b|\bpip\b""").containsMatchIn(t.detail)) R.string.tool_ran_python else R.string.tool_ran_command
        )
        "WebSearch" -> res.getString(R.string.tool_web_search)
        "WebFetch" -> res.getString(R.string.tool_web_fetch)
        "Read" -> res.getString(R.string.tool_read)
        "Write" -> res.getString(R.string.tool_write)
        "Edit", "MultiEdit" -> res.getString(R.string.tool_edit)
        "Glob", "Grep" -> res.getString(R.string.tool_search_files)
        "Setup" -> res.getString(R.string.tool_setup)
        else -> res.getString(R.string.tool_used, t.name.removePrefix("mcp:"))
    }
    return if (Relay.isTerminal) title.lowercase() else title
}

private fun toolIcon(name: String) = when (name) {
    "Bash" -> RelayIcons.Code
    "WebSearch" -> RelayIcons.Search
    "WebFetch" -> RelayIcons.Globe
    "Read", "Write", "Edit", "MultiEdit", "Glob", "Grep" -> RelayIcons.File
    "Setup" -> RelayIcons.Server
    else -> RelayIcons.Bolt
}

/** Collapsed: icon, what happened, the command/query. Tap to see full input and output. */
@Composable
private fun ToolCard(t: ToolCall) {
    var open by rememberSaveable(t.id) { mutableStateOf(false) }
    val shape = Relay.shape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Relay.Chip)
            .then(if (Relay.isTerminal) Modifier.border(1.dp, Relay.Line, shape) else Modifier)
            .clickable(role = Role.Button, onClickLabel = stringResource(if (open) R.string.hide_details else R.string.show_details)) { open = !open }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(toolIcon(t.name), null, Modifier.size(16.dp), tint = Relay.Muted)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(toolTitle(LocalContext.current.resources, t), style = ts(13.5f, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (t.detail.isNotBlank() && !open) {
                    Text(
                        t.detail.lineSequence().first(), style = ts(12f, 400, Relay.Muted, family = Relay.Mono),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                !t.done -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(14.dp), color = Relay.Accent, strokeWidth = 2.dp)
                t.isError -> Icon(RelayIcons.Alert, stringResource(R.string.failed), Modifier.size(16.dp), tint = Relay.Error)
                else -> Icon(
                    RelayIcons.ChevronDown, null,
                    Modifier.size(14.dp).graphicsLayer { rotationZ = if (open) 180f else 0f }, tint = Relay.Muted,
                )
            }
        }
        if (open) {
            if (t.detail.isNotBlank()) ToolText(t.detail, Relay.CodeText)
            t.output?.takeIf { it.isNotBlank() }?.let { ToolText(it.trimEnd(), if (t.isError) Relay.Error else Relay.CodeText) }
        }
    }
}

@Composable
private fun ToolText(text: String, color: androidx.compose.ui.graphics.Color) {
    val lines = text.lines()
    val more = LocalContext.current.resources.plural(R.plurals.more_lines, lines.size - 40)
    val shown = if (lines.size > 40) (lines.take(40) + more).joinToString("\n") else text
    SelectionContainer {
        Box(
            Modifier.fillMaxWidth().clip(Relay.shape(8.dp)).background(Relay.CodeBg)
                .horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp)
        ) { Text(shown, style = ts(11.5f, 400, color, family = Relay.Mono, lineHeight = 1.45f), softWrap = false) }
    }
}

// ── Composer ───────────────────────────────────────────────────────────

@Composable
private fun Composer(vm: AppViewModel, placeholder: String, enabled: Boolean, toast: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: context.getString(R.string.attachment)
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        when {
            bytes == null -> toast(context.getString(R.string.read_failed, name))
            bytes.size > 200_000 -> toast(context.getString(R.string.file_too_large, name))
            bytes.take(4000).any { it == 0.toByte() } -> toast(context.getString(R.string.not_text_file, name))
            else -> vm.attachment = Attachment(name, bytes.toString(Charsets.UTF_8))
        }
    }
    val attach = { picker.launch(arrayOf("text/*", "application/json", "application/xml", "application/javascript")) }
    val send = {
        if (vm.streaming) vm.stop()
        else if (!enabled) toast(context.getString(R.string.choose_model_first))
        else if (text.isNotBlank() || vm.attachment != null) {
            vm.send(text)
            text = ""
        }
    }

    val lilac = Relay.isLilac
    Column(Modifier.fillMaxWidth().background(Relay.Ground)) {
        ThemeRule()
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(start = 12.dp, end = 12.dp, top = if (lilac) 8.dp else 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            vm.attachment?.let { att ->
                Row(
                    Modifier.clip(Relay.round).background(Relay.InlineCode).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        att.name, Modifier.widthIn(max = 240.dp),
                        style = ts(12f, 400, family = Relay.Mono), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    IconBtn(RelayIcons.Close, stringResource(R.string.remove_attachment), { vm.attachment = null }, iconSize = 14.dp, tint = Relay.Muted, shape = CircleShape)
                }
            }
            if (Relay.isTerminal) TerminalComposer(text, { text = it }, placeholder, vm.streaming, attach, send)
            else StandardComposer(text, { text = it }, placeholder, vm.streaming, attach, send)
        }
    }
}

@Composable
private fun StandardComposer(
    text: String, onText: (String) -> Unit, placeholder: String, streaming: Boolean,
    onAttach: () -> Unit, onSend: () -> Unit,
) {
    val lilac = Relay.isLilac
    val shape = RoundedCornerShape(if (lilac) 28.dp else 22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (lilac) Modifier.lilacShadow(shape, 8.dp) else Modifier)
            .clip(shape)
            .background(Relay.Surface)
            .then(if (lilac) Modifier else Modifier.border(1.dp, Relay.Line, shape))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = if (lilac) Alignment.CenterVertically else Alignment.Bottom,
    ) {
        IconBtn(
            RelayIcons.Plus, stringResource(R.string.attach_file), onAttach, iconSize = 20.dp, tint = Relay.Muted, shape = CircleShape,
            background = if (lilac) Relay.Ground else Color.Transparent,
        )
        ComposerField(text, onText, placeholder, Modifier.weight(1f), ts(16f, 400, lineHeight = 1.4f))
        IconBtn(
            if (streaming) RelayIcons.Stop else RelayIcons.ArrowUp,
            stringResource(if (streaming) R.string.stop else R.string.send),
            onSend,
            iconSize = 20.dp, tint = Relay.OnAccent, background = Relay.Accent, shape = CircleShape,
        )
    }
}

/** Terminal: `>` prompt (tap it to attach a file), mono input and a RUN ↵ key. */
@Composable
private fun TerminalComposer(
    text: String, onText: (String) -> Unit, placeholder: String, streaming: Boolean,
    onAttach: () -> Unit, onSend: () -> Unit,
) {
    val shape = Relay.shape(4.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Relay.Surface)
            .border(1.dp, Relay.Line, shape)
            .padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(">", Modifier.clickable(onClickLabel = stringResource(R.string.attach_file), onClick = onAttach), style = ts(15f, 700, Relay.Accent))
        ComposerField(text, onText, placeholder, Modifier.weight(1f), ts(15f, 400, lineHeight = 1.4f))
        Box(
            Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Relay.Accent)
                .clickable(role = Role.Button, onClickLabel = stringResource(if (streaming) R.string.stop else R.string.send), onClick = onSend)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (streaming) "STOP ■" else "RUN ↵", style = ts(13f, 700, Relay.OnAccent)) }
    }
}

@Composable
private fun ComposerField(text: String, onText: (String) -> Unit, placeholder: String, modifier: Modifier, style: TextStyle) {
    BasicTextField(
        value = text,
        onValueChange = onText,
        modifier = modifier.heightIn(min = 44.dp),
        textStyle = style,
        cursorBrush = SolidColor(Relay.Accent),
        maxLines = 6,
        decorationBox = { inner ->
            Box(Modifier.heightIn(min = 44.dp).padding(vertical = 11.dp), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Text(placeholder, style = style.copy(color = Relay.Chevron), maxLines = 1, overflow = TextOverflow.Ellipsis)
                inner()
            }
        },
    )
}

// ── Minimal markdown: fenced code blocks, `inline code`, **bold** ─────────

private sealed interface Block {
    data class Para(val text: String) : Block
    data class Code(val code: String) : Block
}

private fun parseBlocks(src: String): List<Block> {
    val out = mutableListOf<Block>()
    val para = StringBuilder()
    fun flushPara() {
        para.toString().split(Regex("\n{2,}")).map { it.trim('\n') }.filter { it.isNotBlank() }.forEach { out += Block.Para(it) }
        para.clear()
    }
    var code: StringBuilder? = null
    for (line in src.lines()) {
        val open = code
        val fence = line.trimStart().startsWith("```")
        when {
            fence && open == null -> { flushPara(); code = StringBuilder() }
            fence && open != null -> { out += Block.Code(open.toString().trimEnd('\n')); code = null }
            open != null -> open.append(line).append('\n')
            else -> para.append(line).append('\n')
        }
    }
    flushPara()
    code?.let { out += Block.Code(it.toString().trimEnd('\n')) } // fence still open while streaming
    return out
}

/**
 * A paragraph block: one ParagraphStyle per line, so lists get a hanging indent and
 * headings their own weight. `- ` / `* ` become bullets, `1.` stays numbered.
 */
private fun paraMarkdown(s: String): AnnotatedString = buildAnnotatedString {
    s.lines().forEach { raw ->
        val bullet = Regex("""^(\s*)[-*•]\s+(.*)$""").find(raw)
        val numbered = Regex("""^(\s*)(\d{1,3}[.)])\s+(.*)$""").find(raw)
        val heading = Regex("""^(#{1,6})\s+(.*)$""").find(raw)
        when {
            heading != null -> withStyle(ParagraphStyle()) {
                withStyle(SpanStyle(fontWeight = FontWeight(700), fontSize = if (heading.groupValues[1].length <= 2) 17.sp else 16.sp)) {
                    append(inlineMarkdown(heading.groupValues[2]))
                }
            }
            bullet != null || numbered != null -> {
                val depth = ((bullet ?: numbered)!!.groupValues[1].length / 2).coerceAtMost(3)
                val marker = if (bullet != null) "•" else numbered!!.groupValues[2]
                val body = if (bullet != null) bullet.groupValues[2] else numbered!!.groupValues[3]
                withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = (depth * 16).sp, restLine = (depth * 16 + 16).sp))) {
                    append("$marker ")
                    append(inlineMarkdown(body))
                }
            }
            else -> withStyle(ParagraphStyle()) { append(inlineMarkdown(raw)) }
        }
    }
}

/** Inline Markdown: [links](url), bare URLs, `code`, **bold**. Links open in the browser. */
private fun inlineMarkdown(s: String): AnnotatedString = buildAnnotatedString {
    val re = Regex("""\[([^\]\n]+)]\((https?://[^)\s]+)\)|(https?://[^\s)>\]]+)|`([^`\n]+)`|\*\*([^*\n]+)\*\*""")
    val linkStyle = TextLinkStyles(SpanStyle(color = Relay.AccentText, textDecoration = TextDecoration.Underline))
    var i = 0
    re.findAll(s).forEach { m ->
        append(s.substring(i, m.range.first))
        val g = m.groups
        when {
            g[1] != null -> withLink(LinkAnnotation.Url(g[2]!!.value, linkStyle)) { append(g[1]!!.value) }
            g[3] != null -> {
                val url = g[3]!!.value.trimEnd('.', ',', ';', ':')
                withLink(LinkAnnotation.Url(url, linkStyle)) { append(url) }
                append(g[3]!!.value.removePrefix(url))
            }
            g[4] != null -> withStyle(SpanStyle(fontFamily = Relay.Mono, fontSize = if (Relay.isTerminal) 13.5.sp else 13.sp, background = Relay.InlineCode)) {
                append("\u2009${g[4]!!.value}\u2009")
            }
            else -> withStyle(SpanStyle(fontWeight = FontWeight(if (Relay.isTerminal) 700 else 600))) { append(g[5]!!.value) }
        }
        i = m.range.last + 1
    }
    append(s.substring(i))
}
