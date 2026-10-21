package app.tyst.keyboard

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tyst.core.correct.AutocorrectStrength
import app.tyst.core.settings.KeyboardSettings
import app.tyst.core.settings.OneHanded
import app.tyst.core.theme.AccentSource
import app.tyst.core.theme.ThemeMode
import app.tyst.keyboard.data.SettingsRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    @Before fun clear() {
        context().getSharedPreferences(SettingsRepository.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun defaultsMatchTheSpec() {
        val s = SettingsRepository(context()).current
        assertThat(s).isEqualTo(KeyboardSettings())
        assertThat(s.memoryEnabled).isFalse() // private memory is opt-in
        assertThat(s.learning).isTrue()
        assertThat(s.accentSource).isEqualTo(AccentSource.WALLPAPER)
        assertThat(s.themeMode).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test fun updatesPersistAndFlow() {
        val repo = SettingsRepository(context())
        repo.update {
            it.copy(
                autocorrect = AutocorrectStrength.STRONG, swedishRow = true, keyHeightDp = 58, memoryEnabled = true,
                accentSource = AccentSource.PALETTE, paletteId = "lingon", oneHanded = OneHanded.LEFT, codeExpirySeconds = 180,
            )
        }
        assertThat(repo.flow.value.paletteId).isEqualTo("lingon")
        val reread = SettingsRepository(context()).current
        assertThat(reread).isEqualTo(repo.current)
    }

    @Test fun badStoredValuesFallBackToDefaults() {
        context().getSharedPreferences(SettingsRepository.FILE, Context.MODE_PRIVATE).edit()
            .putString("autocorrect", "WILD").putInt("key_height", 999).putInt("code_expiry", 7).commit()
        val s = SettingsRepository(context()).current
        assertThat(s.autocorrect).isEqualTo(AutocorrectStrength.MEDIUM)
        assertThat(s.keyHeightDp).isEqualTo(52)
        assertThat(s.codeExpirySeconds).isEqualTo(60)
    }
}
