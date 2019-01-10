package com.mccorby.smartkeyboard.di

import com.mccorby.machinelearning.nlp.LanguageModel
import com.mccorby.machinelearning.nlp.NGrams
import com.mccorby.machinelearning.nlp.RankingModel
import com.mccorby.machinelearning.nlp.StupidBackoffRanking
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module.module
import java.io.ObjectInputStream


val predictModule = module {
    single {
        val fileName = "saved_model"

        val fileDescriptor = androidApplication().assets.open(fileName)

         ObjectInputStream(fileDescriptor).use { ois ->
            @Suppress("UNCHECKED_CAST")
            ois.readObject() as LanguageModel
        }
    }

    single {
        NGrams(get())
    }

    single {
        StupidBackoffRanking() as RankingModel
    }
}