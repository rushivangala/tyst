package app.tyst.core.field

/** Constants copied from android.text.InputType and android.view.inputmethod.EditorInfo. */
object Android {
    const val TYPE_MASK_CLASS = 0x0000000f
    const val TYPE_MASK_VARIATION = 0x00000ff0
    const val TYPE_MASK_FLAGS = 0x00fff000
    const val TYPE_NULL = 0
    const val TYPE_CLASS_TEXT = 1
    const val TYPE_CLASS_NUMBER = 2
    const val TYPE_CLASS_PHONE = 3
    const val TYPE_CLASS_DATETIME = 4

    const val TYPE_TEXT_VARIATION_URI = 0x10
    const val TYPE_TEXT_VARIATION_EMAIL_ADDRESS = 0x20
    const val TYPE_TEXT_VARIATION_EMAIL_SUBJECT = 0x30
    const val TYPE_TEXT_VARIATION_SHORT_MESSAGE = 0x40
    const val TYPE_TEXT_VARIATION_LONG_MESSAGE = 0x50
    const val TYPE_TEXT_VARIATION_PERSON_NAME = 0x60
    const val TYPE_TEXT_VARIATION_POSTAL_ADDRESS = 0x70
    const val TYPE_TEXT_VARIATION_PASSWORD = 0x80
    const val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 0x90
    const val TYPE_TEXT_VARIATION_WEB_EDIT_TEXT = 0xa0
    const val TYPE_TEXT_VARIATION_FILTER = 0xb0
    const val TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS = 0xd0
    const val TYPE_TEXT_VARIATION_WEB_PASSWORD = 0xe0
    const val TYPE_NUMBER_VARIATION_PASSWORD = 0x10

    const val TYPE_TEXT_FLAG_CAP_CHARACTERS = 0x1000
    const val TYPE_TEXT_FLAG_CAP_WORDS = 0x2000
    const val TYPE_TEXT_FLAG_CAP_SENTENCES = 0x4000
    const val TYPE_TEXT_FLAG_AUTO_CORRECT = 0x8000
    const val TYPE_TEXT_FLAG_AUTO_COMPLETE = 0x10000
    const val TYPE_TEXT_FLAG_MULTI_LINE = 0x20000
    const val TYPE_TEXT_FLAG_NO_SUGGESTIONS = 0x80000
    const val TYPE_NUMBER_FLAG_SIGNED = 0x1000
    const val TYPE_NUMBER_FLAG_DECIMAL = 0x2000

    const val IME_MASK_ACTION = 0x000000ff
    const val IME_ACTION_UNSPECIFIED = 0
    const val IME_ACTION_NONE = 1
    const val IME_ACTION_GO = 2
    const val IME_ACTION_SEARCH = 3
    const val IME_ACTION_SEND = 4
    const val IME_ACTION_NEXT = 5
    const val IME_ACTION_DONE = 6
    const val IME_ACTION_PREVIOUS = 7
    const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x1000000
    const val IME_FLAG_NO_ENTER_ACTION = 0x40000000
}

enum class FieldKind { TEXT, MESSAGE, EMAIL, URI, PERSON_NAME, POSTAL_ADDRESS, PASSWORD, NUMBER, PHONE, CODE, DATETIME, SEARCH }

enum class KeyLayoutKind { LETTERS, NUMBER_PAD, PHONE_PAD }

enum class EnterAction { NEWLINE, GO, SEARCH, SEND, NEXT, DONE, PREVIOUS }

/** What the keyboard may do in the focused field, derived from the field's metadata only. */
data class FieldInfo(
    val kind: FieldKind = FieldKind.TEXT,
    val layout: KeyLayoutKind = KeyLayoutKind.LETTERS,
    val packageName: String = "",
    /** No learning, no memory, a visible "Not learning here" marker. */
    val incognito: Boolean = false,
    val predictions: Boolean = true,
    val autocorrect: Boolean = true,
    val autoCapSentences: Boolean = true,
    val autoCapWords: Boolean = false,
    val autoCapAll: Boolean = false,
    val enterAction: EnterAction = EnterAction.NEWLINE,
    val multiLine: Boolean = false,
    val writingTools: Boolean = true,
    val memoryAllowed: Boolean = true,
    val hint: String = "",
) {
    val learningAllowed: Boolean get() = !incognito
}

object FieldClassifier {
    /** Browsers are excluded from private memory by default: forms there often hold other people's data. */
    val DEFAULT_MEMORY_EXCLUDED_APPS = setOf(
        "com.android.chrome", "org.mozilla.firefox", "com.brave.browser", "com.microsoft.emmx",
        "com.sec.android.app.sbrowser", "com.duckduckgo.mobile.android", "com.opera.browser",
    )

    private val CODE_HINT = Regex("(?i)\\b(code|otp|one[- ]time|verification|verify|pin|passcode|2fa|kod)\\b")

    fun classify(
        inputType: Int,
        imeOptions: Int,
        packageName: String = "",
        hint: String = "",
        memoryExcludedApps: Set<String> = DEFAULT_MEMORY_EXCLUDED_APPS,
    ): FieldInfo {
        val cls = inputType and Android.TYPE_MASK_CLASS
        val variation = inputType and Android.TYPE_MASK_VARIATION
        val flags = inputType and Android.TYPE_MASK_FLAGS
        val noLearning = imeOptions and Android.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        val action = enterAction(imeOptions, flags)
        val memoryApp = packageName !in memoryExcludedApps
        val looksLikeCode = CODE_HINT.containsMatchIn(hint)

        return when (cls) {
            Android.TYPE_CLASS_NUMBER -> {
                val password = variation == Android.TYPE_NUMBER_VARIATION_PASSWORD
                FieldInfo(
                    kind = if (looksLikeCode || password) FieldKind.CODE else FieldKind.NUMBER,
                    layout = KeyLayoutKind.NUMBER_PAD, packageName = packageName,
                    incognito = true, predictions = false, autocorrect = false,
                    autoCapSentences = false, enterAction = action, writingTools = false, memoryAllowed = false, hint = hint,
                )
            }
            Android.TYPE_CLASS_PHONE -> FieldInfo(
                kind = FieldKind.PHONE, layout = KeyLayoutKind.PHONE_PAD, packageName = packageName,
                incognito = noLearning, predictions = false, autocorrect = false, autoCapSentences = false,
                enterAction = action, writingTools = false, memoryAllowed = memoryApp && !noLearning, hint = hint,
            )
            Android.TYPE_CLASS_DATETIME -> FieldInfo(
                kind = FieldKind.DATETIME, layout = KeyLayoutKind.NUMBER_PAD, packageName = packageName,
                incognito = true, predictions = false, autocorrect = false, autoCapSentences = false,
                enterAction = action, writingTools = false, memoryAllowed = false, hint = hint,
            )
            Android.TYPE_CLASS_TEXT -> text(variation, flags, noLearning, action, packageName, hint, memoryApp, looksLikeCode)
            // TYPE_NULL: a terminal or custom view. Send plain key events, no learning.
            else -> FieldInfo(
                kind = FieldKind.TEXT, packageName = packageName, incognito = true, predictions = false,
                autocorrect = false, autoCapSentences = false, enterAction = EnterAction.NEWLINE,
                writingTools = false, memoryAllowed = false, hint = hint,
            )
        }
    }

    private fun text(
        variation: Int, flags: Int, noLearning: Boolean, action: EnterAction,
        pkg: String, hint: String, memoryApp: Boolean, looksLikeCode: Boolean,
    ): FieldInfo {
        val multiLine = flags and Android.TYPE_TEXT_FLAG_MULTI_LINE != 0
        val noSuggestions = flags and Android.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
        val base = FieldInfo(
            packageName = pkg, incognito = noLearning, predictions = !noSuggestions, autocorrect = !noSuggestions,
            autoCapSentences = flags and Android.TYPE_TEXT_FLAG_CAP_SENTENCES != 0,
            autoCapWords = flags and Android.TYPE_TEXT_FLAG_CAP_WORDS != 0,
            autoCapAll = flags and Android.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0,
            enterAction = action, multiLine = multiLine, memoryAllowed = memoryApp && !noLearning, hint = hint,
        )
        return when (variation) {
            Android.TYPE_TEXT_VARIATION_PASSWORD, Android.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, Android.TYPE_TEXT_VARIATION_WEB_PASSWORD ->
                base.copy(
                    kind = if (looksLikeCode) FieldKind.CODE else FieldKind.PASSWORD,
                    incognito = true, predictions = false, autocorrect = false,
                    autoCapSentences = false, autoCapWords = false, writingTools = false, memoryAllowed = false,
                )
            Android.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, Android.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ->
                base.copy(kind = FieldKind.EMAIL, autocorrect = false, autoCapSentences = false, writingTools = false)
            Android.TYPE_TEXT_VARIATION_URI ->
                base.copy(kind = FieldKind.URI, autocorrect = false, autoCapSentences = false, writingTools = false)
            Android.TYPE_TEXT_VARIATION_PERSON_NAME ->
                base.copy(kind = FieldKind.PERSON_NAME, autocorrect = false, autoCapWords = true, writingTools = false)
            Android.TYPE_TEXT_VARIATION_POSTAL_ADDRESS ->
                base.copy(kind = FieldKind.POSTAL_ADDRESS, autocorrect = false, writingTools = false)
            Android.TYPE_TEXT_VARIATION_FILTER ->
                base.copy(kind = FieldKind.SEARCH, autocorrect = false, autoCapSentences = false, writingTools = false)
            Android.TYPE_TEXT_VARIATION_SHORT_MESSAGE, Android.TYPE_TEXT_VARIATION_LONG_MESSAGE ->
                base.copy(kind = FieldKind.MESSAGE)
            else -> when {
                looksLikeCode -> base.copy(kind = FieldKind.CODE, predictions = false, autocorrect = false, incognito = true, writingTools = false, memoryAllowed = false)
                action == EnterAction.SEARCH -> base.copy(kind = FieldKind.SEARCH, writingTools = false)
                else -> base
            }
        }
    }

    private fun enterAction(imeOptions: Int, flags: Int): EnterAction {
        if (imeOptions and Android.IME_FLAG_NO_ENTER_ACTION != 0 || flags and Android.TYPE_TEXT_FLAG_MULTI_LINE != 0) {
            return EnterAction.NEWLINE
        }
        return when (imeOptions and Android.IME_MASK_ACTION) {
            Android.IME_ACTION_GO -> EnterAction.GO
            Android.IME_ACTION_SEARCH -> EnterAction.SEARCH
            Android.IME_ACTION_SEND -> EnterAction.SEND
            Android.IME_ACTION_NEXT -> EnterAction.NEXT
            Android.IME_ACTION_DONE -> EnterAction.DONE
            Android.IME_ACTION_PREVIOUS -> EnterAction.PREVIOUS
            else -> EnterAction.NEWLINE
        }
    }
}
