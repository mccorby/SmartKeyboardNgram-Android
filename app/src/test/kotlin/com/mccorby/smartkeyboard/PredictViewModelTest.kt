package com.mccorby.smartkeyboard

import com.mccorby.machinelearning.nlp.LanguageModel
import org.junit.Assert.assertEquals
import org.junit.Test

class PredictViewModelTest {

    // TODO LiveData triggers Android components :(
    @Test
    fun `Given a number of predictions the view model maintains that number of lines of work`() {
        // Given
        val nPredictions = 3
        val languageModel = LanguageModel()

        val cut = PredictViewModel(languageModel, nPredictions)

        val result = cut.getPredictions().value!!.toList().size

        // Then
        assertEquals(nPredictions, result)
    }
}