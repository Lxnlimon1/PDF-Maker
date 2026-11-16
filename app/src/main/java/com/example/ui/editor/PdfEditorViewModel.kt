package com.example.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.SavedSignatureEntity
import com.example.data.model.AnnotationTool
import com.example.data.model.EditorWorkspaceMode
import com.example.data.model.MarkupKind
import com.example.data.model.PageOverlayItem
import com.example.data.model.PdfDocumentUi
import com.example.data.model.PdfTextBlock
import com.example.data.model.ShapeKind
import com.example.data.repository.PdfLibraryRepository
import com.example.engine.PdfContentEngine
import com.example.engine.PdfRenderEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class EditorUiState(
    val document: PdfDocumentUi? = null,
    val pageCount: Int = 0,
    val currentPageIndex: Int = 0,
    val pageBitmap: Bitmap? = null,
    val thumbnails: List<Bitmap> = emptyList(),
    val workspaceMode: EditorWorkspaceMode = EditorWorkspaceMode.CONTENT_EDIT,
    val activeAnnotationTool: AnnotationTool = AnnotationTool.HIGHLIGHT,
    val activeColorArgb: Int = 0xFFFFD54F.toInt(),
    val strokeWidthNorm: Float = 0.005f,
    val textBlocks: List<PdfTextBlock> = emptyList(),
    val pendingOverlays: List<PageOverlayItem> = emptyList(),
    val isPlacingPermanentText: Boolean = false,
    val selectedTextBlock: PdfTextBlock? = null,
    val isBusy: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val statusMessage: String? = null
)

class PdfEditorViewModel(
    private val repository: PdfLibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    val libraryDocuments: StateFlow<List<PdfDocumentUi>> = repository.documentsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedSignatures: StateFlow<List<SavedSignatureEntity>> = repository.signaturesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val undoFiles = ArrayDeque<File>()
    private val redoFiles = ArrayDeque<File>()

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun openDocument(docId: String?) {
        if (docId == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val doc = repository.getDocumentById(docId)
            if (doc != null && File(doc.filePath).exists()) {
                clearHistoryStacks()
                _uiState.update {
                    it.copy(
                        document = doc,
                        currentPageIndex = 0,
                        pendingOverlays = emptyList(),
                        selectedTextBlock = null,
                        isPlacingPermanentText = false
                    )
                }
                reloadCurrentDocumentState(loadThumbs = true)
            } else {
                _uiState.update { it.copy(isBusy = false, statusMessage = "Document not found") }
            }
        }
    }

    fun importAndOpenPdf(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val doc = repository.importPdfUri(uri)
            if (doc != null) {
                openDocument(doc.id)
            } else {
                _uiState.update { it.copy(isBusy = false, statusMessage = "Unable to open PDF") }
            }
        }
    }

    fun setWorkspaceMode(mode: EditorWorkspaceMode) {
        _uiState.update {
            it.copy(
                workspaceMode = mode,
                selectedTextBlock = null,
                isPlacingPermanentText = false
            )
        }
        if (mode == EditorWorkspaceMode.PAGES) {
            refreshThumbnails()
        }
    }

    fun setAnnotationTool(tool: AnnotationTool) {
        val defaultColor = when (tool) {
            AnnotationTool.HIGHLIGHT -> 0xFFFFD54F.toInt()
            AnnotationTool.UNDERLINE, AnnotationTool.SHAPE_ARROW -> 0xFF2B59C3.toInt()
            AnnotationTool.STRIKETHROUGH -> 0xFFD93036.toInt()
            AnnotationTool.COMMENT -> 0xFFF59E0B.toInt()
            else -> _uiState.value.activeColorArgb
        }
        _uiState.update {
            it.copy(
                activeAnnotationTool = tool,
                activeColorArgb = defaultColor
            )
        }
    }

    fun setColor(argb: Int) {
        _uiState.update { it.copy(activeColorArgb = argb) }
    }

    fun setStrokeWidth(norm: Float) {
        _uiState.update { it.copy(strokeWidthNorm = norm) }
    }

    fun setPlacingPermanentText(enabled: Boolean) {
        _uiState.update { it.copy(isPlacingPermanentText = enabled) }
    }

    fun selectTextBlock(block: PdfTextBlock?) {
        _uiState.update { it.copy(selectedTextBlock = block) }
    }

    fun goToPage(index: Int) {
        val count = _uiState.value.pageCount
        if (index !in 0 until count || index == _uiState.value.currentPageIndex) return
        viewModelScope.launch {
            if (_uiState.value.pendingOverlays.isNotEmpty()) {
                commitPendingOverlaysInternal()
            }
            _uiState.update { it.copy(currentPageIndex = index, selectedTextBlock = null) }
            reloadCurrentDocumentState(loadThumbs = false)
        }
    }

    // -------------------------------------------------------------------------
    // GENUINE PDF CONTENT STREAM EDITING
    // -------------------------------------------------------------------------
    fun editTextBlockInStream(block: PdfTextBlock, newText: String) {
        val doc = _uiState.value.document ?: return
        val file = File(doc.filePath)
        val pageIndex = _uiState.value.currentPageIndex
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, selectedTextBlock = null) }
            pushUndoSnapshot(file)
            val ok = PdfContentEngine.editExistingTextInStream(file, pageIndex, block, newText)
            if (ok) {
                repository.refreshDocumentMetadata(doc.id)
                reloadCurrentDocumentState(loadThumbs = false)
                _uiState.update {
                    it.copy(
                        statusMessage = if (newText.isBlank()) "Deleted text from PDF stream" else "Updated PDF content stream"
                    )
                }
            } else {
                _uiState.update { it.copy(isBusy = false, statusMessage = "Could not edit stream text") }
            }
        }
    }

    fun insertPermanentTextInStream(text: String, normX: Float, normY: Float, fontSize: Float = 13f) {
        val doc = _uiState.value.document ?: return
        val file = File(doc.filePath)
        val pageIndex = _uiState.value.currentPageIndex
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, isPlacingPermanentText = false) }
            pushUndoSnapshot(file)
            val ok = PdfContentEngine.insertPermanentText(
                pdfFile = file,
                pageIndex = pageIndex,
                text = text,
                normX = normX,
                normY = normY,
                fontSizePt = fontSize,
                colorArgb = _uiState.value.activeColorArgb
            )
            if (ok) {
                repository.refreshDocumentMetadata(doc.id)
                reloadCurrentDocumentState(loadThumbs = false)
                _uiState.update { it.copy(statusMessage = "Inserted permanent text into PDF stream") }
            } else {
                _uiState.update { it.copy(isBusy = false, statusMessage = "Failed to insert text") }
            }
        }
    }

    fun insertImageIntoStream(context: Context, imageUri: Uri) {
        val doc = _uiState.value.document ?: return
        val file = File(doc.filePath)
        val pageIndex = _uiState.value.currentPageIndex
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val bmp = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it) }
            }
            if (bmp == null) {
                _uiState.update { it.copy(isBusy = false, statusMessage = "Could not read image") }
                return@launch
            }
            pushUndoSnapshot(file)
            val aspect = bmp.height.toFloat() / bmp.width.coerceAtLeast(1).toFloat()
            val wNorm = 0.42f
            val hNorm = (wNorm * aspect * 0.72f).coerceIn(0.10f, 0.55f)
            val left = 0.29f
            val top = 0.30f
            val ok = PdfContentEngine.insertPermanentImage(
                pdfFile = file,
                pageIndex = pageIndex,
                bitmap = bmp,
                normLeft = left,
                normTop = top,
                normRight = left + wNorm,
                normBottom = top + hNorm
            )
            bmp.recycle()
            if (ok) {
                repository.refreshDocumentMetadata(doc.id)
                reloadCurrentDocumentState(loadThumbs = false)
                _uiState.update { it.copy(statusMessage = "Embedded image into PDF page") }
            } else {
                _uiState.update { it.copy(isBusy = false, statusMessage = "Failed to embed image") }
            }
        }
    }

    // -------------------------------------------------------------------------
    // ANNOTATIONS, MARKUP, DRAWINGS & SIGNATURES (OVERLAYS)
    // -------------------------------------------------------------------------
    fun addOverlay(item: PageOverlayItem) {
        _uiState.update { it.copy(pendingOverlays = it.pendingOverlays + item) }
    }

    fun undoLastOverlay() {
        val current = _uiState.value.pendingOverlays
        if (current.isNotEmpty()) {
            _uiState.update { it.copy(pendingOverlays = current.dropLast(1)) }
        } else {
            undoPdfFile()
        }
    }

    fun commitPendingOverlays() {
        viewModelScope.launch {
            if (_uiState.value.pendingOverlays.isEmpty()) return@launch
            _uiState.update { it.copy(isBusy = true) }
            val ok = commitPendingOverlaysInternal()
            if (ok) {
                reloadCurrentDocumentState(loadThumbs = false)
                _uiState.update { it.copy(statusMessage = "Saved annotations to PDF") }
            } else {
                _uiState.update { it.copy(isBusy = false, statusMessage = "Failed to save annotations") }
            }
        }
    }

    private suspend fun commitPendingOverlaysInternal(): Boolean {
        val doc = _uiState.value.document ?: return false
        val overlays = _uiState.value.pendingOverlays
        if (overlays.isEmpty()) return true
        val file = File(doc.filePath)
        pushUndoSnapshot(file)
        val ok = PdfContentEngine.commitAnnotationsAndOverlays(file, _uiState.value.currentPageIndex, overlays)
        if (ok) {
            _uiState.update { it.copy(pendingOverlays = emptyList()) }
            repository.refreshDocumentMetadata(doc.id)
        }
        return ok
    }

    fun saveSignatureToLibrary(label: String, bitmap: Bitmap, onSaved: (SavedSignatureEntity) -> Unit) {
        viewModelScope.launch {
            val sigFile = File(repository.signatureDir, "sig_${UUID.randomUUID()}.png")
            withContext(Dispatchers.IO) {
                sigFile.parentFile?.mkdirs()
                sigFile.outputStream().use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
            }
            val entity = repository.saveSignature(label, sigFile)
            onSaved(entity)
        }
    }

    fun deleteSavedSignature(entity: SavedSignatureEntity) {
        viewModelScope.launch {
            repository.deleteSignature(entity.id, entity.imagePath)
        }
    }

    fun placeSignatureOnPage(signature: SavedSignatureEntity) {
        addOverlay(
            PageOverlayItem.SignaturePlacement(
                id = UUID.randomUUID().toString(),
                imagePath = signature.imagePath,
                normLeft = 0.25f,
                normTop = 0.65f,
                normRight = 0.72f,
                normBottom = 0.80f
            )
        )
        _uiState.update { it.copy(statusMessage = "Signature placed — tap Save Annotations to commit") }
    }

    // -------------------------------------------------------------------------
    // PAGE STRUCTURE OPERATIONS (ROTATE, REORDER, DUPLICATE, DELETE, MERGE, SPLIT, EXTRACT)
    // -------------------------------------------------------------------------
    fun rotatePage(pageIndex: Int) {
        val doc = _uiState.value.document ?: return
        val file = File(doc.filePath)
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            pushUndoSnapshot(file)
            if (PdfContentEngine.rotatePage(file, pageIndex, 90)) {
                repository.refreshDocumentMetadata(doc.id)
                reloadCurrentDocumentState(loadThumbs = true)
            } else {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun duplicatePage(pageIndex: Int) {
        val doc = _uiState.value.document ?: return
        val file = File(doc.filePath)
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            pushUndoSnapshot(file)
            if (PdfContentEngine.duplicatePage(file, pageIndex)) {
                repository.refreshDocumentMetadata(doc.id)
                reloadCurrentDocumentState(loadThumbs = true)
                _uiState.update { it.copy(statusMessage = "Duplicated page ${pageIndex + 1}") }
            } else {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun deletePage(pageIndex: Int) {
        val doc = _uiState.value.document ?: return
        if (_uiState.value.pageCount <= 1) {
            _uiState.update { it.copy(statusMessage = "Cannot delete the only remaining page") }
            return
        }
        val file = File(doc.filePath)
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            pushUndoSnapshot(file)
            if (PdfContentEngine.deletePage(file, pageIndex)) {
                repository.refreshDocumentMetadata(doc.id)
                val nextIdx = _uiState.value.currentPageIndex.coerceAtMost(_uiState.value.pageCount - 2).coerceAtLeast(0)
                _uiState.update { it.copy(currentPageIndex = nextIdx) }
                reloadCurrentDocumentState(loadThumbs = true)
                _uiState.update { it.copy(statusMessage = "Deleted page ${pageIndex + 1}") }
            } else {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun reorderPages(newOrder: List<Int>) {
        val doc = _uiState.value.document ?: return
        val file = File(doc.filePath)
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            pushUndoSnapshot(file)
            if (PdfContentEngine.reorderPages(file, newOrder)) {
                repository.refreshDocumentMetadata(doc.id)
                reloadCurrentDocumentState(loadThumbs = true)
                _uiState.update { it.copy(statusMessage = "Reordered PDF pages") }
            } else {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun mergeAnotherDocument(otherDoc: PdfDocumentUi) {
        val currentDoc = _uiState.value.document ?: return
        val currentFile = File(currentDoc.filePath)
        val otherFile = File(otherDoc.filePath)
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            pushUndoSnapshot(currentFile)
            val tmpOut = File(repository.pdfDir, "merge_tmp_${UUID.randomUUID()}.pdf")
            val ok = PdfContentEngine.mergePdfs(listOf(currentFile, otherFile), tmpOut)
            if (ok) {
                tmpOut.copyTo(currentFile, overwrite = true)
                tmpOut.delete()
                repository.refreshDocumentMetadata(currentDoc.id)
                reloadCurrentDocumentState(loadThumbs = true)
                _uiState.update { it.copy(statusMessage = "Merged \"${otherDoc.title}\" into document") }
            } else {
                _uiState.update { it.copy(isBusy = false, statusMessage = "Merge failed") }
            }
        }
    }

    fun extractSelectedPages(selectedIndices: List<Int>, newTitle: String, onCreated: (PdfDocumentUi) -> Unit) {
        val currentDoc = _uiState.value.document ?: return
        if (selectedIndices.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val newId = UUID.randomUUID().toString()
            val outFile = File(repository.pdfDir, "$newId.pdf")
            val ok = PdfContentEngine.extractPages(File(currentDoc.filePath), selectedIndices.sorted(), outFile)
            val created = if (ok) {
                repository.registerExistingPdf(outFile, newTitle.ifBlank { "${currentDoc.title} (Extract)" }, "Extracted", newId)
            } else null
            _uiState.update {
                it.copy(
                    isBusy = false,
                    statusMessage = if (created != null) "Extracted ${selectedIndices.size} pages" else "Extract failed"
                )
            }
            if (created != null) onCreated(created)
        }
    }

    fun splitDocumentByRange(rangeSpec: String) {
        val currentDoc = _uiState.value.document ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val files = PdfContentEngine.splitPdfByRanges(
                inputFile = File(currentDoc.filePath),
                rangeSpec = rangeSpec,
                outputDir = repository.exportDir,
                baseTitle = currentDoc.title
            )
            files.forEachIndexed { idx, file ->
                repository.registerExistingPdf(file, "${currentDoc.title} - Part ${idx + 1}", "Split")
            }
            _uiState.update {
                it.copy(
                    isBusy = false,
                    statusMessage = if (files.isNotEmpty()) "Created ${files.size} split PDF(s) in Library" else "Invalid page range"
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // UNDO / REDO & EXPORT
    // -------------------------------------------------------------------------
    private fun pushUndoSnapshot(file: File) {
        val snap = File(repository.exportDir, "undo_${UUID.randomUUID()}.pdf")
        file.copyTo(snap, overwrite = true)
        undoFiles.addLast(snap)
        if (undoFiles.size > 12) {
            undoFiles.removeFirst().delete()
        }
        while (redoFiles.isNotEmpty()) {
            redoFiles.removeLast().delete()
        }
    }

    fun undoPdfFile() {
        if (_uiState.value.pendingOverlays.isNotEmpty()) {
            undoLastOverlay()
            return
        }
        val doc = _uiState.value.document ?: return
        val snap = undoFiles.removeLastOrNull() ?: return
        val currentFile = File(doc.filePath)
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val redoSnap = File(repository.exportDir, "redo_${UUID.randomUUID()}.pdf")
            currentFile.copyTo(redoSnap, overwrite = true)
            redoFiles.addLast(redoSnap)
            snap.copyTo(currentFile, overwrite = true)
            snap.delete()
            repository.refreshDocumentMetadata(doc.id)
            reloadCurrentDocumentState(loadThumbs = true)
            _uiState.update { it.copy(statusMessage = "Undid last PDF edit") }
        }
    }

    fun redoPdfFile() {
        val doc = _uiState.value.document ?: return
        val snap = redoFiles.removeLastOrNull() ?: return
        val currentFile = File(doc.filePath)
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val undoSnap = File(repository.exportDir, "undo_${UUID.randomUUID()}.pdf")
            currentFile.copyTo(undoSnap, overwrite = true)
            undoFiles.addLast(undoSnap)
            snap.copyTo(currentFile, overwrite = true)
            snap.delete()
            repository.refreshDocumentMetadata(doc.id)
            reloadCurrentDocumentState(loadThumbs = true)
            _uiState.update { it.copy(statusMessage = "Redid PDF edit") }
        }
    }

    fun exportToUri(context: Context, destUri: Uri) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (_uiState.value.pendingOverlays.isNotEmpty()) {
                commitPendingOverlaysInternal()
            }
            val ok = runCatching {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    File(doc.filePath).inputStream().use { input -> input.copyTo(out) }
                }
                true
            }.getOrDefault(false)
            _uiState.update {
                it.copy(statusMessage = if (ok) "Exported PDF to device storage" else "Export failed")
            }
        }
    }

    private suspend fun reloadCurrentDocumentState(loadThumbs: Boolean) {
        val doc = _uiState.value.document ?: return
        val refreshedDoc = repository.getDocumentById(doc.id) ?: doc
        val file = File(refreshedDoc.filePath)
        val total = PdfRenderEngine.getPageCount(file).coerceAtLeast(1)
        val safeIdx = _uiState.value.currentPageIndex.coerceIn(0, total - 1)
        val pageBmp = PdfRenderEngine.renderPage(file, safeIdx, targetWidthPx = 1280)
        val blocks = PdfContentEngine.extractTextBlocks(file, safeIdx)
        val thumbs = if (loadThumbs) PdfRenderEngine.renderAllThumbnails(file, 260) else _uiState.value.thumbnails

        _uiState.update {
            it.copy(
                document = refreshedDoc,
                pageCount = total,
                currentPageIndex = safeIdx,
                pageBitmap = pageBmp,
                textBlocks = blocks,
                thumbnails = thumbs,
                isBusy = false,
                canUndo = undoFiles.isNotEmpty() || it.pendingOverlays.isNotEmpty(),
                canRedo = redoFiles.isNotEmpty()
            )
        }
    }

    private fun refreshThumbnails() {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            val thumbs = PdfRenderEngine.renderAllThumbnails(File(doc.filePath), 260)
            _uiState.update { it.copy(thumbnails = thumbs) }
        }
    }

    private fun clearHistoryStacks() {
        while (undoFiles.isNotEmpty()) undoFiles.removeFirst().delete()
        while (redoFiles.isNotEmpty()) redoFiles.removeFirst().delete()
    }

    override fun onCleared() {
        super.onCleared()
        clearHistoryStacks()
    }

    companion object {
        fun factory(repository: PdfLibraryRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PdfEditorViewModel(repository) as T
                }
            }
    }
}
