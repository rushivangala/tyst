package app.tyst.keyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tyst.keyboard.data.SecureStore
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SecureStoreTest {
    private val dir = File(context().filesDir, "store-test").apply { deleteRecursively() }

    @Test fun roundTripsAndIsNotPlainText() {
        val store = SecureStore(dir, SoftwareKeys())
        store.write("memory", "Kungsgatan 4, 411 19 Göteborg")
        assertThat(store.read("memory")).isEqualTo("Kungsgatan 4, 411 19 Göteborg")
        val raw = File(dir, "memory.bin").readBytes().toString(Charsets.ISO_8859_1)
        assertThat(raw).doesNotContain("Kungsgatan")
        assertThat(raw).startsWith("TYS1")
    }

    @Test fun freshIvEveryWrite() {
        val store = SecureStore(dir, SoftwareKeys())
        store.write("a", "same text")
        val first = File(dir, "a.bin").readBytes()
        store.write("a", "same text")
        assertThat(File(dir, "a.bin").readBytes()).isNotEqualTo(first)
    }

    @Test fun tamperedOrWrongKeyReadsAsNothing() {
        val keys = SoftwareKeys()
        val store = SecureStore(dir, keys)
        store.write("x", "secret")
        val f = File(dir, "x.bin")
        val bytes = f.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        f.writeBytes(bytes)
        assertThat(store.read("x")).isNull()

        store.write("y", "secret")
        assertThat(SecureStore(dir, SoftwareKeys()).read("y")).isNull()
        assertThat(store.read("missing")).isNull()
    }

    @Test fun deleteAllRemovesFilesAndDestroysKey() {
        val keys = SoftwareKeys()
        val store = SecureStore(dir, keys)
        store.write("a", "1")
        store.write("b", "2")
        assertThat(store.sizeBytes()).isGreaterThan(0L)
        store.deleteAll()
        assertThat(dir.listFiles().orEmpty()).isEmpty()
        assertThat(keys.destroyed).isEqualTo(1)
    }
}
