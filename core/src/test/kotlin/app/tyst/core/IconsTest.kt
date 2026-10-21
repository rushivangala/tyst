package app.tyst.core

import app.tyst.core.icons.Icon
import app.tyst.core.icons.Icons
import app.tyst.core.icons.PathCmd
import app.tyst.core.icons.SvgPath
import app.tyst.core.text.SwedishRestorer
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class IconsTest {
    private fun allIcons(): List<Icon> = Icons::class.java.declaredFields
        .filter { it.type == Icon::class.java }
        .map { it.isAccessible = true; it.get(Icons) as Icon }

    @Test fun everyIconParsesAndStaysOnTheGrid() {
        val icons = allIcons()
        assertThat(icons.size).isAtLeast(30)
        for (icon in icons) {
            val cmds = icon.commands
            assertThat(cmds).isNotEmpty()
            assertThat(cmds.first()).isInstanceOf(PathCmd.Move::class.java)
            for (c in cmds) {
                val pts = when (c) {
                    is PathCmd.Move -> listOf(c.x, c.y)
                    is PathCmd.Line -> listOf(c.x, c.y)
                    is PathCmd.Cubic -> listOf(c.x, c.y)
                    is PathCmd.Quad -> listOf(c.x, c.y)
                    PathCmd.Close -> emptyList()
                }
                for (p in pts) assertThat(p.toDouble()).isIn(com.google.common.collect.Range.closed(-0.5, 24.5))
            }
        }
    }

    @Test fun relativeCommandsAndArcs() {
        val cmds = SvgPath.parse("m2 2 h4 v4 l-4 0 z")
        assertThat(cmds).containsExactly(
            PathCmd.Move(2f, 2f), PathCmd.Line(6f, 2f), PathCmd.Line(6f, 6f), PathCmd.Line(2f, 6f), PathCmd.Close,
        ).inOrder()
        // A semicircle from (0,0) to (10,0) bulges to y = ±5 and ends exactly on the endpoint.
        val arc = SvgPath.arcToCubics(0f, 0f, 5f, 5f, 0f, false, true, 10f, 0f)
        assertThat(arc).hasSize(2)
        val mid = arc.first() as PathCmd.Cubic
        assertThat(mid.x).isWithin(0.01f).of(5f)
        assertThat(kotlin.math.abs(mid.y)).isWithin(0.01f).of(5f)
        assertThat((arc.last() as PathCmd.Cubic).x).isEqualTo(10f)
    }

    @Test fun swedishRestorerForVoice() {
        val r = SwedishRestorer(Fixtures.lexicon)
        assertThat(r.restore("the train to vasteras leaves from goteborg")).isEqualTo("the train to Västerås leaves from Göteborg")
        assertThat(r.restore("meet at jarntorget for fika")).isEqualTo("meet at Järntorget for fika")
        assertThat(r.restore("I want to go home")).isEqualTo("I want to go home")
    }
}
