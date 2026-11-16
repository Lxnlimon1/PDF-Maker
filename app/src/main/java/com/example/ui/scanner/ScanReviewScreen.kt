package com.example.ui.scanner

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.PageSizeOption
import com.example.data.model.ScanFilter
import java.io.File
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReviewScreen(
    viewModel: ScannerViewModel,
    onBackToCamera: () -> Unit,
    onSavedPdf: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentPage = state.currentPage

    var showSaveDialog by remember { mutableStateOf(false) }
    var defaultPageSize by remember { mutableStateOf(PageSizeOption.A4) }
    var defaultHighRes by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val (size, highRes) = viewModel.getDefaultPreferences()
        defaultPageSize = size
        defaultHighRes = highRes
    }

    BackHandler {
        if (state.isCroppingMode) {
            viewModel.setCroppingMode(false)
        } else {
            onBackToCamera()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackToCamera) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to camera")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = if (state.isCroppingMode) "Adjust Corners" else "Scan Preview",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (state.pages.isNotEmpty()) {
                            Text(
                                text = "Page ${state.selectedIndex + 1} of ${state.pages.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (currentPage != null && !state.isCroppingMode) {
                        Button(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("scan_save_pdf_button"),
                            enabled = !state.isProcessing
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save PDF")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (currentPage == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No scanned pages in this session", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBackToCamera) { Text("Capture Page") }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding()
            ) {
                // Main Preview / Perspective Crop Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val displayPath = if (state.isCroppingMode) currentPage.originalPath else currentPage.filteredPath
                    val bitmap = remember(displayPath, state.isProcessing, currentPage.filter, currentPage.rotationDegrees) {
                        BitmapFactory.decodeFile(displayPath)
                    }

                    if (bitmap != null) {
                        val imgRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val boxW = maxWidth
                            val boxH = maxHeight
                            val containerRatio = boxW / boxH
                            val canvasModifier = if (imgRatio > containerRatio) {
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(imgRatio)
                            } else {
                                Modifier
                                    .fillMaxSize()
                                    .aspectRatio(imgRatio, matchHeightConstraintsFirst = true)
                            }

                            Box(
                                modifier = canvasModifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(Color.White)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Scanned page ${state.selectedIndex + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )

                                if (state.isCroppingMode && currentPage.corners.size == 4) {
                                    var activeHandle by remember { mutableIntStateOf(-1) }
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .pointerInput(currentPage.id) {
                                                detectDragGestures(
                                                    onDragStart = { start ->
                                                        val w = size.width.toFloat()
                                                        val h = size.height.toFloat()
                                                        activeHandle = currentPage.corners.indices.minByOrNull { idx ->
                                                            val pt = currentPage.corners[idx]
                                                            hypot(pt.x * w - start.x, pt.y * h - start.y)
                                                        } ?: -1
                                                    },
                                                    onDragEnd = { activeHandle = -1 },
                                                    onDragCancel = { activeHandle = -1 },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        if (activeHandle in 0..3) {
                                                            val pt = currentPage.corners[activeHandle]
                                                            val nx = pt.x + dragAmount.x / size.width
                                                            val ny = pt.y + dragAmount.y / size.height
                                                            viewModel.updateCorner(activeHandle, Offset(nx, ny))
                                                        }
                                                    }
                                                )
                                            }
                                    ) {
                                        val w = size.width
                                        val h = size.height
                                        val pts = currentPage.corners.map { Offset(it.x * w, it.y * h) }
                                        val path = Path().apply {
                                            moveTo(pts[0].x, pts[0].y)
                                            lineTo(pts[1].x, pts[1].y)
                                            lineTo(pts[2].x, pts[2].y)
                                            lineTo(pts[3].x, pts[3].y)
                                            close()
                                        }
                                        drawPath(path, Color(0x28D93036))
                                        drawPath(path, Color(0xFFD93036), style = Stroke(width = 3.dp.toPx()))
                                        pts.forEach { p ->
                                            drawCircle(Color.White, radius = 12.dp.toPx(), center = p)
                                            drawCircle(Color(0xFFD93036), radius = 8.dp.toPx(), center = p)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (state.isProcessing) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                                Text("Enhancing scan...", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                // Controls Panel
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    if (state.isCroppingMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.resetAutoCorners() },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Auto")
                            }
                            OutlinedButton(
                                onClick = { viewModel.applyPerspectiveCropAndRotate(90) },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate 90 degrees")
                            }
                            Button(
                                onClick = { viewModel.applyPerspectiveCropAndRotate(0) },
                                modifier = Modifier.weight(1.2f).height(48.dp).testTag("apply_crop_button")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Apply Crop")
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Filter Strip
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(ScanFilter.entries) { _, filter ->
                                    FilterChip(
                                        selected = currentPage.filter == filter,
                                        onClick = { viewModel.updateFilterAndAdjustments(filter = filter) },
                                        label = { Text(filter.label) },
                                        modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                                    )
                                }
                            }

                            // Brightness slider
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Bright",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.width(48.dp)
                                )
                                Slider(
                                    value = currentPage.brightness,
                                    onValueChange = { viewModel.updateFilterAndAdjustments(brightness = it) },
                                    valueRange = -0.6f..0.6f,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Page Tools Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { viewModel.setCroppingMode(true) },
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Crop", style = MaterialTheme.typography.labelMedium)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.applyPerspectiveCropAndRotate(90) },
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate page", modifier = Modifier.size(18.dp))
                                    }
                                    OutlinedButton(
                                        onClick = viewModel::deleteCurrentPage,
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete scanned page", modifier = Modifier.size(18.dp))
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { viewModel.movePage(state.selectedIndex, state.selectedIndex - 1) },
                                        enabled = state.selectedIndex > 0
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Move page left")
                                    }
                                    IconButton(
                                        onClick = { viewModel.movePage(state.selectedIndex, state.selectedIndex + 1) },
                                        enabled = state.selectedIndex < state.pages.lastIndex
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Move page right")
                                    }
                                }
                            }

                            // Page Strip + Add Page
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                itemsIndexed(state.pages) { idx, pg ->
                                    val selected = idx == state.selectedIndex
                                    Box(
                                        modifier = Modifier
                                            .size(width = 54.dp, height = 70.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = if (selected) 2.5.dp else 1.dp,
                                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { viewModel.selectPage(idx) }
                                    ) {
                                        AsyncImage(
                                            model = File(pg.filteredPath),
                                            contentDescription = "Page ${idx + 1}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Surface(
                                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f),
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(3.dp)
                                        ) {
                                            Text(
                                                text = "${idx + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                item {
                                    Surface(
                                        modifier = Modifier
                                            .size(width = 54.dp, height = 70.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(onClick = onBackToCamera)
                                            .testTag("scan_add_page_button"),
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                Icons.Default.AddAPhoto,
                                                contentDescription = "Add another page",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                "Add",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        var docTitle by remember { mutableStateOf("Scan_${System.currentTimeMillis() % 10000}") }
        var pageSize by remember { mutableStateOf(defaultPageSize) }
        var highRes by remember { mutableStateOf(defaultHighRes) }

        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Scanned PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = docTitle,
                        onValueChange = { docTitle = it },
                        label = { Text("PDF File Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Page Size", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PageSizeOption.entries.forEach { size ->
                            FilterChip(
                                selected = pageSize == size,
                                onClick = { pageSize = size },
                                label = { Text(size.label.substringBefore(" ")) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("High-Resolution Scan", style = MaterialTheme.typography.titleSmall)
                            Text("Crisp 2100px archival quality", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = highRes, onCheckedChange = { highRes = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveDialog = false
                        viewModel.saveScanAsPdf(docTitle, pageSize, highRes) { savedDoc ->
                            onSavedPdf(savedDoc.id)
                        }
                    }
                ) { Text("Create PDF") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }
}
