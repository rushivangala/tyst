package app.tyst.core.icons

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Absolute path commands; arcs are already converted to cubic curves. */
sealed class PathCmd {
    data class Move(val x: Float, val y: Float) : PathCmd()
    data class Line(val x: Float, val y: Float) : PathCmd()
    data class Cubic(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : PathCmd()
    data class Quad(val x1: Float, val y1: Float, val x: Float, val y: Float) : PathCmd()
    data object Close : PathCmd()
}

/**
 * A line icon on a 24 × 24 grid, drawn with a 2-unit round stroke unless [filled].
 * Tyst draws its own icons so the keyboard and the settings app look the same and need no icon library.
 */
data class Icon(val name: String, val path: String, val filled: Boolean = false) {
    val commands: List<PathCmd> by lazy { SvgPath.parse(path) }
}

object Icons {
    val SHIFT = Icon("shift", "M12 4 L4 12 H8.5 V19 H15.5 V12 H20 Z")
    val SHIFT_ON = Icon("shift_on", "M12 4 L4 12 H8.5 V19 H15.5 V12 H20 Z", filled = true)
    val CAPS_LOCK = Icon("caps_lock", "M12 3 L4 11 H8.5 V16 H15.5 V11 H20 Z M8.5 19 H15.5 V21 H8.5 Z", filled = true)
    val BACKSPACE = Icon("backspace", "M8 5 H20 A1.5 1.5 0 0 1 21.5 6.5 V17.5 A1.5 1.5 0 0 1 20 19 H8 L2.5 12 Z M11.5 9 L17 14.5 M17 9 L11.5 14.5")
    val RETURN = Icon("return", "M20 5 V12 A2 2 0 0 1 18 14 H5 M9 9.5 L4.5 14 L9 18.5")
    val SEND = Icon("send", "M3.5 20 L21 12 L3.5 4 L6 12 Z M6 12 H13")
    val SEARCH = Icon("search", "M4 10.5 A6.5 6.5 0 1 0 17 10.5 A6.5 6.5 0 1 0 4 10.5 Z M15.3 15.3 L20.5 20.5")
    val CHECK = Icon("check", "M4.5 12.5 L9.5 17.5 L19.5 6.5")
    val ARROW_FORWARD = Icon("arrow_forward", "M4 12 H19 M13 6 L19 12 L13 18")
    val ARROW_BACK = Icon("arrow_back", "M20 12 H5 M11 6 L5 12 L11 18")
    val EMOJI = Icon("emoji", "M3 12 A9 9 0 1 0 21 12 A9 9 0 1 0 3 12 Z M8.5 14.5 Q12 18 15.5 14.5 M9 9.5 V10 M15 9.5 V10")
    val MIC = Icon("mic", "M9 5 A3 3 0 0 1 15 5 V11 A3 3 0 0 1 9 11 Z M5.5 11 A6.5 6.5 0 0 0 18.5 11 M12 17.5 V21")
    val SPARKLE = Icon("sparkle", "M12 3 L13.8 10.2 L21 12 L13.8 13.8 L12 21 L10.2 13.8 L3 12 L10.2 10.2 Z", filled = true)
    val LOCK = Icon("lock", "M6 11 H18 V20 H6 Z M8.5 11 V8 A3.5 3.5 0 0 1 15.5 8 V11")
    val KEY = Icon("key", "M4 13 A3.5 3.5 0 1 0 11 13 A3.5 3.5 0 1 0 4 13 Z M11 13 H21 M17.5 13 V16 M20.5 13 V15.5")
    val SMS = Icon("sms", "M4 5 H20 V16 H9 L5 20 V16 H4 Z M8 10.5 H16")
    val CLOSE = Icon("close", "M6 6 L18 18 M18 6 L6 18")
    val SETTINGS = Icon("settings", "M8.5 12 A3.5 3.5 0 1 0 15.5 12 A3.5 3.5 0 1 0 8.5 12 Z M12 2.5 V5 M12 19 V21.5 M2.5 12 H5 M19 12 H21.5 M5.3 5.3 L7.1 7.1 M16.9 16.9 L18.7 18.7 M5.3 18.7 L7.1 16.9 M16.9 7.1 L18.7 5.3")
    val KEYBOARD = Icon("keyboard", "M3 6 H21 V18 H3 Z M7 14.5 H17 M6.5 10 H7 M10 10 H10.5 M13.5 10 H14 M17 10 H17.5")
    val CHEVRON_LEFT = Icon("chevron_left", "M15 5 L8 12 L15 19")
    val CHEVRON_RIGHT = Icon("chevron_right", "M9 5 L16 12 L9 19")
    val OPEN_IN_FULL = Icon("open_in_full", "M14 4 H20 V10 M20 4 L13 11 M10 20 H4 V14 M4 20 L11 13")
    val PHONE = Icon("phone", "M7 2.5 H17 V21.5 H7 Z M10.5 18.5 H13.5")
    val UNDO = Icon("undo", "M9 7 L4.5 11.5 L9 16 M5 11.5 H15 A4.5 4.5 0 0 1 15 20.5 H12")
    val COPY = Icon("copy", "M8 8 H19 V20 H8 Z M5 16 V4 H15")
    val DELETE = Icon("delete", "M5 7 H19 M9.5 7 V4.5 H14.5 V7 M7 7 L8 20 H16 L17 7")
    val SHIELD = Icon("shield", "M12 3 L19.5 6 V11.5 C19.5 16 16.3 19.6 12 21 C7.7 19.6 4.5 16 4.5 11.5 V6 Z")
    val BOOK = Icon("book", "M5 4.5 H17.5 A1.5 1.5 0 0 1 19 6 V20 H6.5 A1.5 1.5 0 0 1 5 18.5 Z M5 17 H19")
    val SIGNATURE = Icon("signature", "M3 17 C6 17 7 8 10 8 C12.5 8 11 16 14 16 C16 16 16.5 12 21 12")
    val INFO = Icon("info", "M3 12 A9 9 0 1 0 21 12 A9 9 0 1 0 3 12 Z M12 11 V17 M12 7.5 V8")
    val TEXT = Icon("text", "M4 6 H20 M4 12 H20 M4 18 H13")
    val PALETTE = Icon("palette", "M12 3 A9 9 0 1 0 12 21 C13.5 21 13.5 19.5 13 18.5 C12.3 17 13.3 15.5 15 15.5 H17.5 A3.5 3.5 0 0 0 21 12 A9 9 0 0 0 12 3 Z M7.5 12 V12.5 M10 7.5 V8 M15 8 V8.5")
    val WIFI_OFF = Icon("wifi_off", "M2 8.5 C7.5 3.5 16.5 3.5 22 8.5 M5.5 12 C9.5 8.5 14.5 8.5 18.5 12 M9 15.5 C10.8 14 13.2 14 15 15.5 M12 19 V19.5 M3 3 L21 21")
    val PIN = Icon("pin", "M9 4 H15 L14 10 L17 13 V14.5 H7 V13 L10 10 Z M12 14.5 V20.5")
    val PAUSE = Icon("pause", "M8 5 V19 M16 5 V19")
    val PLAY = Icon("play", "M7 5 L19 12 L7 19 Z", filled = true)
    val PROOFREAD = Icon("proofread", "M4 18 L8 6 L12 18 M5.5 14 H10.5 M13 15 L16 18 L21 11")
    val SHORT_TEXT = Icon("short_text", "M4 9 H20 M4 15 H13")
    val DOT = Icon("dot", "M8 12 A4 4 0 1 0 16 12 A4 4 0 1 0 8 12 Z", filled = true)
}

/** A small SVG path-data parser (M L H V C S Q T A Z, absolute and relative). */
object SvgPath {
    fun parse(d: String): List<PathCmd> {
        val tokens = Regex("[MmLlHhVvCcSsQqTtAaZz]|-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?").findAll(d).map { it.value }.toList()
        val out = ArrayList<PathCmd>()
        var i = 0
        var cmd = 'M'
        var x = 0f
        var y = 0f
        var startX = 0f
        var startY = 0f
        var lastCtrlX = 0f
        var lastCtrlY = 0f
        var lastWasCubic = false
        var lastWasQuad = false
        fun num(): Float = tokens[i++].toFloat()
        fun isNum(): Boolean = i < tokens.size && !tokens[i][0].isLetter()
        while (i < tokens.size) {
            if (tokens[i][0].isLetter()) cmd = tokens[i++][0]
            val rel = cmd.isLowerCase()
            val ox = if (rel) x else 0f
            val oy = if (rel) y else 0f
            var cubic = false
            var quad = false
            when (cmd.uppercaseChar()) {
                'M' -> {
                    x = ox + num(); y = oy + num(); startX = x; startY = y
                    out += PathCmd.Move(x, y)
                    cmd = if (rel) 'l' else 'L' // further pairs are line-tos
                }
                'L' -> { x = ox + num(); y = oy + num(); out += PathCmd.Line(x, y) }
                'H' -> { x = ox + num(); out += PathCmd.Line(x, y) }
                'V' -> { y = oy + num(); out += PathCmd.Line(x, y) }
                'C' -> {
                    val x1 = ox + num(); val y1 = oy + num(); val x2 = ox + num(); val y2 = oy + num()
                    x = ox + num(); y = oy + num()
                    out += PathCmd.Cubic(x1, y1, x2, y2, x, y); lastCtrlX = x2; lastCtrlY = y2; cubic = true
                }
                'S' -> {
                    val x1 = if (lastWasCubic) 2 * x - lastCtrlX else x
                    val y1 = if (lastWasCubic) 2 * y - lastCtrlY else y
                    val x2 = ox + num(); val y2 = oy + num()
                    x = ox + num(); y = oy + num()
                    out += PathCmd.Cubic(x1, y1, x2, y2, x, y); lastCtrlX = x2; lastCtrlY = y2; cubic = true
                }
                'Q' -> {
                    val x1 = ox + num(); val y1 = oy + num()
                    x = ox + num(); y = oy + num()
                    out += PathCmd.Quad(x1, y1, x, y); lastCtrlX = x1; lastCtrlY = y1; quad = true
                }
                'T' -> {
                    val x1 = if (lastWasQuad) 2 * x - lastCtrlX else x
                    val y1 = if (lastWasQuad) 2 * y - lastCtrlY else y
                    x = ox + num(); y = oy + num()
                    out += PathCmd.Quad(x1, y1, x, y); lastCtrlX = x1; lastCtrlY = y1; quad = true
                }
                'A' -> {
                    val rx = num(); val ry = num(); val rot = num(); val large = num() != 0f; val sweep = num() != 0f
                    val nx = ox + num(); val ny = oy + num()
                    out += arcToCubics(x, y, rx, ry, rot, large, sweep, nx, ny)
                    x = nx; y = ny
                }
                'Z' -> { out += PathCmd.Close; x = startX; y = startY }
                else -> error("Unsupported path command $cmd")
            }
            lastWasCubic = cubic
            lastWasQuad = quad
            if (cmd.uppercaseChar() == 'Z' && isNum()) cmd = 'L'
        }
        return out
    }

    /** SVG endpoint arc → cubic Béziers (SVG spec, appendix F.6). */
    fun arcToCubics(x1: Float, y1: Float, rxIn: Float, ryIn: Float, angleDeg: Float, large: Boolean, sweep: Boolean, x2: Float, y2: Float): List<PathCmd> {
        if (x1 == x2 && y1 == y2) return emptyList()
        var rx = abs(rxIn.toDouble())
        var ry = abs(ryIn.toDouble())
        if (rx == 0.0 || ry == 0.0) return listOf(PathCmd.Line(x2, y2))
        val phi = Math.toRadians(angleDeg.toDouble())
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val dx = (x1 - x2) / 2.0
        val dy = (y1 - y2) / 2.0
        val x1p = cosPhi * dx + sinPhi * dy
        val y1p = -sinPhi * dx + cosPhi * dy
        val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if (lambda > 1) { rx *= sqrt(lambda); ry *= sqrt(lambda) }
        val sign = if (large == sweep) -1 else 1
        val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        val coef = sign * sqrt(maxOf(0.0, num / den))
        val cxp = coef * (rx * y1p / ry)
        val cyp = coef * -(ry * x1p / rx)
        val cx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2.0
        val cy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2.0
        fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val a = atan2(ux * vy - uy * vx, ux * vx + uy * vy)
            return a
        }
        val theta1 = angle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var delta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if (!sweep && delta > 0) delta -= 2 * PI
        if (sweep && delta < 0) delta += 2 * PI
        val segments = ceil(abs(delta) / (PI / 2)).toInt().coerceAtLeast(1)
        val step = delta / segments
        val t = 4.0 / 3.0 * tan(step / 4)
        val out = ArrayList<PathCmd>()
        var th = theta1
        fun point(a: Double): Pair<Double, Double> {
            val px = rx * cos(a)
            val py = ry * sin(a)
            return (cosPhi * px - sinPhi * py + cx) to (sinPhi * px + cosPhi * py + cy)
        }
        repeat(segments) {
            val th2 = th + step
            val (sx, sy) = point(th)
            val (ex, ey) = point(th2)
            val d1x = -rx * sin(th); val d1y = ry * cos(th)
            val d2x = -rx * sin(th2); val d2y = ry * cos(th2)
            val c1x = sx + t * (cosPhi * d1x - sinPhi * d1y)
            val c1y = sy + t * (sinPhi * d1x + cosPhi * d1y)
            val c2x = ex - t * (cosPhi * d2x - sinPhi * d2y)
            val c2y = ey - t * (sinPhi * d2x + cosPhi * d2y)
            out += PathCmd.Cubic(c1x.toFloat(), c1y.toFloat(), c2x.toFloat(), c2y.toFloat(), ex.toFloat(), ey.toFloat())
            th = th2
        }
        // Land exactly on the endpoint.
        val last = out.last() as PathCmd.Cubic
        out[out.size - 1] = last.copy(x = x2, y = y2)
        return out
    }
}
