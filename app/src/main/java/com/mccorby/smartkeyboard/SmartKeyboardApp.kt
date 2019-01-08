package com.mccorby.smartkeyboard

import android.app.Application
import com.mccorby.machinelearning.nlp.LanguageModel
import java.io.ObjectInputStream

class SmartKeyboardApp : Application() {

    lateinit var languageModel: LanguageModel

    override fun onCreate() {
        super.onCreate()
        // start Koin!
//        startKoin(this, listOf(predictModule))
        val fileName = "saved_model"

        val fileDescriptor = assets.open(fileName)

        // TODO This object must be provided or loaded in a separate thread/coroutine
        languageModel = ObjectInputStream(fileDescriptor).use { it ->
            @Suppress("UNCHECKED_CAST")
            it.readObject() as LanguageModel
        }
    }
}
