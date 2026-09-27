package app.relay.chat.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.relay.chat.AppViewModel
import app.relay.chat.PlanSetup
import app.relay.chat.PlanSetup.Step
import app.relay.chat.data.Plan
import app.relay.chat.R
import app.relay.chat.plural
import app.relay.chat.planTitle
import app.relay.chat.formatLabel
import app.relay.chat.localizeError
import androidx.compose.ui.res.stringResource
import app.relay.chat.ui.theme.Relay

/** Setup / sign-in / manage screen for a subscription reached through its official CLI. */
@Composable
fun PlanScreen(vm: AppViewModel, plan: Plan, onClose: () -> Unit, toast: (String) -> Unit) {
    val s = vm.planSetup(plan)
    val connected = vm.planConnection(plan)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val vendor = if (plan == Plan.Claude) "Claude" else "ChatGPT"

    fun open(url: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { toast(context.getString(R.string.no_browser)) }

    // Claude's login hands us an OAuth URL: open it straight away. Codex shows a device code
    // first, so the user copies it before leaving.
    LaunchedEffect(s.url) {
        val url = s.url
        if (url != null && plan == Plan.Claude && s.step == Step.SignIn) open(url)
    }

    Column(Modifier.fillMaxSize().background(Relay.Ground)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextAction(stringResource(if (s.step == Step.Done) R.string.close else R.string.back), ts(16f), onClose)
            Text(planTitle(context.resources, plan), style = ts(16f, 600))
            Box(Modifier.width(56.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (Relay.isTerminal) stringResource(R.string.term_plan_intro, plan.cli.lowercase(), vendor)
                else stringResource(R.string.plan_intro, plan.cli, vendor),
                style = ts(if (Relay.isTerminal) 12.5f else 14f, 400, Relay.Muted, lineHeight = 1.45f),
            )

            if (!vm.runtimeSupported) {
                Notice(stringResource(R.string.plan_unsupported), error = true)
                return@Column
            }

            if (connected != null && !s.running && s.step != Step.Done) {
                ConnectedCard(plan, connected.lastError)
                ToolsToggle(vm)
                ModelsSection(vm, plan, connected.models, connected.manualModels)
                if (s.updating || s.log.isNotEmpty()) LogBox(s.log.takeLast(8))
                s.error?.let { Notice(localizeError(context.resources, it), error = true) }
                OutlinedButton(stringResource(if (s.updating) R.string.updating_cli else R.string.update_cli, plan.cli), Relay.Ink) {
                    if (!s.updating) vm.updatePlanCli(plan)
                }
                FilledButton(stringResource(R.string.sign_in_again)) { vm.startPlanSetup(plan) }
                OutlinedButton(stringResource(R.string.sign_out_of, plan.cli), Relay.Error) { vm.signOutPlan(plan); onClose() }
                return@Column
            }

            Card {
                StepRow(1, stringResource(R.string.step_runtime), stringResource(R.string.step_runtime_detail), stateOf(s, Step.Runtime))
                RowDivider()
                StepRow(2, stringResource(R.string.step_install, plan.cli), stringResource(if (plan == Plan.Claude) R.string.step_install_claude else R.string.step_install_codex), stateOf(s, Step.Install))
                RowDivider()
                StepRow(3, stringResource(R.string.step_sign_in, vendor), stringResource(if (plan == Plan.Claude) R.string.step_sign_in_claude else R.string.step_sign_in_codex), stateOf(s, Step.SignIn))
            }

            if (s.step == Step.SignIn) SignInCard(vm, s, plan, ::open, clipboard::setText)

            if (s.log.isNotEmpty() && s.step != Step.Done) LogBox(s.log.takeLast(8))

            when (s.step) {
                Step.Failed -> {
                    Notice(s.error?.let { localizeError(context.resources, it) } ?: stringResource(R.string.setup_failed), error = true)
                    FilledButton(stringResource(R.string.try_again)) { vm.startPlanSetup(plan) }
                }
                Step.Done -> {
                    Notice(stringResource(R.string.signed_in_done, plan.cli), error = false)
                    FilledButton(stringResource(R.string.start_chatting), onClose)
                }
                Step.Idle -> FilledButton(stringResource(R.string.set_up_sign_in)) { vm.startPlanSetup(plan) }
                else -> OutlinedButton(stringResource(R.string.cancel), Relay.Ink) { vm.cancelPlanSetup(plan) }
            }
        }
    }
}

private enum class StepState { Pending, Running, Done }

private fun stateOf(s: PlanSetup, step: Step): StepState {
    val order = listOf(Step.Runtime, Step.Install, Step.SignIn)
    val i = order.indexOf(step)
    return when (s.step) {
        Step.Done -> StepState.Done
        Step.Idle, Step.Failed -> StepState.Pending
        else -> {
            val cur = order.indexOf(s.step)
            when {
                i < cur -> StepState.Done
                i == cur -> StepState.Running
                else -> StepState.Pending
            }
        }
    }
}

@Composable
private fun StepRow(n: Int, title: String, detail: String, state: StepState) {
    Row(
        Modifier.fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(Relay.round)
                .background(if (state == StepState.Done) Relay.AccentTint else Relay.TileNeutral),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                StepState.Done -> Icon(RelayIcons.Check, null, Modifier.size(16.dp), tint = Relay.AccentOnTint)
                StepState.Running -> CircularProgressIndicator(Modifier.size(16.dp), color = Relay.Accent, strokeWidth = 2.dp)
                else -> Text("$n", style = ts(13f, 600, Relay.Muted))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = ts(15f, 600, if (state == StepState.Pending) Relay.Muted else Relay.Ink))
            Text(detail, style = ts(12f, 400, Relay.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SignInCard(vm: AppViewModel, s: PlanSetup, plan: Plan, open: (String) -> Unit, copy: (AnnotatedString) -> Unit) {
    val shape = Relay.shape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Relay.AccentTint).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val code = s.deviceCode
        val url = s.url
        when {
            plan == Plan.Codex && code != null -> {
                Text(stringResource(R.string.enter_code), style = ts(14f, 600, Relay.AccentDeep))
                Text(
                    code, Modifier.fillMaxWidth(),
                    style = ts(28f, 700, Relay.Ink, family = Relay.Mono), textAlign = TextAlign.Center,
                )
                FilledButton(stringResource(R.string.copy_code_open)) {
                    copy(AnnotatedString(code))
                    url?.let(open)
                }
            }
            url != null -> {
                Text(stringResource(R.string.finish_in_browser), style = ts(14f, 600, Relay.AccentDeep, lineHeight = 1.4f))
                FilledButton(stringResource(R.string.open_sign_in)) { open(url) }
                if (plan == Plan.Claude) PasteCode(vm, plan)
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Relay.AccentDeep, strokeWidth = 2.dp)
                Text(stringResource(R.string.waiting_for, plan.cli), style = ts(14f, 500, Relay.AccentDeep))
            }
        }
    }
}

/** For when the browser shows an authorization code instead of returning to the app. */
@Composable
private fun PasteCode(vm: AppViewModel, plan: Plan) {
    var code by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.paste_code_hint), style = ts(12.5f, 500, Relay.AccentDeep))
        RelayField(
            code, { code = it },
            placeholder = stringResource(R.string.auth_code),
            textStyle = ts(14f, 400, family = Relay.Mono),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            trailing = {
                Box(
                    Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) {
                        if (code.isNotBlank() && vm.sendPlanCode(plan, code)) code = ""
                    }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.send), style = ts(14f, 600, Relay.AccentText)) }
            },
        )
    }
}

@Composable
private fun ConnectedCard(plan: Plan, error: String?) {
    Card {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(Relay.shape(10.dp)).background(Relay.AmberTint), contentAlignment = Alignment.Center) {
                Text(plan.letter, style = ts(15f, 700, Relay.Amber))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(plan.cli, style = ts(16f, 600))
                if (error != null) Text(stringResource(R.string.sign_in_again_suffix, localizeError(LocalContext.current.resources, error)), style = ts(12f, 500, Relay.Error))
                else Text(LocalContext.current.resources.plural(R.plurals.signed_in_models, plan.models.size), style = ts(12f, 400, Relay.Muted))
            }
        }
    }
}

/** Global switch for the CLI's tools in subscription chats. */
@Composable
private fun ToolsToggle(vm: AppViewModel) {
    Card {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Switch) { vm.chooseTools(!vm.toolsEnabled) }.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.tools_title), style = ts(16f, 600))
                Text(
                    stringResource(R.string.tools_body),
                    style = ts(12f, 400, Relay.Muted, lineHeight = 1.4f),
                )
            }
            androidx.compose.material3.Switch(
                checked = vm.toolsEnabled,
                onCheckedChange = { vm.chooseTools(it) },
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = Relay.OnAccent, checkedTrackColor = Relay.Accent,
                    uncheckedThumbColor = Relay.Muted, uncheckedTrackColor = Relay.Chip, uncheckedBorderColor = Relay.Line,
                ),
            )
        }
    }
}

/** The plan's model list, plus extra IDs the user can add (kept across refreshes). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelsSection(vm: AppViewModel, plan: Plan, models: List<String>, custom: String) {
    var draft by remember(custom) { mutableStateOf(custom) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.section_models), Modifier.padding(horizontal = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            models.forEach {
                Text(
                    it,
                    Modifier.clip(Relay.shape(6.dp)).background(Relay.Surface).padding(horizontal = 8.dp, vertical = 4.dp),
                    style = ts(11.5f, 400, family = Relay.Mono),
                    maxLines = 1,
                )
            }
        }
        Text(
            stringResource(if (plan == Plan.Codex) R.string.models_codex_note else R.string.models_claude_note),
            Modifier.padding(horizontal = 4.dp),
            style = ts(12f, 400, Relay.Muted, lineHeight = 1.4f),
        )
        RelayField(
            draft, { draft = it },
            placeholder = if (plan == Plan.Codex) "extra-model-id, …" else "claude-…, …",
            textStyle = ts(13f, 400, family = Relay.Mono),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            trailing = {
                Box(
                    Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, enabled = draft != custom) {
                        vm.setPlanCustomModels(plan, draft)
                    }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.save), style = ts(14f, 600, if (draft != custom) Relay.AccentText else Relay.Chevron)) }
            },
        )
    }
}

@Composable
private fun LogBox(lines: List<String>) {
    val shape = Relay.shape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Relay.CodeBg)
            .then(Relay.CodeBorder?.let { Modifier.border(1.dp, it, shape) } ?: Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        lines.forEach {
            Text(it, style = ts(11.5f, 400, Relay.CodeText, family = Relay.Mono, lineHeight = 1.4f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Notice(text: String, error: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(Relay.shape(14.dp)).background(if (error) Relay.ErrorTint else Relay.AccentTint).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(if (error) RelayIcons.Alert else RelayIcons.Check, null, Modifier.size(18.dp), tint = if (error) Relay.Error else Relay.AccentDeep)
        Text(text, style = ts(14f, 500, if (error) Relay.Error else Relay.AccentDeep, lineHeight = 1.4f))
    }
}

@Composable
private fun FilledButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(if (Relay.isLilac) Relay.round else Relay.shape(14.dp))
            .background(Relay.Accent).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = ts(15f, 600, Relay.OnAccent)) }
}

@Composable
private fun OutlinedButton(text: String, color: Color, onClick: () -> Unit) {
    val shape = if (Relay.isLilac) Relay.round else Relay.shape(14.dp)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).border(1.5.dp, color, shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = ts(15f, 600, color)) }
}

@Composable
private fun TextAction(text: String, style: TextStyle, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).clip(Relay.shape(10.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = style) }
}
