package com.almazich.finder

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Path
import com.google.mlkit.vision.digitalink.recognition.Ink

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable? = null
)

data class DrawingState(
    val paths: List<Path> = emptyList(),
    val currentPath: Path = Path(),
    val inkStrokes: List<Ink.Stroke> = emptyList(),
    val isDrawing: Boolean = false,
    val recognizedText: String = ""
)
