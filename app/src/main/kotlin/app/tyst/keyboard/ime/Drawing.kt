package app.tyst.keyboard.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.TypedValue
import app.tyst.core.icons.Icon
import app.tyst.core.icons.PathCmd
import app.tyst.core.settings.KeyboardSettings
import app.tyst.core.theme.AccentRoles
import app.tyst.core.theme.KeyboardColors
import app.tyst.core.theme.Tokens
import java.util.Calendar

fun Context.dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
fun Context.sp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

/** Text weights from the design system (Google Sans Flex on Pixel; the system face elsewhere). */
object Type {
    val regular: Typeface = Typeface.create(Typeface.DEFAULT, 400, false)
    val medium: Typeface = Typeface.create(Typeface.DEFAULT, 500, false)
    val semibold: Typeface = Typeface.create(Typeface.DEFAULT, 600, false)
}

/** Draws [Icon]s from core with Android's Path, scaled from the 24-unit grid. */
object IconPainter {
    private val cache = HashMap<String, Path>()

    fun path(icon: Icon): Path = cache.getOrPut(icon.name) {
        Path().apply {
            for (c in icon.commands) when (c) {
                is PathCmd.Move -> moveTo(c.x, c.y)
                is PathCmd.Line -> lineTo(c.x, c.y)
                is PathCmd.Cubic -> cubicTo(c.x1, c.y1, c.x2, c.y2, c.x, c.y)
                is PathCmd.Quad -> quadTo(c.x1, c.y1, c.x, c.y)
                PathCmd.Close -> close()
            }
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Draws [icon] centred at ([cx], [cy]) with the given size in pixels. */
    fun draw(canvas: Canvas, icon: Icon, cx: Float, cy: Float, size: Float, color: Int) {
        val scale = size / 24f
        paint.color = color
        paint.style = if (icon.filled) Paint.Style.FILL else Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.save()
        canvas.translate(cx - size / 2, cy - size / 2)
        canvas.scale(scale, scale)
        canvas.drawPath(path(icon), paint)
        canvas.restore()
    }
}

/** Resolves the keyboard's colours for this show from settings, the system theme and the clock. */
object KeyboardTheme {
    fun resolve(context: Context, settings: KeyboardSettings): KeyboardColors {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return Tokens.resolve(settings.themeMode, night, settings.accentSource, settings.paletteId, hour, wallpaperRoles(context))
    }

    /** The system's wallpaper-based accent, mapped as in the design system (tone 40/80, 100/20, 90/30, 10/90). */
    fun wallpaperRoles(context: Context): Pair<AccentRoles, AccentRoles>? = try {
        fun c(id: Int) = context.getColor(id)
        val light = AccentRoles(
            accent = c(android.R.color.system_accent1_600), onAccent = c(android.R.color.system_accent1_0),
            container = c(android.R.color.system_accent1_100), onContainer = c(android.R.color.system_accent1_900),
        )
        val dark = AccentRoles(
            accent = c(android.R.color.system_accent1_200), onAccent = c(android.R.color.system_accent1_800),
            container = c(android.R.color.system_accent1_700), onContainer = c(android.R.color.system_accent1_100),
        )
        light to dark
    } catch (_: Exception) {
        null
    }
}

fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)

fun blend(from: Int, to: Int, t: Float): Int {
    fun ch(c: Int, s: Int) = c shr s and 0xFF
    fun mix(s: Int) = (ch(from, s) + (ch(to, s) - ch(from, s)) * t).toInt().coerceIn(0, 255)
    return (mix(24) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
}
