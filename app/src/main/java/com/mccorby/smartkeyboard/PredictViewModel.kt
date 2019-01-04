package com.mccorby.smartkeyboard

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.mccorby.machinelearning.nlp.LanguageModel
import com.mccorby.machinelearning.nlp.NGrams
import com.mccorby.machinelearning.nlp.StupidBackoffRanking
import kotlin.math.max

// TODO modelOrder must be provided in a config file representing the model

const val MODEL_ORDER = 5

class PredictViewModel(private val lm: LanguageModel, private val nPredictions: Int) : ViewModel() {

    internal val prediction = MutableLiveData<Sequence<String>>()
    // TODO inject
    private val ngrams = NGrams(StupidBackoffRanking())

    // Select candidates and increase the corresponding history
    fun getPredictions(seed: String): LiveData<Sequence<String>> {
        // Only interested in the first nPredictions best predictions
        val candidates = generateInitialCandidates(seed).entries.sortedByDescending { it.value }.take(nPredictions)

        // Build a word for each candidate
        prediction.value = candidates.map { buildWord("$seed${it.key}") }.asSequence()
        return prediction
    }

    // Generate candidates based on the input (one for each word that will be shown to the user)
    private fun generateInitialCandidates(seed: String = ""): Map<Char, Float> {
        val initValue = NGrams.START_CHAR.repeat(max(MODEL_ORDER - seed.length, 0))
        val history = "$initValue$seed"

        val candidates = ngrams.generateCandidates(lm, MODEL_ORDER, history)
        println(candidates)
        return candidates
    }

    // TODO Should this be done in parallel for each seed?
    private fun buildWord(history: String): String {
        var tmp = history

        while (!tmp.endsWith(" ")) {
            tmp = "$tmp${ngrams.generateNextChar(lm, MODEL_ORDER, tmp)}"
        }
        // history can be a set of words thus we must split it and take the last one
        val result = tmp.trimEnd().split(" ").takeLast(1)[0]
        println(result)
        return result
    }
}