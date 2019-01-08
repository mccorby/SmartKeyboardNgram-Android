package com.mccorby.smartkeyboard.di

import android.content.res.AssetManager
import com.mccorby.machinelearning.nlp.LanguageModel
import org.koin.dsl.module.module
import java.io.ObjectInputStream

// just declare it
val predictModule = module {
    single {
        (assets: AssetManager) ->
        {
            val fileName = "saved_model"

            val fileDescriptor = assets.open(fileName)

            // TODO This object must be provided or loaded in a separate thread/coroutine
            val lm = ObjectInputStream(fileDescriptor).use { it ->
                @Suppress("UNCHECKED_CAST")
                it.readObject() as LanguageModel
            }
            lm
        }
    }
}