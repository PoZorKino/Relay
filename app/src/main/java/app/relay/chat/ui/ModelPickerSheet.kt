package app.relay.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.relay.chat.AppViewModel
import app.relay.chat.data.ModelRef
import app.relay.chat.R
import app.relay.chat.plural
import app.relay.chat.planTitle
import app.relay.chat.formatLabel
import app.relay.chat.localizeError
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import app.relay.chat.ui.theme.Relay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(vm: AppViewModel, onDismiss: () -> Unit, onAddConnection: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit = {}) {
        scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss(); then() }
    }
    var query by remember { mutableStateOf("") }
    val selected = vm.selection
    val total = vm.connections.sumOf { it.models.size }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        shape = RoundedCornerShape(topStart = Relay.r(24.dp), topEnd = Relay.r(24.dp)),
        containerColor = Relay.Ground,
        scrimColor = Relay.Scrim,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp).width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Relay.Handle))
        },
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(690f / 844f)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (Relay.isTerminal) Text("~/models", style = ts(20f, 700, Relay.Accent))
                else Text(stringResource(R.string.choose_model), style = ts(24f, 700, family = Relay.Display, letterSpacing = (-0.01).em))
                IconBtn(RelayIcons.Close, stringResource(R.string.close), { close() }, iconSize = 18.dp, background = Relay.Chip, shape = Relay.round)
            }

            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(Relay.shape(12.dp)).background(Relay.Chip).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(RelayIcons.Search, null, Modifier.size(18.dp), tint = Relay.Muted)
                BasicTextField(
                    query, { query = it },
                    Modifier.weight(1f),
                    textStyle = ts(16f),
                    singleLine = true,
                    cursorBrush = SolidColor(Relay.Accent),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Text(LocalContext.current.resources.plural(R.plurals.search_models, total), style = ts(16f, 400, Relay.Chevron))
                            inner()
                        }
                    },
                )
            }

            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val q = query.trim().lowercase()
                vm.connections.forEach { c ->
                    val models = if (q.isEmpty() || c.name.lowercase().contains(q)) c.models
                    else c.models.filter { it.lowercase().contains(q) }
                    if (q.isNotEmpty() && models.isEmpty()) return@forEach
                    item(key = c.id) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SectionLabel(c.name, Modifier.weight(1f, fill = false))
                                if (c.plan != null) Badge(stringResource(R.string.badge_subscription), fg = Relay.Amber, bg = Relay.AmberTint)
                                else Badge(stringResource(R.string.badge_api_key))
                            }
                            Card(radius = 14.dp) {
                                if (models.isEmpty()) {
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                            .clickable(role = Role.Button) { vm.refreshModels(c.id) }
                                            .padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            stringResource(R.string.tap_to_retry, c.lastError?.let { localizeError(LocalContext.current.resources, it) } ?: stringResource(R.string.no_models_loaded)),
                                            style = ts(14f, 500, if (c.lastError != null) Relay.Error else Relay.Muted),
                                        )
                                    }
                                }
                                models.forEachIndexed { i, m ->
                                    if (i > 0) RowDivider()
                                    val isSel = selected?.connectionId == c.id && selected.model == m
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 52.dp)
                                            .clickable(role = Role.Button) {
                                                vm.select(ModelRef(c.id, m))
                                                close()
                                            }
                                            .padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(m, Modifier.weight(1f), style = ts(14f, 400, family = Relay.Mono), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (isSel) Icon(RelayIcons.CheckBold, stringResource(R.string.selected), Modifier.size(20.dp), tint = Relay.Accent)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(Relay.shape(12.dp))
                    .clickable(role = Role.Button) { close(onAddConnection) },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(RelayIcons.PlusBold, null, Modifier.size(18.dp), tint = Relay.AccentText)
                Text(stringResource(R.string.connect_another), style = ts(15f, 600, Relay.AccentText))
            }
        }
    }
}
