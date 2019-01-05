# SMART KEYBOARD - nGram approach

This app shows how to use an ngram model to allow the user to select words based on the input


The model is generated using this [NLP ngram project](https://github.com/mccorby/MachineLearning/tree/master/src/main/kotlin/com/mccorby/machinelearning/nlp)

Because the model can be built using a specific corpus the predictions are relevant to the potential business model of the app.

The model included in this app (saved_model) was created using movie titles. This means the predictions will be movie-related instead of a more generic keyboard that would show whatever the user types in any other app

The keyboard layout was borrowed from ADAM SINICKI's excellent post at https://www.androidauthority.com/lets-build-custom-keyboard-android-832362/

To use the keyboard, enable it on your device. Go on Settings > Language and Input. Click on the Smart Keyboard Ngram and enable