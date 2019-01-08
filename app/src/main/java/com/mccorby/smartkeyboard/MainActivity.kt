package com.mccorby.smartkeyboard

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import com.mccorby.machinelearning.nlp.LanguageModel
import kotlinx.android.synthetic.main.activity_main.*
import java.io.ObjectInputStream

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        val fileName = "saved_model"
//
//        val fileDescriptor = assets.open(fileName)
//
//        // TODO This object must be provided or loaded in a separate thread/coroutine
//        val lm = ObjectInputStream(fileDescriptor).use { it ->
//            @Suppress("UNCHECKED_CAST")
//            it.readObject() as LanguageModel
//        }
//
//        val nPredictions = 3
//        val viewModel = ViewModelProviders.of(
//            this,
//            viewModelFactory { PredictViewModel(lm, nPredictions) }
//        ).get(PredictViewModel::class.java)
//
//        viewModel.prediction.observe(this, Observer<Sequence<String>> { prediction ->
//            // update UI
//            updatePredictions(prediction!!.toList())
//        })
//
//        edit_area.addTextChangedListener(object : TextWatcher {
//            override fun afterTextChanged(s: Editable?) {
//                viewModel.getPredictions(edit_area.text.toString())
//            }
//
//            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
//            }
//
//            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
//            }
//        })
    }

}
