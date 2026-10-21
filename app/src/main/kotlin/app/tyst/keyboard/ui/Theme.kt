package app.tyst.keyboard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tyst.core.icons.Icon
import app.tyst.core.icons.PathCmd
import app.tyst.core.settings.KeyboardSettings
import app.tyst.core.theme.AccentRoles
import app.tyst.core.theme.KeyboardColors
import app.tyst.core.theme.Tokens

/** The keyboard's resolved colours for the settings app, so both always match. */
@Composable
fun rememberKeyboardColors(settings: KeyboardSettings, wallpaper: Pair<AccentRoles, AccentRoles>?): KeyboardColors {
    val systemDark = isSystemInDarkTheme()
    return remember(settings.themeMode, settings.accentSource, settings.paletteId, systemDark, wallpaper) {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        Tokens.resolve(settings.themeMode, systemDark, settings.accentSource, settings.paletteId, hour, wallpaper)
    }
}

fun colorScheme(c: KeyboardColors): ColorScheme {
    val n = c.n
    val a = c.accent
    return if (c.dark) {
        darkColorScheme(
            primary = Color(a.accent), onPrimary = Color(a.onAccent),
            primaryContainer = Color(a.container), onPrimaryContainer = Color(a.onContainer),
            secondary = Color(a.accent), onSecondary = Color(a.onAccent),
            secondaryContainer = Color(a.container), onSecondaryContainer = Color(a.onContainer),
            background = Color(n.surface), onBackground = Color(n.textPrimary),
            surface = Color(n.surface), onSurface = Color(n.textPrimary),
            surfaceVariant = Color(n.plate), onSurfaceVariant = Color(n.textSecondary),
            surfaceContainer = Color(n.plate), surfaceContainerHigh = Color(n.key), surfaceContainerHighest = Color(n.special),
            surfaceContainerLow = Color(n.strip), surfaceContainerLowest = Color(n.surface),
            outline = Color(n.outline), outlineVariant = Color(n.divider),
            error = Color(n.error), onError = Color(n.surface),
        )
    } else {
        lightColorScheme(
            primary = Color(a.accent), onPrimary = Color(a.onAccent),
            primaryContainer = Color(a.container), onPrimaryContainer = Color(a.onContainer),
            secondary = Color(a.accent), onSecondary = Color(a.onAccent),
            secondaryContainer = Color(a.container), onSecondaryContainer = Color(a.onContainer),
            background = Color(n.surface), onBackground = Color(n.textPrimary),
            surface = Color(n.surface), onSurface = Color(n.textPrimary),
            surfaceVariant = Color(n.plate), onSurfaceVariant = Color(n.textSecondary),
            surfaceContainer = Color(n.plate), surfaceContainerHigh = Color(n.key), surfaceContainerHighest = Color(n.special),
            surfaceContainerLow = Color(n.strip), surfaceContainerLowest = Color(0xFFFFFFFF),
            outline = Color(n.outline), outlineVariant = Color(n.divider),
            error = Color(n.error), onError = Color(0xFFFFFFFF),
        )
    }
}

/** Type scale from the design system's settings tokens. */
val TystTypography = Typography(
    headlineMedium = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Normal),
    headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

@Composable
fun TystTheme(colors: KeyboardColors, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme(colors), typography = TystTypography, content = content)
}

private val iconPaths = HashMap<String, Path>()

private fun composePath(icon: Icon): Path = iconPaths.getOrPut(icon.name) {
    Path().apply {
        for (c in icon.commands) when (c) {
            is PathCmd.Move -> moveTo(c.x, c.y)
            is PathCmd.Line -> lineTo(c.x, c.y)
            is PathCmd.Cubic -> cubicTo(c.x1, c.y1, c.x2, c.y2, c.x, c.y)
            is PathCmd.Quad -> quadraticTo(c.x1, c.y1, c.x, c.y)
            PathCmd.Close -> close()
        }
    }
}

/** Draws one of Tyst's own icons (the same ones the keyboard uses). */
@Composable
fun TIcon(icon: Icon, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, pivot = Offset.Zero) {
            if (icon.filled) drawPath(composePath(icon), tint, style = Fill)
            else drawPath(composePath(icon), tint, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
