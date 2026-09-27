package app.relay.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.relay.chat.AppViewModel
import app.relay.chat.data.ApiFormat
import app.relay.chat.data.Connection
import app.relay.chat.R
import app.relay.chat.Lang
import app.relay.chat.plural
import app.relay.chat.planTitle
import app.relay.chat.compatLabel
import app.relay.chat.localizeError
import android.content.res.Resources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.FlowRow
import app.relay.chat.data.Plan
import app.relay.chat.ui.theme.Relay
import app.relay.chat.ui.theme.RelayPalette
import app.relay.chat.ui.theme.ThemeId
import app.relay.chat.ui.theme.paletteFor

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ConnectionsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onPlan: (Plan) -> Unit,
    onLanguage: (String) -> Unit,
) {
    val res = LocalContext.current.resources
    val apiConns = vm.connections.filter { it.plan == null }
    val theme = Relay.theme
    val terminal = theme == ThemeId.Terminal
    val lilac = theme == ThemeId.Lilac

    Column(Modifier.fillMaxSize().background(Relay.Ground)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = if (terminal) 8.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (theme) {
                ThemeId.Terminal -> {
                    val shape = Relay.shape(4.dp)
                    Box(
                        Modifier.heightIn(min = 44.dp).clip(shape).border(1.dp, Relay.Line, shape)
                            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.back_to_chat), onClick = onBack)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.term_back), style = ts(13f)) }
                }
                ThemeId.Lilac -> IconBtn(RelayIcons.Back, stringResource(R.string.back_to_chat), onBack, iconSize = 20.dp, background = Relay.Surface, shape = CircleShape)
                else -> IconBtn(RelayIcons.Back, stringResource(R.string.back_to_chat), onBack)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Column(
                Modifier.padding(
                    start = 20.dp, end = 20.dp,
                    top = if (terminal || lilac) 8.dp else 0.dp,
                    bottom = if (terminal || lilac) 18.dp else 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (theme) {
                    ThemeId.Terminal -> {
                        Text("~/connections", style = ts(26f, 700, Relay.Accent))
                        Text(
                            stringResource(R.string.term_connections_subtitle),
                            style = ts(12.5f, 400, Relay.Muted, lineHeight = 1.5f),
                        )
                    }
                    ThemeId.Lilac -> {
                        Text(stringResource(R.string.connections), style = ts(36f, 700, family = Relay.Display, letterSpacing = (-0.01).em))
                        Text(
                            stringResource(R.string.connections_subtitle_short),
                            style = ts(14f, 400, Relay.Muted, lineHeight = 1.45f),
                        )
                    }
                    else -> {
                        Text(stringResource(R.string.connections), style = ts(34f, 700, family = Relay.Display, letterSpacing = (-0.02).em))
                        Text(
                            stringResource(R.string.connections_subtitle),
                            style = ts(14f, 400, Relay.Muted, lineHeight = 1.45f),
                        )
                    }
                }
            }

            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(if (terminal || lilac) 20.dp else 22.dp),
            ) {
                // ── API keys & endpoints ──
                Column(verticalArrangement = Arrangement.spacedBy(if (terminal) 8.dp else 10.dp)) {
                    when (theme) {
                        ThemeId.Classic -> Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            SectionLabel(stringResource(R.string.section_api))
                            Text("${apiConns.size}", style = ts(12f, 400, Relay.Muted))
                        }
                        ThemeId.Terminal -> Text("[ api_keys ] ${apiConns.size}", style = ts(12f, 700, Relay.Muted))
                        else -> SectionLabel(stringResource(R.string.section_api), Modifier.padding(horizontal = 4.dp))
                    }
                    when {
                        apiConns.isEmpty() -> Unit
                        terminal -> apiConns.forEach { c ->
                            TerminalConnectionBox(c, c.id == vm.defaultId) { onEdit(c.id) }
                        }
                        lilac -> apiConns.forEach { c ->
                            Card { ConnectionRow(c, c.id == vm.defaultId) { onEdit(c.id) } }
                        }
                        else -> Card {
                            apiConns.forEachIndexed { i, c ->
                                if (i > 0) RowDivider()
                                ConnectionRow(c, c.id == vm.defaultId) { onEdit(c.id) }
                            }
                        }
                    }
                    AddButton(onAdd)
                }

                // ── Subscriptions ──
                Column(verticalArrangement = Arrangement.spacedBy(if (terminal) 8.dp else 10.dp)) {
                    SectionLabel(stringResource(R.string.section_subscriptions), Modifier.padding(horizontal = if (terminal) 0.dp else 4.dp))
                    when (theme) {
                        ThemeId.Lilac -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Plan.entries.forEach { plan ->
                                LilacPlanCard(plan, planStatus(vm, plan), Modifier.weight(1f)) { onPlan(plan) }
                            }
                        }
                        ThemeId.Terminal -> {
                            val shape = Relay.shape(4.dp)
                            Column(Modifier.fillMaxWidth().clip(shape).background(Relay.Surface).border(1.dp, Relay.Line, shape)) {
                                Plan.entries.forEachIndexed { i, plan ->
                                    if (i > 0) DashedDivider()
                                    TerminalPlanRow(plan, planStatus(vm, plan)) { onPlan(plan) }
                                }
                            }
                        }
                        else -> Card {
                            Plan.entries.forEachIndexed { i, plan ->
                                if (i > 0) RowDivider()
                                SubscriptionRow(plan, planStatus(vm, plan)) { onPlan(plan) }
                            }
                        }
                    }
                }

                // ── Theme (not in the boards; lets the user pick between them) ──
                Column(verticalArrangement = Arrangement.spacedBy(if (terminal) 8.dp else 10.dp)) {
                    SectionLabel(stringResource(R.string.section_theme), Modifier.padding(horizontal = if (terminal) 0.dp else 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeId.entries.forEach { id ->
                            ThemeSwatch(paletteFor(id), selected = id == vm.theme, Modifier.weight(1f)) { vm.chooseTheme(id) }
                        }
                    }
                }

                // ── Language ──
                Column(verticalArrangement = Arrangement.spacedBy(if (terminal) 8.dp else 10.dp)) {
                    SectionLabel(stringResource(R.string.section_language), Modifier.padding(horizontal = if (terminal) 0.dp else 4.dp))
                    val current = Lang.current(LocalContext.current)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Lang.options.forEach { (tag, name) ->
                            LanguageChip(name ?: stringResource(R.string.language_system), tag == current) {
                                if (tag != current) onLanguage(tag)
                            }
                        }
                    }
                }

                when (theme) {
                    ThemeId.Terminal -> Text(
                        stringResource(R.string.term_keys_note),
                        style = ts(11f, 400, Relay.Muted, lineHeight = 1.5f),
                    )
                    ThemeId.Lilac -> Unit
                    else -> Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(RelayIcons.Lock, null, Modifier.padding(top = 1.dp).size(16.dp), tint = Relay.Muted)
                        Text(
                            stringResource(R.string.keys_note),
                            style = ts(12f, 400, Relay.Muted, lineHeight = 1.45f),
                        )
                    }
                }
            }
        }
    }
}

private data class TileStyle(val icon: ImageVector, val fg: Color, val bg: Color)

private fun tileFor(c: Connection) = when {
    c.usesDefaultBaseUrl -> TileStyle(RelayIcons.Key, Relay.Ink, Relay.TileNeutral)
    c.isLocal -> TileStyle(RelayIcons.Server, Relay.AccentOnTint, Relay.AccentTint)
    else -> TileStyle(RelayIcons.Globe, Relay.Amber, Relay.AmberTint)
}

private fun modelCount(res: Resources, c: Connection) =
    if (c.models.isEmpty()) res.getString(R.string.no_models_yet) else res.plural(R.plurals.models_count, c.models.size)

/** Classic / Midnight / Lilac connection row (only Classic shows the chevron; Lilac uses a larger tile). */
@Composable
private fun ConnectionRow(c: Connection, isDefault: Boolean, onClick: () -> Unit) {
    val tile = tileFor(c)
    val lilac = Relay.isLilac
    val res = LocalContext.current.resources
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Edit ${c.name}", onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(if (lilac) 44.dp else 40.dp).clip(RoundedCornerShape(if (lilac) 14.dp else 10.dp)).background(tile.bg),
            contentAlignment = Alignment.Center,
        ) { Icon(tile.icon, null, Modifier.size(20.dp), tint = tile.fg) }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, Modifier.weight(1f, fill = false), style = ts(16f, if (lilac) 700 else 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (isDefault) Badge(stringResource(R.string.badge_default))
            }
            if (c.usesDefaultBaseUrl) {
                Text(
                    if (c.apiKey.isBlank()) stringResource(R.string.default_url_no_key) else stringResource(R.string.default_url_key, c.maskedKey),
                    style = ts(12f, 400, Relay.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(c.baseUrl, style = ts(12f, 400, Relay.Muted, family = Relay.Mono), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (c.apiKey.isBlank()) stringResource(R.string.compat_no_key, compatLabel(res, c.format), modelCount(res, c))
                    else stringResource(R.string.key_and_models, c.maskedKey, modelCount(res, c)),
                    style = ts(12f, 400, Relay.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            c.lastError?.let {
                Text(stringResource(R.string.tap_to_fix, localizeError(res, it)), style = ts(12f, if (lilac) 600 else 500, Relay.Error), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (Relay.theme == ThemeId.Classic) Icon(RelayIcons.ChevronRight, null, Modifier.size(18.dp), tint = Relay.Chevron)
    }
}

/** Terminal: one bordered box per connection, key=value lines, status on the right. */
@Composable
private fun TerminalConnectionBox(c: Connection, isDefault: Boolean, onClick: () -> Unit) {
    val shape = Relay.shape(4.dp)
    val failed = c.lastError != null
    val border = when {
        failed -> Color(0xFF5A2620)
        isDefault -> Relay.Accent
        else -> Relay.Line
    }
    val code = c.lastError?.let { Regex("""\((\d{3})\)""").find(it)?.groupValues?.get(1) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isDefault && !failed) Relay.AccentTint else Relay.Surface)
            .border(1.dp, border, shape)
            .clickable(role = Role.Button, onClickLabel = "Edit ${c.name}", onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(slug(c.name), Modifier.weight(1f, fill = false), style = ts(14f, 700), maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                failed -> Text("✕ ${code ?: "err"}", style = ts(11f, 400, Relay.Error))
                isDefault -> Text(stringResource(R.string.term_status_default), style = ts(11f, 400, Relay.Accent))
                else -> Text(stringResource(R.string.term_status_ok), style = ts(11f, 400, Relay.Accent))
            }
        }
        Text(
            "base_url = " + if (c.usesDefaultBaseUrl) "(default)" else c.baseUrl,
            style = ts(12f, 400, Relay.CodeText), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val err = c.lastError
        if (err != null) {
            Text(stringResource(R.string.term_tap_to_fix, localizeError(LocalContext.current.resources, err).replace(Regex("""\s*\(\d{3}\)"""), "").lowercase()), style = ts(12f, 400, Relay.Error))
        } else {
            val format = when (c.format) {
                ApiFormat.OpenAI -> "openai"
                ApiFormat.Anthropic -> "anthropic"
                ApiFormat.Gemini -> "gemini"
            }
            Text(
                if (c.apiKey.isBlank()) "format=$format · models=${c.models.size} · key=none"
                else "key=${c.maskedKey} · models=${c.models.size}",
                style = ts(12f, 400, Relay.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** What a subscription row shows: signed in (with an optional error) or not. */
private data class PlanStatus(val signedIn: Boolean, val error: String?) {
    fun label(res: Resources, plan: Plan): String = when {
        !signedIn -> res.getString(R.string.not_connected)
        error != null -> res.getString(R.string.tap_to_fix, localizeError(res, error))
        else -> res.getString(R.string.signed_in_via, plan.cli)
    }
}

private fun planStatus(vm: AppViewModel, plan: Plan) =
    vm.planConnection(plan).let { PlanStatus(it != null, it?.lastError) }

@Composable
private fun SubscriptionRow(plan: Plan, status: PlanStatus, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Relay.TileNeutral),
            contentAlignment = Alignment.Center,
        ) { Text(plan.letter, style = ts(15f, 700)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(planTitle(LocalContext.current.resources, plan), style = ts(16f, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                status.label(LocalContext.current.resources, plan),
                style = ts(12f, if (status.error != null) 500 else 400, if (status.error != null) Relay.Error else Relay.Muted),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        val shape = RoundedCornerShape(12.dp)
        Box(
            Modifier
                .heightIn(min = 44.dp)
                .clip(shape)
                .then(
                    if (status.signedIn) Modifier.background(Relay.Surface).border(1.dp, Relay.Line, shape)
                    else Modifier.background(Relay.StrongBg)
                )
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (status.signedIn) Text(stringResource(R.string.manage), style = ts(14f, 500))
            else Text(stringResource(R.string.sign_in), style = ts(14f, if (Relay.theme == ThemeId.Midnight) 600 else 500, Relay.StrongFg))
        }
    }
}

@Composable
private fun LilacPlanCard(plan: Plan, status: PlanStatus, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Relay.Surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(planTitle(LocalContext.current.resources, plan), style = ts(15f, 700), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            status.label(LocalContext.current.resources, plan),
            style = ts(12f, 400, if (status.error != null) Relay.Error else Relay.Muted),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val pill = RoundedCornerShape(50)
        Box(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(pill)
                .then(if (status.signedIn) Modifier.border(1.dp, Relay.Line, pill) else Modifier.background(Relay.StrongBg))
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(if (status.signedIn) R.string.manage else R.string.sign_in), style = ts(14f, 600, if (status.signedIn) Relay.Ink else Relay.StrongFg))
        }
    }
}

@Composable
private fun TerminalPlanRow(plan: Plan, status: PlanStatus, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("[${plan.cli.lowercase().replace(' ', '-')}]", style = ts(13.5f, 700))
            Text(status.label(LocalContext.current.resources, plan).lowercase(), style = ts(11.5f, 400, if (status.error != null) Relay.Error else Relay.Muted))
        }
        val shape = RoundedCornerShape(3.dp)
        Box(
            Modifier.heightIn(min = 44.dp).clip(shape)
                .border(1.dp, if (status.signedIn) Relay.Line else Relay.Accent, shape)
                .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(if (status.signedIn) R.string.term_manage else R.string.term_login), style = ts(12f, 400, if (status.signedIn) Relay.Ink else Relay.Accent)) }
    }
}

@Composable
private fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = if (Relay.isTerminal) Relay.shape(4.dp) else RoundedCornerShape(50)
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (selected) Relay.Accent else Relay.Surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, Relay.Line, shape))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = ts(14f, if (selected) 600 else 500, if (selected) Relay.OnAccent else Relay.Ink)) }
}

/** A mini preview of a theme, drawn in that theme's own colors. */
@Composable
private fun ThemeSwatch(p: RelayPalette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = Relay.shape(14.dp)
    val terminalSwatch = p.id == ThemeId.Terminal
    Column(
        modifier
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Relay.Accent else Relay.Line, shape)
            .clickable(role = Role.RadioButton, onClickLabel = "Use ${p.id.label} theme", onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val inner = RoundedCornerShape(if (terminalSwatch) 3.dp else 9.dp)
        Column(
            Modifier.fillMaxWidth().height(56.dp).clip(inner).background(p.ground).border(1.dp, p.line, inner).padding(7.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier.align(Alignment.End).size(width = 26.dp, height = 10.dp)
                    .clip(RoundedCornerShape(if (terminalSwatch) 1.dp else 5.dp)).background(p.accent)
            )
            Box(Modifier.size(width = 34.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(p.muted.copy(alpha = 0.5f)))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(if (terminalSwatch) 1.dp else 5.dp)).background(p.surface))
                Box(Modifier.size(10.dp).clip(if (terminalSwatch) RoundedCornerShape(1.dp) else CircleShape).background(p.accent))
            }
        }
        Text(
            if (Relay.isTerminal) p.id.label.lowercase() else p.id.label,
            style = ts(12f, if (selected) 700 else 500, if (selected) Relay.Ink else Relay.Muted),
            maxLines = 1,
        )
    }
}
