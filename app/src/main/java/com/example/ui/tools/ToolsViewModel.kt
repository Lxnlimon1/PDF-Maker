package com.example.ui.tools

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.CompressionLevel
import com.example.data.model.ConversionToolType
import com.example.data.model.ImageExportFormat
import com.example.data.model.PageSizeOption
import com.example.data.model.PdfDocumentUi
import com.example.data.repository.PdfLibraryRepository
import com.example.engine.PdfContentEngine
import com.example.engine.PdfConversionEngine
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

sealed class ToolResult {
    data class CreatedPdf(val doc: PdfDocumentUi, val note: String? = null) : ToolResult()
    data class CreatedMultiplePdfs(val docs: List<PdfDocumentUi>) : ToolResult()
    data class CreatedExternalFile(val file: File, val mimeType: String, val displayName: String) : ToolResult()
    data class CreatedImages(val files: List<File>, val mimeType: String) : ToolResult()
}

data class ToolsUiState(
    val isProcessing: Boolean = false,
    val result: ToolResult? = null,
    val errorMessage: String? = null
)

class ToolsViewModel(
    private val repository: PdfLibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    val libraryDocuments: StateFlow<List<PdfDocumentUi>> = repository.documentsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun resetResult() {
        _uiState.update { ToolsUiState() }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun convertImagesToPdf(context: Context, uris: List<Uri>, pageSize: PageSizeOption, title: String) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null, result = null) }
            val id = UUID.randomUUID().toString()
            val outFile = File(repository.pdfDir, "$id.pdf")
            val ok = PdfConversionEngine.imagesToPdf(context, uris, pageSize, outFile)
            val doc = if (ok) {
                repository.registerExistingPdf(outFile, title.ifBlank { "Images PDF" }, "Converted", id)
            } else null
            _uiState.update {
                if (doc != null) ToolsUiState(result = ToolResult.CreatedPdf(doc, "Converted ${uris.size} image(s)"))
                else ToolsUiState(errorMessage = "Could not convert selected images")
            }
        }
    }

    fun convertTextToPdf(title: String, body: String, fontSize: Float) {
        if (body.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null, result = null) }
            val id = UUID.randomUUID().toString()
            val outFile = File(repository.pdfDir, "$id.pdf")
            val ok = PdfConversionEngine.textToPdf(title, body, fontSize, outFile)
            val doc = if (ok) {
                repository.registerExistingPdf(outFile, title.ifBlank { "Text Document" }, "Converted", id)
            } else null
            _uiState.update {
                if (doc != null) ToolsUiState(result = ToolResult.CreatedPdf(doc))
                else ToolsUiState(errorMessage = "Failed to generate PDF from text")
            }
        }
    }

    fun convertHtmlToPdf(title: String, html: String) {
        if (html.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null, result = null) }
            val id = UUID.randomUUID().toString()
            val outFile = File(repository.pdfDir, "$id.pdf")
            val ok = PdfConversionEngine.htmlToPdf(html, outFile)
            val doc = if (ok) {
                repository.registerExistingPdf(outFile, title.ifBlank { "HTML Document" }, "HTML", id)
            } else null
            _uiState.update {
                if (doc != null) ToolsUiState(result = ToolResult.CreatedPdf(doc))
                else ToolsUiState(errorMessage = "Failed to convert HTML to PDF")
            }
        }
    }

    fun convertOfficeUriToPdf(context: Context, uri: Uri, tool: ConversionToolType) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null, result = null) }
            val rawName = repository.resolveDisplayName(uri) ?: "Document"
            val baseTitle = rawName.substringBeforeLast('.')
            val id = UUID.randomUUID().toString()
            val outFile = File(repository.pdfDir, "$id.pdf")

            val ok = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    when (tool) {
                        ConversionToolType.WORD_TO_PDF -> PdfConversionEngine.docxToPdf(stream, outFile)
                        ConversionToolType.EXCEL_TO_PDF -> {
                            val isCsv = rawName.endsWith(".csv", ignoreCase = true)
                            PdfConversionEngine.spreadsheetToPdf(stream, isCsv, outFile)
                        }
                        ConversionToolType.PPT_TO_PDF -> PdfConversionEngine.pptxToPdf(stream, outFile)
                        else -> false
                    }
                } ?: false
            }

            val doc = if (ok) {
                repository.registerExistingPdf(outFile, baseTitle, "Office", id)
            } else null

            _uiState.update {
                if (doc != null) ToolsUiState(result = ToolResult.CreatedPdf(doc, "Converted $rawName to PDF"))
                else ToolsUiState(errorMessage = "Please select a valid OOXML file (.docx / .xlsx / .csv / .pptx)")
            }
        }
    }

    fun convertPdfToImages(doc: PdfDocumentUi, format: ImageExportFormat, highRes: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null, result = null) }
            val subDir = File(repository.exportDir, "imgs_${System.currentTimeMillis()}")
            val files = PdfConversionEngine.pdfToImages(
                pdfFile = File(doc.filePath),
                format = format,
                widthPx = if (highRes) 2000 else 1200,
                outputDir = subDir,
                baseName = doc.title.replace(Regex("[^A-Za-z0-9_-]"), "_")
            )
            _uiState.update {
                if (files.isNotEmpty()) ToolsUiState(result = ToolResult.CreatedImages(files, format.mimeType))
                else ToolsUiState(errorMessage = "Failed to export PDF pages as images")
            }
        }
    }

    fun convertPdfToOffice(doc: PdfDocumentUi, tool: ConversionToolType) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null, result = null) }
            val safeName = doc.title.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val (ext, mime) = when (tool) {
                ConversionToolType.PDF_TO_WORD -> "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                ConversionToolType.PDF_TO_EXCEL -> "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ConversionToolType.PDF_TO_PPT -> "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                else -> return@launch
            }
            val outFile = File(repository.exportDir, "${safeName}_${System.currentTimeMillis()}.$ext")
            val pdfFile = File(doc.filePath)
            val ok = when (tool) {
                ConversionToolType.PDF_TO_WORD -> PdfConversionEngine.pdfToDocx(pdfFile, outFile)
                ConversionToolType.PDF_TO_EXCEL -> PdfConversionEngine.pdfToXlsx(pdfFile, outFile)
                ConversionToolType.PDF_TO_PPT -> PdfConversionEngine.pdfToPptx(pdfFile, outFile)
                else -> false
            }
            _uiState.update {
                if (ok) ToolsUiState(result = ToolResult.CreatedExternalFile(outFile, mime, "${doc.title}.$ext"))
                else ToolsUiState(errorMessage = "Conversion to .$ext failed")
            }
        }
    }

    fun compressPdf(doc: PdfDocumentUi, level: CompressionLevel) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null, result = null) }
            val id = UUID.randomUUID().toString()
            val outFile = File(repository.pdfDir, "$id.pdf")
            val stats = PdfContentEngine.compressPdf(File(doc.filePath), outFile, level)
            val savedDoc = if (stats != null) {
                repository.registerExistingPdf(outFile, "${doc.title} (Compressed)", "Compressed", id)
            } else null
            _uiState.update {
                if (savedDoc != null && stats != null) {
                    val (before, after) = stats
                    val pct = if (before > 0) ((before - after).coerceAtLeast(0) * 100 / before) else 0
                    ToolsUiState(
                        result = ToolResult.CreatedPdf(
                            savedDoc,
                            "Reduced by $pct% (${before / 1024} KB -> ${after / 1024} KB)"
                        )
                    )
                } else {
                    ToolsUiState(errorMessage = "Compression failed")
                }
            }
        }
    }

    fun mergePdfs(docs: List<PdfDocumentUi>, outputTitle: String) {
        if (docs.size < 2) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null, result = null) }
            val id = UUID.randomUUID().toString()
            val outFile = File(repository.pdfDir, "$id.pdf")
            val ok = PdfContentEngine.mergePdfs(docs.map { File(it.filePath) }, outFile)
            val merged = if (ok) {
                repository.registerExistingPdf(outFile, outputTitle.ifBlank { "Merged PDF" }, "Merged", id)
            } else null
            _uiState.update {
                if (merged != null) ToolsUiState(result = ToolResult.CreatedPdf(merged, "Combined ${docs.size} PDFs"))
                else ToolsUiState(errorMessage = "Merge failed")
            }
        }
    }

    fun splitPdf(doc: PdfDocumentUi, rangeSpec: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null, result = null) }
            val outFiles = PdfContentEngine.splitPdfByRanges(
                inputFile = File(doc.filePath),
                rangeSpec = rangeSpec,
                outputDir = repository.exportDir,
                baseTitle = doc.title
            )
            val createdDocs = outFiles.mapIndexedNotNull { idx, file ->
                repository.registerExistingPdf(file, "${doc.title} - Part ${idx + 1}", "Split")
            }
            _uiState.update {
                if (createdDocs.isNotEmpty()) ToolsUiState(result = ToolResult.CreatedMultiplePdfs(createdDocs))
                else ToolsUiState(errorMessage = "No valid page ranges found")
            }
        }
    }

    fun importPdfIntoLibrary(uri: Uri, onImported: (PdfDocumentUi) -> Unit) {
        viewModelScope.launch {
            val doc = repository.importPdfUri(uri)
            if (doc != null) onImported(doc)
        }
    }

    fun readTextFromUri(context: Context, uri: Uri, onRead: (String, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val name = repository.resolveDisplayName(uri) ?: "Document"
            val txt = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
            withContext(Dispatchers.Main) {
                onRead(name.substringBeforeLast('.'), txt)
            }
        }
    }

    fun saveExternalFileToUri(context: Context, sourceFile: File, destUri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                }
                true
            }.getOrDefault(false)
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    companion object {
        fun factory(repository: PdfLibraryRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ToolsViewModel(repository) as T
                }
            }
    }
}
