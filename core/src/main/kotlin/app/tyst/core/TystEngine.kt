package app.tyst.core

import app.tyst.core.correct.Autocorrector
import app.tyst.core.lexicon.Lexicon
import app.tyst.core.memory.MemoryStore
import app.tyst.core.predict.NGramModel
import app.tyst.core.predict.PersonalModel
import app.tyst.core.predict.Predictor
import app.tyst.core.style.StyleProfile
import app.tyst.core.write.Proofreader
import app.tyst.core.write.Rewriter

/**
 * The whole on-device brain: built-in word lists plus everything learned about the user.
 * Learned parts are mutable and must be used from one thread (the IME's main thread).
 */
class TystEngine(
    val lexicon: Lexicon,
    val base: NGramModel,
    val personal: PersonalModel = PersonalModel(),
    val memory: MemoryStore = MemoryStore(),
    val style: StyleProfile = StyleProfile(),
) {
    val autocorrector = Autocorrector(lexicon, base, personal)
    val predictor = Predictor(lexicon, base, personal, autocorrector)
    val rewriter = Rewriter(style, lexicon)
    val proofreader = Proofreader(autocorrector)

    /** Set when learned data changes, so the app layer knows to save. */
    @Volatile var dirty: Boolean = false

    /** "Delete all learned data": style, personal words and private memory. */
    fun deleteAllLearned() {
        personal.clear()
        memory.clear()
        style.clear()
        dirty = true
    }

    companion object {
        fun loadDefault(
            personal: PersonalModel = PersonalModel(),
            memory: MemoryStore = MemoryStore(),
            style: StyleProfile = StyleProfile(),
        ) = TystEngine(Lexicon.loadDefault(), NGramModel.loadBase(), personal, memory, style)
    }
}
