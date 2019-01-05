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

    //     @get:Rule
    //    val testRule: TestRule = InstantTaskExecutorRule()

    // setUp {
//    given(boxConnectivityViewModelFactory.create(any())).willReturn(baseBoxConnectivityViewModel)
    // }


    //    @Test
    //    fun `given no downloads and connected to box, when loading downloads, then view model is given connected empty downloads error state`() {
    //        // Given
    //        whenever(getDownloadItemsSortedByCreationTimeUseCase.buildUseCase()).thenReturn(Flowable.just(emptyList()))
    //        whenever(getCurrentBoxConnectivityResultUseCase.buildUseCase()).thenReturn(Single.just(BoxConnectivityResult(BoxConnectivityState.Connection.Connected.NotFirstTime, false)))
    //
    //        // When
    //        cut.initialize()
    //        cut.viewState.observeForever(downloadsViewStateObserver)
    //
    //        // Then
    //        val viewStateCaptor = ArgumentCaptor.forClass(DownloadsViewState::class.java)
    //        verify(downloadsViewStateObserver).onChanged(capture(viewStateCaptor))
    //        val viewState = viewStateCaptor.value
    //        assertNotNull(viewState)
    //        assertEquals(DownloadsViewState.ErrorType.NO_DOWNLOADS_CONNECTED, viewState.errorType)
    //        assertEquals(0, viewState.collectionItemUiModels.size)
    //    }
}