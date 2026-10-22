package app.tyst.core

import app.tyst.core.typing.SelectionTracker
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SelectionTrackerTest {
    private val t = SelectionTracker().apply { reset(0, 0) }

    @Test fun ownEditsAreRecognised() {
        t.committed(5)
        assertThat(t.onUpdate(5, 5)).isFalse()
        t.committed(1)
        assertThat(t.onUpdate(6, 6)).isFalse()
    }

    @Test fun userTapIsAMove() {
        t.committed(5)
        assertThat(t.onUpdate(5, 5)).isFalse()
        assertThat(t.onUpdate(2, 2)).isTrue()
        assertThat(t.start).isEqualTo(2)
    }

    @Test fun editThatChangesNothingDoesNotHideTheNextTap() {
        // The old counter expected an update here; none comes, and the user's tap was swallowed.
        t.committed(0)
        assertThat(t.onUpdate(3, 3)).isTrue()
    }

    @Test fun skippedIntermediateUpdatesAreFine() {
        t.committed(1)
        t.committed(1)
        t.committed(1)
        // The editor coalesced three edits into one update.
        assertThat(t.onUpdate(3, 3)).isFalse()
        assertThat(t.onUpdate(1, 1)).isTrue()
    }

    @Test fun batchExpectsOnlyTheFinalPosition() {
        t.committed(4) ; t.onUpdate(4, 4)
        t.beginBatch()
        t.deleted(4, 0)
        t.committed(5)
        t.endBatch()
        assertThat(t.onUpdate(5, 5)).isFalse()
    }

    @Test fun commitReplacesSelection() {
        t.reset(2, 6)
        t.committed(3)
        assertThat(t.onUpdate(5, 5)).isFalse()
    }

    @Test fun selectingTextIsAMove() {
        t.committed(5); t.onUpdate(5, 5)
        assertThat(t.onUpdate(1, 4)).isTrue()
        assertThat(t.end).isEqualTo(4)
    }

    @Test fun unknownPositionsTreatUpdatesAsMoves() {
        t.reset(-1, -1)
        t.committed(3)
        assertThat(t.onUpdate(3, 3)).isTrue()
        // Now known: later own edits match again.
        t.committed(1)
        assertThat(t.onUpdate(4, 4)).isFalse()
        t.unpredictable()
        assertThat(t.onUpdate(5, 5)).isTrue()
    }

    @Test fun repeatedIdenticalUpdateIsNotAMove() {
        t.committed(2); t.onUpdate(2, 2)
        assertThat(t.onUpdate(2, 2)).isFalse()
    }
}
