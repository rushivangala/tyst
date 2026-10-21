package app.tyst.core.typing

import app.tyst.core.TystEngine
import app.tyst.core.codes.CopiedCode
import app.tyst.core.codes.CopiedText
import app.tyst.core.codes.OtpDetector
import app.tyst.core.correct.AutocorrectStrength
import app.tyst.core.correct.CorrectionOptions
import app.tyst.core.field.EnterAction
import app.tyst.core.field.FieldInfo
import app.tyst.core.field.FieldKind
import app.tyst.core.memory.MemoryExtractor
import app.tyst.core.memory.MemoryItem
import app.tyst.core.memory.MemoryMatcher
import app.tyst.core.predict.Suggestion
import app.tyst.core.predict.SuggestionKind
import app.tyst.core.settings.KeyboardSettings
import app.tyst.core.text.Words

/** The editor the keyboard types into; the app wraps Android's InputConnection in this. */
interface TextTarget {
    fun before(n: Int): String
    fun after(n: Int): String
    fun selected(): String
    fun commit(text: String)
    fun deleteAround(before: Int, after: Int)
    /** A raw delete key press, for empty fields and editors that ignore text edits. */
    fun sendBackspaceKey()
    /** Runs the field's action (Send, Search…) or inserts a new line for [EnterAction.NEWLINE]. */
    fun enter(action: EnterAction)
    fun batch(block: () -> Unit) = block()
}

enum class ShiftState { OFF, ON, LOCKED }

data class LastCorrection(val original: String, val replacement: String, val suffix: String)

/**
 * What the suggestion strip shows. Priority: code > memory > copied text > predictions
 * (autofill pills are added by the app, after the code).
 */
data class StripState(
    val suggestions: List<Suggestion> = emptyList(),
    /** Word space will put in, drawn bold; null when space just adds a space. */
    val autoCorrection: String? = null,
    /** Original word of the last autocorrect, shown to undo it. */
    val undoWord: String? = null,
    val memory: List<MemoryItem> = emptyList(),
    val code: CopiedCode? = null,
    val codeSecondsLeft: Int = 0,
    /** The last copied text, offered to paste while no word is being typed. */
    val clip: CopiedText? = null,
    val incognito: Boolean = false,
    val writingTools: Boolean = true,
)

/**
 * Turns key presses into edits: shift and caps, autocorrect with undo, suggestions, smart spacing,
 * private memory, copied codes and learning. Pure Kotlin so it can be tested without a phone.
 */
class TypingController(
    private val engine: TystEngine,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var settings = KeyboardSettings()
        private set
    var field = FieldInfo()
        private set
    private var target: TextTarget? = null

    var shift = ShiftState.OFF
        private set
    private var shiftIsAuto = false
    private var lastShiftTap = 0L

    var lastCorrection: LastCorrection? = null
        private set
    /** A word the user restored after an autocorrect; it isn't corrected again in this spot. */
    private var keptWord: String? = null
    private var autoSpace = false
    private var lastWasSpace = false
    private var copiedCode: CopiedCode? = null
    private var copiedText: CopiedText? = null
    private var lastLearnedText: String? = null

    /** Called when [StripState] or the shift state may have changed. */
    var onChanged: (() -> Unit)? = null

    fun start(field: FieldInfo, target: TextTarget, settings: KeyboardSettings) {
        this.field = field
        this.target = target
        this.settings = settings
        lastCorrection = null
        keptWord = null
        autoSpace = false
        lastWasSpace = false
        lastLearnedText = null
        shift = ShiftState.OFF
        if (field.autoCapAll) shift = ShiftState.LOCKED else updateAutoShift()
        changed()
    }

    fun updateSettings(settings: KeyboardSettings) {
        this.settings = settings
        changed()
    }

    /** The field is closing: learn from what was written, if allowed. */
    fun finish() {
        val t = target ?: return
        learnMessage(t.before(LEARN_CHARS) + t.after(LEARN_CHARS))
        target = null
    }

    // ---- keys ----

    fun onText(raw: String) {
        val t = target ?: return
        val text = if (shift != ShiftState.OFF && raw.length == 1 && raw[0].isLetter()) raw.uppercase() else raw
        val c = text.singleOrNull()
        t.batch {
            if (c != null && c in SENTENCE_PUNCT) punctuation(t, c) else t.commit(text)
        }
        if (shift == ShiftState.ON && text.any { it.isLetter() }) { shift = ShiftState.OFF; shiftIsAuto = false }
        lastCorrection = null
        lastWasSpace = false
        if (c == null || c !in SENTENCE_PUNCT) autoSpace = false
        updateAutoShift()
        changed()
    }

    private fun punctuation(t: TextTarget, c: Char) {
        val before = t.before(64)
        val word = Words.currentWord(before)
        if (word.isNotEmpty()) {
            val replaced = correctCurrentWord(t, word, c.toString())
            learnWord(replaced ?: word, before)
            if (replaced != null) return
        }
        // "lunch |" + "." → "lunch. |": move the automatic space after the punctuation.
        if (autoSpace && before.endsWith(" ") && c in ATTACHING_PUNCT) {
            t.deleteAround(1, 0)
            t.commit("$c ")
            autoSpace = true
            return
        }
        t.commit(c.toString())
    }

    fun onSpace() {
        val t = target ?: return
        val before = t.before(64)
        t.batch {
            val word = Words.currentWord(before)
            when {
                word.isEmpty() && settings.doubleSpacePeriod && (lastWasSpace || autoSpace) && endsWithWordThenSpace(before) -> {
                    t.deleteAround(1, 0)
                    t.commit(". ")
                    lastWasSpace = false
                    autoSpace = false
                    lastCorrection = null
                }
                word.isEmpty() && autoSpace -> {
                    // The suggestion already added a space; a second one is just a space.
                    t.commit(" ")
                    autoSpace = false
                    lastWasSpace = true
                    lastCorrection = null
                }
                else -> {
                    val replaced = if (word.isNotEmpty()) correctCurrentWord(t, word, " ") else null
                    if (replaced == null) t.commit(" ")
                    if (word.isNotEmpty()) learnWord(replaced ?: word, before)
                    if (replaced == null) lastCorrection = null
                    lastWasSpace = true
                    autoSpace = false
                }
            }
        }
        if (field.autoCapWords && settings.autoCapitalize) setAutoShift(true) else updateAutoShift()
        changed()
    }

    private fun endsWithWordThenSpace(before: String): Boolean =
        before.length >= 2 && before.last() == ' ' && Words.isWordChar(before[before.length - 2])

    /** Applies autocorrect to [word] if confident. Returns the replacement, or null. */
    private fun correctCurrentWord(t: TextTarget, word: String, suffix: String): String? {
        if (!field.autocorrect || settings.autocorrect == AutocorrectStrength.OFF) return null
        if (keptWord != null && Words.normalize(keptWord!!) == Words.normalize(word)) return null
        val prev = Words.previousWords(t.before(64), 2)
        val fix = engine.autocorrector.check(word, prev, options()).autoApply ?: return null
        t.deleteAround(word.length, 0)
        t.commit(fix + suffix)
        lastCorrection = LastCorrection(word, fix, suffix)
        return fix
    }

    fun onBackspace() {
        val t = target ?: return
        val corr = lastCorrection
        t.batch {
            val selected = t.selected()
            val before = t.before(64)
            when {
                selected.isNotEmpty() -> t.commit("")
                corr != null && before.endsWith(corr.replacement + corr.suffix) -> {
                    t.deleteAround(corr.replacement.length + corr.suffix.length, 0)
                    t.commit(corr.original)
                    keptWord = corr.original
                    if (learningAllowed()) engine.personal.onCorrectionUndone(corr.original, field.packageName, clock()).also { engine.dirty = true }
                }
                before.isEmpty() -> t.sendBackspaceKey()
                else -> t.deleteAround(Words.lastGraphemeLength(before), 0)
            }
        }
        lastCorrection = null
        lastWasSpace = false
        autoSpace = false
        updateAutoShift()
        changed()
    }

    /** Held backspace speeds up to deleting whole words. */
    fun onDeleteWord() {
        val t = target ?: return
        val before = t.before(64)
        if (before.isEmpty()) { t.sendBackspaceKey(); return }
        var n = 0
        while (n < before.length && before[before.length - 1 - n].isWhitespace()) n++
        while (n < before.length && !before[before.length - 1 - n].isWhitespace()) n++
        t.deleteAround(n, 0)
        lastCorrection = null
        updateAutoShift()
        changed()
    }

    fun onEnter() {
        val t = target ?: return
        val before = t.before(64)
        val word = Words.currentWord(before)
        if (word.isNotEmpty()) learnWord(word, before)
        if (field.enterAction != EnterAction.NEWLINE && field.enterAction != EnterAction.NEXT && field.enterAction != EnterAction.PREVIOUS) {
            // The message is about to be sent: learn from it now, while it is still in the field.
            learnMessage(t.before(LEARN_CHARS) + t.after(LEARN_CHARS))
        }
        t.enter(field.enterAction)
        lastCorrection = null
        autoSpace = false
        lastWasSpace = false
        updateAutoShift()
        changed()
    }

    fun onShift() {
        val now = clock()
        shift = when (shift) {
            ShiftState.OFF -> ShiftState.ON
            ShiftState.ON -> if (now - lastShiftTap < DOUBLE_TAP_MS && !shiftIsAuto) ShiftState.LOCKED else ShiftState.OFF
            ShiftState.LOCKED -> ShiftState.OFF
        }
        shiftIsAuto = false
        lastShiftTap = now
        changed()
    }

    /** The cursor moved or the text changed outside the keyboard. */
    fun onCursorMoved() {
        lastCorrection = null
        autoSpace = false
        lastWasSpace = false
        val t = target
        if (t != null && keptWord != null && Words.currentWord(t.before(64)) != keptWord) keptWord = null
        updateAutoShift()
        changed()
    }

    fun pick(s: Suggestion) {
        val t = target ?: return
        val before = t.before(64)
        val word = Words.currentWord(before)
        t.batch {
            if (s.kind == SuggestionKind.NEXT_WORD || word.isEmpty()) {
                val needsSpace = before.isNotEmpty() && !before.last().isWhitespace() && before.last() !in "([“\"'"
                t.commit((if (needsSpace) " " else "") + s.text + " ")
            } else {
                val after = Words.wordAfter(t.after(32))
                t.deleteAround(word.length, after.length)
                t.commit(s.text + " ")
                if (s.text == word) keptWord = word
            }
        }
        learnWord(s.text, before)
        lastCorrection = null
        autoSpace = true
        lastWasSpace = false
        updateAutoShift()
        changed()
    }

    /** Puts back the word that autocorrect replaced, keeping the space after it. */
    fun undoCorrection() {
        val t = target ?: return
        val corr = lastCorrection ?: return
        if (!t.before(64).endsWith(corr.replacement + corr.suffix)) return
        t.batch {
            t.deleteAround(corr.replacement.length + corr.suffix.length, 0)
            t.commit(corr.original + corr.suffix)
        }
        keptWord = corr.original
        if (learningAllowed()) { engine.personal.onCorrectionUndone(corr.original, field.packageName, clock()); engine.dirty = true }
        lastCorrection = null
        changed()
    }

    fun insertMemory(item: MemoryItem) {
        val t = target ?: return
        val before = t.before(1)
        t.commit((if (before.isNotEmpty() && !before.last().isWhitespace()) " " else "") + item.value)
        engine.memory.markUsed(item.id, clock())
        engine.dirty = true
        autoSpace = false
        changed()
    }

    /** Fills a copied code. Returns true so the app can clear the clipboard if the user asked for it. */
    fun insertCode(): Boolean {
        val t = target ?: return false
        val code = copiedCode ?: return false
        t.commit(code.code)
        copiedCode = null
        changed()
        return true
    }

    fun onEmoji(e: String) {
        val t = target ?: return
        t.commit(e)
        if (learningAllowed()) { engine.personal.onEmojiUsed(e); engine.dirty = true }
        autoSpace = false
        lastCorrection = null
        changed()
    }

    /**
     * New clipboard text. A verification code becomes the code chip; anything else becomes the
     * copied-text chip. Both live in memory only and are never written to disk.
     */
    fun onClipboard(text: String?, copiedAt: Long = clock(), sensitive: Boolean = false) {
        if (text.isNullOrBlank()) return
        val code = OtpDetector.extract(text)
        if (code != null) {
            copiedCode = CopiedCode(code, copiedAt)
            copiedText = null
        } else if (text.length <= CopiedText.MAX_CHARS) {
            copiedText = CopiedText(text, copiedAt, sensitive)
        }
        changed()
    }

    /** True while a copied code is on show, so the app can tick its countdown. */
    fun hasLiveCode(): Boolean = copiedCode?.let { settings.showCopiedCodes && it.isFresh(clock(), settings.codeExpirySeconds) } == true

    /** True while copied text may still be offered, so the app can hide the chip when it expires. */
    fun hasLiveClip(): Boolean = copiedText?.let { settings.showCopiedText && it.isFresh(clock()) } == true

    /** Pastes the copied text at the cursor (replacing any selection) and stops offering it. */
    fun insertClip() {
        val t = target ?: return
        val clip = copiedText ?: return
        t.commit(clip.text)
        copiedText = null
        lastCorrection = null
        autoSpace = false
        lastWasSpace = false
        updateAutoShift()
        changed()
    }

    /** The user dismissed the copied-text chip. */
    fun dismissClip() {
        copiedText = null
        changed()
    }

    fun clearCode() {
        copiedCode = null
        changed()
    }

    /** Inserts text at the cursor (voice typing), replacing any selection. */
    fun insertText(text: String) {
        val t = target ?: return
        t.commit(text)
        lastCorrection = null
        autoSpace = false
        lastWasSpace = false
        updateAutoShift()
        changed()
    }

    /** Replaces the selection, or the whole field when nothing is selected (writing tools). */
    fun replaceText(newText: String) {
        val t = target ?: return
        t.batch {
            if (t.selected().isNotEmpty()) t.commit(newText)
            else {
                val b = t.before(MAX_EDIT_CHARS)
                val a = t.after(MAX_EDIT_CHARS)
                t.deleteAround(b.length, a.length)
                t.commit(newText)
            }
        }
        lastCorrection = null
        changed()
    }

    /** True unless the tools are working on a selection that begins mid-sentence. */
    fun toolsTextStartsSentence(): Boolean {
        val t = target ?: return true
        return t.selected().isEmpty() || Words.isSentenceStart(t.before(8))
    }

    /** The text the writing tools work on: the selection, or the whole field. */
    fun textForTools(): String {
        val t = target ?: return ""
        val sel = t.selected()
        return sel.ifEmpty { t.before(MAX_EDIT_CHARS) + t.after(MAX_EDIT_CHARS) }
    }

    // ---- strip ----

    fun strip(): StripState {
        val t = target ?: return StripState(incognito = field.incognito)
        val now = clock()
        val code = copiedCode?.takeIf { settings.showCopiedCodes && it.isFresh(now, settings.codeExpirySeconds) }
        if (copiedCode != null && code == null) copiedCode = null
        if (copiedText?.isFresh(now) == false) copiedText = null
        val before = t.before(64)
        val memory = if (settings.memoryEnabled && !field.incognito) MemoryMatcher.match(before, field, engine.memory) else emptyList()
        val predictions = if (field.predictions && t.selected().isEmpty()) engine.predictor.predict(before, options()) else null
        val word = Words.currentWord(before)
        val kept = keptWord != null && Words.normalize(keptWord!!) == Words.normalize(word)
        val auto = predictions?.autoCorrection?.takeIf { field.autocorrect && settings.autocorrect != AutocorrectStrength.OFF && !kept }
        return StripState(
            suggestions = predictions?.suggestions ?: emptyList(),
            autoCorrection = auto,
            undoWord = lastCorrection?.original?.takeIf { before.endsWith(lastCorrection!!.replacement + lastCorrection!!.suffix) },
            memory = memory,
            code = code,
            codeSecondsLeft = code?.remainingSeconds(now, settings.codeExpirySeconds) ?: 0,
            // Out of the way while a word is being typed; back after a space.
            clip = copiedText?.takeIf { settings.showCopiedText && code == null && word.isEmpty() },
            incognito = field.incognito,
            writingTools = settings.writingTools && field.writingTools,
        )
    }

    fun options() = CorrectionOptions(
        strength = if (field.autocorrect) settings.autocorrect else AutocorrectStrength.OFF,
        swedishWords = settings.swedishWords,
        restoreDiacritics = settings.swedishWords && settings.restoreDiacritics,
    )

    // ---- learning ----

    private fun learningAllowed() = settings.learning && field.learningAllowed

    private fun learnWord(word: String, before: String) {
        if (!learningAllowed() || word.isBlank()) return
        val prev = Words.previousWords(before, 2)
        val known = engine.lexicon.isEnglish(word) || (settings.swedishWords && engine.lexicon.isSwedish(word))
        engine.personal.onWordCommitted(word, prev, known, field.packageName, clock())
        engine.dirty = true
    }

    private fun learnMessage(text: String) {
        val msg = text.trim()
        if (msg.isEmpty() || msg == lastLearnedText) return
        lastLearnedText = msg
        if (learningAllowed() && field.kind in LEARN_STYLE_KINDS) {
            engine.style.learn(msg, field.packageName, clock())
            engine.dirty = true
        }
        if (settings.memoryEnabled && field.memoryAllowed && !field.incognito) {
            for (x in MemoryExtractor.extract(msg)) engine.memory.add(x.type, x.value, field.packageName, clock())
            engine.dirty = true
        }
    }

    // ---- shift ----

    private fun updateAutoShift() {
        if (shift == ShiftState.LOCKED) return
        val t = target ?: return
        val want = settings.autoCapitalize && field.autoCapSentences && t.selected().isEmpty() && Words.isSentenceStart(t.before(8))
        setAutoShift(want)
    }

    private fun setAutoShift(on: Boolean) {
        if (on && shift == ShiftState.OFF) { shift = ShiftState.ON; shiftIsAuto = true }
        else if (!on && shift == ShiftState.ON && shiftIsAuto) { shift = ShiftState.OFF; shiftIsAuto = false }
    }

    private fun changed() { onChanged?.invoke() }

    companion object {
        const val DOUBLE_TAP_MS = 350L
        const val LEARN_CHARS = 2000
        const val MAX_EDIT_CHARS = 4000
        private const val SENTENCE_PUNCT = ".,!?;:"
        private const val ATTACHING_PUNCT = ".,!?;:"
        private val LEARN_STYLE_KINDS = setOf(FieldKind.TEXT, FieldKind.MESSAGE)
    }
}
