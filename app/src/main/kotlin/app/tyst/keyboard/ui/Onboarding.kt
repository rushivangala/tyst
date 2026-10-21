package app.tyst.keyboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tyst.core.icons.Icons
import app.tyst.core.theme.AccentSource
import app.tyst.core.theme.ThemeMode
import app.tyst.core.theme.Tokens
import kotlinx.coroutines.delay

private const val STEPS = 5

@Composable
fun Onboarding() {
    val env = LocalEnv.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step-- }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
            if (step > 0) {
                Text("${step + 1} of $STEPS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
            }
            when (step) {
                0 -> Welcome()
                1 -> TurnOn()
                2 -> PrivacyStep()
                3 -> LookStep()
                else -> ReadyStep()
            }
        }
        if (step == 3) KeyboardPreview(env.settings, env.colors)
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
            when (step) {
                0 -> PrimaryButton("Get started") { step = 1 }
                STEPS - 1 -> PrimaryButton("Done") { env.update { it.copy(onboardingDone = true) } }
                else -> PrimaryButton("Continue") { step++ }
            }
        }
    }
}

@Composable
private fun Headline(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Para(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Welcome() {
    val context = LocalContext.current
    val env = LocalEnv.current
    val internet = remember(env.tick) { Platform.hasInternetPermission(context) }
    Spacer(Modifier.height(40.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppMark(64)
        Spacer(Modifier.width(16.dp))
        Text("Tyst", style = MaterialTheme.typography.headlineMedium)
    }
    Spacer(Modifier.height(24.dp))
    Para("A keyboard that learns how you write and never goes online.")
    InfoCard(Modifier.padding(0.dp)) {
        Text("What Tyst can access", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        AccessRow(Icons.WIFI_OFF, "Internet", if (internet) "Requested" else "Not requested")
        AccessRow(Icons.MIC, "Microphone", "Asks the first time you use voice typing")
    }
    Spacer(Modifier.height(8.dp))
    Para("The word lists are part of the app, so Tyst works fully from the first minute. Nothing is downloaded later.")
}

@Composable
private fun AccessRow(icon: app.tyst.core.icons.Icon, title: String, sub: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TIcon(icon, MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The launcher icon (1a): a keycap whose spacebar is a flat, silent line. */
@Composable
fun AppMark(sizeDp: Int) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(sizeDp.dp).clip(RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(sizeDp.dp)) {
            val s = size.minDimension / 108f
            drawCircle(Color(0xFF0D5C58))
            drawRoundRect(
                Color.White, topLeft = androidx.compose.ui.geometry.Offset(31 * s, 34 * s),
                size = androidx.compose.ui.geometry.Size(46 * s, 40 * s), cornerRadius = androidx.compose.ui.geometry.CornerRadius(13 * s),
            )
            drawRoundRect(
                Color(0xFF0D5C58), topLeft = androidx.compose.ui.geometry.Offset(42 * s, 58 * s),
                size = androidx.compose.ui.geometry.Size(24 * s, 6 * s), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3 * s),
            )
        }
    }
}

@Composable
private fun TurnOn() {
    val context = LocalContext.current
    val env = LocalEnv.current
    var poll by remember { mutableIntStateOf(0) }
    // The picker is a system dialog, so keep checking while this step is open.
    LaunchedEffect(Unit) { while (true) { delay(1000); poll++ } }
    val enabled = remember(env.tick, poll) { Platform.isEnabled(context) }
    val chosen = remember(env.tick, poll) { Platform.isDefault(context) }
    Headline("Turn on Tyst")
    Para("Two quick steps in Android settings. Each one ticks off when you come back.")
    StepRow("Enable Tyst", if (enabled) "Done" else "Opens Android’s keyboard settings", enabled) { Platform.openKeyboardSettings(context) }
    StepRow("Choose Tyst as your keyboard", if (chosen) "Done" else "Opens the keyboard picker", chosen, active = enabled) { Platform.showKeyboardPicker(context) }
    Spacer(Modifier.height(12.dp))
    InfoCard(Modifier.padding(0.dp)) {
        Text(
            "Android warns that keyboards can collect what you type. Every keyboard gets this warning. Tyst keeps everything on this phone.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun StepRow(title: String, sub: String, done: Boolean, active: Boolean = true, onClick: () -> Unit) {
    SettingRow(
        title = title, subtitle = sub, enabled = active && !done, onClick = onClick,
        trailing = { TIcon(if (done) Icons.CHECK else Icons.CHEVRON_RIGHT, if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

@Composable
private fun PrivacyStep() {
    val env = LocalEnv.current
    Headline("Everything you type stays on this phone.")
    Para("Tyst has no internet permission, so it can’t send what you write anywhere. What it learns is also kept out of Google backup.")
    SectionLabel("You choose what it learns")
    ToggleRow("Learn my writing style", "From what you type with Tyst. You can see all of it in My style.", env.settings.learning) { v -> env.update { it.copy(learning = v) } }
    ToggleRow("Private memory", "Remembers addresses and booking codes you type. Off until you turn it on.", env.settings.memoryEnabled) { v -> env.update { it.copy(memoryEnabled = v) } }
    Spacer(Modifier.height(8.dp))
    Body("Check it yourself: Android Settings › Apps › Tyst › Permissions.")
}

@Composable
private fun LookStep() {
    val env = LocalEnv.current
    val s = env.settings
    Headline("Pick a look")
    Para("Change it any time in Settings.")
    Segmented(listOf(ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark", ThemeMode.SYSTEM to "System"), s.themeMode) { m -> env.update { it.copy(themeMode = m) } }
    ToggleRow("Match wallpaper", "Takes the accent from your wallpaper", s.accentSource == AccentSource.WALLPAPER) { on ->
        env.update { it.copy(accentSource = if (on) AccentSource.WALLPAPER else AccentSource.PALETTE) }
    }
    Swatches()
}

@Composable
fun Swatches() {
    val env = LocalEnv.current
    val s = env.settings
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Tokens.PALETTES.forEach { p ->
            AccentSwatch(p, env.colors.dark, selected = s.accentSource == AccentSource.PALETTE && s.paletteId == p.id) {
                env.update { it.copy(accentSource = AccentSource.PALETTE, paletteId = p.id) }
            }
        }
    }
}

@Composable
private fun ReadyStep() {
    var text by remember { mutableStateOf("") }
    Headline("You’re set")
    Para("Try it below. Type “Can we meet at Järntorget around” and watch the suggestions.")
    OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Practice") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
    Spacer(Modifier.height(16.dp))
    InfoCard(Modifier.padding(0.dp)) {
        Text("Tips", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text("Hold a for å and ä, and o for ö.", style = MaterialTheme.typography.bodyMedium)
        Text("Backspace right after a correction puts your word back.", style = MaterialTheme.typography.bodyMedium)
        Text("Hold the comma for emoji, settings and one-handed mode.", style = MaterialTheme.typography.bodyMedium)
        Text("Tap ✦ above the keys to rewrite or proofread.", style = MaterialTheme.typography.bodyMedium)
    }
}
