package app.tyst.keyboard.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.tyst.core.correct.AutocorrectStrength
import app.tyst.core.icons.Icons
import app.tyst.core.settings.KeyboardSettings
import app.tyst.core.settings.OneHanded
import app.tyst.core.theme.AccentSource
import app.tyst.core.theme.ThemeMode
import app.tyst.core.theme.Tokens
import app.tyst.keyboard.ime.AutofillLog
import app.tyst.keyboard.ime.VoiceInput

/** A scrolling screen with the standard top bar. */
@Composable
fun Screen(title: String, back: Boolean = true, content: LazyListScope.() -> Unit) {
    val env = LocalEnv.current
    Column(Modifier.fillMaxSize()) {
        TopBar(title, if (back) env.back else null)
        LazyColumn(Modifier.fillMaxWidth().weight(1f), content = content)
    }
}

@Composable
fun HomeScreen() {
    val env = LocalEnv.current
    val context = LocalContext.current
    val s = env.settings
    val e = env.engine
    val enabled = remember(env.tick) { Platform.isEnabled(context) }
    val chosen = remember(env.tick) { Platform.isDefault(context) }
    val manager = remember(env.tick) { Platform.autofillServiceName(context) }
    Screen("Tyst", back = false) {
        item {
            InfoCard(container = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppMark(40)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                chosen -> "Tyst is your keyboard"
                                enabled -> "Tyst is on, but not chosen"
                                else -> "Tyst isn’t switched on"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Works offline · learning ${if (s.learning) "on" else "paused"}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (!chosen) {
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(if (enabled) "Choose Tyst" else "Turn on Tyst") {
                        if (enabled) Platform.showKeyboardPicker(context) else Platform.openKeyboardSettings(context)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OnDeviceBadge("No network permission")
            }
        }
        item {
            SettingRow("Typing & dictionaries", "English · Swedish words ${if (s.swedishWords) "on" else "off"}", Icons.TEXT, onClick = { env.navigate(Route.TYPING) })
            SettingRow("Writing tools", "Rewrite, Proofread, Voice", Icons.SPARKLE, onClick = { env.navigate(Route.TOOLS) })
            val habits = e?.style?.habits()?.size ?: 0
            SettingRow("My style", if (!s.learning) "Paused" else if (habits > 0) "Learning · $habits habits" else "Learning", Icons.SIGNATURE, onClick = { env.navigate(Route.STYLE) })
            SettingRow("Privacy & memory", "${e?.memory?.size ?: 0} remembered items", Icons.SHIELD, onClick = { env.navigate(Route.PRIVACY) })
            SettingRow("Passwords & codes", "${manager ?: "No password manager"} · copied codes", Icons.KEY, onClick = { env.navigate(Route.PASSWORDS) })
            SettingRow("Appearance", "${modeName(s.themeMode)} · ${accentName(s)}", Icons.PALETTE, onClick = { env.navigate(Route.APPEARANCE) })
            SettingRow("Privacy proof", "Permissions, read live from Android", Icons.WIFI_OFF, onClick = { env.navigate(Route.PROOF) })
            SettingRow("About", "Version ${remember { Platform.versionName(context) }}", Icons.INFO, onClick = { env.navigate(Route.ABOUT) })
        }
    }
}

fun modeName(m: ThemeMode) = when (m) {
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.SYSTEM -> "System"
}

fun accentName(s: KeyboardSettings) = when (s.accentSource) {
    AccentSource.WALLPAPER -> "Wallpaper"
    AccentSource.TIME_OF_DAY -> "Changes with the time of day"
    AccentSource.PALETTE -> Tokens.palette(s.paletteId).name
}

@Composable
fun TypingScreen() {
    val env = LocalEnv.current
    val s = env.settings
    val e = env.engine
    Screen("Typing & dictionaries") {
        item {
            SettingRow("English", "Main language for autocorrect and predictions")
            ToggleRow("Swedish words", "Recognise Swedish names and words in English", s.swedishWords) { v -> env.update { it.copy(swedishWords = v) } }
            ToggleRow(
                "Restore å ä ö",
                if (s.swedishWords) "Suggest Västerås when you type Vasteras" else "Turn on Swedish words to use this.",
                s.restoreDiacritics && s.swedishWords, enabled = s.swedishWords,
            ) { v -> env.update { it.copy(restoreDiacritics = v) } }
            SectionLabel("Autocorrect")
            Segmented(
                listOf(AutocorrectStrength.OFF to "Off", AutocorrectStrength.MILD to "Mild", AutocorrectStrength.MEDIUM to "Medium", AutocorrectStrength.STRONG to "Strong"),
                s.autocorrect,
            ) { v -> env.update { it.copy(autocorrect = v) } }
            Body("Backspace right after a correction puts your word back. Undo the same word twice and Tyst learns it.")
            SectionLabel("Keys")
            ToggleRow("Show å ä ö row", "A small row above the letters", s.swedishRow) { v -> env.update { it.copy(swedishRow = v) } }
            ToggleRow("Number row", "Digits above the letters", s.numberRow) { v -> env.update { it.copy(numberRow = v) } }
            ToggleRow("Auto-capitalise", "Capital letter at the start of a sentence", s.autoCapitalize) { v -> env.update { it.copy(autoCapitalize = v) } }
            ToggleRow("Double-space for full stop", "Two spaces type “. ”", s.doubleSpacePeriod) { v -> env.update { it.copy(doubleSpacePeriod = v) } }
            ToggleRow("Vibrate on key press", null, s.haptics) { v -> env.update { it.copy(haptics = v) } }
            ToggleRow("Sound on key press", null, s.sound) { v -> env.update { it.copy(sound = v) } }
            SectionLabel("Dictionaries")
            val words = e?.personal?.dictionaryWords.orEmpty()
            val swedish = words.count { w -> e?.lexicon?.isSwedish(w.word) == true || w.word.any { it in "åäöÅÄÖ" } }
            SettingRow("Personal dictionary", "${words.size} words, $swedish of them Swedish", Icons.BOOK, onClick = { env.navigate(Route.WORDS) })
            val never = e?.personal?.neverCorrectWords.orEmpty()
            SettingRow(
                "Never correct",
                if (never.isEmpty()) "Words Tyst should always leave alone" else never.take(2).joinToString(", ") + if (never.size > 2) " and ${never.size - 2} more" else "",
                Icons.CHECK, onClick = { env.navigate(Route.WORDS) },
            )
        }
    }
}

@Composable
fun ToolsScreen() {
    val env = LocalEnv.current
    val context = LocalContext.current
    val s = env.settings
    val voiceOk = remember { VoiceInput(context, NoopVoice).available() }
    Screen("Writing tools") {
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { OnDeviceBadge() }
            Body("Everything runs on this phone, so it works offline. Tap ✦ above the keys to use them.")
            ToggleRow("Rewrite in my style", "Rewrites selected text the way you usually write", s.writingTools, icon = Icons.SIGNATURE) { v -> env.update { it.copy(writingTools = v) } }
            ToggleRow("Proofread", "Finds spelling and grammar slips, one at a time", s.writingTools, icon = Icons.PROOFREAD) { v -> env.update { it.copy(writingTools = v) } }
            ToggleRow(
                "Voice typing",
                if (voiceOk) "Speech to text without a network" else "This phone has no on-device speech recogniser, so voice typing is off",
                s.voiceTyping && voiceOk, enabled = voiceOk, icon = Icons.MIC,
            ) { v -> env.update { it.copy(voiceTyping = v) } }
            SettingRow("Translate", "English ↔ Swedish · Later", Icons.TEXT, enabled = false)
            SettingRow("Reply", "Draft a reply to copied text · Later", Icons.SMS, enabled = false)
            Body(
                "Rewrite and Proofread use Tyst’s built-in writing rules and what it has learned about your style. " +
                    "They don’t invent facts, and names and Swedish words stay exactly as you typed them.",
            )
        }
    }
}

private object NoopVoice : VoiceInput.Listener {
    override fun onPartial(text: String) {}
    override fun onFinal(text: String) {}
    override fun onLevel(rms: Float) {}
    override fun onError(message: String, permissionMissing: Boolean) {}
}

@Composable
fun AppearanceScreen() {
    val env = LocalEnv.current
    val s = env.settings
    Column(Modifier.fillMaxSize()) {
        TopBar("Appearance", env.back)
        LazyColumn(Modifier.weight(1f)) {
            item {
                SectionLabel("Mode")
                Segmented(listOf(ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark", ThemeMode.SYSTEM to "System"), s.themeMode) { m -> env.update { it.copy(themeMode = m) } }
                SectionLabel("Accent")
                ToggleRow("Match wallpaper", "Uses colours from your wallpaper", s.accentSource == AccentSource.WALLPAPER) { on ->
                    env.update { it.copy(accentSource = if (on) AccentSource.WALLPAPER else AccentSource.PALETTE) }
                }
                ToggleRow(
                    "Change with the time of day",
                    if (s.accentSource == AccentSource.TIME_OF_DAY) {
                        val w = Tokens.windowAt(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
                        "Now: ${w.label.lowercase()} · ${Tokens.palette(w.paletteId).name}"
                    } else "Six palettes through the day. Changes only when the keyboard opens.",
                    s.accentSource == AccentSource.TIME_OF_DAY,
                ) { on -> env.update { it.copy(accentSource = if (on) AccentSource.TIME_OF_DAY else AccentSource.PALETTE) } }
                Swatches()
                SectionLabel("Keys")
                Segmented(KeyboardSettings.KEY_HEIGHTS.zip(listOf("S", "M", "L", "XL")), s.keyHeightDp) { h -> env.update { it.copy(keyHeightDp = h) } }
                ToggleRow("Key borders", "A 1dp outline that makes keys easier to see", s.keyBorders) { v -> env.update { it.copy(keyBorders = v) } }
                ToggleRow("Number row", null, s.numberRow) { v -> env.update { it.copy(numberRow = v) } }
                ToggleRow("Show å ä ö row", null, s.swedishRow) { v -> env.update { it.copy(swedishRow = v) } }
                SectionLabel("One-handed")
                Segmented(listOf(OneHanded.OFF to "Off", OneHanded.LEFT to "Left", OneHanded.RIGHT to "Right"), s.oneHanded) { v -> env.update { it.copy(oneHanded = v) } }
                Spacer(Modifier.height(8.dp))
            }
        }
        KeyboardPreview(s, env.colors)
    }
}

@Composable
fun PasswordsScreen() {
    val env = LocalEnv.current
    val context = LocalContext.current
    val s = env.settings
    val manager = remember(env.tick) { Platform.autofillServiceName(context) }
    Screen("Passwords & codes") {
        item {
            SectionLabel("Passwords")
            ToggleRow("Show password suggestions", "From your password manager, above the keys", s.showPasswordSuggestions) { v -> env.update { it.copy(showPasswordSuggestions = v) } }
            SettingRow(
                "Password manager",
                if (manager != null) "$manager · change in Android settings" else "None chosen · pick one in Android settings",
                Icons.KEY, onClick = { Platform.openAutofillSettings(context) },
            )
            SectionLabel("Verification codes")
            ToggleRow("Show copied codes", "A code you copy appears above the keys", s.showCopiedCodes) { v -> env.update { it.copy(showCopiedCodes = v) } }
            Text("Hide a copied code after", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Segmented(listOf(30 to "30 sec", 60 to "1 min", 180 to "3 min"), s.codeExpirySeconds) { v -> env.update { it.copy(codeExpirySeconds = v) } }
            ToggleRow("Clear the clipboard after filling a code", null, s.clearClipboardAfterCode) { v -> env.update { it.copy(clearClipboardAfterCode = v) } }
            SectionLabel("Clipboard")
            ToggleRow(
                "Show copied text",
                "The last thing you copy appears above the keys for 3 minutes. Kept in memory only; passwords show as dots.",
                s.showCopiedText,
            ) { v -> env.update { it.copy(showCopiedText = v) } }
            AutofillCheck()
            InfoCard {
                Text(
                    "Passwords you type aren’t learned or saved. Saved passwords come from ${manager ?: "your password manager"} through Android, " +
                        "and Tyst can’t read them. A code only appears after you copy it, and it’s never stored.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun ProofScreen() {
    val env = LocalEnv.current
    val context = LocalContext.current
    val rows = remember(env.tick) { Platform.requestedPermissions(context) }
    val internet = rows.any { it.name == android.Manifest.permission.INTERNET }
    val stored = remember(env.tick) { env.graph.store.sizeBytes() }
    Screen("Privacy proof") {
        item {
            Body("This list is read from Android right now, not written by us.")
            SettingRow("Internet", if (internet) "Requested" else "Not requested. Tyst can’t reach the network.", Icons.WIFI_OFF)
            rows.filter { it.name != android.Manifest.permission.INTERNET }.forEach { p ->
                SettingRow(p.label, if (p.granted) "Allowed" else "Not allowed", Icons.SHIELD)
            }
            SettingRow("Google backup", "Off for everything Tyst learns", Icons.LOCK)
            SettingRow("Stored on this phone", "${Platform.formatBytes(stored)}, encrypted with a key in Android Keystore", Icons.PHONE)
            SectionLabel("Check it yourself")
            Body("Android Settings › Apps › Tyst › Permissions")
            Row(Modifier.padding(horizontal = 12.dp)) { LinkButton("Open Tyst in Android settings") { Platform.openAppPermissions(context) } }
        }
    }
}

@Composable
fun AboutScreen() {
    val env = LocalEnv.current
    val context = LocalContext.current
    val e = env.engine
    Screen("About") {
        item {
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppMark(48)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Tyst ${remember { Platform.versionName(context) }}", style = MaterialTheme.typography.titleMedium)
                        Text("Android ${Platform.androidVersion()} · ${Platform.deviceName()}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OnDeviceBadge("No network permission")
            }
            SectionLabel("Models on this phone")
            SettingRow("Prediction", "Built-in phrase model plus what you teach it")
            SettingRow("English word list", "${e?.lexicon?.englishSize ?: "…"} words")
            SettingRow("Swedish word list", "${e?.lexicon?.swedishSize ?: "…"} words, names and places")
            SettingRow("Writing tools", "Built-in rules · no download needed")
            SettingRow("Voice", "Android’s on-device speech recogniser")
            SectionLabel("Licences")
            Body("Word frequencies from wordfreq by Robyn Speer, CC BY-SA 4.0. The word lists Tyst ships are shared under the same licence.")
            Body("Built with Kotlin, AndroidX and Jetpack Compose (Apache License 2.0).")
            Body("Tagline: A quiet keyboard that learns you.")
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** What the keyboard last heard from Android's autofill, to tell a quiet password manager from a Tyst problem. */
@Composable
private fun AutofillCheck() {
    var refresh by remember { mutableIntStateOf(0) }
    val events = remember(refresh, LocalEnv.current.tick) { AutofillLog.all() }
    SectionLabel("Autofill check")
    Body(
        if (events.isEmpty()) "Nothing yet. Open a login screen in any app with Tyst as your keyboard, then come back here."
        else "What happened the last few times a field opened, newest first:",
    )
    events.take(10).forEach { e ->
        Text(e, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
    }
    Row(Modifier.padding(horizontal = 12.dp)) { LinkButton("Refresh") { refresh++ } }
}
