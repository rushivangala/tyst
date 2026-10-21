package app.tyst.core.write

/** Phrase-level rewrite helpers shared by the rewrite and proofread tools. */
internal object TextRules {
    /** Contracted form to full form; order matters for overlapping keys. */
    val CONTRACTIONS = listOf(
        "I'm" to "I am", "I'll" to "I will", "I've" to "I have", "I'd" to "I would",
        "you're" to "you are", "you'll" to "you will", "you've" to "you have",
        "we're" to "we are", "we'll" to "we will", "we've" to "we have",
        "they're" to "they are", "they'll" to "they will",
        "it's" to "it is", "that's" to "that is", "there's" to "there is", "what's" to "what is",
        "don't" to "do not", "doesn't" to "does not", "didn't" to "did not",
        "can't" to "cannot", "won't" to "will not", "wouldn't" to "would not", "couldn't" to "could not",
        "shouldn't" to "should not", "isn't" to "is not", "aren't" to "are not", "wasn't" to "was not",
        "weren't" to "were not", "haven't" to "have not", "hasn't" to "has not", "let's" to "let us",
    )

    private fun phrase(p: String) = Regex("(?<![\\p{L}'’])" + Regex.escape(p).replace("'", "\\E['’]\\Q") + "(?![\\p{L}'’])", RegexOption.IGNORE_CASE)

    /** Replaces [from] with [to], keeping a leading capital and leaving "I" capitalised. */
    fun replacePhrase(text: String, from: String, to: String): String =
        phrase(from).replace(text) { m ->
            val found = m.value
            when {
                found.first().isUpperCase() -> to.replaceFirstChar { it.uppercaseChar() }
                to == "I" || to.startsWith("I ") || to.startsWith("I'") -> to
                to.length > 1 && to[0].isUpperCase() && to[1].isUpperCase() -> to
                else -> to.replaceFirstChar { it.lowercaseChar() }
            }
        }

    fun expandContractions(text: String): String =
        CONTRACTIONS.fold(text) { t, (short, full) -> replacePhrase(t, short, full) }

    fun contract(text: String): String =
        CONTRACTIONS.filter { it.second != "let us" && it.second != "it is" }.fold(text) { t, (short, full) ->
            replacePhrase(t, full, short)
        }.let { replacePhrase(it, "can not", "can't") }

    val GREETING = Regex("^(hi|hey|hello|hej|dear|morning|good morning)\\b[ ]*([\\p{Lu}][\\p{L}\\-]*)?[ ]*[,!.]?[ ]*", RegexOption.IGNORE_CASE)

    fun splitGreeting(text: String): Pair<String?, String> {
        val m = GREETING.find(text) ?: return null to text
        val name = m.groupValues[2].takeIf { it.isNotEmpty() }
        return (name ?: "") to text.substring(m.range.last + 1)
    }

    val SENTENCE = Regex("[^.!?]+[.!?]*\\s*")

    fun sentences(text: String): List<String> = SENTENCE.findAll(text).map { it.value }.filter { it.isNotBlank() }.toList()

    fun capitalizeSentences(text: String): String {
        val sb = StringBuilder(text)
        var start = true
        for (i in sb.indices) {
            val c = sb[i]
            if (start && c.isLetter()) {
                sb.setCharAt(i, c.uppercaseChar()); start = false
            } else if (c == '.' || c == '!' || c == '?' || c == '\n') {
                start = true
            } else if (!c.isWhitespace() && c != '"' && c != '(') {
                start = false
            }
        }
        return sb.toString()
    }

    fun tidy(text: String): String = text
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex(" +([,.!?;:])"), "$1")
        .replace(Regex(",\\s*([.!?])"), "$1")
        .lines().joinToString("\n") { it.trim() }
        .trim()

    val NUMBER_WORDS = mapOf(
        "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5", "six" to "6", "seven" to "7",
        "eight" to "8", "nine" to "9", "ten" to "10", "eleven" to "11", "twelve" to "12",
    )

    fun digitsForTimes(text: String): String =
        Regex("\\b(at|around|by|until|from|after|before) (one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\\b", RegexOption.IGNORE_CASE)
            .replace(text) { m -> m.groupValues[1] + " " + NUMBER_WORDS.getValue(m.groupValues[2].lowercase()) }

    val EMOJI = Regex("\\s*[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}][\\x{FE0F}\\x{1F3FB}-\\x{1F3FF}]?")
}

/** Changed-word ranges in a rewritten text, for the accent underline. */
object WordDiff {
    fun changedRanges(old: String, new: String): List<IntRange> {
        val a = Regex("\\S+").findAll(old).map { it.value.lowercase().trim(',', '.', '!', '?') }.toList()
        val bMatches = Regex("\\S+").findAll(new).toList()
        val b = bMatches.map { it.value.lowercase().trim(',', '.', '!', '?') }
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val keep = BooleanArray(b.size)
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { keep[j] = true; i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> i++
                else -> j++
            }
        }
        val out = ArrayList<IntRange>()
        for (k in b.indices) if (!keep[k]) {
            val r = bMatches[k].range
            if (out.isNotEmpty() && out.last().last + 2 >= r.first) out[out.size - 1] = out.last().first..r.last else out += r
        }
        return out
    }
}
