package app.tyst.core.memory

import app.tyst.core.field.FieldInfo
import app.tyst.core.field.FieldKind

enum class MemoryType(val label: String) {
    ADDRESS("Address"),
    BOOKING("Booking reference"),
    PHONE("Phone number"),
    EMAIL("Email"),
}

data class MemoryItem(
    val id: String,
    val type: MemoryType,
    val value: String,
    val sourceApp: String,
    val createdAt: Long,
    val pinned: Boolean = false,
    val lastUsedAt: Long = createdAt,
) {
    /** Short form for the strip, so a full address isn't shown to people nearby. */
    fun preview(max: Int = 18): String = if (value.length <= max) value else value.take(max - 1).trimEnd(' ', ',') + "…"
}

/** Private memory items. The app layer stores [encode] output encrypted. Not thread-safe. */
class MemoryStore {
    private val items = LinkedHashMap<String, MemoryItem>()
    private var nextId = 1L

    val all: List<MemoryItem> get() = items.values.sortedByDescending { it.lastUsedAt }
    val size: Int get() = items.size

    fun get(id: String): MemoryItem? = items[id]

    /** Adds an item, or refreshes the existing one with the same type and value. */
    fun add(type: MemoryType, value: String, sourceApp: String, now: Long): MemoryItem {
        val existing = items.values.firstOrNull { it.type == type && norm(it.value) == norm(value) }
        if (existing != null) {
            val updated = existing.copy(lastUsedAt = now)
            items[existing.id] = updated
            return updated
        }
        val item = MemoryItem("m${nextId++}", type, value.trim(), sourceApp, now)
        items[item.id] = item
        return item
    }

    /** Puts back an item removed by [remove], for Undo. */
    fun restore(item: MemoryItem) {
        items[item.id] = item
        val n = item.id.removePrefix("m").toLongOrNull()
        if (n != null && n >= nextId) nextId = n + 1
    }

    fun remove(id: String): MemoryItem? = items.remove(id)

    fun setPinned(id: String, pinned: Boolean) {
        items[id]?.let { items[id] = it.copy(pinned = pinned) }
    }

    fun markUsed(id: String, now: Long) {
        items[id]?.let { items[id] = it.copy(lastUsedAt = now) }
    }

    fun search(query: String): List<MemoryItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter { it.value.lowercase().contains(q) || it.type.label.lowercase().contains(q) || it.sourceApp.lowercase().contains(q) }
    }

    /** Forgets unpinned items not used for [days] days. 0 keeps everything. Returns how many went. */
    fun expire(now: Long, days: Int): Int {
        if (days <= 0) return 0
        val cutoff = now - days * DAY_MS
        val gone = items.values.filter { !it.pinned && it.lastUsedAt < cutoff }.map { it.id }
        gone.forEach { items.remove(it) }
        return gone.size
    }

    fun clear() = items.clear()

    fun encode(): String = buildString {
        appendLine("V\t1")
        for (i in items.values) {
            appendLine(listOf("I", i.id, i.type.name, esc(i.value), esc(i.sourceApp), i.createdAt, if (i.pinned) 1 else 0, i.lastUsedAt).joinToString("\t"))
        }
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000

        private fun esc(s: String) = s.replace('\t', ' ').replace('\n', ' ')

        fun decode(text: String): MemoryStore {
            val store = MemoryStore()
            for (line in text.lineSequence()) {
                val p = line.split('\t')
                if (p.size < 8 || p[0] != "I") continue
                try {
                    store.restore(MemoryItem(p[1], MemoryType.valueOf(p[2]), p[3], p[4], p[5].toLong(), p[6] == "1", p[7].toLong()))
                } catch (_: RuntimeException) {
                }
            }
            return store
        }
    }
}

data class Extracted(val type: MemoryType, val value: String)

/**
 * Picks out things worth remembering from text the user typed. It only saves values introduced by a
 * phrase that marks them as the user's own ("my address is …"), never card numbers or one-time codes.
 */
object MemoryExtractor {
    private const val END = "(?=[.!?\\n]|$| and | but )"
    private val ADDRESS = Regex("(?i)\\b(?:my (?:home |new )?address is|i live at|deliver(?:y)? to|send it to)\\s+([^.!?\\n]{5,80}?)$END")
    private val BOOKING = Regex("(?i)\\b(?:booking|reservation|confirmation)(?: ref(?:erence)?| code| number| no\\.?)?(?: is|:)?\\s+([A-Za-z0-9]{5,8})\\b")
    private val PHONE = Regex("(?i)\\b(?:my (?:phone |mobile )?number is|my number|call me (?:on|at)|reach me on)\\s*:?\\s*(\\+?[0-9][0-9 \\-]{6,16}[0-9])(?![ \\-]?[0-9])")
    private val EMAIL = Regex("(?i)\\b(?:email me at|my email is|my email address is|reach me at|mail me at)\\s+([A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,})")

    fun extract(text: String): List<Extracted> {
        val out = ArrayList<Extracted>()
        ADDRESS.findAll(text).forEach { m ->
            val v = m.groupValues[1].trim().trimEnd(',', ';')
            if (v.any { it.isDigit() } && v.any { it.isLetter() } && !looksSecret(v)) out += Extracted(MemoryType.ADDRESS, v)
        }
        BOOKING.findAll(text).forEach { m ->
            val v = m.groupValues[1]
            // Needs a letter: an all-digit "booking code" is too close to a one-time code to keep.
            if (v.any { it.isLetter() } && v.any { it.isDigit() || it.isUpperCase() } && v.uppercase() == v) out += Extracted(MemoryType.BOOKING, v)
        }
        PHONE.findAll(text).forEach { m ->
            val v = m.groupValues[1].trim()
            val digits = v.count { it.isDigit() }
            if (digits in 7..15 && !looksSecret(v)) out += Extracted(MemoryType.PHONE, v)
        }
        EMAIL.findAll(text).forEach { m -> out += Extracted(MemoryType.EMAIL, m.groupValues[1]) }
        return out.distinct()
    }

    /** True for payment card numbers (Luhn-valid 13–19 digits). */
    fun looksSecret(value: String): Boolean {
        val digits = value.filter { it.isDigit() }
        if (digits.length !in 13..19) return false
        var sum = 0
        digits.reversed().forEachIndexed { i, c ->
            var d = c - '0'
            if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9 }
            sum += d
        }
        return sum % 10 == 0
    }
}

/** Decides which remembered items to offer for the text before the cursor and the focused field. */
object MemoryMatcher {
    private val TRIGGERS: Map<MemoryType, List<String>> = mapOf(
        MemoryType.ADDRESS to listOf("my address is", "address is", "i live at", "deliver to", "send it to", "my address:"),
        MemoryType.BOOKING to listOf("booking ref", "booking ref is", "booking reference", "booking reference is", "booking code", "reservation code", "confirmation number", "confirmation code"),
        MemoryType.PHONE to listOf("my number is", "my number", "my phone number is", "call me on", "call me at", "reach me on"),
        MemoryType.EMAIL to listOf("email me at", "my email is", "my email address is", "reach me at", "mail me at"),
    )

    fun typeFor(before: String, field: FieldInfo): MemoryType? {
        when (field.kind) {
            FieldKind.POSTAL_ADDRESS -> return MemoryType.ADDRESS
            FieldKind.EMAIL -> return MemoryType.EMAIL
            FieldKind.PHONE -> return MemoryType.PHONE
            else -> Unit
        }
        val tail = before.takeLast(48).lowercase().trimEnd()
        val stripped = tail.trimEnd(':')
        for ((type, phrases) in TRIGGERS) {
            if (phrases.any { stripped.endsWith(it) }) return type
        }
        return null
    }

    fun match(before: String, field: FieldInfo, store: MemoryStore, limit: Int = 2): List<MemoryItem> {
        // Offering is allowed in excluded apps; saving there is not (see FieldInfo.memoryAllowed).
        if (field.incognito) return emptyList()
        val type = typeFor(before, field) ?: return emptyList()
        return store.all.filter { it.type == type }
            .sortedWith(compareByDescending<MemoryItem> { it.pinned }.thenByDescending { it.lastUsedAt })
            .take(limit)
    }
}
