package app.tyst.core.style

import app.tyst.core.text.Words

/** Friendly names for common packages; used in plain-language habit notes. */
object AppNames {
    private val names = mapOf(
        "com.slack" to "Slack", "com.google.android.gm" to "Gmail", "com.whatsapp" to "WhatsApp",
        "com.google.android.apps.messaging" to "Messages", "org.telegram.messenger" to "Telegram",
        "com.microsoft.teams" to "Teams", "com.microsoft.office.outlook" to "Outlook",
        "com.google.android.keep" to "Keep", "org.thoughtcrime.securesms" to "Signal",
        "com.discord" to "Discord", "com.facebook.orca" to "Messenger", "app.tyst.keyboard" to "Tyst",
    )
    private val emailApps = setOf("com.google.android.gm", "com.microsoft.office.outlook", "com.samsung.android.email.provider", "ch.protonmail.android")

    fun name(pkg: String): String = names[pkg] ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercaseChar() }
    fun isEmail(pkg: String): Boolean = pkg in emailApps
}

/** A plain-language observation about how the user writes. Each can be turned off. */
data class Habit(val id: String, val title: String, val detail: String, val enabled: Boolean = true)

/**
 * Learns writing habits from messages the user sends: sentence length, contractions, digits for
 * times, emoji, exclamation marks, greetings and sign-offs, and phrases used often.
 * Kept on the phone; the app layer stores [encode] output encrypted. Not thread-safe.
 */
class StyleProfile {
    class Stats {
        var messages = 0
        var sentences = 0
        var words = 0
        var contractions = 0
        var fullForms = 0
        var digitTimes = 0
        var wordTimes = 0
        var exclaims = 0
        val emoji = HashMap<String, Int>()
        val signoffs = HashMap<String, Int>()
        val phrases = HashMap<String, Int>()

        val avgSentenceWords: Double get() = if (sentences == 0) 0.0 else words.toDouble() / sentences
        val contractionRatio: Double get() = if (contractions + fullForms == 0) 0.5 else contractions.toDouble() / (contractions + fullForms)
    }

    private val apps = HashMap<String, Stats>()
    val global = Stats()
    private val disabled = HashSet<String>()
    var firstLearnedAt: Long = 0
        private set

    val messageCount: Int get() = global.messages
    val disabledHabits: Set<String> get() = disabled

    fun stats(app: String): Stats? = apps[app]

    fun learn(message: String, app: String, now: Long) {
        val text = message.trim()
        if (text.length < 2) return
        if (firstLearnedAt == 0L) firstLearnedAt = now
        for (s in listOf(global, apps.getOrPut(app) { Stats() })) learnInto(s, text)
        if (global.phrases.size > MAX_PHRASES * 4) prunePhrases()
    }

    private fun learnInto(s: Stats, text: String) {
        s.messages++
        val sentences = text.split(Regex("[.!?\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
        s.sentences += sentences.size
        val tokens = Words.tokens(text)
        s.words += tokens.size
        val lower = Words.normalize(text)
        s.contractions += CONTRACTION.findAll(lower).count()
        s.fullForms += FULL_FORM.findAll(lower).count()
        s.digitTimes += DIGIT_TIME.findAll(lower).count()
        s.wordTimes += WORD_TIME.findAll(lower).count()
        s.exclaims += text.count { it == '!' }
        EMOJI.findAll(text).forEach { s.emoji.merge(it.value, 1, Int::plus) }
        val lastLine = text.lines().last().trim()
        if (lastLine.length in 2..24 && text.lines().size > 1) s.signoffs.merge(lastLine.trimEnd(',', '.', '!'), 1, Int::plus)
        else SIGNOFF.find(text)?.let { s.signoffs.merge(it.groupValues[1].trim(), 1, Int::plus) }
        val norm = tokens.map(Words::normalize)
        for (n in 3..4) for (i in 0..norm.size - n) s.phrases.merge(norm.subList(i, i + n).joinToString(" "), 1, Int::plus)
    }

    private fun prunePhrases() {
        for (s in apps.values + global) s.phrases.entries.removeAll { it.value < 2 }
    }

    fun setHabitEnabled(id: String, enabled: Boolean) {
        if (enabled) disabled.remove(id) else disabled += id
    }

    fun isEnabled(id: String) = id !in disabled

    /** Phrases typed at least three times, most used first. */
    fun commonPhrases(limit: Int = 6): List<String> =
        global.phrases.entries.filter { it.value >= 3 }.sortedByDescending { it.value * it.key.length }.map { it.key }
            .fold(ArrayList<String>()) { acc, p -> if (acc.none { it.contains(p) || p.contains(it) }) acc += p; acc }
            .take(limit)

    fun topEmoji(app: String? = null): String? =
        (app?.let { apps[it] } ?: global).emoji.entries.filter { it.value >= 2 }.maxByOrNull { it.value }?.key

    fun signoff(app: String? = null): String? =
        (app?.let { apps[it] } ?: global).signoffs.entries.filter { it.value >= 2 }.maxByOrNull { it.value }?.key

    /** Habits worth showing; needs [MIN_MESSAGES] messages before saying anything. */
    fun habits(): List<Habit> {
        if (global.messages < MIN_MESSAGES) return emptyList()
        val out = ArrayList<Habit>()
        fun add(id: String, title: String, detail: String) { out += Habit(id, title, detail, isEnabled(id)) }
        if (global.avgSentenceWords in 0.1..9.0) add(SHORT, "Short sentences", "About ${global.avgSentenceWords.toInt().coerceAtLeast(1)} words a sentence")
        if (global.avgSentenceWords > 16) add(LONG, "Longer sentences", "About ${global.avgSentenceWords.toInt()} words a sentence")
        if (global.contractionRatio >= 0.6) add(CASUAL, "Casual tone", "“can’t” rather than “cannot”")
        for ((app, s) in apps) {
            if (s.messages >= MIN_MESSAGES && s.contractionRatio <= 0.3 && global.contractionRatio > 0.45) {
                add("$FORMAL_IN$app", "More formal in ${AppNames.name(app)}", "Full forms like “I am” and “do not”")
            }
        }
        if (global.digitTimes >= 2 && global.digitTimes > global.wordTimes) add(DIGITS, "Digits for times", "“at 6” rather than “at six”")
        topEmoji()?.let { add(EMOJI_ID, "Uses $it", "Your most used emoji") }
        if (global.exclaims.toDouble() / global.messages >= 0.4) add(EXCLAIM, "Friendly exclamation marks", "Often ends with “!”")
        signoff()?.let { add(SIGNOFF_ID, "Signs off with “$it”", "Added when a rewrite has a greeting") }
        return out
    }

    /** True when the habit is known and switched on, for [app] or overall. */
    fun has(id: String, app: String? = null): Boolean {
        if (!isEnabled(id) || global.messages < MIN_MESSAGES) return false
        val s = app?.let { apps[it] }?.takeIf { it.messages >= MIN_MESSAGES } ?: global
        return when (id) {
            SHORT -> s.avgSentenceWords in 0.1..9.0
            CASUAL -> s.contractionRatio >= 0.6
            DIGITS -> s.digitTimes >= 2 && s.digitTimes > s.wordTimes
            EXCLAIM -> s.exclaims.toDouble() / s.messages >= 0.4
            FORMAL_IN -> app != null && s.contractionRatio <= 0.3 && isEnabled("$FORMAL_IN$app")
            EMOJI_ID -> topEmoji(app) != null
            SIGNOFF_ID -> signoff(app) != null
            else -> false
        }
    }

    fun clear() {
        apps.clear(); disabled.clear(); firstLearnedAt = 0
        global.apply { messages = 0; sentences = 0; words = 0; contractions = 0; fullForms = 0; digitTimes = 0; wordTimes = 0; exclaims = 0; emoji.clear(); signoffs.clear(); phrases.clear() }
    }

    fun forgetApp(app: String) { apps.remove(app) }

    fun encode(): String = buildString {
        appendLine("V\t1")
        appendLine("F\t$firstLearnedAt")
        for (d in disabled) appendLine("X\t$d")
        fun stats(key: String, s: Stats) {
            appendLine(listOf("S", key, s.messages, s.sentences, s.words, s.contractions, s.fullForms, s.digitTimes, s.wordTimes, s.exclaims).joinToString("\t"))
            for ((e, n) in s.emoji) appendLine("E\t$key\t$e\t$n")
            for ((e, n) in s.signoffs) appendLine("O\t$key\t${e.replace('\t', ' ')}\t$n")
            for ((e, n) in s.phrases) if (n >= 2) appendLine("P\t$key\t$e\t$n")
        }
        stats("*", global)
        for ((app, s) in apps) stats(app, s)
    }

    companion object {
        const val MIN_MESSAGES = 5
        const val MAX_PHRASES = 2000
        const val SHORT = "short_sentences"
        const val LONG = "long_sentences"
        const val CASUAL = "casual"
        const val FORMAL_IN = "formal_in:"
        const val DIGITS = "digits_for_times"
        const val EMOJI_ID = "emoji"
        const val EXCLAIM = "exclaim"
        const val SIGNOFF_ID = "signoff"

        private val CONTRACTION = Regex("\\b(i'm|i'll|i've|i'd|you're|we're|they're|it's|that's|don't|doesn't|didn't|can't|won't|isn't|aren't|wasn't|couldn't|wouldn't|shouldn't|haven't|let's|we'll|you'll)\\b")
        private val FULL_FORM = Regex("\\b(i am|i will|i have|you are|we are|they are|it is|that is|do not|does not|did not|cannot|can not|will not|is not|are not|was not|could not|would not|should not|have not|let us|we will|you will)\\b")
        private val DIGIT_TIME = Regex("\\b(at|around|by|until|from|after|before) [0-9]{1,2}(:[0-9]{2})?\\b")
        private val WORD_TIME = Regex("\\b(at|around|by|until|from|after|before) (one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\\b")
        val EMOJI = Regex("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}][\\x{FE0F}\\x{1F3FB}-\\x{1F3FF}]?")
        private val SIGNOFF = Regex("(?i)(?:^|[.!?\\n]\\s*)((?:best|kind regards|regards|cheers|thanks|hej hej|kram|/\\w+)[^.!?\\n]{0,16})\\s*$")

        fun decode(text: String): StyleProfile {
            val p = StyleProfile()
            for (line in text.lineSequence()) {
                val f = line.split('\t')
                try {
                    fun stats(key: String) = if (key == "*") p.global else p.apps.getOrPut(key) { Stats() }
                    when (f[0]) {
                        "F" -> p.firstLearnedAt = f[1].toLong()
                        "X" -> p.disabled += f[1]
                        "S" -> stats(f[1]).apply {
                            messages = f[2].toInt(); sentences = f[3].toInt(); words = f[4].toInt(); contractions = f[5].toInt()
                            fullForms = f[6].toInt(); digitTimes = f[7].toInt(); wordTimes = f[8].toInt(); exclaims = f[9].toInt()
                        }
                        "E" -> stats(f[1]).emoji[f[2]] = f[3].toInt()
                        "O" -> stats(f[1]).signoffs[f[2]] = f[3].toInt()
                        "P" -> stats(f[1]).phrases[f[2]] = f[3].toInt()
                    }
                } catch (_: RuntimeException) {
                }
            }
            return p
        }
    }
}
