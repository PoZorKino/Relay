package app.relay.chat

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.relay.chat.ui.AddConnectionScreen
import app.relay.chat.ui.ChatScreen
import app.relay.chat.ui.ConnectionsScreen
import app.relay.chat.ui.ModelPickerSheet
import app.relay.chat.ui.HistoryDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import app.relay.chat.ui.PlanScreen
import app.relay.chat.data.Plan
import app.relay.chat.ui.theme.Relay
import app.relay.chat.ui.theme.RelayTheme
import app.relay.chat.ui.theme.paletteFor
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.relay.chat.ui.ts
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Chat : Screen
    data object Connections : Screen
    data class Edit(val id: String?) : Screen
    data class PlanSetup(val plan: Plan) : Screen
}

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            // Snapshot state: every token read below recomposes when the theme changes.
            Relay.palette = paletteFor(vm.theme)
            val dark = Relay.palette.dark
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
                window.setBackgroundDrawable(ColorDrawable(Relay.Ground.toArgb()))
            }
            RelayTheme {
                val stack = remember { mutableStateListOf<Screen>(Screen.Chat) }
                var picker by remember { mutableStateOf(false) }
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val toast: (String) -> Unit = { msg ->
                    scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) }
                }
                fun push(s: Screen) { stack += s }
                fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

                BackHandler(enabled = stack.size > 1) { pop() }

                Box(Modifier.fillMaxSize()) {
                    val top = stack.last()
                    val depth = stack.size
                    AnimatedContent(
                        targetState = top to depth,
                        transitionSpec = {
                            val forward = targetState.second >= initialState.second
                            val dir = if (forward) 1 else -1
                            (slideInHorizontally(tween(260)) { it * dir / 4 } + fadeIn(tween(200)))
                                .togetherWith(slideOutHorizontally(tween(260)) { -it * dir / 4 } + fadeOut(tween(160)))
                        },
                        label = "nav",
                    ) { (screen, _) ->
                        when (screen) {
                            Screen.Chat -> {
                                val drawer = rememberDrawerState(DrawerValue.Closed)
                                fun closeThen(action: () -> Unit) {
                                    action()
                                    scope.launch { drawer.close() }
                                }
                                BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
                                ModalNavigationDrawer(
                                    drawerState = drawer,
                                    // Swipe right anywhere to open, left to close. Code blocks still
                                    // scroll sideways: they consume the drag before the drawer sees it.
                                    gesturesEnabled = true,
                                    scrimColor = Relay.Scrim,
                                    drawerContent = {
                                        HistoryDrawer(
                                            vm,
                                            onNewChat = { closeThen { vm.newChat() } },
                                            onOpen = { id -> closeThen { vm.openChat(id) } },
                                            onConnections = { closeThen { push(Screen.Connections) } },
                                        )
                                    },
                                ) {
                                    ChatScreen(
                                        vm,
                                        onMenu = { scope.launch { drawer.open() } },
                                        onPickModel = { picker = true },
                                        onAddConnection = { push(Screen.Edit(null)) },
                                        toast = toast,
                                    )
                                }
                            }
                            Screen.Connections -> ConnectionsScreen(
                                vm,
                                onBack = ::pop,
                                onAdd = { push(Screen.Edit(null)) },
                                onEdit = { push(Screen.Edit(it)) },
                                onPlan = { push(Screen.PlanSetup(it)) },
                                onLanguage = { tag ->
                                    Lang.set(this@MainActivity, tag)
                                    recreate()
                                },
                            )
                            is Screen.Edit -> AddConnectionScreen(vm, screen.id, onClose = ::pop)
                            is Screen.PlanSetup -> PlanScreen(vm, screen.plan, onClose = ::pop, toast = toast)
                        }
                    }

                    if (picker) {
                        ModelPickerSheet(
                            vm,
                            onDismiss = { picker = false },
                            onAddConnection = { push(Screen.Edit(null)) },
                        )
                    }

                    SnackbarHost(
                        snackbar,
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp),
                    ) { data ->
                        Snackbar(
                            shape = Relay.shape(12.dp),
                            containerColor = Relay.StrongBg,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) { Text(data.visuals.message, style = ts(14f, 500, Relay.StrongFg)) }
                    }
                }
            }
        }
    }
}
