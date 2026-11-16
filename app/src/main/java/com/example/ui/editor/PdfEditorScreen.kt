package com.example.ui.editor

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.AnnotationTool
import com.example.data.model.EditorWorkspaceMode
import com.example.data.model.MarkupKind
import com.example.data.model.PageOverlayItem
import com.example.data.model.ShapeKind
import com.example.ui.components.formatFileSize
import com.example.ui.components.shareFile
import java.io.File
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(
    docId: String?,
    viewModel: PdfEditorViewModel,
    onBack: (() -> Unit)?,
    contentBottomPadding: PaddingValues
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val libraryDocs by viewModel.libraryDocuments.collectAsStateWithLifecycle()
    val savedSignatures by viewModel.savedSignatures.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(docId) {
        if (docId != null && state.document?.id != docId) {
            viewModel.openDocument(docId)
        }
    }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatus()
        }
    }

    val importPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importAndOpenPdf(it) }
    }

    val insertImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.insertImageIntoStream(context, it) }
    }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { viewModel.exportToUri(context, it) }
    }

    var showSignatureSheet by remember { mutableStateOf(false) }
    var pendingTextTapNorm by remember { mutableStateOf<Offset?>(null) }
    var pendingOverlayTextNorm by remember { mutableStateOf<Offset?>(null) }
    var pendingCommentNorm by remember { mutableStateOf<Offset?>(null) }
    var activeCommentRead by remember { mutableStateOf<PageOverlayItem.CommentPin?>(null) }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                title = {
                    val doc = state.document
                    if (doc != null) {
                        Column {
                            Text(
                                text = doc.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Page ${state.currentPageIndex + 1}/${state.pageCount}  •  ${formatFileSize(doc.fileSizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text("PDF Studio Editor", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    val doc = state.document
                    if (doc != null) {
                        IconButton(
                            onClick = viewModel::undoPdfFile,
                            enabled = state.canUndo || state.pendingOverlays.isNotEmpty()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }
                        IconButton(
                            onClick = viewModel::redoPdfFile,
                            enabled = state.canRedo
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                        }
                        IconButton(
                            onClick = { exportPdfLauncher.launch("${doc.title}.pdf") }
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = "Save PDF copy")
                        }
                        IconButton(
                            onClick = {
                                shareFile(context, File(doc.filePath), "application/pdf", "Share ${doc.title}")
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share PDF")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val doc = state.document
        if (doc == null) {
            // Document Selector Empty Workspace
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = contentBottomPadding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Select or Import a PDF to Edit",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Edit existing PDF text streams directly, highlight & sign with annotations, or reorder, merge, and split pages.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                            Button(
                                onClick = { importPdfLauncher.launch(arrayOf("application/pdf")) },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Import PDF from Storage")
                            }
                        }
                    }
                }

                if (libraryDocs.isNotEmpty()) {
                    item {
                        Text(
                            text = "Open from Workspace",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    items(libraryDocs, key = { it.id }) { item ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { viewModel.openDocument(item.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val thumb = item.thumbnailPath?.let { File(it) }
                                if (thumb != null && thumb.exists()) {
                                    AsyncImage(
                                        model = thumb,
                                        contentDescription = item.title,
                                        modifier = Modifier
                                            .size(width = 44.dp, height = 58.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${item.pageCount} pages  •  ${formatFileSize(item.fileSizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        bottom = contentBottomPadding.calculateBottomPadding()
                    )
            ) {
                // Workspace Mode Segmented Switcher (Explicitly distinguishes Content Stream vs Overlays vs Pages)
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    EditorWorkspaceMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = EditorWorkspaceMode.entries.size
                            ),
                            selected = state.workspaceMode == mode,
                            onClick = { viewModel.setWorkspaceMode(mode) },
                            modifier = Modifier.testTag("editor_mode_${mode.name.lowercase()}")
                        ) {
                            Text(
                                text = mode.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                // Clear distinction banner
                Surface(
                    color = when (state.workspaceMode) {
                        EditorWorkspaceMode.CONTENT_EDIT -> MaterialTheme.colorScheme.secondaryContainer
                        EditorWorkspaceMode.ANNOTATE -> MaterialTheme.colorScheme.primaryContainer
                        EditorWorkspaceMode.PAGES -> MaterialTheme.colorScheme.tertiaryContainer
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (state.workspaceMode) {
                                EditorWorkspaceMode.CONTENT_EDIT ->
                                    if (state.isPlacingPermanentText) {
                                        "Tap anywhere on the page to embed permanent PDF text"
                                    } else {
                                        "Stream Edit • Tap outlined text to edit/delete actual PDF content"
                                    }
                                EditorWorkspaceMode.ANNOTATE ->
                                    "Overlay Mode • Add annotations, ink, shapes & signatures"
                                EditorWorkspaceMode.PAGES ->
                                    "Structure • Reorder, rotate, duplicate, merge, split & extract"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = when (state.workspaceMode) {
                                EditorWorkspaceMode.CONTENT_EDIT -> MaterialTheme.colorScheme.onSecondaryContainer
                                EditorWorkspaceMode.ANNOTATE -> MaterialTheme.colorScheme.onPrimaryContainer
                                EditorWorkspaceMode.PAGES -> MaterialTheme.colorScheme.onTertiaryContainer
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (state.workspaceMode == EditorWorkspaceMode.ANNOTATE && state.pendingOverlays.isNotEmpty()) {
                            Button(
                                onClick = viewModel::commitPendingOverlays,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("commit_annotations_button")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save (${state.pendingOverlays.size})", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                if (state.workspaceMode == EditorWorkspaceMode.PAGES) {
                    PageOrganizerPane(
                        currentDocId = doc.id,
                        thumbnails = state.thumbnails,
                        currentPageIndex = state.currentPageIndex,
                        otherDocuments = libraryDocs,
                        onSelectPage = { idx ->
                            viewModel.goToPage(idx)
                            viewModel.setWorkspaceMode(EditorWorkspaceMode.CONTENT_EDIT)
                        },
                        onRotatePage = viewModel::rotatePage,
                        onDuplicatePage = viewModel::duplicatePage,
                        onDeletePage = viewModel::deletePage,
                        onCommitReorder = viewModel::reorderPages,
                        onMergeDocument = viewModel::mergeAnotherDocument,
                        onExtractPages = { indices, title ->
                            viewModel.extractSelectedPages(indices, title) { created ->
                                viewModel.openDocument(created.id)
                            }
                        },
                        onSplitRange = viewModel::splitDocumentByRange,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // Interactive PDF Page Canvas
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val pageBmp = state.pageBitmap
                        if (pageBmp != null) {
                            val pageAspect = pageBmp.width.toFloat() / pageBmp.height.coerceAtLeast(1).toFloat()
                            BoxWithConstraints(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                val containerAspect = maxWidth / maxHeight
                                val sheetModifier = if (pageAspect > containerAspect) {
                                    Modifier.fillMaxWidth().aspectRatio(pageAspect)
                                } else {
                                    Modifier.fillMaxSize().aspectRatio(pageAspect, matchHeightConstraintsFirst = true)
                                }

                                var activeInkPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
                                var dragStartNorm by remember { mutableStateOf<Offset?>(null) }
                                var dragEndNorm by remember { mutableStateOf<Offset?>(null) }

                                Box(
                                    modifier = sheetModifier
                                        .graphicsLayer {
                                            scaleX = zoomScale
                                            scaleY = zoomScale
                                            translationX = panOffset.x
                                            translationY = panOffset.y
                                        }
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                ) {
                                    Image(
                                        bitmap = pageBmp.asImageBitmap(),
                                        contentDescription = "PDF Page ${state.currentPageIndex + 1}",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.FillBounds
                                    )

                                    // Interactive Overlay & Content-Stream Layer
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .pointerInput(
                                                state.workspaceMode,
                                                state.isPlacingPermanentText,
                                                state.textBlocks,
                                                state.activeAnnotationTool,
                                                state.pendingOverlays
                                            ) {
                                                detectTapGestures { tapOffset ->
                                                    val nx = (tapOffset.x / size.width).coerceIn(0f, 1f)
                                                    val ny = (tapOffset.y / size.height).coerceIn(0f, 1f)

                                                    if (state.workspaceMode == EditorWorkspaceMode.CONTENT_EDIT) {
                                                        if (state.isPlacingPermanentText) {
                                                            pendingTextTapNorm = Offset(nx, ny)
                                                        } else {
                                                            val hit = state.textBlocks.firstOrNull { b ->
                                                                nx in (b.normLeft - 0.015f)..(b.normRight + 0.015f) &&
                                                                    ny in (b.normTop - 0.015f)..(b.normBottom + 0.015f)
                                                            }
                                                            if (hit != null) {
                                                                viewModel.selectTextBlock(hit)
                                                            }
                                                        }
                                                    } else if (state.workspaceMode == EditorWorkspaceMode.ANNOTATE) {
                                                        when (state.activeAnnotationTool) {
                                                            AnnotationTool.TEXT_BOX -> {
                                                                pendingOverlayTextNorm = Offset(nx, ny)
                                                            }
                                                            AnnotationTool.COMMENT -> {
                                                                val existing = state.pendingOverlays
                                                                    .filterIsInstance<PageOverlayItem.CommentPin>()
                                                                    .firstOrNull {
                                                                        kotlin.math.hypot(it.normX - nx, it.normY - ny) < 0.06f
                                                                    }
                                                                if (existing != null) {
                                                                    activeCommentRead = existing
                                                                } else {
                                                                    pendingCommentNorm = Offset(nx, ny)
                                                                }
                                                            }
                                                            AnnotationTool.SIGNATURE -> {
                                                                showSignatureSheet = true
                                                            }
                                                            else -> Unit
                                                        }
                                                    }
                                                }
                                            }
                                            .pointerInput(
                                                state.workspaceMode,
                                                state.activeAnnotationTool,
                                                state.activeColorArgb,
                                                state.strokeWidthNorm
                                            ) {
                                                if (state.workspaceMode == EditorWorkspaceMode.ANNOTATE &&
                                                    state.activeAnnotationTool in listOf(
                                                        AnnotationTool.DRAW,
                                                        AnnotationTool.HIGHLIGHT,
                                                        AnnotationTool.UNDERLINE,
                                                        AnnotationTool.STRIKETHROUGH,
                                                        AnnotationTool.SHAPE_RECT,
                                                        AnnotationTool.SHAPE_CIRCLE,
                                                        AnnotationTool.SHAPE_ARROW
                                                    )
                                                ) {
                                                    detectDragGestures(
                                                        onDragStart = { start ->
                                                            val n = Offset(
                                                                (start.x / size.width).coerceIn(0f, 1f),
                                                                (start.y / size.height).coerceIn(0f, 1f)
                                                            )
                                                            if (state.activeAnnotationTool == AnnotationTool.DRAW) {
                                                                activeInkPoints = listOf(n)
                                                            } else {
                                                                dragStartNorm = n
                                                                dragEndNorm = n
                                                            }
                                                        },
                                                        onDrag = { change, _ ->
                                                            change.consume()
                                                            val n = Offset(
                                                                (change.position.x / size.width).coerceIn(0f, 1f),
                                                                (change.position.y / size.height).coerceIn(0f, 1f)
                                                            )
                                                            if (state.activeAnnotationTool == AnnotationTool.DRAW) {
                                                                activeInkPoints = activeInkPoints + n
                                                            } else {
                                                                dragEndNorm = n
                                                            }
                                                        },
                                                        onDragEnd = {
                                                            if (state.activeAnnotationTool == AnnotationTool.DRAW) {
                                                                if (activeInkPoints.size >= 2) {
                                                                    viewModel.addOverlay(
                                                                        PageOverlayItem.InkStroke(
                                                                            id = UUID.randomUUID().toString(),
                                                                            points = activeInkPoints,
                                                                            strokeWidthNorm = state.strokeWidthNorm,
                                                                            colorArgb = state.activeColorArgb
                                                                        )
                                                                    )
                                                                }
                                                                activeInkPoints = emptyList()
                                                            } else {
                                                                val s = dragStartNorm
                                                                val e = dragEndNorm
                                                                if (s != null && e != null) {
                                                                    val l = min(s.x, e.x)
                                                                    val t = min(s.y, e.y)
                                                                    val r = max(s.x, e.x).coerceAtLeast(l + 0.03f)
                                                                    val b = max(s.y, e.y).coerceAtLeast(t + 0.02f)
                                                                    when (state.activeAnnotationTool) {
                                                                        AnnotationTool.HIGHLIGHT -> viewModel.addOverlay(
                                                                            PageOverlayItem.TextMarkup(UUID.randomUUID().toString(), MarkupKind.HIGHLIGHT, l, t, r, b, state.activeColorArgb)
                                                                        )
                                                                        AnnotationTool.UNDERLINE -> viewModel.addOverlay(
                                                                            PageOverlayItem.TextMarkup(UUID.randomUUID().toString(), MarkupKind.UNDERLINE, l, t, r, b, state.activeColorArgb)
                                                                        )
                                                                        AnnotationTool.STRIKETHROUGH -> viewModel.addOverlay(
                                                                            PageOverlayItem.TextMarkup(UUID.randomUUID().toString(), MarkupKind.STRIKETHROUGH, l, t, r, b, state.activeColorArgb)
                                                                        )
                                                                        AnnotationTool.SHAPE_RECT -> viewModel.addOverlay(
                                                                            PageOverlayItem.ShapeItem(UUID.randomUUID().toString(), ShapeKind.RECTANGLE, s, e, state.strokeWidthNorm, state.activeColorArgb)
                                                                        )
                                                                        AnnotationTool.SHAPE_CIRCLE -> viewModel.addOverlay(
                                                                            PageOverlayItem.ShapeItem(UUID.randomUUID().toString(), ShapeKind.CIRCLE, s, e, state.strokeWidthNorm, state.activeColorArgb)
                                                                        )
                                                                        AnnotationTool.SHAPE_ARROW -> viewModel.addOverlay(
                                                                            PageOverlayItem.ShapeItem(UUID.randomUUID().toString(), ShapeKind.ARROW, s, e, state.strokeWidthNorm, state.activeColorArgb)
                                                                        )
                                                                        else -> Unit
                                                                    }
                                                                }
                                                                dragStartNorm = null
                                                                dragEndNorm = null
                                                            }
                                                        },
                                                        onDragCancel = {
                                                            activeInkPoints = emptyList()
                                                            dragStartNorm = null
                                                            dragEndNorm = null
                                                        }
                                                    )
                                                }
                                            }
                                    ) {
                                        val w = size.width
                                        val h = size.height

                                        // Highlight editable stream text blocks in CONTENT_EDIT mode
                                        if (state.workspaceMode == EditorWorkspaceMode.CONTENT_EDIT && !state.isPlacingPermanentText) {
                                            for (block in state.textBlocks) {
                                                drawRect(
                                                    color = Color(0x202B59C3),
                                                    topLeft = Offset(block.normLeft * w, block.normTop * h),
                                                    size = Size(
                                                        (block.normRight - block.normLeft) * w,
                                                        (block.normBottom - block.normTop) * h
                                                    )
                                                )
                                                drawRect(
                                                    color = Color(0x662B59C3),
                                                    topLeft = Offset(block.normLeft * w, block.normTop * h),
                                                    size = Size(
                                                        (block.normRight - block.normLeft) * w,
                                                        (block.normBottom - block.normTop) * h
                                                    ),
                                                    style = Stroke(width = 1.dp.toPx())
                                                )
                                            }
                                        }

                                        // Draw uncommitted overlays
                                        for (overlay in state.pendingOverlays) {
                                            when (overlay) {
                                                is PageOverlayItem.TextMarkup -> {
                                                    val l = overlay.normLeft * w
                                                    val t = overlay.normTop * h
                                                    val r = overlay.normRight * w
                                                    val b = overlay.normBottom * h
                                                    val c = Color(overlay.colorArgb)
                                                    when (overlay.kind) {
                                                        MarkupKind.HIGHLIGHT -> drawRect(
                                                            color = c.copy(alpha = 0.35f),
                                                            topLeft = Offset(l, t),
                                                            size = Size(r - l, b - t)
                                                        )
                                                        MarkupKind.UNDERLINE -> drawLine(
                                                            color = c,
                                                            start = Offset(l, b),
                                                            end = Offset(r, b),
                                                            strokeWidth = 2.5.dp.toPx()
                                                        )
                                                        MarkupKind.STRIKETHROUGH -> drawLine(
                                                            color = c,
                                                            start = Offset(l, (t + b) / 2f),
                                                            end = Offset(r, (t + b) / 2f),
                                                            strokeWidth = 2.5.dp.toPx()
                                                        )
                                                    }
                                                }
                                                is PageOverlayItem.InkStroke -> {
                                                    if (overlay.points.size >= 2) {
                                                        val p = Path().apply {
                                                            moveTo(overlay.points.first().x * w, overlay.points.first().y * h)
                                                            overlay.points.drop(1).forEach { pt ->
                                                                lineTo(pt.x * w, pt.y * h)
                                                            }
                                                        }
                                                        drawPath(
                                                            p,
                                                            Color(overlay.colorArgb),
                                                            style = Stroke(
                                                                width = (overlay.strokeWidthNorm * w).coerceAtLeast(2.dp.toPx()),
                                                                cap = StrokeCap.Round,
                                                                join = StrokeJoin.Round
                                                            )
                                                        )
                                                    }
                                                }
                                                is PageOverlayItem.ShapeItem -> {
                                                    val sx = overlay.start.x * w
                                                    val sy = overlay.start.y * h
                                                    val ex = overlay.end.x * w
                                                    val ey = overlay.end.y * h
                                                    val c = Color(overlay.colorArgb)
                                                    val sw = (overlay.strokeWidthNorm * w).coerceAtLeast(2.dp.toPx())
                                                    when (overlay.kind) {
                                                        ShapeKind.RECTANGLE -> drawRect(
                                                            color = c,
                                                            topLeft = Offset(min(sx, ex), min(sy, ey)),
                                                            size = Size(kotlin.math.abs(ex - sx), kotlin.math.abs(ey - sy)),
                                                            style = Stroke(width = sw)
                                                        )
                                                        ShapeKind.CIRCLE -> drawOval(
                                                            color = c,
                                                            topLeft = Offset(min(sx, ex), min(sy, ey)),
                                                            size = Size(kotlin.math.abs(ex - sx), kotlin.math.abs(ey - sy)),
                                                            style = Stroke(width = sw)
                                                        )
                                                        ShapeKind.ARROW -> {
                                                            drawLine(c, Offset(sx, sy), Offset(ex, ey), strokeWidth = sw, cap = StrokeCap.Round)
                                                            val angle = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                                                            val len = 14.dp.toPx()
                                                            drawLine(
                                                                c,
                                                                Offset(ex, ey),
                                                                Offset((ex - len * cos(angle - 0.5)).toFloat(), (ey - len * sin(angle - 0.5)).toFloat()),
                                                                strokeWidth = sw,
                                                                cap = StrokeCap.Round
                                                            )
                                                            drawLine(
                                                                c,
                                                                Offset(ex, ey),
                                                                Offset((ex - len * cos(angle + 0.5)).toFloat(), (ey - len * sin(angle + 0.5)).toFloat()),
                                                                strokeWidth = sw,
                                                                cap = StrokeCap.Round
                                                            )
                                                        }
                                                    }
                                                }
                                                is PageOverlayItem.CommentPin -> {
                                                    val center = Offset(overlay.normX * w, overlay.normY * h)
                                                    drawCircle(Color(overlay.colorArgb), radius = 11.dp.toPx(), center = center)
                                                    drawCircle(Color.White, radius = 4.dp.toPx(), center = center)
                                                }
                                                is PageOverlayItem.TextBoxItem -> {
                                                    val center = Offset(overlay.normX * w, overlay.normY * h)
                                                    drawRect(
                                                        Color(overlay.colorArgb).copy(alpha = 0.25f),
                                                        topLeft = center,
                                                        size = Size(120.dp.toPx(), 28.dp.toPx())
                                                    )
                                                }
                                                is PageOverlayItem.SignaturePlacement -> {
                                                    val l = overlay.normLeft * w
                                                    val t = overlay.normTop * h
                                                    val rw = (overlay.normRight - overlay.normLeft) * w
                                                    val rh = (overlay.normBottom - overlay.normTop) * h
                                                    drawRect(
                                                        Color(0x182B59C3),
                                                        topLeft = Offset(l, t),
                                                        size = Size(rw, rh)
                                                    )
                                                    drawRect(
                                                        Color(0xFF2B59C3),
                                                        topLeft = Offset(l, t),
                                                        size = Size(rw, rh),
                                                        style = Stroke(width = 1.5.dp.toPx())
                                                    )
                                                }
                                            }
                                        }

                                        // Live in-progress stroke / drag preview
                                        if (activeInkPoints.size >= 2) {
                                            val p = Path().apply {
                                                moveTo(activeInkPoints.first().x * w, activeInkPoints.first().y * h)
                                                activeInkPoints.drop(1).forEach { pt -> lineTo(pt.x * w, pt.y * h) }
                                            }
                                            drawPath(
                                                p,
                                                Color(state.activeColorArgb),
                                                style = Stroke(
                                                    width = (state.strokeWidthNorm * w).coerceAtLeast(2.dp.toPx()),
                                                    cap = StrokeCap.Round,
                                                    join = StrokeJoin.Round
                                                )
                                            )
                                        }
                                        val ds = dragStartNorm
                                        val de = dragEndNorm
                                        if (ds != null && de != null) {
                                            drawRect(
                                                color = Color(state.activeColorArgb).copy(alpha = 0.3f),
                                                topLeft = Offset(min(ds.x, de.x) * w, min(ds.y, de.y) * h),
                                                size = Size(
                                                    kotlin.math.abs(de.x - ds.x) * w,
                                                    kotlin.math.abs(de.y - ds.y) * h
                                                )
                                            )
                                        }
                                    }

                                    // Render placed signatures & text boxes crisply on top
                                    state.pendingOverlays.filterIsInstance<PageOverlayItem.SignaturePlacement>().forEach { sig ->
                                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                            val bw = maxWidth
                                            val bh = maxHeight
                                            AsyncImage(
                                                model = File(sig.imagePath),
                                                contentDescription = "Placed signature",
                                                modifier = Modifier
                                                    .padding(
                                                        start = bw * sig.normLeft,
                                                        top = bh * sig.normTop
                                                    )
                                                    .size(
                                                        width = bw * (sig.normRight - sig.normLeft),
                                                        height = bh * (sig.normBottom - sig.normTop)
                                                    ),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                    }

                                    state.pendingOverlays.filterIsInstance<PageOverlayItem.TextBoxItem>().forEach { tb ->
                                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                            Text(
                                                text = tb.text,
                                                color = Color(tb.colorArgb),
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(
                                                    start = maxWidth * tb.normX,
                                                    top = maxHeight * tb.normY
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Page switcher + Zoom pill floating cleanly at bottom-center of canvas
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            tonalElevation = 4.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.goToPage(state.currentPageIndex - 1) },
                                    enabled = state.currentPageIndex > 0,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                                }
                                Text(
                                    text = "${state.currentPageIndex + 1} / ${state.pageCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                                IconButton(
                                    onClick = { viewModel.goToPage(state.currentPageIndex + 1) },
                                    enabled = state.currentPageIndex < state.pageCount - 1,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                                }
                                IconButton(
                                    onClick = { zoomScale = (zoomScale - 0.35f).coerceAtLeast(1f); if (zoomScale == 1f) panOffset = Offset.Zero },
                                    enabled = zoomScale > 1f,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { zoomScale = (zoomScale + 0.35f).coerceAtMost(2.5f) },
                                    enabled = zoomScale < 2.5f,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in", modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        if (state.isBusy) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                tonalElevation = 6.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                                    Text("Updating PDF...", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    // Bottom Contextual Toolbar
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        if (state.workspaceMode == EditorWorkspaceMode.CONTENT_EDIT) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = !state.isPlacingPermanentText,
                                    onClick = { viewModel.setPlacingPermanentText(false) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Edit Text") },
                                    modifier = Modifier.testTag("stream_edit_text_chip")
                                )
                                FilterChip(
                                    selected = state.isPlacingPermanentText,
                                    onClick = { viewModel.setPlacingPermanentText(!state.isPlacingPermanentText) },
                                    leadingIcon = { Icon(Icons.Default.Title, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Add Text") },
                                    modifier = Modifier.testTag("stream_add_text_chip")
                                )
                                OutlinedButton(
                                    onClick = {
                                        insertImageLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier.height(36.dp).testTag("stream_insert_image_button")
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Insert Image", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                // Color palette row
                                val colors = listOf(
                                    0xFFFFD54F.toInt(),
                                    0xFF4ADE80.toInt(),
                                    0xFF38BDF8.toInt(),
                                    0xFFD93036.toInt(),
                                    0xFF2B59C3.toInt(),
                                    0xFF131B2E.toInt()
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        colors.forEach { argb ->
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(argb))
                                                    .border(
                                                        width = if (state.activeColorArgb == argb) 2.5.dp else 1.dp,
                                                        color = if (state.activeColorArgb == argb) MaterialTheme.colorScheme.primary else Color.LightGray,
                                                        shape = CircleShape
                                                    )
                                                    .clickable { viewModel.setColor(argb) }
                                            )
                                        }
                                    }
                                    Text(
                                        text = state.activeAnnotationTool.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(Modifier.height(6.dp))

                                val toolItems = listOf(
                                    AnnotationTool.HIGHLIGHT to Icons.Default.Highlight,
                                    AnnotationTool.UNDERLINE to Icons.Default.FormatUnderlined,
                                    AnnotationTool.STRIKETHROUGH to Icons.Default.StrikethroughS,
                                    AnnotationTool.DRAW to Icons.Default.Draw,
                                    AnnotationTool.SIGNATURE to Icons.Default.Gesture,
                                    AnnotationTool.TEXT_BOX to Icons.Default.TextFields,
                                    AnnotationTool.COMMENT to Icons.Default.Comment,
                                    AnnotationTool.SHAPE_RECT to Icons.Default.CropSquare,
                                    AnnotationTool.SHAPE_CIRCLE to Icons.Default.Circle,
                                    AnnotationTool.SHAPE_ARROW to Icons.Default.NorthEast
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(toolItems) { (tool, icon) ->
                                        FilterChip(
                                            selected = state.activeAnnotationTool == tool,
                                            onClick = {
                                                viewModel.setAnnotationTool(tool)
                                                if (tool == AnnotationTool.SIGNATURE) {
                                                    showSignatureSheet = true
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                            },
                                            label = { Text(tool.label) },
                                            modifier = Modifier.testTag("ann_tool_${tool.name.lowercase()}")
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

    // Genuine Content-Stream Text Edit / Delete Dialog
    val editingBlock = state.selectedTextBlock
    if (editingBlock != null) {
        var editedText by remember(editingBlock.id) { mutableStateOf(editingBlock.text) }
        AlertDialog(
            onDismissRequest = { viewModel.selectTextBlock(null) },
            title = { Text("Edit PDF Stream Text") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Updates the underlying PDF content stream directly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editedText,
                        onValueChange = { editedText = it },
                        label = { Text("Text content") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_stream_text_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.editTextBlockInStream(editingBlock, editedText) },
                    modifier = Modifier.testTag("save_stream_text_button")
                ) { Text("Update Stream") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { viewModel.editTextBlockInStream(editingBlock, "") }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { viewModel.selectTextBlock(null) }) { Text("Cancel") }
                }
            }
        )
    }

    // Add Permanent Text to PDF Stream Dialog
    val tapPt = pendingTextTapNorm
    if (tapPt != null) {
        var newText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingTextTapNorm = null },
            title = { Text("Insert Permanent PDF Text") },
            text = {
                OutlinedTextField(
                    value = newText,
                    onValueChange = { newText = it },
                    label = { Text("Text to write into PDF stream") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val txt = newText
                        pendingTextTapNorm = null
                        viewModel.insertPermanentTextInStream(txt, tapPt.x, tapPt.y)
                    },
                    enabled = newText.isNotBlank()
                ) { Text("Insert") }
            },
            dismissButton = {
                TextButton(onClick = { pendingTextTapNorm = null }) { Text("Cancel") }
            }
        )
    }

    // Add Overlay Text Box Dialog
    val overlayPt = pendingOverlayTextNorm
    if (overlayPt != null) {
        var boxText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingOverlayTextNorm = null },
            title = { Text("Add Text Box Annotation") },
            text = {
                OutlinedTextField(
                    value = boxText,
                    onValueChange = { boxText = it },
                    label = { Text("Callout text") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addOverlay(
                            PageOverlayItem.TextBoxItem(
                                id = UUID.randomUUID().toString(),
                                text = boxText,
                                normX = overlayPt.x,
                                normY = overlayPt.y,
                                fontSizeSp = 13f,
                                colorArgb = state.activeColorArgb
                            )
                        )
                        pendingOverlayTextNorm = null
                    },
                    enabled = boxText.isNotBlank()
                ) { Text("Place") }
            },
            dismissButton = {
                TextButton(onClick = { pendingOverlayTextNorm = null }) { Text("Cancel") }
            }
        )
    }

    // Add Sticky Comment Pin Dialog
    val commentPt = pendingCommentNorm
    if (commentPt != null) {
        var noteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingCommentNorm = null },
            title = { Text("Add Comment Note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Comment / review note") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addOverlay(
                            PageOverlayItem.CommentPin(
                                id = UUID.randomUUID().toString(),
                                comment = noteText,
                                normX = commentPt.x,
                                normY = commentPt.y,
                                colorArgb = state.activeColorArgb
                            )
                        )
                        pendingCommentNorm = null
                    },
                    enabled = noteText.isNotBlank()
                ) { Text("Add Pin") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCommentNorm = null }) { Text("Cancel") }
            }
        )
    }

    val readPin = activeCommentRead
    if (readPin != null) {
        AlertDialog(
            onDismissRequest = { activeCommentRead = null },
            title = { Text("Comment Note") },
            text = { Text(readPin.comment) },
            confirmButton = {
                TextButton(onClick = { activeCommentRead = null }) { Text("Close") }
            }
        )
    }

    if (showSignatureSheet) {
        SignaturePadSheet(
            savedSignatures = savedSignatures,
            onDismiss = { showSignatureSheet = false },
            onCreateAndPlace = { label, bitmap ->
                showSignatureSheet = false
                viewModel.saveSignatureToLibrary(label, bitmap) { saved ->
                    bitmap.recycle()
                    viewModel.placeSignatureOnPage(saved)
                }
            },
            onSelectSaved = { saved ->
                showSignatureSheet = false
                viewModel.placeSignatureOnPage(saved)
            },
            onDeleteSaved = viewModel::deleteSavedSignature
        )
    }
}
