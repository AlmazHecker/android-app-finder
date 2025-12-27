package com.almazich.finder

import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.digitalink.recognition.Ink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DrawingScreen(
    onRecognize: (Ink, (String) -> Unit) -> Unit,
    onLaunchApp: (String) -> Unit,
) {
    var drawingState by remember { mutableStateOf(DrawingState()) }
    val currentStroke = remember { mutableStateOf(Ink.Stroke.builder()) }
    var searchText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val packageManager = context.packageManager
    val coroutineScope = rememberCoroutineScope()

    val installedApps = remember {
        packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { appInfo ->
                val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                launchIntent != null &&
                        !appInfo.packageName.startsWith("com.android") &&
                        !appInfo.packageName.startsWith("android")
            }
            .map {
                val icon = try { packageManager.getApplicationIcon(it.packageName) } catch (e: Exception) { null }
                AppInfo(
                    name = packageManager.getApplicationLabel(it).toString(),
                    packageName = it.packageName,
                    icon = icon
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.name.lowercase() }
    }

    val filteredApps = installedApps.filter {
        val query = if (searchText.isNotEmpty()) {
            searchText.trim().lowercase()
        } else {
            drawingState.recognizedText.trim().lowercase()
        }
        query.isNotEmpty() && it.name.lowercase().contains(query)
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val strokeColor = MaterialTheme.colorScheme.onSurface
    val mutedTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        Text(
            text = when {
                searchText.isEmpty() && drawingState.recognizedText.isEmpty() -> "Draw or type to search apps"
                filteredApps.isEmpty() -> "No apps found matching \"${searchText.ifEmpty { drawingState.recognizedText }}\""
                else -> "${filteredApps.size} apps found"
            },
            modifier = Modifier.padding(16.dp),
            fontSize = 18.sp,
            color = mutedTextColor,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.weight(1f))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = true
        ) {
            items(filteredApps, key = { it.packageName }) { app ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLaunchApp(app.packageName) },
                    colors = CardDefaults.cardColors(containerColor = surfaceColor)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        app.icon?.let { drawable ->
                            val bitmap = drawable.toBitmap()
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = app.name,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Text(
                            text = app.name,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = mutedTextColor.copy(alpha = 0.3f)
        )

        OutlinedTextField(
            value = searchText,
            onValueChange = {
                searchText = it
                if (it.isNotEmpty() && drawingState.recognizedText.isNotEmpty()) {
                    drawingState = DrawingState()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Type to search apps...") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = surfaceColor,
                unfocusedContainerColor = surfaceColor
            ),
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = { searchText = "" }) {
                        Text("✕", fontSize = 20.sp, color = mutedTextColor)
                    }
                }
            }
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pointer = event.changes.first()
                                    val position = pointer.position
                                    val timestamp = System.currentTimeMillis()
                                    when {
                                        pointer.pressed && !pointer.previousPressed -> {
                                            drawingState = drawingState.copy(
                                                currentPath = Path().apply { moveTo(position.x, position.y) },
                                                isDrawing = true
                                            )
                                            currentStroke.value = Ink.Stroke.builder()
                                                .addPoint(Ink.Point.create(position.x, position.y, timestamp))
                                        }
                                        pointer.pressed -> {
                                            val newPath = Path().apply {
                                                addPath(drawingState.currentPath)
                                                lineTo(position.x, position.y)
                                            }
                                            drawingState = drawingState.copy(currentPath = newPath)

                                            currentStroke.value.addPoint(
                                                Ink.Point.create(position.x, position.y, timestamp)
                                            )
                                        }
                                        else -> {
                                            if (drawingState.isDrawing) {
                                                val newPaths = drawingState.paths + drawingState.currentPath
                                                val newStrokes = drawingState.inkStrokes + currentStroke.value.build()
                                                drawingState = drawingState.copy(
                                                    paths = newPaths,
                                                    currentPath = Path(),
                                                    inkStrokes = newStrokes,
                                                    isDrawing = false
                                                )
                                                coroutineScope.launch {
                                                    delay(300)
                                                    val ink = Ink.builder().apply {
                                                        newStrokes.forEach { addStroke(it) }
                                                    }.build()
                                                    onRecognize(ink) { text ->
                                                        drawingState = drawingState.copy(recognizedText = text)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    pointer.consume()
                                }
                            }
                        }
                ) {
                    drawingState.paths.forEach { path ->
                        drawPath(
                            path = path,
                            color = strokeColor,
                            style = Stroke(
                                width = 8f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                    drawPath(
                        path = drawingState.currentPath,
                        color = strokeColor,
                        style = Stroke(
                            width = 8f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                if (drawingState.paths.isEmpty() && drawingState.currentPath.isEmpty) {
                    Text(
                        text = "Draw symbols here",
                        modifier = Modifier
                            .align(Alignment.Center),
                        fontSize = 16.sp,
                        color = mutedTextColor.copy(alpha = 0.6f)
                    )
                }

                if (drawingState.recognizedText.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = drawingState.recognizedText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                drawingState = DrawingState()
                searchText = ""
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Clear All", color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}