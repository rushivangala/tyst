package app.tyst.keyboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.tyst.core.TystEngine
import app.tyst.core.settings.KeyboardSettings
import app.tyst.core.theme.KeyboardColors
import app.tyst.keyboard.data.Graph
import app.tyst.keyboard.ime.KeyboardTheme

enum class Route { HOME, TYPING, TOOLS, STYLE, PRIVACY, WORDS, APPEARANCE, PASSWORDS, PROOF, ABOUT }

/** What every screen needs: settings, the engine (null while loading), colours and the snackbar. */
class Env(
    val graph: Graph,
    val settings: KeyboardSettings,
    val engine: TystEngine?,
    val colors: KeyboardColors,
    val snackbar: SnackbarHostState,
    /** Changes whenever learned data changes, or the activity resumes. */
    val tick: Int,
    val navigate: (Route) -> Unit,
    val back: () -> Unit,
) {
    fun update(change: (KeyboardSettings) -> KeyboardSettings) = graph.settings.update(change)
}

val LocalEnv = staticCompositionLocalOf<Env> { error("No Env") }

@Composable
fun TystRoot(graph: Graph, resumeTick: Int) {
    val settings by graph.settings.flow.collectAsState()
    val engine by graph.engine.collectAsState()
    val revision by graph.revision.collectAsState()
    val context = LocalContext.current
    val wallpaper = remember(resumeTick) { KeyboardTheme.wallpaperRoles(context) }
    val colors = rememberKeyboardColors(settings, wallpaper)
    val snackbar = remember { SnackbarHostState() }
    val stack = remember { mutableStateListOf(Route.HOME) }

    TystTheme(colors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val env = Env(
                    graph, settings, engine, colors, snackbar, revision * 1000 + resumeTick,
                    navigate = { stack.add(it) },
                    back = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                )
                CompositionLocalProvider(LocalEnv provides env) {
                    if (!settings.onboardingDone) {
                        Onboarding()
                    } else {
                        BackHandler(enabled = stack.size > 1) { env.back() }
                        when (stack.last()) {
                            Route.HOME -> HomeScreen()
                            Route.TYPING -> TypingScreen()
                            Route.TOOLS -> ToolsScreen()
                            Route.STYLE -> StyleScreen()
                            Route.PRIVACY -> PrivacyScreen()
                            Route.WORDS -> WordsScreen()
                            Route.APPEARANCE -> AppearanceScreen()
                            Route.PASSWORDS -> PasswordsScreen()
                            Route.PROOF -> ProofScreen()
                            Route.ABOUT -> AboutScreen()
                        }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }
}
