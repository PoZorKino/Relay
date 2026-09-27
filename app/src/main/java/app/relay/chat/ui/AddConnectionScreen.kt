package app.relay.chat.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.relay.chat.AppViewModel
import app.relay.chat.data.ApiFormat
import app.relay.chat.data.Connection
import app.relay.chat.R
import app.relay.chat.plural
import app.relay.chat.planTitle
import app.relay.chat.formatLabel
import app.relay.chat.localizeError
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import app.relay.chat.net.ApiException
import app.relay.chat.net.ModelsResult
import app.relay.chat.ui.theme.Relay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.URI
import java.util.UUID

private sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Ok(val result: ModelsResult) : TestState
    data class Failed(val short: String, val detail: String?) : TestState
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddConnectionScreen(vm: AppViewModel, editId: String?, onClose: () -> Unit) {
    val existing = remember(editId) { vm.connection(editId) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var format by remember { mutableStateOf(existing?.format ?: ApiFormat.OpenAI) }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(existing?.apiKey ?: "") }
    var headers by remember { mutableStateOf(existing?.headers ?: "") }
    var timeout by remember { mutableStateOf((existing?.timeoutSeconds ?: 60).toString()) }
    var manualModels by remember { mutableStateOf(existing?.manualModels ?: "") }
    var showKey by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(existing != null && (existing.headers.isNotBlank() || existing.manualModels.isNotBlank())) }
    var test by remember { mutableStateOf<TestState>(TestState.Idle) }
    var testJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // Any edit invalidates a previous test result.
    fun edited(f: () -> Unit) {
        f(); testJob?.cancel(); test = TestState.Idle
    }

    fun draft(): Connection {
        val url = baseUrl.trim().ifEmpty { format.defaultBaseUrl }
        val fallbackName = runCatching { URI(url).host }.getOrNull() ?: context.getString(R.string.connection_fallback_name)
        return Connection(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { fallbackName },
            format = format,
            baseUrl = url,
            apiKey = apiKey.trim(),
            headers = headers,
            timeoutSeconds = timeout.toIntOrNull()?.coerceIn(5, 600) ?: 60,
            manualModels = manualModels,
            models = existing?.models ?: emptyList(),
            lastError = existing?.lastError,
        )
    }

    Column(Modifier.fillMaxSize().background(Relay.Ground)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderAction(stringResource(R.string.cancel), ts(16f, 400), onClose)
            Text(stringResource(if (existing == null) R.string.new_connection else R.string.edit_connection), style = ts(16f, 600))
            HeaderAction(stringResource(R.string.save), ts(16f, 600, Relay.AccentText)) {
                val ok = (test as? TestState.Ok)?.result
                vm.save(draft(), ok)
                onClose()
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Name
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(stringResource(R.string.field_name))
                RelayField(name, { edited { name = it } }, placeholder = "Home Lab")
            }

            // API format
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(stringResource(R.string.field_format))
                Row(
                    Modifier.fillMaxWidth().clip(Relay.shape(12.dp)).background(Relay.Chip).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ApiFormat.entries.forEach { f ->
                        val selected = f == format
                        val shape = Relay.shape(9.dp)
                        Box(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .then(if (selected) Modifier.shadow(1.dp, shape, ambientColor = Relay.Ink.copy(alpha = 0.12f), spotColor = Relay.Ink.copy(alpha = 0.12f)) else Modifier)
                                .clip(shape)
                                .background(if (selected) Relay.Raised else Color.Transparent)
                                .clickable(role = Role.Tab) {
                                    edited {
                                        if (baseUrl.isBlank() || baseUrl.trimEnd('/') == format.defaultBaseUrl) baseUrl = f.defaultBaseUrl
                                        format = f
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                formatLabel(context.resources, f),
                                style = ts(13f, if (selected) 600 else 500, if (selected) Relay.Ink else Relay.Muted),
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            // Base URL
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(stringResource(R.string.field_base_url))
                RelayField(
                    baseUrl, { edited { baseUrl = it } },
                    placeholder = format.defaultBaseUrl,
                    textStyle = ts(14f, 400, family = Relay.Mono),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                )
                FlowRow(
                    Modifier.padding(start = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(R.string.presets), Modifier.padding(vertical = 5.dp).align(Alignment.CenterVertically), style = ts(12f, 400, Relay.Muted))
                    PresetChip(stringResource(R.string.preset_default)) { edited { baseUrl = format.defaultBaseUrl } }
                    PresetChip("localhost:11434") { edited { baseUrl = "http://localhost:11434/v1" } }
                    PresetChip("localhost:1234") { edited { baseUrl = "http://localhost:1234/v1" } }
                }
            }

            // API key
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.field_api_key), style = ts(13f, 600))
                    Text(stringResource(R.string.api_key_optional), style = ts(12f, 400, Relay.Muted))
                }
                RelayField(
                    apiKey, { edited { apiKey = it } },
                    placeholder = "sk-…",
                    textStyle = ts(14f, 400, family = Relay.Mono),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailing = {
                        IconBtn(RelayIcons.Clipboard, stringResource(R.string.paste_clipboard), {
                            clipboard.getText()?.text?.trim()?.let { edited { apiKey = it } }
                        }, iconSize = 18.dp, tint = Relay.Muted, shape = RoundedCornerShape(0.dp))
                        IconBtn(if (showKey) RelayIcons.EyeOff else RelayIcons.Eye, stringResource(if (showKey) R.string.hide_key else R.string.show_key), {
                            showKey = !showKey
                        }, iconSize = 18.dp, tint = Relay.Muted, shape = RoundedCornerShape(0.dp))
                    },
                )
            }

            // Advanced
            val advShape = Relay.shape(12.dp)
            Column(
                Modifier.fillMaxWidth().clip(advShape).background(Relay.Surface).border(1.dp, Relay.Line, advShape)
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { advanced = !advanced }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.advanced), Modifier.weight(1f), style = ts(14f, 600))
                    Icon(RelayIcons.ChevronExpand, null, Modifier.size(16.dp).rotate(if (advanced) 180f else 0f), tint = Relay.Muted)
                }
                AnimatedVisibility(advanced) {
                    Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.extra_headers), style = ts(12f, 600))
                            RelayField(
                                headers, { edited { headers = it } },
                                placeholder = "X-Header-Name: value",
                                textStyle = ts(13f, 400, family = Relay.Mono, lineHeight = 1.5f),
                                singleLine = false,
                                minHeight = 72.dp,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.timeout_seconds), style = ts(12f, 600))
                            RelayField(
                                timeout, { v -> edited { timeout = v.filter { it.isDigit() }.take(3) } },
                                placeholder = "60",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.model_list_fallback), style = ts(12f, 600))
                            RelayField(
                                manualModels, { edited { manualModels = it } },
                                placeholder = "model-a, model-b",
                                textStyle = ts(13f, 400, family = Relay.Mono, lineHeight = 1.5f),
                                singleLine = false,
                                minHeight = 72.dp,
                            )
                        }
                    }
                }
            }

            // Test connection
            val testShape = Relay.shape(14.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(testShape)
                    .border(1.5.dp, Relay.Ink, testShape)
                    .clickable(enabled = test != TestState.Running, role = Role.Button) {
                        test = TestState.Running
                        testJob = scope.launch {
                            val d = draft()
                            test = vm.test(d).fold(
                                onSuccess = { TestState.Ok(it) },
                                onFailure = { e -> TestState.Failed((e as? ApiException)?.short ?: "Failed", e.message) },
                            )
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(RelayIcons.Bolt, null, Modifier.size(18.dp), tint = Relay.Ink)
                Text(stringResource(if (test == TestState.Running) R.string.testing else R.string.test_connection), style = ts(15f, 600))
            }

            when (val t = test) {
                is TestState.Ok -> TestSuccess(t.result)
                is TestState.Failed -> TestFailure(t)
                else -> Unit
            }

            if (existing != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (vm.defaultId != existing.id) {
                        HeaderAction(stringResource(R.string.make_default), ts(15f, 600, Relay.AccentText)) { vm.setDefault(existing.id) }
                    } else {
                        Text(stringResource(R.string.default_connection), Modifier.padding(horizontal = 8.dp, vertical = 12.dp), style = ts(15f, 500, Relay.Muted))
                    }
                    HeaderAction(stringResource(R.string.delete), ts(15f, 600, Relay.Error)) {
                        vm.delete(existing.id)
                        onClose()
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderAction(text: String, style: androidx.compose.ui.text.TextStyle, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(Relay.shape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = style) }
}

@Composable
private fun PresetChip(text: String, onClick: () -> Unit) {
    val shape = Relay.round
    Box(
        Modifier
            .heightIn(min = 28.dp)
            .clip(shape)
            .background(Relay.Surface)
            .border(1.dp, Relay.Line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = ts(11.5f, 400, family = Relay.Mono)) }
}

private fun reason(code: Int) = when (code) {
    200 -> "OK"; 201 -> "Created"; 204 -> "No Content"; else -> ""
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestSuccess(r: ModelsResult) {
    val res = LocalContext.current.resources
    Column(
        Modifier.fillMaxWidth().clip(Relay.shape(14.dp)).background(Relay.AccentTint).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(RelayIcons.Check, null, Modifier.size(18.dp), tint = Relay.AccentDeep)
            Text(stringResource(R.string.connected_status, "${r.status} ${reason(r.status)}".trim(), r.millis.toInt()), style = ts(14f, 600, Relay.AccentDeep))
        }
        Text(
            buildAnnotatedString {
                append(res.plural(R.plurals.found_models_at, r.models.size))
                withStyle(androidx.compose.ui.text.SpanStyle(fontFamily = Relay.Mono, fontSize = 12.sp)) { append(r.path) }
            },
            style = ts(13f, 400, Relay.AccentDeep),
        )
        if (r.models.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                r.models.take(3).forEach {
                    Text(
                        it,
                        Modifier.clip(Relay.shape(6.dp)).background(Relay.Surface).padding(horizontal = 8.dp, vertical = 4.dp),
                        style = ts(11.5f, 400, family = Relay.Mono),
                        maxLines = 1,
                    )
                }
                if (r.models.size > 3) {
                    Text(res.plural(R.plurals.n_more, r.models.size - 3), Modifier.padding(4.dp), style = ts(11.5f, 600, Relay.AccentDeep))
                }
            }
        }
    }
}

@Composable
private fun TestFailure(t: TestState.Failed) {
    Column(
        Modifier.fillMaxWidth().clip(Relay.shape(14.dp)).background(Relay.ErrorTint).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(RelayIcons.Alert, null, Modifier.size(18.dp), tint = Relay.Error)
            Text(stringResource(R.string.couldnt_connect, localizeError(LocalContext.current.resources, t.short)), style = ts(14f, 600, Relay.Error))
        }
        t.detail?.takeIf { it != t.short }?.let {
            Text(it.removePrefix("${t.short} — "), Modifier.alpha(0.9f), style = ts(13f, 400, Relay.Error, lineHeight = 1.4f))
        }
    }
}
