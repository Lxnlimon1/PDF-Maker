package com.example.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Output
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.PdfDocumentUi

@Composable
fun PageOrganizerPane(
    currentDocId: String,
    thumbnails: List<Bitmap>,
    currentPageIndex: Int,
    otherDocuments: List<PdfDocumentUi>,
    onSelectPage: (Int) -> Unit,
    onRotatePage: (Int) -> Unit,
    onDuplicatePage: (Int) -> Unit,
    onDeletePage: (Int) -> Unit,
    onCommitReorder: (List<Int>) -> Unit,
    onMergeDocument: (PdfDocumentUi) -> Unit,
    onExtractPages: (List<Int>, String) -> Unit,
    onSplitRange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Working order list for instant drag-and-drop visual feedback
    val pageOrder = remember(thumbnails.size) {
        mutableStateListOf<Int>().apply { addAll(thumbnails.indices) }
    }
    LaunchedEffect(thumbnails) {
        pageOrder.clear()
        pageOrder.addAll(thumbnails.indices)
    }

    val selectedIndices = remember { mutableStateListOf<Int>() }
    var selectionMode by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var showSplitDialog by remember { mutableStateOf(false) }
    var showExtractDialog by remember { mutableStateOf(false) }

    var draggingSlot by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier.fillMaxSize()) {
        // Structural Action Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { showMergeDialog = true },
                modifier = Modifier.weight(1f).height(42.dp).testTag("pages_merge_button"),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.MergeType, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Merge", style = MaterialTheme.typography.labelMedium)
            }
            FilledTonalButton(
                onClick = { showSplitDialog = true },
                modifier = Modifier.weight(1f).height(42.dp).testTag("pages_split_button"),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Split", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = {
                    selectionMode = !selectionMode
                    if (!selectionMode) selectedIndices.clear()
                },
                modifier = Modifier.weight(1f).height(42.dp).testTag("pages_select_button"),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.Output, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (selectionMode) "Done" else "Extract",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        if (selectionMode) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedIndices.size} selected to extract",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Button(
                        onClick = { showExtractDialog = true },
                        enabled = selectedIndices.isNotEmpty(),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Extract PDF")
                    }
                }
            }
        } else {
            Text(
                text = "Long-press & drag pages to reorder, or use arrows",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 148.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(pageOrder, key = { slot, origIdx -> "${slot}_$origIdx" }) { slot, origIdx ->
                val bmp = thumbnails.getOrNull(origIdx)
                val isCurrent = origIdx == currentPageIndex
                val isSelected = selectedIndices.contains(origIdx)
                val isDragging = draggingSlot == slot

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = if (isDragging) 8.dp else 1.dp,
                    border = BorderStroke(
                        width = if (isSelected || isCurrent) 2.dp else 1.dp,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.secondary
                            isCurrent -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier
                        .graphicsLayer {
                            if (isDragging) {
                                translationX = dragOffsetX
                                translationY = dragOffsetY
                                scaleX = 1.04f
                                scaleY = 1.04f
                            }
                        }
                        .pointerInput(pageOrder.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingSlot = slot
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetX += dragAmount.x
                                    dragOffsetY += dragAmount.y
                                    val stepX = (dragOffsetX / 150f).toInt()
                                    val stepY = (dragOffsetY / 220f).toInt()
                                    val target = (draggingSlot + stepX + stepY * 2)
                                        .coerceIn(0, pageOrder.lastIndex)
                                    if (target != draggingSlot && draggingSlot in pageOrder.indices) {
                                        val item = pageOrder.removeAt(draggingSlot)
                                        pageOrder.add(target, item)
                                        draggingSlot = target
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                    }
                                },
                                onDragEnd = {
                                    draggingSlot = -1
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    if (pageOrder.toList() != thumbnails.indices.toList()) {
                                        onCommitReorder(pageOrder.toList())
                                    }
                                },
                                onDragCancel = {
                                    draggingSlot = -1
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                }
                            )
                        }
                        .clickable {
                            if (selectionMode) {
                                if (isSelected) selectedIndices.remove(origIdx) else selectedIndices.add(origIdx)
                            } else {
                                onSelectPage(origIdx)
                            }
                        }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.72f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Page ${origIdx + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = "${slot + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            if (selectionMode) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = if (isSelected) "Selected" else "Not selected",
                                    tint = if (isSelected) MaterialTheme.colorScheme.secondary else Color.LightGray,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(22.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.DragIndicator,
                                    contentDescription = "Drag to reorder",
                                    tint = Color.Gray,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(18.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        // Per-page compact actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (slot > 0) {
                                        val copy = pageOrder.toMutableList()
                                        val item = copy.removeAt(slot)
                                        copy.add(slot - 1, item)
                                        onCommitReorder(copy)
                                    }
                                },
                                enabled = slot > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Move earlier",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onRotatePage(origIdx) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.RotateRight,
                                    contentDescription = "Rotate page",
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDuplicatePage(origIdx) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Duplicate page",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDeletePage(origIdx) },
                                enabled = thumbnails.size > 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete page",
                                    tint = if (thumbnails.size > 1) MaterialTheme.colorScheme.error else Color.Gray,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (slot < pageOrder.lastIndex) {
                                        val copy = pageOrder.toMutableList()
                                        val item = copy.removeAt(slot)
                                        copy.add(slot + 1, item)
                                        onCommitReorder(copy)
                                    }
                                },
                                enabled = slot < pageOrder.lastIndex,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Move later",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMergeDialog) {
        val candidates = otherDocuments.filter { it.id != currentDocId }
        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            title = { Text("Merge Another PDF") },
            text = {
                if (candidates.isEmpty()) {
                    Text("Import or scan another PDF into your library first to append it to this document.")
                } else {
                    LazyColumn(
                        modifier = Modifier.height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(candidates, key = { it.id }) { item ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        showMergeDialog = false
                                        onMergeDocument(item)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${item.pageCount} pg",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMergeDialog = false }) { Text("Close") }
            }
        )
    }

    if (showSplitDialog) {
        var rangeText by remember { mutableStateOf("1-${(thumbnails.size / 2).coerceAtLeast(1)}, ${(thumbnails.size / 2 + 1).coerceAtMost(thumbnails.size)}-${thumbnails.size}") }
        AlertDialog(
            onDismissRequest = { showSplitDialog = false },
            title = { Text("Split PDF by Page Ranges") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter comma-separated ranges (e.g. 1-2, 3-5) or \"all\" to split every page.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = rangeText,
                        onValueChange = { rangeText = it },
                        label = { Text("Page ranges (Total: ${thumbnails.size})") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSplitDialog = false
                        onSplitRange(rangeText)
                    }
                ) { Text("Split") }
            },
            dismissButton = {
                TextButton(onClick = { showSplitDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showExtractDialog) {
        var title by remember { mutableStateOf("Extracted Pages") }
        AlertDialog(
            onDismissRequest = { showExtractDialog = false },
            title = { Text("Extract ${selectedIndices.size} Page(s)") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("New PDF Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExtractDialog = false
                        val copy = selectedIndices.toList()
                        selectionMode = false
                        selectedIndices.clear()
                        onExtractPages(copy, title)
                    }
                ) { Text("Create PDF") }
            },
            dismissButton = {
                TextButton(onClick = { showExtractDialog = false }) { Text("Cancel") }
            }
        )
    }
}
