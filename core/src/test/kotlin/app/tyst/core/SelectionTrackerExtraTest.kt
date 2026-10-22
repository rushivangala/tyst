package app.tyst.core

import app.tyst.core.typing.SelectionTracker
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SelectionTrackerExtraTest {
    @Test fun ownSetSelectionIsNotAMove() {
        val t = SelectionTracker().apply { reset(10, 10) }
        t.selected(4, 10)
        assertThat(t.onUpdate(4, 10)).isFalse()
        t.selected(10, 10)
        assertThat(t.onUpdate(10, 10)).isFalse()
        assertThat(t.onUpdate(2, 2)).isTrue()
    }
}
