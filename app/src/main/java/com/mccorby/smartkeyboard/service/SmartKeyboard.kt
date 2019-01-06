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
import android.view.inputmethod.*
import com.mccorby.smartkeyboard.R
import com.mccorby.smartkeyboard.ui.CandidateView


class SmartKeyboard : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    val DEBUG = false

    /**
     * This boolean indicates the optional example code for performing
     * processing of hard keys in addition to regular text generation
     * from on-screen interaction.  It would be used for input methods that
     * perform language translations (such as converting text entered on
     * a QWERTY keyboard to Chinese), but may not be used for input methods
     * that are primarily intended to be used for on-screen text entry.
     */
    val PROCESS_HARD_KEYS = true
    private var mInputMethodManager: InputMethodManager? = null
    private var mInputView: LatinKeyboardView? = null
    private var mCandidateView: CandidateView? = null
    private var mCompletions: Array<CompletionInfo>? = null

    private val mComposing = StringBuilder()
    private var mPredictionOn: Boolean = false
    private var mCompletionOn: Boolean = false
    private var mLastDisplayWidth: Int = 0
    private var mCapsLock: Boolean = false
    private var mLastShiftTime: Long = 0
    private var mMetaState: Long = 0

    private var mSymbolsKeyboard: LatinKeyboard? = null
    private var mSymbolsShiftedKeyboard: LatinKeyboard? = null
    private var mQwertyKeyboard: LatinKeyboard? = null

    private var mCurKeyboard: LatinKeyboard? = null

    private var mWordSeparators: String? = null

    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    override fun onCreate() {
        super.onCreate()
        mInputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        mWordSeparators = resources.getString(R.string.word_separators)
    }

    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    override fun onInitializeInterface() {
        if (mQwertyKeyboard != null) {
            // Configuration changes can happen after the keyboard gets recreated,
            // so we need to be able to re-build the keyboards if the available
            // space has changed.
            val displayWidth = maxWidth
            if (displayWidth == mLastDisplayWidth) return
            mLastDisplayWidth = displayWidth
        }
        mQwertyKeyboard = LatinKeyboard(this, R.xml.qwerty)
        mSymbolsKeyboard = LatinKeyboard(this, R.xml.symbols)
        mSymbolsShiftedKeyboard = LatinKeyboard(this, R.xml.symbols_shift)
    }

    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    override fun onCreateInputView(): View? {
        mInputView = layoutInflater.inflate(
            R.layout.input, null
        ) as LatinKeyboardView
        mInputView!!.setOnKeyboardActionListener(this)
        setLatinKeyboard(mQwertyKeyboard!!)
        return mInputView
    }

    private fun setLatinKeyboard(nextKeyboard: LatinKeyboard) {
        val shouldSupportLanguageSwitchKey = mInputMethodManager!!.shouldOfferSwitchingToNextInputMethod(getToken())
        nextKeyboard.setLanguageSwitchKeyVisibility(shouldSupportLanguageSwitchKey)
        mInputView!!.keyboard = nextKeyboard
    }

    /**
     * Called by the framework when your view for showing candidates needs to
     * be generated, like [.onCreateInputView].
     */
    override fun onCreateCandidatesView(): View? {
        mCandidateView = CandidateView(this).also {
            it.setService(this)
        }
        return mCandidateView
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
        mComposing.setLength(0)
        updateCandidates()

        if (!restarting) {
            // Clear shift states.
            mMetaState = 0
        }

        mPredictionOn = false
        mCompletionOn = false
        mCompletions = null

        // We are now going to initialize our state based on the type of
        // text being edited.
        when (attribute.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME ->
                // Numbers and dates default to the symbols keyboard, with
                // no extra features.
                mCurKeyboard = mSymbolsKeyboard

            InputType.TYPE_CLASS_PHONE ->
                // Phones will also default to the symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
                mCurKeyboard = mSymbolsKeyboard

            InputType.TYPE_CLASS_TEXT -> {
                // This is general text editing.  We will default to the
                // normal alphabetic keyboard, and assume that we should
                // be doing predictive text (showing candidates as the
                // user types).
                mCurKeyboard = mQwertyKeyboard
                mPredictionOn = true

                // We now look for a few special variations of text that will
                // modify our behavior.
                val variation = attribute.inputType and InputType.TYPE_MASK_VARIATION
                if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    // Do not display predictions / what the user is typing
                    // when they are entering a password.
                    mPredictionOn = false
                }

                if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || variation == InputType.TYPE_TEXT_VARIATION_URI
                    || variation == InputType.TYPE_TEXT_VARIATION_FILTER
                ) {
                    // Our predictions are not useful for e-mail addresses
                    // or URIs.
                    mPredictionOn = false
                }

                if (attribute.inputType and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE != 0) {
                    // If this is an auto-complete text view, then our predictions
                    // will not be shown and instead we will allow the editor
                    // to supply their own.  We only show the editor's
                    // candidates when in fullscreen mode, otherwise relying
                    // own it displaying its own UI.
                    mPredictionOn = false
                    mCompletionOn = isFullscreenMode
                }

                // We also want to look at the current state of the editor
                // to decide whether our alphabetic keyboard should start out
                // shifted.
                updateShiftKeyState(attribute)
            }

            else -> {
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                mCurKeyboard = mQwertyKeyboard
                updateShiftKeyState(attribute)
            }
        }

        // Update the label on the enter key, depending on what the application
        // says it will do.
        mCurKeyboard!!.setImeOptions(resources, attribute.imeOptions)
    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    override fun onFinishInput() {
        super.onFinishInput()

        // Clear current composing text and candidates.
        mComposing.setLength(0)
        updateCandidates()

        // We only hide the candidates window when finishing input on
        // a particular editor, to avoid popping the underlying application
        // up and down if the user is entering text into the bottom of
        // its window.
        setCandidatesViewShown(false)

        mCurKeyboard = mQwertyKeyboard
        if (mInputView != null) {
            mInputView!!.closing()
        }
    }

    override fun onStartInputView(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)
        // Apply the selected keyboard to the input view.
        setLatinKeyboard(mCurKeyboard!!)
        mInputView!!.closing()
        val subtype = mInputMethodManager!!.getCurrentInputMethodSubtype()
        mInputView!!.setSubtypeOnSpaceKey(subtype)
    }

    public override fun onCurrentInputMethodSubtypeChanged(subtype: InputMethodSubtype) {
        mInputView!!.setSubtypeOnSpaceKey(subtype)
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
        if (mComposing.length > 0 && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            mComposing.setLength(0)
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
        if (mCompletionOn) {
            mCompletions = completions
            if (completions == null) {
                setSuggestions(null, false, false)
                return
            }

            val stringList = ArrayList<String>()
            for (i in completions.indices) {
                val ci = completions[i]
                if (ci != null) stringList.add(ci.text.toString())
            }
            setSuggestions(stringList, true, true)
        }
    }

    /**
     * This translates incoming hard key events in to edit operations on an
     * InputConnection.  It is only needed when using the
     * PROCESS_HARD_KEYS option.
     */
    private fun translateKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        mMetaState = MetaKeyKeyListener.handleKeyDown(
            mMetaState,
            keyCode, event
        )
        var c = event.getUnicodeChar(MetaKeyKeyListener.getMetaState(mMetaState))
        mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState)
        val ic = currentInputConnection
        if (c == 0 || ic == null) {
            return false
        }

        var dead = false
        if (c and KeyCharacterMap.COMBINING_ACCENT != 0) {
            dead = true
            c = c and KeyCharacterMap.COMBINING_ACCENT_MASK
        }

        if (mComposing.length > 0) {
            val accent = mComposing[mComposing.length - 1]
            val composed = KeyEvent.getDeadChar(accent.toInt(), c)
            if (composed != 0) {
                c = composed
                mComposing.setLength(mComposing.length - 1)
            }
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
                if (event.repeatCount == 0 && mInputView != null) {
                    if (mInputView!!.handleBack()) {
                        return true
                    }
                }

            KeyEvent.KEYCODE_DEL ->
                // Special handling of the delete key: if we currently are
                // composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                if (mComposing.length > 0) {
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
                    if (mPredictionOn && translateKeyDown(keyCode, event)) {
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
        if (PROCESS_HARD_KEYS) {
            if (mPredictionOn) {
                mMetaState = MetaKeyKeyListener.handleKeyUp(
                    mMetaState,
                    keyCode, event
                )
            }
        }

        return super.onKeyUp(keyCode, event)
    }

    /**
     * Helper function to commit any text being composed in to the editor.
     */
    private fun commitTyped(inputConnection: InputConnection) {
        if (mComposing.length > 0) {
            inputConnection.commitText(mComposing, mComposing.length)
            mComposing.setLength(0)
            updateCandidates()
        }
    }

    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    private fun updateShiftKeyState(attr: EditorInfo?) {
        if (attr != null
            && mInputView != null && mQwertyKeyboard == mInputView!!.keyboard
        ) {
            var caps = 0
            val ei = currentInputEditorInfo
            if (ei != null && ei.inputType != InputType.TYPE_NULL) {
                caps = currentInputConnection.getCursorCapsMode(attr.inputType)
            }
            mInputView!!.isShifted = mCapsLock || caps != 0
        }
    }

    /**
     * Helper to determine if a given character code is alphabetic.
     */
    private fun isAlphabet(code: Int): Boolean {
        return if (Character.isLetter(code)) {
            true
        } else {
            false
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

    /**
     * Helper to send a character to the editor as raw key events.
     */
    private fun sendKey(keyCode: Int) {
        when (keyCode) {
            '\n'.toInt() -> keyDownUp(KeyEvent.KEYCODE_ENTER)
            else -> if (keyCode >= '0'.toInt() && keyCode <= '9'.toInt()) {
                keyDownUp(keyCode - '0'.toInt() + KeyEvent.KEYCODE_0)
            } else {
                currentInputConnection.commitText(keyCode.toChar().toString(), 1)
            }
        }
    }

    // Implementation of KeyboardViewListener
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        if (isWordSeparator(primaryCode)) {
            // Handle separator
            if (mComposing.length > 0) {
                commitTyped(currentInputConnection)
            }
            sendKey(primaryCode)
            updateShiftKeyState(currentInputEditorInfo)
        } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
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
        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE && mInputView != null) {
            val current = mInputView!!.keyboard
            if (current === mSymbolsKeyboard || current === mSymbolsShiftedKeyboard) {
                setLatinKeyboard(mQwertyKeyboard!!)
            } else {
                setLatinKeyboard(mSymbolsKeyboard!!)
                mSymbolsKeyboard!!.isShifted = false
            }
        } else {
            handleCharacter(primaryCode, keyCodes)
        }
    }

    override fun onText(text: CharSequence) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (mComposing.length > 0) {
            commitTyped(ic)
        }
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
        if (!mCompletionOn) {
            if (mComposing.length > 0) {
                val list = ArrayList<String>()
                list.add(mComposing.toString())
                setSuggestions(list, true, true)
            } else {
                setSuggestions(null, false, false)
            }
        }
    }

    fun setSuggestions(
        suggestions: List<String>?, completions: Boolean,
        typedWordValid: Boolean
    ) {
        if (suggestions != null && suggestions.size > 0) {
            setCandidatesViewShown(true)
        } else if (isExtractViewShown) {
            setCandidatesViewShown(true)
        }
        if (mCandidateView != null) {
            mCandidateView!!.setSuggestions(suggestions, completions, typedWordValid)
        }
    }

    private fun handleBackspace() {
        val length = mComposing.length
        if (length > 1) {
            mComposing.delete(length - 1, length)
            currentInputConnection.setComposingText(mComposing, 1)
            updateCandidates()
        } else if (length > 0) {
            mComposing.setLength(0)
            currentInputConnection.commitText("", 0)
            updateCandidates()
        } else {
            keyDownUp(KeyEvent.KEYCODE_DEL)
        }
        updateShiftKeyState(currentInputEditorInfo)
    }

    private fun handleShift() {
        if (mInputView == null) {
            return
        }

        val currentKeyboard = mInputView!!.keyboard
        if (mQwertyKeyboard == currentKeyboard) {
            // Alphabet keyboard
            checkToggleCapsLock()
            mInputView!!.isShifted = mCapsLock || !mInputView!!.isShifted
        } else if (currentKeyboard === mSymbolsKeyboard) {
            mSymbolsKeyboard!!.isShifted = true
            setLatinKeyboard(mSymbolsShiftedKeyboard!!)
            mSymbolsShiftedKeyboard!!.isShifted = true
        } else if (currentKeyboard === mSymbolsShiftedKeyboard) {
            mSymbolsShiftedKeyboard!!.isShifted = false
            setLatinKeyboard(mSymbolsKeyboard!!)
            mSymbolsKeyboard!!.isShifted = false
        }
    }

    private fun handleCharacter(primaryCode: Int, keyCodes: IntArray?) {
        var primaryCode = primaryCode
        if (isInputViewShown) {
            if (mInputView!!.isShifted) {
                primaryCode = Character.toUpperCase(primaryCode)
            }
        }
        if (isAlphabet(primaryCode) && mPredictionOn) {
            mComposing.append(primaryCode.toChar())
            currentInputConnection.setComposingText(mComposing, 1)
            updateShiftKeyState(currentInputEditorInfo)
            updateCandidates()
        } else {
            currentInputConnection.commitText(
                primaryCode.toChar().toString(), 1
            )
        }
    }

    private fun handleClose() {
        commitTyped(currentInputConnection)
        requestHideSelf(0)
        mInputView!!.closing()
    }

    private fun getToken(): IBinder? {
        val dialog = window ?: return null
        val window = dialog.window ?: return null
        return window.attributes.token
    }

    private fun handleLanguageSwitch() {
        mInputMethodManager!!.switchToNextInputMethod(getToken(), false /* onlyCurrentIme */)
    }

    private fun checkToggleCapsLock() {
        val now = System.currentTimeMillis()
        if (mLastShiftTime + 800 > now) {
            mCapsLock = !mCapsLock
            mLastShiftTime = 0
        } else {
            mLastShiftTime = now
        }
    }

    private fun getWordSeparators(): String? {
        return mWordSeparators
    }

    fun isWordSeparator(code: Int): Boolean {
        val separators = getWordSeparators()
        return separators!!.contains(code.toChar().toString())
    }

    fun pickDefaultCandidate() {
        pickSuggestionManually(0)
    }

    fun pickSuggestionManually(index: Int) {
        if (mCompletionOn && mCompletions != null && index >= 0
            && index < mCompletions!!.size
        ) {
            val ci = mCompletions!![index]
            currentInputConnection.commitCompletion(ci)
            if (mCandidateView != null) {
                mCandidateView!!.clear()
            }
            updateShiftKeyState(currentInputEditorInfo)
        } else if (mComposing.length > 0) {
            // If we were generating candidate suggestions for the current
            // text, we would commit one of them here.  But for this sample,
            // we will just commit the current text.
            commitTyped(currentInputConnection)
        }
    }

    override fun swipeRight() {
        if (mCompletionOn) {
            pickDefaultCandidate()
        }
    }

    override fun swipeLeft() {
        handleBackspace()
    }

    override fun swipeDown() {
        handleClose()
    }

    override fun swipeUp() {}

    override fun onPress(primaryCode: Int) {}

    override fun onRelease(primaryCode: Int) {}
}