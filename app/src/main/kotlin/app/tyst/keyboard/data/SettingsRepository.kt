package app.tyst.keyboard.data

import android.content.Context
import android.content.SharedPreferences
import app.tyst.core.settings.KeyboardSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Settings in SharedPreferences, exposed as a [StateFlow]. Settings hold no personal text. */
class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read(prefs))

    val flow: StateFlow<KeyboardSettings> = state.asStateFlow()
    val current: KeyboardSettings get() = state.value

    fun update(change: (KeyboardSettings) -> KeyboardSettings) {
        val next = change(state.value)
        if (next == state.value) return
        write(prefs, next)
        state.value = next
    }

    companion object {
        const val FILE = "tyst_settings"

        private inline fun <reified E : Enum<E>> SharedPreferences.enum(key: String, default: E): E =
            getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

        fun read(p: SharedPreferences): KeyboardSettings {
            val d = KeyboardSettings()
            return KeyboardSettings(
                autocorrect = p.enum("autocorrect", d.autocorrect),
                swedishWords = p.getBoolean("swedish_words", d.swedishWords),
                restoreDiacritics = p.getBoolean("restore_diacritics", d.restoreDiacritics),
                swedishRow = p.getBoolean("swedish_row", d.swedishRow),
                numberRow = p.getBoolean("number_row", d.numberRow),
                keyHeightDp = p.getInt("key_height", d.keyHeightDp).takeIf { it in KeyboardSettings.KEY_HEIGHTS } ?: d.keyHeightDp,
                autoCapitalize = p.getBoolean("auto_capitalize", d.autoCapitalize),
                doubleSpacePeriod = p.getBoolean("double_space_period", d.doubleSpacePeriod),
                haptics = p.getBoolean("haptics", d.haptics),
                sound = p.getBoolean("sound", d.sound),
                keyBorders = p.getBoolean("key_borders", d.keyBorders),
                learning = p.getBoolean("learning", d.learning),
                memoryEnabled = p.getBoolean("memory", d.memoryEnabled),
                memoryExpiryDays = p.getInt("memory_expiry_days", d.memoryExpiryDays),
                showPasswordSuggestions = p.getBoolean("password_suggestions", d.showPasswordSuggestions),
                showCopiedCodes = p.getBoolean("copied_codes", d.showCopiedCodes),
                codeExpirySeconds = p.getInt("code_expiry", d.codeExpirySeconds).takeIf { it in KeyboardSettings.CODE_EXPIRY_OPTIONS } ?: d.codeExpirySeconds,
                clearClipboardAfterCode = p.getBoolean("clear_clipboard", d.clearClipboardAfterCode),
                themeMode = p.enum("theme_mode", d.themeMode),
                accentSource = p.enum("accent_source", d.accentSource),
                paletteId = p.getString("palette", d.paletteId) ?: d.paletteId,
                oneHanded = p.enum("one_handed", d.oneHanded),
                writingTools = p.getBoolean("writing_tools", d.writingTools),
                voiceTyping = p.getBoolean("voice", d.voiceTyping),
                onboardingDone = p.getBoolean("onboarding_done", d.onboardingDone),
            )
        }

        fun write(p: SharedPreferences, s: KeyboardSettings) {
            p.edit()
                .putString("autocorrect", s.autocorrect.name)
                .putBoolean("swedish_words", s.swedishWords)
                .putBoolean("restore_diacritics", s.restoreDiacritics)
                .putBoolean("swedish_row", s.swedishRow)
                .putBoolean("number_row", s.numberRow)
                .putInt("key_height", s.keyHeightDp)
                .putBoolean("auto_capitalize", s.autoCapitalize)
                .putBoolean("double_space_period", s.doubleSpacePeriod)
                .putBoolean("haptics", s.haptics)
                .putBoolean("sound", s.sound)
                .putBoolean("key_borders", s.keyBorders)
                .putBoolean("learning", s.learning)
                .putBoolean("memory", s.memoryEnabled)
                .putInt("memory_expiry_days", s.memoryExpiryDays)
                .putBoolean("password_suggestions", s.showPasswordSuggestions)
                .putBoolean("copied_codes", s.showCopiedCodes)
                .putInt("code_expiry", s.codeExpirySeconds)
                .putBoolean("clear_clipboard", s.clearClipboardAfterCode)
                .putString("theme_mode", s.themeMode.name)
                .putString("accent_source", s.accentSource.name)
                .putString("palette", s.paletteId)
                .putString("one_handed", s.oneHanded.name)
                .putBoolean("writing_tools", s.writingTools)
                .putBoolean("voice", s.voiceTyping)
                .putBoolean("onboarding_done", s.onboardingDone)
                .apply()
        }
    }
}
