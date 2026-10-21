package app.tyst.core.settings

import app.tyst.core.correct.AutocorrectStrength
import app.tyst.core.theme.AccentSource
import app.tyst.core.theme.ThemeMode

enum class OneHanded { OFF, LEFT, RIGHT }

/** Every user setting with its default. The app layer stores these in SharedPreferences. */
data class KeyboardSettings(
    val autocorrect: AutocorrectStrength = AutocorrectStrength.MEDIUM,
    val swedishWords: Boolean = true,
    val restoreDiacritics: Boolean = true,
    val swedishRow: Boolean = false,
    val numberRow: Boolean = false,
    val keyHeightDp: Int = 52,
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val haptics: Boolean = true,
    val sound: Boolean = false,
    val keyBorders: Boolean = false,
    /** "Learn my writing style": words, phrases and habits. Off = paused, nothing is deleted. */
    val learning: Boolean = true,
    /** Private memory is opt-in. */
    val memoryEnabled: Boolean = false,
    val memoryExpiryDays: Int = 90,
    val showPasswordSuggestions: Boolean = true,
    val showCopiedCodes: Boolean = true,
    val codeExpirySeconds: Int = 60,
    val clearClipboardAfterCode: Boolean = false,
    /** Offer the last copied text as a paste chip for 3 minutes. */
    val showCopiedText: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentSource: AccentSource = AccentSource.WALLPAPER,
    val paletteId: String = "hav",
    val oneHanded: OneHanded = OneHanded.OFF,
    val writingTools: Boolean = true,
    val voiceTyping: Boolean = true,
    val onboardingDone: Boolean = false,
) {
    companion object {
        val KEY_HEIGHTS = listOf(46, 52, 58, 60)
        val CODE_EXPIRY_OPTIONS = listOf(30, 60, 180)
        val MEMORY_EXPIRY_OPTIONS = listOf(30, 90, 0)
    }
}
