package app.tyst.core.text

import app.tyst.core.lexicon.Lexicon

/** Puts å ä ö back into Swedish names and places in voice transcripts ("vasteras" → "Västerås"). */
class SwedishRestorer(private val lexicon: Lexicon) {
    fun restore(text: String): String =
        Regex("[\\p{L}']+").replace(text) { m ->
            val w = m.value
            val restored = lexicon.restoreDiacritics(w)
            when {
                restored != null -> if (lexicon.hasDisplayForm(restored)) restored else Words.matchCase(w, restored)
                lexicon.hasDisplayForm(w) && lexicon.isSwedishOnly(w) -> lexicon.display(w)
                else -> w
            }
        }
}
