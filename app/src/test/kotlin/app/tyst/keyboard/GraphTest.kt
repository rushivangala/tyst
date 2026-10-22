package app.tyst.keyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tyst.core.memory.MemoryType
import app.tyst.keyboard.data.Graph
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GraphTest {
    @Before fun clean() {
        File(context().filesDir, "learned").deleteRecursively()
    }

    @Test fun learnedDataSurvivesARestartEncrypted() {
        val keys = SoftwareKeys()
        val g = loadedGraph(keys)
        val e = g.engine.value!!
        e.memory.add(MemoryType.BOOKING, "ABX42K", "com.google.android.gm", 1_700_000_000_000L)
        e.personal.addWord("Järntorget", "test", 0)
        e.style.learn("Running 10 minutes late!", "com.slack", 1)
        g.changed()
        g.saveNow()

        val again = loadedGraph(keys).engine.value!!
        assertThat(again.memory.all.single().value).isEqualTo("ABX42K")
        assertThat(again.personal.inDictionary("järntorget")).isTrue()
        assertThat(again.style.messageCount).isEqualTo(1)
        File(context().filesDir, "learned").listFiles()!!.forEach { f ->
            assertThat(f.readBytes().toString(Charsets.ISO_8859_1)).doesNotContain("ABX42K")
        }
    }

    @Test fun deleteAllForgetsEverythingAndTheKey() {
        val keys = SoftwareKeys()
        val g = loadedGraph(keys)
        g.engine.value!!.memory.add(MemoryType.EMAIL, "elin.berg@hey.com", "x", 1)
        g.changed()
        g.saveNow()
        val before = g.revision.value
        g.deleteAll()
        assertThat(g.engine.value!!.memory.size).isEqualTo(0)
        assertThat(keys.destroyed).isEqualTo(1)
        assertThat(g.revision.value).isGreaterThan(before)
        assertThat(loadedGraph(keys).engine.value!!.memory.size).isEqualTo(0)
    }

    @Test fun expiredMemoryIsForgottenOnLoad() {
        val keys = SoftwareKeys()
        val day = 24L * 60 * 60 * 1000
        val g = loadedGraph(keys) { 0L }
        g.engine.value!!.memory.add(MemoryType.ADDRESS, "Kungsgatan 4", "x", 0)
        g.changed()
        g.saveNow()
        val later = loadedGraph(keys) { 120 * day }
        assertThat(later.engine.value!!.memory.size).isEqualTo(0)
    }

    @Test fun keystoreHiccupNeverOverwritesLearnedData() {
        val keys = FlakyKeys(SoftwareKeys())
        val g = loadedGraph(keys)
        g.engine.value!!.personal.addWord("Järntorget", "test", 0)
        g.changed()
        g.saveNow()

        keys.failing = true
        val blind = loadedGraph(keys)
        assertThat(blind.canSave).isFalse()
        assertThat(blind.engine.value!!.personal.inDictionary("järntorget")).isFalse()
        // Typing while the key is unavailable must not replace the file with an empty dictionary.
        blind.engine.value!!.personal.addWord("Haga", "test", 0)
        blind.changed()
        blind.saveNow()

        keys.failing = false
        val back = loadedGraph(keys).engine.value!!
        assertThat(back.personal.inDictionary("järntorget")).isTrue()
    }

    @Test fun damagedFileIsSetAsideNotOverwritten() {
        val keys = SoftwareKeys()
        val g = loadedGraph(keys)
        g.engine.value!!.personal.addWord("Järntorget", "test", 0)
        g.changed()
        g.saveNow()
        val dir = File(context().filesDir, "learned")
        val f = File(dir, "personal.bin")
        val bytes = f.readBytes().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        f.writeBytes(bytes)

        val fresh = loadedGraph(keys)
        assertThat(fresh.canSave).isTrue()
        assertThat(fresh.store.quarantinedNames()).containsExactly("personal")
        assertThat(File(dir, "personal.damaged").readBytes()).isEqualTo(bytes)
        fresh.engine.value!!.personal.addWord("Haga", "test", 0)
        fresh.changed()
        fresh.saveNow()
        assertThat(File(dir, "personal.damaged").readBytes()).isEqualTo(bytes)
        assertThat(loadedGraph(keys).engine.value!!.personal.inDictionary("haga")).isTrue()
    }

    @Test fun withEngineRunsOnceLoaded() {
        val g = Graph(context(), SoftwareKeys(), Inline)
        var ran = 0
        g.withEngine { ran++ }
        assertThat(ran).isEqualTo(0)
        g.start()
        idleMain()
        assertThat(ran).isEqualTo(1)
        g.withEngine { ran++ }
        assertThat(ran).isEqualTo(2)
    }
}
