package app.tyst.core

import app.tyst.core.correct.Autocorrector
import app.tyst.core.lexicon.Lexicon
import app.tyst.core.predict.NGramModel
import app.tyst.core.predict.PersonalModel
import app.tyst.core.predict.Predictor

/** The bundled data loads once per test run; it is read-only. */
object Fixtures {
    val lexicon: Lexicon by lazy { Lexicon.loadDefault() }
    val base: NGramModel by lazy { NGramModel.loadBase() }

    class Engine(val personal: PersonalModel = PersonalModel()) {
        val autocorrector = Autocorrector(lexicon, base, personal)
        val predictor = Predictor(lexicon, base, personal, autocorrector)
    }
}
