package com.mccorby.smartkeyboard.service

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import com.mccorby.smartkeyboard.R
import android.text.method.TextKeyListener.clear
import android.view.inputmethod.CompletionInfo
import com.mccorby.smartkeyboard.ui.CandidateView


class SmartKeyboard : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard

    private var caps = false

    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
        keyboard = Keyboard(this, R.xml.keys_layout)
        keyboardView.keyboard = keyboard
        keyboardView.setOnKeyboardActionListener(this)
        return keyboardView
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray) {
        val inputConnection = currentInputConnection
        if (inputConnection != null) {
            when (primaryCode) {
                Keyboard.KEYCODE_DELETE -> {
                    val selectedText = inputConnection.getSelectedText(0)

                    if (TextUtils.isEmpty(selectedText)) {
                        inputConnection.deleteSurroundingText(1, 0)
                    } else {
                        inputConnection.commitText("", 1)
                    }
                    caps = !caps
                    keyboard.isShifted = caps
                    keyboardView.invalidateAllKeys()
                }
                Keyboard.KEYCODE_SHIFT -> {
                    caps = !caps
                    keyboard.isShifted = caps
                    keyboardView.invalidateAllKeys()
                }
                Keyboard.KEYCODE_DONE -> inputConnection.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_ENTER
                    )
                )
                else -> {
                    var code = primaryCode.toChar()
                    if (Character.isLetter(code) && caps) {
                        code = Character.toUpperCase(code)
                    }
                    inputConnection.commitText(code.toString(), 1)
                }
            }
        }
    }

    private lateinit var candidateView: CandidateView

    override fun onCreateCandidatesView(): View {
        candidateView = CandidateView(this).also {
            it.setService(this)
        }
        return candidateView
    }

    // TODO
    fun pickSuggestionManually(index: Int) {
//        if (mCompletionOn && mCompletions != null && index >= 0
//            && index < mCompletions.length
//        ) {
//            val ci = mCompletions[index]
//            currentInputConnection.commitCompletion(ci)
//            if (mCandidateView != null) {
//                mCandidateView.clear()
//            }
//            updateShiftKeyState(currentInputEditorInfo)
//        } else if (mComposing.length() > 0) {
//            // If we were generating candidate suggestions for the current
//            // text, we would commit one of them here.  But for this sample,
//            // we will just commit the current text.
//            commitTyped(currentInputConnection)
//        }
    }

    override fun swipeRight() {
    }

    override fun onPress(primaryCode: Int) {
    }

    override fun onRelease(primaryCode: Int) {
    }

    override fun swipeLeft() {
    }

    override fun swipeUp() {
    }

    override fun swipeDown() {
    }

    override fun onText(text: CharSequence?) {
    }
}