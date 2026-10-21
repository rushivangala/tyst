package app.tyst.core

import app.tyst.core.theme.AccentRoles
import app.tyst.core.theme.AccentSource
import app.tyst.core.theme.ThemeMode
import app.tyst.core.theme.Tokens
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

class ThemeTest {
    @Test fun everyPaletteMeetsAaInBothModes() {
        for (p in Tokens.PALETTES) for (dark in listOf(false, true)) {
            val n = Tokens.neutrals(dark)
            val r = p.roles(dark)
            val where = "${p.name} ${if (dark) "dark" else "light"}"
            assertWithMessage("$where on-accent").that(Tokens.contrast(r.accent, r.onAccent)).isAtLeast(4.5)
            assertWithMessage("$where accent on key").that(Tokens.contrast(r.accent, n.key)).isAtLeast(4.5)
            assertWithMessage("$where container").that(Tokens.contrast(r.onContainer, r.container)).isAtLeast(4.5)
            assertWithMessage("$where guard").that(Tokens.passesGuard(r, n)).isTrue()
        }
    }

    @Test fun textAndOutlineContrast() {
        for (dark in listOf(false, true)) {
            val n = Tokens.neutrals(dark)
            assertThat(Tokens.contrast(n.textPrimary, n.key)).isAtLeast(7.0)
            assertThat(Tokens.contrast(n.textSecondary, n.plate)).isAtLeast(4.5)
            assertThat(Tokens.contrast(n.textSecondary, n.special)).isAtLeast(4.5)
            // The review asked for a ≥3:1 outline for interactive edges.
            assertWithMessage("outline on plate dark=$dark").that(Tokens.contrast(n.outline, n.plate)).isAtLeast(3.0)
            assertWithMessage("outline on key dark=$dark").that(Tokens.contrast(n.outline, n.key)).isAtLeast(3.0)
        }
    }

    @Test fun timeOfDayWindows() {
        assertThat(Tokens.windowAt(6).paletteId).isEqualTo("barnsten")
        assertThat(Tokens.windowAt(9).paletteId).isEqualTo("hav")
        assertThat(Tokens.windowAt(13).paletteId).isEqualTo("mossa")
        assertThat(Tokens.windowAt(17).paletteId).isEqualTo("lingon")
        assertThat(Tokens.windowAt(20).paletteId).isEqualTo("skymning")
        assertThat(Tokens.windowAt(23).paletteId).isEqualTo("fjall")
        assertThat(Tokens.windowAt(2).paletteId).isEqualTo("fjall")
        for (h in 0..23) Tokens.windowAt(h)
    }

    @Test fun resolveHonoursModeAndFallsBackFromBadWallpaper() {
        val light = Tokens.resolve(ThemeMode.LIGHT, systemDark = true, source = AccentSource.PALETTE, paletteId = "lingon", hour = 12)
        assertThat(light.dark).isFalse()
        assertThat(light.paletteId).isEqualTo("lingon")
        val system = Tokens.resolve(ThemeMode.SYSTEM, systemDark = true, source = AccentSource.PALETTE, paletteId = "nope", hour = 12)
        assertThat(system.dark).isTrue()
        assertThat(system.paletteId).isEqualTo("hav")

        val washedOut = AccentRoles(0xFFDDDDDD.toInt(), 0xFFFFFFFF.toInt(), 0xFFEEEEEE.toInt(), 0xFFFFFFFF.toInt())
        val r = Tokens.resolve(ThemeMode.LIGHT, false, AccentSource.WALLPAPER, "hav", 12, washedOut to washedOut)
        assertThat(r.paletteId).isEqualTo("hav")
        val good = Tokens.PALETTES[1]
        val ok = Tokens.resolve(ThemeMode.DARK, false, AccentSource.WALLPAPER, "hav", 12, good.light to good.dark)
        assertThat(ok.paletteId).isEqualTo("wallpaper")
        assertThat(ok.accent).isEqualTo(good.dark)
        val tod = Tokens.resolve(ThemeMode.LIGHT, false, AccentSource.TIME_OF_DAY, "hav", 20)
        assertThat(tod.paletteId).isEqualTo("skymning")
    }
}
