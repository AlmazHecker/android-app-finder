package com.almazich.finder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.almazich.finder.ui.theme.FinderTheme
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.*

class MainActivity : ComponentActivity() {
    private lateinit var recognizer: DigitalInkRecognizer
    private var model: DigitalInkRecognitionModel? = null

    private var isModelLoading = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")
        if (modelIdentifier != null) {
            model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
            recognizer = DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(model!!).build()
            )
            RemoteModelManager.getInstance().download(model!!, DownloadConditions.Builder().build())
                .addOnSuccessListener { isModelLoading.value = false }
                .addOnFailureListener { isModelLoading.value = false }
        }

        setContent {
            FinderTheme() {
                DrawingScreenWithLoading(
                    onRecognize = ::recognizeInk,
                    onLaunchApp = { launchApp(it) },
                    isModelLoading = isModelLoading.value
                )
            }
        }
    }

    private fun recognizeInk(ink: com.google.mlkit.vision.digitalink.recognition.Ink, callback: (String) -> Unit) {
        if (ink.strokes.isEmpty()) return
        recognizer.recognize(ink)
            .addOnSuccessListener { result ->
                if (result.candidates.isNotEmpty()) {
                    callback(result.candidates[0].text.trim())
                }
            }
            .addOnFailureListener { }
    }

    private fun launchApp(packageName: String) {
        packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::recognizer.isInitialized) recognizer.close()
    }
}
