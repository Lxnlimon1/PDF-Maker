package com.example.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.SavedSignatureEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignaturePadSheet(
    savedSignatures: List<SavedSignatureEntity>,
    onDismiss: () -> Unit,
    onCreateAndPlace: (label: String, bitmap: Bitmap) -> Unit,
    onSelectSaved: (SavedSignatureEntity) -> Unit,
    onDeleteSaved: (SavedSignatureEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize(600, 260)) }
    var selectedColor by remember { mutableIntStateOf(0xFF131B2E.toInt()) }
    var signatureLabel by remember { mutableStateOf("My Signature") }

    val inkColors = listOf(
        0xFF131B2E.toInt(), // Deep Ink
        0xFF1D4ED8.toInt(), // Royal Blue
        0xFFD93036.toInt()  // Crimson
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Handwritten Signature", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Draw below or tap a saved signature",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    inkColors.forEach { argb ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (selectedColor == argb) 3.dp else 1.dp,
                                    color = if (selectedColor == argb) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = argb }
                        )
                    }
                }
            }

            if (savedSignatures.isNotEmpty()) {
                Text("Saved Signatures", style = MaterialTheme.typography.labelLarge)
                LazyRow(
                    contentPadding = PaddingValues(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(savedSignatures, key = { it.id }) { sig ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = Color.White,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .width(140.dp)
                                .height(64.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onSelectSaved(sig) }
                        ) {
                            Box(modifier = Modifier.padding(6.dp)) {
                                AsyncImage(
                                    model = File(sig.imagePath),
                                    contentDescription = sig.label,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    contentScale = ContentScale.Fit
                                )
                                IconButton(
                                    onClick = { onDeleteSaved(sig) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(22.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete saved signature",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Drawing Pad
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color.White)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentStroke = listOf(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentStroke = currentStroke + change.position
                            },
                            onDragEnd = {
                                if (currentStroke.size >= 2) {
                                    strokes.add(currentStroke)
                                }
                                currentStroke = emptyList()
                            },
                            onDragCancel = { currentStroke = emptyList() }
                        )
                    }
                    .testTag("signature_canvas")
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                    // Subtle signature baseline
                    val lineY = size.height * 0.74f
                    drawLine(
                        color = Color(0xFFE2E6F0),
                        start = Offset(24.dp.toPx(), lineY),
                        end = Offset(size.width - 24.dp.toPx(), lineY),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    val strokeStyle = Stroke(
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                    (strokes + listOf(currentStroke)).forEach { pts ->
                        if (pts.size >= 2) {
                            val p = androidx.compose.ui.graphics.Path().apply {
                                moveTo(pts.first().x, pts.first().y)
                                for (pt in pts.drop(1)) {
                                    lineTo(pt.x, pt.y)
                                }
                            }
                            drawPath(p, Color(selectedColor), style = strokeStyle)
                        }
                    }
                }

                if (strokes.isEmpty() && currentStroke.isEmpty()) {
                    Text(
                        text = "Sign with your finger or stylus",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF9AA3B5),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            OutlinedTextField(
                value = signatureLabel,
                onValueChange = { signatureLabel = it },
                label = { Text("Signature label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { strokes.clear() },
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear")
                }
                Button(
                    onClick = {
                        val bmp = renderSignatureBitmap(
                            strokes = strokes.toList(),
                            width = canvasSize.width.coerceAtLeast(400),
                            height = canvasSize.height.coerceAtLeast(180),
                            colorArgb = selectedColor
                        )
                        onCreateAndPlace(signatureLabel, bmp)
                    },
                    enabled = strokes.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("signature_save_place_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save & Place")
                }
            }
        }
    }
}

private fun renderSignatureBitmap(
    strokes: List<List<Offset>>,
    width: Int,
    height: Int,
    colorArgb: Int
): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = Paint.Style.STROKE
        strokeWidth = (width * 0.012f).coerceIn(5f, 14f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    for (pts in strokes) {
        if (pts.size < 2) continue
        val path = Path()
        path.moveTo(pts.first().x, pts.first().y)
        for (pt in pts.drop(1)) {
            path.lineTo(pt.x, pt.y)
        }
        canvas.drawPath(path, paint)
    }
    return bmp
}
