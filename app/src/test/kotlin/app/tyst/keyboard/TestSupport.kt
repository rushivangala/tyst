package app.tyst.keyboard

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.tyst.keyboard.data.Graph
import app.tyst.keyboard.data.KeyProvider
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.Executor
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Robolectric has no Android Keystore, so tests use a plain software AES key. */
class SoftwareKeys : KeyProvider {
    private var key: SecretKey? = null
    var destroyed = 0

    override fun key(): SecretKey = key ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }

    override fun destroy() {
        key = null
        destroyed++
    }
}

/** Wraps [real] and fails every key request while [failing] is true, like a Keystore that isn't ready. */
class FlakyKeys(private val real: KeyProvider) : KeyProvider {
    var failing = false
    override fun key(): SecretKey = if (failing) throw java.security.KeyStoreException("keystore not ready") else real.key()
    override fun destroy() = real.destroy()
}

/** Runs background work inline so tests are deterministic. */
object Inline : Executor {
    override fun execute(command: Runnable) = command.run()
}

fun context(): Context = ApplicationProvider.getApplicationContext()

fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

/** A graph with software keys and inline IO, already loaded. */
fun loadedGraph(keys: KeyProvider = SoftwareKeys(), now: () -> Long = { 1_700_000_000_000L }): Graph {
    val g = Graph(context(), keys, Inline, now)
    g.start()
    idleMain()
    checkNotNull(g.engine.value) { "engine did not load" }
    return g
}
