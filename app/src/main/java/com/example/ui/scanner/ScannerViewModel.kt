package com.example.ui.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserPreferences
import com.example.data.model.PageSizeOption
import com.example.data.model.PdfDocumentUi
import com.example.data.model.ScanFilter
import com.example.data.model.ScanPageItem
import com.example.data.repository.PdfLibraryRepository
import com.example.engine.ScannerProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ScannerUiState(
    val pages: List<ScanPageItem> = emptyList(),
    val selectedIndex: Int = 0,
    val liveCorners: List<Offset> = listOf(
        Offset(0.10f, 0.12f),
        Offset(0.90f, 0.12f),
        Offset(0.90f, 0.88f),
        Offset(0.10f, 0.88f)
    ),
    val autoDetectEnabled: Boolean = true,
    val flashEnabled: Boolean = false,
    val isCroppingMode: Boolean = false,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null
) {
    val currentPage: ScanPageItem?
        get() = pages.getOrNull(selectedIndex)
}

class ScannerViewModel(
    private val repository: PdfLibraryRepository,
    private val preferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun updateLiveCorners(corners: List<Offset>) {
        if (corners.size == 4) {
            _uiState.update { it.copy(liveCorners = corners) }
        }
    }

    fun toggleFlash() {
        _uiState.update { it.copy(flashEnabled = !it.flashEnabled) }
    }

    fun toggleAutoDetect() {
        _uiState.update { it.copy(autoDetectEnabled = !it.autoDetectEnabled) }
    }

    fun selectPage(index: Int) {
        _uiState.update {
            it.copy(selectedIndex = index.coerceIn(0, (it.pages.size - 1).coerceAtLeast(0)))
        }
    }

    fun setCroppingMode(enabled: Boolean) {
        _uiState.update { it.copy(isCroppingMode = enabled) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun addCapturedFile(imageFile: File, onAdded: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val page = processSourceImageFile(imageFile)
            _uiState.update { state ->
                if (page != null) {
                    val updated = state.pages + page
                    state.copy(
                        pages = updated,
                        selectedIndex = updated.lastIndex,
                        isProcessing = false,
                        isCroppingMode = true
                    )
                } else {
                    state.copy(isProcessing = false, errorMessage = "Could not decode captured photo")
                }
            }
            if (page != null) onAdded()
        }
    }

    fun importGalleryImages(context: Context, uris: List<Uri>, onImported: () -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val importedPages = mutableListOf<ScanPageItem>()
            for (uri in uris) {
                val tmpFile = File(repository.scanDir, "import_${UUID.randomUUID()}.jpg")
                val copied = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tmpFile.outputStream().use { out -> input.copyTo(out) }
                        }
                        tmpFile.exists() && tmpFile.length() > 0
                    }.getOrDefault(false)
                }
                if (copied) {
                    processSourceImageFile(tmpFile)?.let { importedPages.add(it) }
                }
            }
            _uiState.update { state ->
                val updated = state.pages + importedPages
                state.copy(
                    pages = updated,
                    selectedIndex = (updated.lastIndex).coerceAtLeast(0),
                    isProcessing = false,
                    isCroppingMode = importedPages.size == 1
                )
            }
            if (importedPages.isNotEmpty()) onImported()
        }
    }

    private suspend fun processSourceImageFile(file: File): ScanPageItem? = withContext(Dispatchers.IO) {
        val raw = decodeOrientedBitmap(file, maxDim = 2200) ?: return@withContext null
        val id = UUID.randomUUID().toString()
        val origFile = File(repository.scanDir, "${id}_orig.jpg")
        val croppedFile = File(repository.scanDir, "${id}_crop.jpg")
        val filteredFile = File(repository.scanDir, "${id}_filt.jpg")

        ScannerProcessor.saveBitmapToFile(raw, origFile, 92)
        val corners = if (_uiState.value.autoDetectEnabled) {
            ScannerProcessor.detectDocumentCorners(raw)
        } else {
            listOf(
                Offset(0.06f, 0.06f),
                Offset(0.94f, 0.06f),
                Offset(0.94f, 0.94f),
                Offset(0.06f, 0.94f)
            )
        }
        val cropped = ScannerProcessor.perspectiveCrop(raw, corners, 0)
        ScannerProcessor.saveBitmapToFile(cropped, croppedFile, 92)
        val enhanced = ScannerProcessor.applyScanFilter(cropped, ScanFilter.AUTO_ENHANCE, 0f, 1.05f)
        ScannerProcessor.saveBitmapToFile(enhanced, filteredFile, 92)

        raw.recycle()
        cropped.recycle()
        enhanced.recycle()

        ScanPageItem(
            id = id,
            originalPath = origFile.absolutePath,
            croppedPath = croppedFile.absolutePath,
            filteredPath = filteredFile.absolutePath,
            corners = corners,
            filter = ScanFilter.AUTO_ENHANCE,
            rotationDegrees = 0
        )
    }

    private fun decodeOrientedBitmap(file: File, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDim * 2) {
            sample *= 2
        }
        val bmp = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        val rotation = runCatching {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)

        return if (rotation != 0f) {
            val m = Matrix().apply { postRotate(rotation) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated !== bmp) bmp.recycle()
            rotated
        } else {
            bmp
        }
    }

    fun updateCorner(cornerIndex: Int, newOffset: Offset) {
        val page = _uiState.value.currentPage ?: return
        val updatedCorners = page.corners.toMutableList()
        if (cornerIndex !in updatedCorners.indices) return
        updatedCorners[cornerIndex] = Offset(
            newOffset.x.coerceIn(0.01f, 0.99f),
            newOffset.y.coerceIn(0.01f, 0.99f)
        )
        val updatedPage = page.copy(corners = updatedCorners)
        replaceCurrentPage(updatedPage)
    }

    fun resetAutoCorners() {
        val page = _uiState.value.currentPage ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val detected = withContext(Dispatchers.IO) {
                val orig = BitmapFactory.decodeFile(page.originalPath) ?: return@withContext page.corners
                val c = ScannerProcessor.detectDocumentCorners(orig)
                orig.recycle()
                c
            }
            replaceCurrentPage(page.copy(corners = detected))
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun applyPerspectiveCropAndRotate(deltaRotation: Int = 0) {
        val page = _uiState.value.currentPage ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val newRot = (page.rotationDegrees + deltaRotation + 360) % 360
            withContext(Dispatchers.IO) {
                val orig = BitmapFactory.decodeFile(page.originalPath) ?: return@withContext
                val cropped = ScannerProcessor.perspectiveCrop(orig, page.corners, newRot)
                ScannerProcessor.saveBitmapToFile(cropped, File(page.croppedPath), 92)
                val filtered = ScannerProcessor.applyScanFilter(
                    cropped,
                    page.filter,
                    page.brightness,
                    page.contrast
                )
                ScannerProcessor.saveBitmapToFile(filtered, File(page.filteredPath), 92)
                orig.recycle()
                cropped.recycle()
                filtered.recycle()
            }
            replaceCurrentPage(page.copy(rotationDegrees = newRot))
            _uiState.update { it.copy(isProcessing = false, isCroppingMode = false) }
        }
    }

    fun updateFilterAndAdjustments(
        filter: ScanFilter? = null,
        brightness: Float? = null,
        contrast: Float? = null
    ) {
        val page = _uiState.value.currentPage ?: return
        val targetFilter = filter ?: page.filter
        val targetBright = brightness ?: page.brightness
        val targetContrast = contrast ?: page.contrast
        val updated = page.copy(
            filter = targetFilter,
            brightness = targetBright,
            contrast = targetContrast
        )
        replaceCurrentPage(updated)
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            withContext(Dispatchers.IO) {
                val cropped = BitmapFactory.decodeFile(updated.croppedPath) ?: return@withContext
                val filtered = ScannerProcessor.applyScanFilter(
                    cropped,
                    targetFilter,
                    targetBright,
                    targetContrast
                )
                ScannerProcessor.saveBitmapToFile(filtered, File(updated.filteredPath), 92)
                cropped.recycle()
                filtered.recycle()
            }
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value.pages
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val mutable = current.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        _uiState.update { it.copy(pages = mutable, selectedIndex = toIndex) }
    }

    fun deleteCurrentPage() {
        val state = _uiState.value
        val idx = state.selectedIndex
        if (idx !in state.pages.indices) return
        val mutable = state.pages.toMutableList()
        mutable.removeAt(idx)
        _uiState.update {
            it.copy(
                pages = mutable,
                selectedIndex = (idx - 1).coerceAtLeast(0).coerceAtMost((mutable.size - 1).coerceAtLeast(0))
            )
        }
    }

    fun saveScanAsPdf(
        title: String,
        pageSize: PageSizeOption,
        highResolution: Boolean,
        onSaved: (PdfDocumentUi) -> Unit
    ) {
        val pages = _uiState.value.pages
        if (pages.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val docId = UUID.randomUUID().toString()
            val outFile = File(repository.pdfDir, "$docId.pdf")
            val ok = ScannerProcessor.exportScansToPdf(pages, pageSize, highResolution, outFile)
            val savedDoc = if (ok) {
                repository.registerExistingPdf(
                    file = outFile,
                    title = title.ifBlank { "Scanned Document" },
                    sourceTag = "Scanned",
                    id = docId
                )
            } else {
                null
            }
            _uiState.update { state ->
                if (savedDoc != null) {
                    ScannerUiState() // reset session after saving
                } else {
                    state.copy(isProcessing = false, errorMessage = "Failed to generate PDF from scans")
                }
            }
            if (savedDoc != null) onSaved(savedDoc)
        }
    }

    suspend fun getDefaultPreferences(): Pair<PageSizeOption, Boolean> {
        return preferences.defaultPageSize.first() to preferences.highResScan.first()
    }

    private fun replaceCurrentPage(updated: ScanPageItem) {
        _uiState.update { state ->
            val list = state.pages.toMutableList()
            if (state.selectedIndex in list.indices) {
                list[state.selectedIndex] = updated
            }
            state.copy(pages = list)
        }
    }

    companion object {
        fun factory(
            repository: PdfLibraryRepository,
            preferences: UserPreferences
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ScannerViewModel(repository, preferences) as T
            }
        }
    }
}
