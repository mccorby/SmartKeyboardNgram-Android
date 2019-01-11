package com.mccorby.smartkeyboard.service

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.IBinder
import android.text.InputType
import android.text.method.MetaKeyKeyListener
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import com.mccorby.machinelearning.nlp.LanguageModel
import com.mccorby.machinelearning.nlp.NGrams
import com.mccorby.smartkeyboard.R
import org.koin.android.ext.android.inject
import kotlin.math.max


private const val N_PREDICTIONS = 3
// TODO This should come from a config file representing the model
private const val MODEL_ORDER = 5

/**
 * This class is based on SoftKeyboard from AOSP https://android.googlesource.com/platform/development/+/master/samples/SoftKeyboard/src/com/example/android/softkeyboard/SoftKeyboard.java
 * It makes use of the NGram class and a Language model to generate suggestions as the user writes in the corresponding text view
 */
class SmartKeyboard : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private val PROCESS_HARD_KEYS = true
    private var inputMethodManager: InputMethodManager? = null
    private var inputView: LatinKeyboardView? = null
    private var candidateView: CandidateView? = null
    private var completions: Array<CompletionInfo>? = null

    private val composing = StringBuilder()
    private var predictionOn: Boolean = false
    private var completionOn: Boolean = false
    private var lastDisplayWidth: Int = 0
    private var capsLock: Boolean = false
    private var lastShiftTime: Long = 0
    private var metaState: Long = 0

    private var qwertyKeyboard: LatinKeyboard? = null

    private lateinit var wordSeparators: Set<Char>

    val ngrams: NGrams by inject()
    val languageModel: LanguageModel by inject()

    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    override fun onCreate() {
        super.onCreate()
        inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        wordSeparators = resources.getString(R.string.word_separators).toSet()
    }

    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    override fun onInitializeInterface() {
        if (this.qwertyKeyboard != null) {
            // Configuration changes can happen after the keyboard gets recreated,
            // so we need to be able to re-build the keyboards if the available
            // space has changed.
            val displayWidth = maxWidth
            if (displayWidth == lastDisplayWidth) return
            lastDisplayWidth = displayWidth
        }
        this.qwertyKeyboard = LatinKeyboard(this, R.xml.qwerty)
    }

    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    override fun onCreateInputView(): View? {
        inputView = layoutInflater.inflate(
            R.layout.input, null
        ) as LatinKeyboardView
        inputView!!.setOnKeyboardActionListener(this)
        setLatinKeyboard(this.qwertyKeyboard!!)
        return inputView
    }

    private fun setLatinKeyboard(nextKeyboard: LatinKeyboard) {
        val shouldSupportLanguageSwitchKey = inputMethodManager!!.shouldOfferSwitchingToNextInputMethod(getToken())
        nextKeyboard.setLanguageSwitchKeyVisibility(shouldSupportLanguageSwitchKey)
        inputView!!.keyboard = nextKeyboard
    }

    /**
     * Called by the framework when your view for showing candidates needs to
     * be generated, like [.onCreateInputView].
     */
    override fun onCreateCandidatesView(): View? {
        candidateView = CandidateView(this).also {
            it.setService(this)
        }
        return candidateView
    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)

        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
        updateCandidates()

        if (!restarting) {
            // Clear shift states.
            metaState = 0
        }

        predictionOn = false
        completionOn = false
        completions = null

        // We are now going to initialize our state based on the type of
        // text being edited.
        when (attribute.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> {
                // This is general text editing.  We will default to the
                // normal alphabetic keyboard, and assume that we should
                // be doing predictive text (showing candidates as the
                // user types).

                predictionOn = true

                // We now look for a few special variations of text that will
                // modify our behavior.
                val variation = attribute.inputType and InputType.TYPE_MASK_VARIATION
                if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    // Do not display predictions / what the user is typing
                    // when they are entering a password.
                    predictionOn = false
                }

                if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || variation == InputType.TYPE_TEXT_VARIATION_URI
                    || variation == InputType.TYPE_TEXT_VARIATION_FILTER
                ) {
                    // Our predictions are not useful for e-mail addresses
                    // or URIs.
                    predictionOn = false
                }

                if (attribute.inputType and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE != 0) {
                    // If this is an auto-complete text view, then our predictions
                    // will not be shown and instead we will allow the editor
                    // to supply their own.  We only show the editor's
                    // candidates when in fullscreen mode, otherwise relying
                    // own it displaying its own UI.
                    predictionOn = false
                    completionOn = isFullscreenMode
                }

                // We also want to look at the current state of the editor
                // to decide whether our alphabetic keyboard should start out
                // shifted.
                updateShiftKeyState(attribute)
            }

            else -> {
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.

                updateShiftKeyState(attribute)
            }
        }

        // Update the label on the enter key, depending on what the application
        // says it will do.
        this.qwertyKeyboard!!.setImeOptions(resources, attribute.imeOptions)
    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    override fun onFinishInput() {
        super.onFinishInput()

        // Clear current composing text and candidates.
        composing.setLength(0)
        updateCandidates()

        // We only hide the candidates window when finishing input on
        // a particular editor, to avoid popping the underlying application
        // up and down if the user is entering text into the bottom of
        // its window.
        setCandidatesViewShown(false)


        if (inputView != null) {
            inputView!!.closing()
        }
    }

    override fun onStartInputView(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)
        // Apply the selected keyboard to the input view.
        setLatinKeyboard(this.qwertyKeyboard!!)
        inputView!!.closing()
        val subtype = inputMethodManager!!.currentInputMethodSubtype
        inputView!!.setSubtypeOnSpaceKey(subtype)
    }

    public override fun onCurrentInputMethodSubtypeChanged(subtype: InputMethodSubtype) {
        inputView!!.setSubtypeOnSpaceKey(subtype)
    }

    /**
     * Deal with the editor reporting movement of its cursor.
     */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd,
            candidatesStart, candidatesEnd
        )

        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if (composing.isNotEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            updateCandidates()
            val ic = currentInputConnection
            ic?.finishComposingText()
        }
    }

    /**
     * This tells us about completions that the editor has determined based
     * on the current text in it.  We want to use this in fullscreen mode
     * to show the completions ourself, since the editor can not be seen
     * in that situation.
     */
    override fun onDisplayCompletions(completions: Array<CompletionInfo>?) {
        if (completionOn) {
            this.completions = completions
            if (completions == null) {
                setSuggestions(emptyList())
                return
            }

            val stringList = ArrayList<String>()
            for (i in completions.indices) {
                val ci = completions[i]
                stringList.add(ci.text.toString())
            }
            setSuggestions(stringList)
        }
    }

    /**
     * This translates incoming hard key events in to edit operations on an
     * InputConnection.  It is only needed when using the
     * PROCESS_HARD_KEYS option.
     */
    private fun translateKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        metaState = MetaKeyKeyListener.handleKeyDown(
            metaState,
            keyCode, event
        )
        var c = event.getUnicodeChar(MetaKeyKeyListener.getMetaState(metaState))
        metaState = MetaKeyKeyListener.adjustMetaAfterKeypress(metaState)
        val ic = currentInputConnection
        if (c == 0 || ic == null) {
            return false
        }

        if (c and KeyCharacterMap.COMBINING_ACCENT != 0) {
            c = c and KeyCharacterMap.COMBINING_ACCENT_MASK
        }

        onKey(c, null)

        return true
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK ->
                // The InputMethodService already takes care of the back
                // key for us, to dismiss the input method if it is shown.
                // However, our keyboard could be showing a pop-up window
                // that back should dismiss, so we first allow it to do that.
                if (event.repeatCount == 0 && inputView != null) {
                    if (inputView!!.handleBack()) {
                        return true
                    }
                }

            KeyEvent.KEYCODE_DEL ->
                // Special handling of the delete key: if we currently are
                // composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                if (composing.length > 0) {
                    onKey(Keyboard.KEYCODE_DELETE, null)
                    return true
                }

            KeyEvent.KEYCODE_ENTER ->
                // Let the underlying text editor always handle these.
                return false

            else ->
                // For all other keys, if we want to do transformations on
                // text being entered with a hard keyboard, we need to process
                // it and do the appropriate action.
                if (PROCESS_HARD_KEYS) {
                    if (keyCode == KeyEvent.KEYCODE_SPACE && event.metaState and KeyEvent.META_ALT_ON != 0) {
                        // A silly example: in our input method, Alt+Space
                        // is a shortcut for 'android' in lower case.
                        val ic = currentInputConnection
                        if (ic != null) {
                            // First, tell the editor that it is no longer in the
                            // shift state, since we are consuming this.
                            ic.clearMetaKeyStates(KeyEvent.META_ALT_ON)
                            keyDownUp(KeyEvent.KEYCODE_A)
                            keyDownUp(KeyEvent.KEYCODE_N)
                            keyDownUp(KeyEvent.KEYCODE_D)
                            keyDownUp(KeyEvent.KEYCODE_R)
                            keyDownUp(KeyEvent.KEYCODE_O)
                            keyDownUp(KeyEvent.KEYCODE_I)
                            keyDownUp(KeyEvent.KEYCODE_D)
                            // And we consume this event.
                            return true
                        }
                    }
                    if (predictionOn && translateKeyDown(keyCode, event)) {
                        return true
                    }
                }
        }

        return super.onKeyDown(keyCode, event)
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // If we want to do transformations on text being entered with a hard
        // keyboard, we need to process the up events to update the meta key
        // state we are tracking.
        if (PROCESS_HARD_KEYS && predictionOn) {
            metaState = MetaKeyKeyListener.handleKeyUp(metaState, keyCode, event)
        }

        return super.onKeyUp(keyCode, event)
    }

    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    private fun updateShiftKeyState(attr: EditorInfo?) {
        if (attr != null && inputView != null && this.qwertyKeyboard == inputView!!.keyboard
        ) {
            var caps = 0
            val ei = currentInputEditorInfo
            if (ei != null && ei.inputType != InputType.TYPE_NULL) {
                caps = currentInputConnection.getCursorCapsMode(attr.inputType)
            }
            inputView!!.isShifted = capsLock || caps != 0
        }
    }

    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private fun keyDownUp(keyEventCode: Int) {
        currentInputConnection.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode)
        )
        currentInputConnection.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_UP, keyEventCode)
        )
    }

    // Implementation of KeyboardViewListener
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace()
        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift()
        } else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
            handleClose()
            return
        } else if (primaryCode == LatinKeyboardView.KEYCODE_LANGUAGE_SWITCH) {
            handleLanguageSwitch()
            return
        } else if (primaryCode == LatinKeyboardView.KEYCODE_OPTIONS) {
            // Show a menu or somethin'
        } else {
            handleCharacter(primaryCode, keyCodes)
        }
    }

    override fun onText(text: CharSequence) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()

        ic.commitText(text, 0)
        ic.endBatchEdit()
        updateShiftKeyState(currentInputEditorInfo)
    }

    /**
     * Update the list of available candidates from the current composing
     * text.  This will need to be filled in by however you are determining
     * candidates.
     */
    private fun updateCandidates() {
        if (!completionOn) {
            if (composing.isNotEmpty()) {
                val list = getPredictions(composing.toString()).toList()
                setSuggestions(list)
            } else {
                setSuggestions(emptyList())
            }
        }
    }

    private fun setSuggestions(suggestions: List<String>) {
        if (suggestions.isNotEmpty()) {
            setCandidatesViewShown(true)
        } else if (isExtractViewShown) {
            setCandidatesViewShown(true)
        }
        if (candidateView != null) {
            candidateView!!.setSuggestions(suggestions)
        }
    }

    private fun handleBackspace() {
        val length = composing.length
        when {
            length > 1 -> {
                composing.delete(length - 1, length)
                currentInputConnection.setComposingText(composing, 1)
                updateCandidates()
            }
            length > 0 -> {
                composing.clear()
                currentInputConnection.commitText("", 0)
                updateCandidates()
            }
            else -> keyDownUp(KeyEvent.KEYCODE_DEL)
        }
        updateShiftKeyState(currentInputEditorInfo)
    }

    private fun handleShift() {
        inputView?.let {
            checkToggleCapsLock()
            inputView!!.isShifted = capsLock || !inputView!!.isShifted
        }
    }

    private fun handleCharacter(inputPrimaryCode: Int, keyCodes: IntArray?) {
        var primaryCode = inputPrimaryCode
        if (isInputViewShown) {
            if (inputView!!.isShifted) {
                primaryCode = Character.toUpperCase(primaryCode)
            }
        }
        if (predictionOn) {
            composing.append(primaryCode.toChar())
            currentInputConnection.setComposingText(composing, 1)
            updateShiftKeyState(currentInputEditorInfo)
            updateCandidates()
        }
    }

    private fun handleClose() {
        requestHideSelf(0)
        inputView!!.closing()
    }

    private fun getToken(): IBinder? {
        val dialog = window ?: return null
        val window = dialog.window ?: return null
        return window.attributes.token
    }

    private fun handleLanguageSwitch() {
        inputMethodManager!!.switchToNextInputMethod(getToken(), false /* onlyCurrentIme */)
    }

    private fun checkToggleCapsLock() {
        val now = System.currentTimeMillis()
        if (lastShiftTime + 800 > now) {
            capsLock = !capsLock
            lastShiftTime = 0
        } else {
            lastShiftTime = now
        }
    }

    fun pickSuggestion(suggestion: String) {
        // Find the last space and replace from there with the suggestion
        val spacePos = composing.lastIndexOf(" ")
        if (spacePos > 0) {
            composing.delete(spacePos + 1, composing.length)
        } else {
            composing.setLength(0)
        }
        composing.append(suggestion)
        currentInputConnection.setComposingText(composing, 1)
    }

    override fun swipeRight() {}

    override fun swipeLeft() {}

    override fun swipeDown() {}

    override fun swipeUp() {}

    override fun onPress(primaryCode: Int) {}

    override fun onRelease(primaryCode: Int) {}


    // Select candidates and increase the corresponding history
    private fun getPredictions(seed: String): Sequence<String> {
        // Only interested in the first nPredictions best predictions
        val candidates = generateInitialCandidates(seed)
            .entries
            .sortedByDescending { it.value }
            .take(N_PREDICTIONS)

        // Build a word for each candidate
        return candidates.map { buildWord("$seed${it.key}") }.asSequence()
    }

    // Generate candidates based on the input (one for each word that will be shown to the user)
    private fun generateInitialCandidates(seed: String = ""): Map<Char, Float> {
        val initValue = NGrams.START_CHAR.repeat(max(MODEL_ORDER - seed.length, 0))
        val history = "$initValue$seed"

        return ngrams.generateCandidates(languageModel, MODEL_ORDER, history)
    }

    // TODO Should this be done in parallel for each seed?
    private fun buildWord(history: String): String {
        val buffer = StringBuffer(history)

        while (buffer.last() !in wordSeparators) {
            buffer.append(
                ngrams.generateNextChar(
                    languageModel,
                    MODEL_ORDER,
                    buffer.toString()
                )
            )
        }
        // history can be a set of words thus we must split it and take the last one
        // We could use here the list of word separators
        return buffer.trimEnd().split(" ").takeLast(1)[0]
    }
}
