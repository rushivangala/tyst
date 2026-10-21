package app.tyst.keyboard.ime

import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputMethodManager
import app.tyst.core.TystEngine
import app.tyst.core.field.FieldClassifier
import app.tyst.core.field.FieldInfo
import app.tyst.core.field.KeyLayoutKind
import app.tyst.core.layout.Key
import app.tyst.core.layout.KeyType
import app.tyst.core.layout.KeyboardLayout
import app.tyst.core.layout.Layouts
import app.tyst.core.layout.PopupAction
import app.tyst.core.emoji.Emoji
import app.tyst.core.memory.MemoryItem
import app.tyst.core.predict.Suggestion
import app.tyst.core.settings.KeyboardSettings
import app.tyst.core.settings.OneHanded
import app.tyst.core.text.SwedishRestorer
import app.tyst.core.theme.KeyboardColors
import app.tyst.core.typing.StripState
import app.tyst.core.typing.TypingController
import app.tyst.core.write.Fix
import app.tyst.core.write.RewriteResult
import app.tyst.core.write.Tone
import app.tyst.keyboard.data.Graph
import app.tyst.keyboard.graph
import app.tyst.keyboard.ui.MainActivity
import app.tyst.keyboard.ui.MicPermissionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** The Tyst keyboard. All typing logic lives in core's [TypingController]; this class wires it to Android. */
class TystImeService : InputMethodService(), KeyboardView.Listener, StripView.Listener {
    private lateinit var g: Graph
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private var controller: TypingController? = null
    private var engine: TystEngine? = null
    private var root: KeyboardRoot? = null
    private var keyboard: KeyboardView? = null
    private var strip: StripView? = null
    private var target: InputConnectionTarget? = null
    private var editor: EditorInfo? = null
    private var focused = FieldInfo()
    private var colors: KeyboardColors? = null
    private var mode = Mode.LETTERS
    private var voice: VoiceInput? = null
    private var voicePanel: VoicePanel? = null
    private var toolsPanel: WritingToolsPanel? = null
    private val settings: KeyboardSettings get() = g.settings.current

    private enum class Mode { LETTERS, SYMBOLS, SYMBOLS_MORE, NUMBER, PHONE }

    private val keySender = object : InputConnectionTarget.KeySender {
        override fun sendDownUpKeyEvents(keyCode: Int) = this@TystImeService.sendDownUpKeyEvents(keyCode)
        override fun sendKeyChar(c: Char) = this@TystImeService.sendKeyChar(c)
    }

    private val clipboard by lazy { getSystemService(ClipboardManager::class.java) }
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { readClipboard(onlyFresh = false) }
    private val ticker = object : Runnable {
        override fun run() {
            renderStrip()
            if (controller?.hasLiveCode() == true) handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        g = graph
        g.withEngine { e ->
            engine = e
            controller = TypingController(e).apply { onChanged = { onControllerChanged() } }
            // A field may already be focused while the engine was loading.
            val t = target
            if (t != null && isInputViewShown) controller?.start(focused, t, settings)
        }
        scope.launch {
            g.settings.flow.drop(1).collect { s ->
                controller?.updateSettings(s)
                applyLook()
            }
        }
        clipboard?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        clipboard?.removePrimaryClipChangedListener(clipListener)
        voice?.cancel()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        g.saveNow()
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        val kb = KeyboardView(this).also { it.listener = this }
        val st = StripView(this, this)
        val r = KeyboardRoot(
            this, st, kb,
            onSwapSide = { g.settings.update { it.copy(oneHanded = if (it.oneHanded == OneHanded.RIGHT) OneHanded.LEFT else OneHanded.RIGHT) } },
            onExitOneHanded = { g.settings.update { it.copy(oneHanded = OneHanded.OFF) } },
        )
        keyboard = kb
        strip = st
        root = r
        applyLook()
        return r
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        editor = info
        focused = FieldClassifier.classify(info.inputType, info.imeOptions, info.packageName ?: "", info.hintText?.toString() ?: "")
        mode = when (focused.layout) {
            KeyLayoutKind.NUMBER_PAD -> Mode.NUMBER
            KeyLayoutKind.PHONE_PAD -> Mode.PHONE
            KeyLayoutKind.LETTERS -> Mode.LETTERS
        }
        closePanels()
        strip?.setInlineSuggestions(emptyList())
        val t = InputConnectionTarget({ currentInputConnection }, keySender, info, focused)
        target = t
        controller?.start(focused, t, settings)
        applyLook()
        readClipboard(onlyFresh = true)
        renderStrip()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        closePanels()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        controller?.finish()
        target = null
        g.saveNow()
        super.onFinishInput()
    }

    override fun onWindowHidden() {
        g.saveNow()
        super.onWindowHidden()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        val t = target ?: return
        if (t.pendingUpdates > 0 && newSelStart == newSelEnd) {
            t.pendingUpdates--
        } else {
            t.pendingUpdates = 0
            controller?.onCursorMoved()
        }
    }

    // ---- look ----

    private fun applyLook() {
        val r = root ?: return
        val kb = keyboard ?: return
        val s = settings
        val c = KeyboardTheme.resolve(this, s)
        colors = c
        r.applyColors(c)
        kb.keyHeightDp = if (s.oneHanded != OneHanded.OFF) maxOf(50, s.keyHeightDp) else s.keyHeightDp
        kb.keyBorders = s.keyBorders
        kb.spaceLabel = if (s.swedishWords) "EN · SV" else "EN"
        kb.enterAction = focused.enterAction
        kb.layout = layoutFor(mode, s)
        kb.shift = controller?.shift ?: kb.shift
        r.setOneHanded(s.oneHanded)
        renderStrip()
    }

    private fun layoutFor(m: Mode, s: KeyboardSettings): KeyboardLayout = when (m) {
        Mode.LETTERS -> Layouts.letters(s.numberRow, s.swedishRow)
        Mode.SYMBOLS -> Layouts.symbols
        Mode.SYMBOLS_MORE -> Layouts.symbolsMore
        Mode.NUMBER -> Layouts.numberPad
        Mode.PHONE -> Layouts.phonePad
    }

    private fun setMode(m: Mode) {
        mode = m
        keyboard?.layout = layoutFor(m, settings)
    }

    private fun onControllerChanged() {
        keyboard?.shift = controller?.shift ?: return
        renderStrip()
    }

    private fun renderStrip() {
        val st = strip ?: return
        val state = controller?.strip() ?: StripState(incognito = focused.incognito, writingTools = false)
        val voiceOk = settings.voiceTyping && focused.writingTools && voiceAvailable()
        st.render(state, voiceOk, toolsPanel != null)
        if (state.code != null) {
            handler.removeCallbacks(ticker)
            handler.postDelayed(ticker, 1_000)
        }
    }

    private var voiceAvailableCache: Boolean? = null
    private fun voiceAvailable(): Boolean = voiceAvailableCache ?: VoiceInput(this, NoVoice).available().also { voiceAvailableCache = it }

    // ---- keys ----

    override fun onKeyDown(key: Key) {
        val s = settings
        if (s.haptics) keyboard?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (s.sound) {
            val fx = when (key.type) {
                KeyType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                KeyType.BACKSPACE -> AudioManager.FX_KEYPRESS_DELETE
                KeyType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            getSystemService(AudioManager::class.java)?.playSoundEffect(fx, -1f)
        }
    }

    override fun onKey(key: Key) {
        val c = controller
        when (key.type) {
            KeyType.CHAR -> if (c != null) c.onText(key.text) else basicCommit(key.text)
            KeyType.SPACE -> if (c != null) c.onSpace() else basicCommit(" ")
            KeyType.ENTER -> c?.onEnter() ?: sendDefaultEditorAction(true)
            KeyType.SHIFT -> c?.onShift()
            KeyType.SYMBOLS -> setMode(Mode.SYMBOLS)
            KeyType.SYMBOLS_MORE -> setMode(Mode.SYMBOLS_MORE)
            KeyType.LETTERS -> setMode(Mode.LETTERS)
            KeyType.EMOJI -> showEmoji()
            KeyType.BACKSPACE, KeyType.SPACER -> Unit
        }
    }

    override fun onPopupText(text: String) {
        controller?.onText(text) ?: basicCommit(text)
    }

    override fun onPopupAction(action: String) {
        when (action) {
            PopupAction.EMOJI -> showEmoji()
            PopupAction.SETTINGS -> openSettings()
            PopupAction.ONE_HANDED -> g.settings.update { it.copy(oneHanded = if (it.oneHanded == OneHanded.OFF) OneHanded.RIGHT else OneHanded.OFF) }
        }
    }

    override fun onBackspace(step: Int) {
        val c = controller
        when {
            c == null -> sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL)
            step >= WORD_DELETE_AFTER -> c.onDeleteWord()
            else -> c.onBackspace()
        }
    }

    override fun onSpaceLongPress() {
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    /** Used only in the moment before the word lists finish loading. */
    private fun basicCommit(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    // ---- strip ----

    override fun onSuggestion(s: Suggestion) { controller?.pick(s) }
    override fun onUndoCorrection() { controller?.undoCorrection() }
    override fun onMemory(item: MemoryItem) { controller?.insertMemory(item); g.scheduleSave() }

    override fun onCode() {
        if (controller?.insertCode() == true && settings.clearClipboardAfterCode) {
            runCatching { clipboard?.clearPrimaryClip() }
        }
    }

    override fun onWritingTools() {
        if (toolsPanel != null) { closePanels(); return }
        val e = engine ?: return
        val r = root ?: return
        val panel = WritingToolsPanel(this, ToolsHost(e))
        toolsPanel = panel
        r.showPanel(panel)
        renderStrip()
    }

    private inner class ToolsHost(private val e: TystEngine) : WritingToolsPanel.Host {
        override val colors: KeyboardColors get() = this@TystImeService.colors ?: KeyboardTheme.resolve(this@TystImeService, settings)
        override val appPackage: String get() = focused.packageName
        override val swedishWords: Boolean get() = settings.swedishWords
        override fun sourceText(): String = controller?.textForTools().orEmpty()
        override fun replace(text: String) { controller?.replaceText(text) }
        override fun copy(text: String) {
            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Tyst", text))
        }
        override fun rewrite(text: String, tone: Tone): Result<RewriteResult> = e.rewriter.rewrite(text, tone, focused.packageName)
        override fun proofread(text: String): List<Fix> = e.proofreader.check(text, settings.swedishWords)
        override fun applyFixes(text: String, fixes: List<Fix>): String = e.proofreader.apply(text, fixes)
        override fun close() = closePanels()
    }

    override fun onVoice() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, MicPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val c = colors ?: return
        val r = root ?: return
        val restorer = engine?.let { SwedishRestorer(it.lexicon) }
        val panel = VoicePanel(this, c) { voice?.stop() }
        voicePanel = panel
        r.showPanel(panel)
        val v = VoiceInput(this, object : VoiceInput.Listener {
            override fun onPartial(text: String) = panel.setTranscript(text)
            override fun onLevel(rms: Float) = panel.setLevel(rms)
            override fun onFinal(text: String) {
                val fixed = if (settings.swedishWords && restorer != null) restorer.restore(text) else text
                if (fixed.isNotBlank()) {
                    val before = target?.before(1).orEmpty()
                    val lead = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
                    controller?.insertText(lead + fixed)
                }
                closePanels()
            }
            override fun onError(message: String, permissionMissing: Boolean) {
                panel.setStatus(message)
                if (permissionMissing) startActivity(Intent(this@TystImeService, MicPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                handler.postDelayed({ if (voicePanel === panel) closePanels() }, 2_500)
            }
        })
        voice = v
        v.start()
    }

    private fun showEmoji() {
        val c = colors ?: return
        val r = root ?: return
        val recent = engine?.let { Emoji.recent(it.personal.emojiUsage) }.orEmpty()
        r.showPanel(EmojiPanel(this, c, recent,
            onEmoji = { controller?.onEmoji(it) ?: basicCommit(it) },
            onBackspace = { onBackspace(0) },
            onClose = { closePanels() },
        ))
    }

    private fun closePanels() {
        voice?.cancel()
        voice = null
        voicePanel = null
        toolsPanel = null
        root?.showKeys()
        renderStrip()
    }

    private fun openSettings() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---- clipboard & autofill ----

    private fun readClipboard(onlyFresh: Boolean) {
        val c = controller ?: return
        val cm = clipboard ?: return
        val clip = try { cm.primaryClip } catch (_: SecurityException) { null } ?: return
        val text = clip.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString() ?: return
        val copiedAt = clip.description?.timestamp?.takeIf { it > 0 } ?: System.currentTimeMillis()
        if (onlyFresh && System.currentTimeMillis() - copiedAt > settings.codeExpirySeconds * 1000L) return
        c.onClipboard(text, copiedAt)
    }

    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        if (!settings.showPasswordSuggestions) return null
        return try { InlineAutofill.request(this) } catch (_: RuntimeException) { null }
    }

    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        if (!settings.showPasswordSuggestions) return false
        InlineAutofill.inflate(this, response) { views -> strip?.setInlineSuggestions(views) }
        return true
    }

    private object NoVoice : VoiceInput.Listener {
        override fun onPartial(text: String) {}
        override fun onFinal(text: String) {}
        override fun onLevel(rms: Float) {}
        override fun onError(message: String, permissionMissing: Boolean) {}
    }

    companion object {
        /** Held backspace switches from letters to whole words after this many repeats. */
        const val WORD_DELETE_AFTER = 15
    }
}
