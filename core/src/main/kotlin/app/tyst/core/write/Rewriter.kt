package app.tyst.core.write

import app.tyst.core.lexicon.Lexicon
import app.tyst.core.style.AppNames
import app.tyst.core.style.StyleProfile

enum class Tone(val label: String) { MY_STYLE("My style"), WARMER("Warmer"), FORMAL("Formal"), SHORTER("Shorter"), LONGER("Longer") }

data class RewriteResult(
    val text: String,
    val tone: Tone,
    /** Why it reads this way, e.g. "Short sentences · your Slack style". */
    val notes: List<String>,
    val changed: List<IntRange>,
)

sealed class RewriteError {
    data object Empty : RewriteError()
    data object TooLong : RewriteError()
}

/**
 * The on-device rewrite tool. It works on phrases and sentences with fixed rules, so it never invents
 * facts, and it leaves names and Swedish words exactly as typed.
 */
class Rewriter(private val style: StyleProfile, private val lexicon: Lexicon? = null) {
    /** Lower-cases the first letter unless the first word is "I" or a name, which keep their capital. */
    private fun lowerFirst(s: String): String {
        val first = s.substringBefore(' ').trimEnd(',', '.', '!', '?')
        val keep = first == "I" || first.startsWith("I'") || first.startsWith("I’") ||
            first.drop(1).any { it.isUpperCase() } ||
            lexicon != null && (lexicon.hasDisplayForm(first) || !lexicon.isEnglish(first))
        return if (keep) s else s.replaceFirstChar { it.lowercaseChar() }
    }

    fun rewrite(text: String, tone: Tone, app: String): Result<RewriteResult> {
        val input = text.trim()
        if (input.length < 2) return Result.failure(RewriteException(RewriteError.Empty))
        if (input.length > MAX_CHARS) return Result.failure(RewriteException(RewriteError.TooLong))
        val notes = ArrayList<String>()
        val out = when (tone) {
            Tone.FORMAL -> formal(input)
            Tone.WARMER -> warmer(input, app)
            Tone.SHORTER -> shorter(input)
            Tone.LONGER -> longer(input)
            Tone.MY_STYLE -> myStyle(input, app, notes)
        }
        val finalText = TextRules.capitalizeSentences(TextRules.tidy(out))
        return Result.success(RewriteResult(finalText, tone, notes, WordDiff.changedRanges(input, finalText)))
    }

    private fun formal(t: String): String {
        val (name, body0) = TextRules.splitGreeting(t)
        var body = TextRules.expandContractions(body0)
        for ((from, to) in FORMAL_WORDS) body = TextRules.replacePhrase(body, from, to)
        body = TextRules.EMOJI.replace(body, "")
        body = body.replace('!', '.')
        val greeting = when {
            name == null -> ""
            name.isEmpty() -> "Hello,\n"
            else -> "Dear $name,\n"
        }
        val signoff = if (name != null && SIGNOFFS.none { body.contains(it, ignoreCase = true) }) "\n\nKind regards," else ""
        return greeting + TextRules.capitalizeSentences(body.trim()) + signoff
    }

    private fun warmer(t: String, app: String): String {
        val (name, body0) = TextRules.splitGreeting(t)
        var body = TextRules.contract(body0)
        body = TextRules.replacePhrase(body, "can we", "could we")
        val parts = TextRules.sentences(body).toMutableList()
        val sorryAt = parts.indexOfFirst { NEGATIVE.containsMatchIn(it) }
        if (sorryAt >= 0 && !Regex("(?i)\\bsorry\\b").containsMatchIn(body)) {
            parts[sorryAt] = "So sorry, " + lowerFirst(parts[sorryAt].trimStart())
        }
        body = parts.joinToString("") { if (it.endsWith(" ") || it == parts.last()) it else "$it " }.trim()
        if (!Regex("(?i)\\bthank").containsMatchIn(body)) body = body.trimEnd() + " Thanks so much!"
        else body = body.replace(Regex("\\.\\s*$"), "!")
        style.topEmoji(app)?.takeIf { style.has(StyleProfile.EMOJI_ID, app) }?.let { if (!body.contains(it)) body = "$body $it" }
        val greeting = when (name) {
            null -> ""
            "" -> "Hi! "
            else -> "Hi $name! "
        }
        return greeting + body
    }

    private fun shorter(t: String): String {
        val (name, body0) = TextRules.splitGreeting(t)
        var body = body0
        for (p in PLEASANTRIES) body = body.replace(p, "")
        for (f in FILLERS) body = TextRules.replacePhrase(body, f, "")
        body = TextRules.tidy(body)
        for ((from, to) in SHORTER_PHRASES) body = TextRules.replacePhrase(body, from, to)
        body = TextRules.contract(body)
        val tidied = TextRules.tidy(body)
        return if (name.isNullOrEmpty()) tidied else "$name, " + lowerFirst(tidied)
    }

    private fun longer(t: String): String {
        val (name, body0) = TextRules.splitGreeting(t)
        var body = body0
        for ((from, to) in ABBREVIATIONS) body = TextRules.replacePhrase(body, from, to)
        val greeting = when (name) {
            null -> ""
            "" -> "Hi, I hope you're well. "
            else -> "Hi $name, I hope you're well. "
        }
        if (!Regex("(?i)let me know|questions").containsMatchIn(body)) body = body.trimEnd() + " Let me know if you have any questions."
        if (!Regex("(?i)\\bthank").containsMatchIn(body)) body = body.trimEnd() + " Thanks!"
        return greeting + body
    }

    private fun myStyle(t: String, app: String, notes: MutableList<String>): String {
        val appName = AppNames.name(app)
        if (style.messageCount < StyleProfile.MIN_MESSAGES) {
            notes += "Not enough of your writing yet, so this is a tidy, neutral version"
            return TextRules.contract(t)
        }
        var out = t
        when {
            style.has(StyleProfile.FORMAL_IN, app) || AppNames.isEmail(app) && !style.has(StyleProfile.CASUAL, app) -> {
                out = TextRules.expandContractions(out); notes += "More formal in $appName"
            }
            style.has(StyleProfile.CASUAL, app) -> { out = TextRules.contract(out); notes += "Casual tone" }
        }
        if (style.has(StyleProfile.SHORT, app)) {
            for (p in PLEASANTRIES) out = out.replace(p, "")
            for (f in FILLERS) out = TextRules.replacePhrase(out, f, "")
            notes += "Short sentences"
        }
        if (style.has(StyleProfile.DIGITS, app)) {
            val d = TextRules.digitsForTimes(out)
            if (d != out) { out = d; notes += "Digits for times" }
        }
        if (style.has(StyleProfile.EXCLAIM, app)) out = out.trimEnd().replace(Regex("\\.$"), "!")
        style.signoff(app)?.takeIf { style.has(StyleProfile.SIGNOFF_ID, app) && TextRules.GREETING.containsMatchIn(t) }?.let { s ->
            if (!out.contains(s, ignoreCase = true)) { out = out.trimEnd() + "\n" + s; notes += "Your sign-off" }
        }
        style.topEmoji(app)?.takeIf { style.has(StyleProfile.EMOJI_ID, app) }?.let { e ->
            if (!out.contains(e)) out = out.trimEnd() + " " + e
        }
        if (notes.isEmpty()) notes += "Matches how you usually write"
        notes[notes.size - 1] = notes.last() + " · your $appName style"
        return out
    }

    companion object {
        const val MAX_CHARS = 1500

        private val NEGATIVE = Regex("(?i)\\b(can't|can’t|cannot|won't be able|unable|not going to make|can't make)\\b")
        private val SIGNOFFS = listOf("regards", "best wishes", "sincerely", "cheers", "thanks", "thank you")
        private val FORMAL_WORDS = listOf(
            "yeah" to "yes", "yep" to "yes", "nope" to "no", "gonna" to "going to", "wanna" to "want to",
            "gotta" to "have to", "thx" to "thank you", "thanks" to "thank you", "asap" to "as soon as possible",
            "cannot make it to" to "am unable to attend", "cannot make it" to "am unable to attend",
            "can we" to "would it be possible to", "let me know" to "please let me know",
            "please please let me know" to "please let me know", "if that works" to "whether this suits you",
            "kids" to "children", "a bit" to "slightly", "get back to you" to "follow up with you", "so sorry" to "I apologise",
        )
        private val SHORTER_PHRASES = listOf(
            "in order to" to "to", "at this point in time" to "now", "due to the fact that" to "because",
            "please let me know" to "let me know", "would it be possible to" to "can we",
            "I am not able to" to "I can't", "I am unable to" to "I can't", "a lot of" to "lots of",
            "as soon as possible" to "ASAP", "at the moment" to "now", "in the near future" to "soon",
        )
        private val FILLERS = listOf(
            "I just wanted to say that", "I just wanted to say", "I was wondering if", "I wanted to ask if",
            "I think that", "just", "really", "actually", "basically", "very", "kind of", "sort of",
        )
        private val PLEASANTRIES = listOf(
            Regex("(?i)\\b(I )?hope (you are|you're|you’re|this finds you|this email finds you) (doing )?(well|good|great)[.!]?\\s*"),
            Regex("(?i)\\bI hope you had a (great|good|nice) (weekend|week|day)[.!]?\\s*"),
        )
        private val ABBREVIATIONS = listOf(
            "pls" to "please", "plz" to "please", "thx" to "thanks", "tmrw" to "tomorrow", "tmr" to "tomorrow",
            "asap" to "as soon as possible", "btw" to "by the way", "idk" to "I don't know", "u" to "you", "ur" to "your",
        )
    }
}

class RewriteException(val error: RewriteError) : Exception(error.toString())
