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

/** Runs background work inline so tests are deterministic. */
object Inline : Executor {
    override fun execute(command: Runnable) = command.run()
}

fun context(): Context = ApplicationProvider.getApplicationContext()

fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

/** A graph with software keys and inline IO, already loaded. */
fun loadedGraph(keys: SoftwareKeys = SoftwareKeys(), now: () -> Long = { 1_700_000_000_000L }): Graph {
    val g = Graph(context(), keys, Inline, now)
    g.start()
    idleMain()
    checkNotNull(g.engine.value) { "engine did not load" }
    return g
}
