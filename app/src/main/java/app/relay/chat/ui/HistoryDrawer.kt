package app.relay.chat.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.relay.chat.AppViewModel
import app.relay.chat.data.ChatSummary
import app.relay.chat.R
import app.relay.chat.plural
import app.relay.chat.planTitle
import app.relay.chat.formatLabel
import app.relay.chat.localizeError
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import android.content.res.Resources
import app.relay.chat.ui.theme.Relay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Side drawer behind ☰: new chat, saved chats by recency, and the way to Connections. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryDrawer(vm: AppViewModel, onNewChat: () -> Unit, onOpen: (String) -> Unit, onConnections: () -> Unit) {
    var confirmDelete by remember { mutableStateOf<ChatSummary?>(null) }
    val terminal = Relay.isTerminal

    Column(
        Modifier
            .fillMaxHeight()
            .width(304.dp)
            .background(Relay.Ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 20.dp, bottom = 12.dp),
    ) {
        Text(
            if (terminal) "~/chats" else stringResource(R.string.chats),
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            style = if (terminal) ts(22f, 700, Relay.Accent)
            else ts(28f, 700, family = Relay.Display, letterSpacing = (-0.02).em),
        )
        DrawerAction(RelayIcons.NewChat, stringResource(if (terminal) R.string.term_new_chat else R.string.new_chat), onNewChat)

        val groups = groupByDay(LocalContext.current.resources, vm.chats)
        val openLabel = stringResource(R.string.open_chat)
        val deleteLabel = stringResource(R.string.delete_chat)
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            if (groups.isEmpty()) {
                item {
                    Text(
                        stringResource(if (terminal) R.string.term_no_chats else R.string.no_chats),
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = ts(13f, 400, Relay.Muted),
                    )
                }
            }
            groups.forEach { (label, chats) ->
                item(key = "h-$label") {
                    SectionLabel(label, Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp))
                }
                items(chats, key = { it.id }) { c ->
                    val selected = c.id == vm.chatId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .clip(Relay.shape(12.dp))
                            .background(if (selected) Relay.Chip else Relay.Ground)
                            .combinedClickable(
                                role = Role.Button,
                                onClickLabel = openLabel,
                                onLongClickLabel = deleteLabel,
                                onLongClick = { confirmDelete = c },
                                onClick = { onOpen(c.id) },
                            )
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            c.title, Modifier.weight(1f),
                            style = ts(14.5f, if (selected) 600 else 400, lineHeight = 1.3f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        RowDivider()
        DrawerAction(RelayIcons.Server, stringResource(if (terminal) R.string.term_connections else R.string.connections_and_theme), onConnections)
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Relay.Surface,
            shape = Relay.shape(20.dp),
            title = { Text(stringResource(R.string.delete_chat_question), style = ts(18f, 700, family = Relay.Display)) },
            text = { Text(c.title, style = ts(14f, 400, Relay.Muted), maxLines = 3, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = { vm.deleteChat(c.id); confirmDelete = null }) {
                    Text(stringResource(R.string.delete), style = ts(15f, 600, Relay.Error))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel), style = ts(15f, 500)) }
            },
        )
    }
}

@Composable
private fun DrawerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(Relay.shape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = Relay.Ink)
        Text(label, style = ts(15f, 600))
    }
}

/** Today / Yesterday / Previous 7 days / Older, newest first within each. */
private fun groupByDay(res: Resources, chats: List<ChatSummary>): List<Pair<String, List<ChatSummary>>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    return chats.groupBy {
        val day = Instant.ofEpochMilli(it.updatedAt).atZone(zone).toLocalDate()
        when {
            day == today -> res.getString(R.string.today)
            day == today.minusDays(1) -> res.getString(R.string.yesterday)
            day.isAfter(today.minusDays(7)) -> res.getString(R.string.previous_7_days)
            else -> res.getString(R.string.older)
        }
    }.toList()
}
