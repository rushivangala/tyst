package app.tyst.core.bench

import app.tyst.core.bench.TypingBenchmark.Config
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

/**
 * Regression gates on the typing benchmark (all held-out messages, one seeded run each). The floors sit
 * a little under today's results, so a change that makes typing worse fails here. Print the full report
 * with `TypingBenchmark.main`.
 */
class TypingBenchmarkTest {
    @Test fun typicalThumb() {
        val r = TypingBenchmark.run(Config(runs = 1))
        println(r.format())
        assertWithMessage("false corrections\n${r.format()}").that(r.falseCorrectionRate).isAtMost(0.003)
        assertWithMessage("typos fixed\n${r.format()}").that(r.typoFixRate).isAtLeast(0.80)
        assertWithMessage("keystroke savings\n${r.format()}").that(r.keystrokeSavings).isAtLeast(0.38)
        assertWithMessage("next word top 3\n${r.format()}").that(r.nextWordTop3).isAtLeast(0.26)
        // Desktop JVM; the phone is slower, so keep generous headroom under the 30 ms spec target.
        assertWithMessage("strip latency p95\n${r.format()}").that(r.p95Ms).isLessThan(15.0)
    }

    /** A sloppier thumb than the tap model assumes: still few wrong changes, still most typos fixed. */
    @Test fun sloppyThumb() {
        val r = TypingBenchmark.run(Config(runs = 1, sigmaX = 0.30, sigmaY = 0.24, seed = 7))
        println(r.format())
        assertWithMessage("false corrections\n${r.format()}").that(r.falseCorrectionRate).isAtMost(0.01)
        assertWithMessage("typos fixed\n${r.format()}").that(r.typoFixRate).isAtLeast(0.65)
    }
}
