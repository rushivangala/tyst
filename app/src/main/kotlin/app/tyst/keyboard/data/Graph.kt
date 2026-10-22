package app.tyst.keyboard.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.tyst.core.TystEngine
import app.tyst.core.memory.MemoryStore
import app.tyst.core.predict.PersonalModel
import app.tyst.core.style.StyleProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * App-wide objects shared by the keyboard service and the settings screens (same process).
 * The engine is loaded off the main thread and then only touched on the main thread.
 */
class Graph(
    context: Context,
    keys: KeyProvider = KeystoreKeyProvider(KEY_ALIAS),
    private val io: Executor = Executors.newSingleThreadExecutor { r -> Thread(r, "tyst-io").apply { priority = Thread.NORM_PRIORITY - 1 } },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val settings = SettingsRepository(context)
    val store = SecureStore(File(context.filesDir, "learned"), keys)

    private val main = Handler(Looper.getMainLooper())
    private val engineState = MutableStateFlow<TystEngine?>(null)
    private val revisionState = MutableStateFlow(0)
    private var started = false
    private val waiting = ArrayList<(TystEngine) -> Unit>()

    /** Null until the word lists and learned data are loaded. */
    val engine: StateFlow<TystEngine?> = engineState.asStateFlow()

    /** Bumped whenever learned data changes, so screens can refresh lists. */
    val revision: StateFlow<Int> = revisionState.asStateFlow()

    /**
     * False when learned data couldn't be read because the key wasn't available. Saving is then
     * off until the next start, so a temporary Keystore hiccup can't overwrite good data with empty data.
     */
    @Volatile var canSave = true
        private set

    fun start() {
        if (started) return
        started = true
        io.execute {
            val personal = load("personal") { PersonalModel.decode(it) } ?: PersonalModel()
            val memory = load("memory") { MemoryStore.decode(it) } ?: MemoryStore()
            val style = load("style") { StyleProfile.decode(it) } ?: StyleProfile()
            val engine = TystEngine.loadDefault(personal, memory, style)
            val expired = engine.memory.expire(clock(), settings.current.memoryExpiryDays)
            if (expired > 0) engine.dirty = true
            main.post { publish(engine) }
        }
    }

    private fun publish(engine: TystEngine) {
        engineState.value = engine
        val callbacks = waiting.toList()
        waiting.clear()
        callbacks.forEach { it(engine) }
        if (engine.dirty) scheduleSave()
    }

    /** Runs [block] on the main thread once the engine is ready. */
    fun withEngine(block: (TystEngine) -> Unit) {
        val e = engineState.value
        if (e != null) block(e) else waiting += block
    }

    /**
     * Loads one learned-data file. Missing → null (fresh start). Damaged, or written by a format this
     * build can't decode → moved aside, then null. Key unavailable after retries → null and saving off.
     */
    private fun <T> load(name: String, decode: (String) -> T): T? {
        var result = store.load(name)
        var attempt = 0
        while (result is Stored.KeyUnavailable && attempt < KEY_RETRIES) {
            Thread.sleep(KEY_RETRY_MS shl attempt)
            attempt++
            result = store.load(name)
        }
        return when (result) {
            Stored.Missing -> null
            is Stored.Ok -> try {
                decode(result.text)
            } catch (_: Exception) {
                store.quarantine(name)
                null
            }
            is Stored.Damaged -> { store.quarantine(name); null }
            is Stored.KeyUnavailable -> { canSave = false; null }
        }
    }

    /** Call after changing learned data from the settings screens. */
    fun changed() {
        engineState.value?.dirty = true
        revisionState.value++
        scheduleSave()
    }

    private val saveRunnable = Runnable { saveNow() }

    fun scheduleSave() {
        main.removeCallbacks(saveRunnable)
        main.postDelayed(saveRunnable, SAVE_DELAY_MS)
    }

    /** Snapshots learned data on the calling (main) thread and writes it encrypted in the background. */
    fun saveNow() {
        main.removeCallbacks(saveRunnable)
        val e = engineState.value ?: return
        if (!e.dirty || !canSave) return
        e.dirty = false
        val personal = e.personal.encode()
        val memory = e.memory.encode()
        val style = e.style.encode()
        io.execute {
            try {
                store.write("personal", personal)
                store.write("memory", memory)
                store.write("style", style)
            } catch (_: Exception) {
                main.post { e.dirty = true }
            }
        }
    }

    /** "Delete all learned data": clears memory, style and dictionary, deletes the files and the key. */
    fun deleteAll() {
        main.removeCallbacks(saveRunnable)
        engineState.value?.let { it.deleteAllLearned(); it.dirty = false }
        // The user chose to start over, so writing (empty) data is safe again.
        canSave = true
        io.execute { store.deleteAll() }
        revisionState.value++
    }

    companion object {
        const val KEY_ALIAS = "tyst_learned_v1"
        const val SAVE_DELAY_MS = 2_000L
        private const val KEY_RETRIES = 3
        private const val KEY_RETRY_MS = 200L
    }
}
