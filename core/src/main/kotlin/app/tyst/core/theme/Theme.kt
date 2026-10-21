package app.tyst.core.theme

import kotlin.math.pow

/** Colours are ARGB ints, as Android uses them. */
data class Neutrals(
    val surface: Int, val plate: Int, val key: Int, val keyPressed: Int, val special: Int, val space: Int,
    val strip: Int, val popup: Int, val textPrimary: Int, val textSecondary: Int, val divider: Int,
    /** Outline for interactive edges at ≥ 3:1, added after the accessibility review. */
    val outline: Int, val error: Int, val success: Int, val keyEdge: Int,
)

data class AccentRoles(val accent: Int, val onAccent: Int, val container: Int, val onContainer: Int)

data class Palette(val id: String, val name: String, val gloss: String, val light: AccentRoles, val dark: AccentRoles) {
    fun roles(dark: Boolean) = if (dark) this.dark else light
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AccentSource { WALLPAPER, PALETTE, TIME_OF_DAY }

/** Fully resolved colours for one keyboard show. */
data class KeyboardColors(val dark: Boolean, val n: Neutrals, val accent: AccentRoles, val paletteId: String)

private fun c(hex: String): Int = (0xFF000000 or hex.removePrefix("#").toLong(16)).toInt()

object Tokens {
    val LIGHT = Neutrals(
        surface = c("#F7F9F8"), plate = c("#E8ECEA"), key = c("#FFFFFF"), keyPressed = c("#C4CDCA"),
        special = c("#D2DAD7"), space = c("#FFFFFF"), strip = c("#E8ECEA"), popup = c("#FFFFFF"),
        textPrimary = c("#161D1B"), textSecondary = c("#46524F"), divider = c("#C9D2CF"),
        outline = c("#7A8784"), error = c("#B3261E"), success = c("#1F6C3B"), keyEdge = c("#B9C3C0"),
    )
    val DARK = Neutrals(
        surface = c("#0E1211"), plate = c("#141918"), key = c("#2B3230"), keyPressed = c("#4D5754"),
        special = c("#3A4341"), space = c("#2B3230"), strip = c("#141918"), popup = c("#3A4341"),
        textPrimary = c("#E2E8E6"), textSecondary = c("#A7B2AF"), divider = c("#2E3634"),
        outline = c("#899491"), error = c("#F2B8B5"), success = c("#8DD5A1"), keyEdge = c("#0A0D0C"),
    )

    val PALETTES = listOf(
        Palette("hav", "Hav", "sea", AccentRoles(c("#0D5C58"), c("#FFFFFF"), c("#CDEBE6"), c("#00201D")), AccentRoles(c("#86D4CA"), c("#00332F"), c("#164A45"), c("#CDEBE6"))),
        Palette("fjall", "Fjäll", "fell", AccentRoles(c("#36598F"), c("#FFFFFF"), c("#D8E2FF"), c("#0B1C3A")), AccentRoles(c("#AAC7FF"), c("#0A2C5C"), c("#23436F"), c("#D8E2FF"))),
        Palette("mossa", "Mossa", "moss", AccentRoles(c("#43602C"), c("#FFFFFF"), c("#D4EBBD"), c("#0E2003")), AccentRoles(c("#B3D398"), c("#1D3608"), c("#2E4A1A"), c("#D4EBBD"))),
        Palette("lingon", "Lingon", "lingonberry", AccentRoles(c("#A02541"), c("#FFFFFF"), c("#FFD9DD"), c("#3F0012")), AccentRoles(c("#FFB2BC"), c("#5C0F24"), c("#7C1D33"), c("#FFD9DD"))),
        Palette("barnsten", "Bärnsten", "amber", AccentRoles(c("#7F4D00"), c("#FFFFFF"), c("#FFDDB6"), c("#2A1700")), AccentRoles(c("#FFB866"), c("#462A00"), c("#653E00"), c("#FFDDB6"))),
        Palette("skymning", "Skymning", "dusk", AccentRoles(c("#5D4A8C"), c("#FFFFFF"), c("#E8DDFF"), c("#1A0C40")), AccentRoles(c("#CEBDFF"), c("#2F1F5B"), c("#463673"), c("#E8DDFF"))),
    )
    val DEFAULT = PALETTES.first()

    fun palette(id: String?): Palette = PALETTES.firstOrNull { it.id == id } ?: DEFAULT
    fun neutrals(dark: Boolean) = if (dark) DARK else LIGHT

    /** Time-of-day windows (start hour inclusive, end exclusive; night wraps past midnight). */
    data class Window(val start: Int, val end: Int, val paletteId: String, val label: String)

    val WINDOWS = listOf(
        Window(5, 8, "barnsten", "Dawn"), Window(8, 12, "hav", "Morning"), Window(12, 16, "mossa", "Afternoon"),
        Window(16, 19, "lingon", "Early evening"), Window(19, 22, "skymning", "Dusk"), Window(22, 29, "fjall", "Night"),
    )

    fun windowAt(hour: Int): Window {
        val h = ((hour % 24) + 24) % 24
        return WINDOWS.first { h in it.start until it.end || h + 24 in it.start until it.end }
    }

    /**
     * Resolves the colours for a keyboard show. [wallpaper] holds the system's dynamic accent roles
     * when available; it is only used if it passes the contrast guard, otherwise Hav is used.
     */
    fun resolve(
        mode: ThemeMode, systemDark: Boolean, source: AccentSource, paletteId: String,
        hour: Int, wallpaper: Pair<AccentRoles, AccentRoles>? = null,
    ): KeyboardColors {
        val dark = when (mode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        val n = neutrals(dark)
        return when (source) {
            AccentSource.WALLPAPER -> {
                val roles = wallpaper?.let { if (dark) it.second else it.first }
                if (roles != null && passesGuard(roles, n)) KeyboardColors(dark, n, roles, "wallpaper")
                else KeyboardColors(dark, n, DEFAULT.roles(dark), DEFAULT.id)
            }
            AccentSource.PALETTE -> palette(paletteId).let { KeyboardColors(dark, n, it.roles(dark), it.id) }
            AccentSource.TIME_OF_DAY -> palette(windowAt(hour).paletteId).let { KeyboardColors(dark, n, it.roles(dark), it.id) }
        }
    }

    /** Accent must reach 4.5:1 against on-accent and against the key fill. */
    fun passesGuard(roles: AccentRoles, n: Neutrals): Boolean =
        contrast(roles.accent, roles.onAccent) >= 4.5 && contrast(roles.accent, n.key) >= 4.5 &&
            contrast(roles.onContainer, roles.container) >= 4.5

    fun luminance(argb: Int): Double {
        fun ch(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(argb shr 16 and 0xFF) + 0.7152 * ch(argb shr 8 and 0xFF) + 0.0722 * ch(argb and 0xFF)
    }

    fun contrast(a: Int, b: Int): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
}
