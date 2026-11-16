package com.example.ui.tools

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.CompressionLevel
import com.example.data.model.ConversionToolType
import com.example.data.model.ImageExportFormat
import com.example.data.model.PageSizeOption
import com.example.data.model.PdfDocumentUi
import com.example.ui.components.formatFileSize
import com.example.ui.components.shareFile
import com.example.ui.components.shareMultipleFiles
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolRunnerScreen(
    tool: ConversionToolType,
    preselectedDocId: String?,
    viewModel: ToolsViewModel,
    onBack: () -> Unit,
    onOpenInEditor: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val libraryDocs by viewModel.libraryDocuments.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler {
        viewModel.resetResult()
        onBack()
    }

    LaunchedEffect(tool, preselectedDocId) {
        viewModel.resetResult()
    }

    LaunchedEffect(state.errorMessage) {
        val err = state.errorMessage
        if (err != null) {
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }

    // Tool-specific inputs
    val selectedImages = remember { mutableStateListOf<Uri>() }
    var pageSize by remember { mutableStateOf(PageSizeOption.A4) }
    var docTitle by remember { mutableStateOf(tool.title) }

    var textBody by remember { mutableStateOf("Executive Notes\n\nWrite or paste plain text here, or import a .txt file to generate a clean multi-page PDF.") }
    var textFontSize by remember { mutableFloatStateOf(11.5f) }

    var htmlContent by remember {
        mutableStateOf(
            """<h1>Quarterly Studio Report</h1>
<p>Prepared with <b>PDF Master</b> native HTML-to-PDF engine.</p>
<h2>Highlights</h2>
<ul>
  <li>High-resolution document scanning and auto perspective crop</li>
  <li>Genuine PDF content stream text editing</li>
  <li>Multi-format conversion across Word, Excel, and PowerPoint</li>
</ul>
<p>Generated locally on Android with zero cloud uploads.</p>""".trimIndent()
        )
    }

    var selectedPdf by remember(libraryDocs, preselectedDocId) {
        mutableStateOf(
            libraryDocs.firstOrNull { it.id == preselectedDocId } ?: libraryDocs.firstOrNull()
        )
    }
    val mergeQueue = remember { mutableStateListOf<PdfDocumentUi>() }
    var compressionLevel by remember { mutableStateOf(CompressionLevel.BALANCED) }
    var imageFormat by remember { mutableStateOf(ImageExportFormat.JPG) }
    var highResImages by remember { mutableStateOf(true) }
    var splitRange by remember { mutableStateOf("1-1") }
    var pendingSaveExternal by remember { mutableStateOf<ToolResult.CreatedExternalFile?>(null) }

    val multiImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) selectedImages.addAll(uris)
    }

    val importPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importPdfIntoLibrary(it) { imported ->
                if (tool == ConversionToolType.MERGE_PDF) {
                    mergeQueue.add(imported)
                } else {
                    selectedPdf = imported
                }
            }
        }
    }

    val officeFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.convertOfficeUriToPdf(context, it, tool) }
    }

    val textFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.readTextFromUri(context, it) { name, content ->
                docTitle = name
                if (tool == ConversionToolType.HTML_TO_PDF) htmlContent = content else textBody = content
            }
        }
    }

    val createExternalDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val ext = pendingSaveExternal
        if (uri != null && ext != null) {
            viewModel.saveExternalFileToUri(context, ext.file, uri) { ok ->
                // handled silently or via UI
            }
        }
        pendingSaveExternal = null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetResult()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to tools")
                    }
                },
                title = { Text(tool.title, style = MaterialTheme.typography.titleLarge) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (tool) {
                ConversionToolType.IMAGE_TO_PDF -> {
                    item {
                        OutlinedTextField(
                            value = docTitle,
                            onValueChange = { docTitle = it },
                            label = { Text("Output PDF Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PageSizeOption.entries.forEach { size ->
                                FilterChip(
                                    selected = pageSize == size,
                                    onClick = { pageSize = size },
                                    label = { Text(size.label.substringBefore(" ")) }
                                )
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                multiImagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Select JPG / PNG / WEBP Images (${selectedImages.size})")
                        }
                    }
                    if (selectedImages.isNotEmpty()) {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(selectedImages) { idx, uri ->
                                    Box(modifier = Modifier.size(84.dp, 110.dp)) {
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = "Selected image ${idx + 1}",
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        IconButton(
                                            onClick = { selectedImages.removeAt(idx) },
                                            modifier = Modifier.align(Alignment.TopEnd).size(26.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove image", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = { viewModel.convertImagesToPdf(context, selectedImages.toList(), pageSize, docTitle) },
                            enabled = selectedImages.isNotEmpty() && !state.isProcessing,
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("run_convert_button")
                        ) {
                            Text("Create PDF from ${selectedImages.size} Image(s)")
                        }
                    }
                }

                ConversionToolType.WORD_TO_PDF,
                ConversionToolType.EXCEL_TO_PDF,
                ConversionToolType.PPT_TO_PDF -> {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(tool.subtitle, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = when (tool) {
                                        ConversionToolType.WORD_TO_PDF -> "Pick any .docx Word document from your device storage. Headings, paragraphs, and bold sections are paginated cleanly into A4 PDF."
                                        ConversionToolType.EXCEL_TO_PDF -> "Pick any .xlsx spreadsheet or .csv table. Worksheets are formatted into landscape gridded PDF tables."
                                        else -> "Pick any .pptx presentation. Slides are converted into 16:9 widescreen PDF pages."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        val mimes = when (tool) {
                                            ConversionToolType.WORD_TO_PDF -> arrayOf(
                                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                                "*/*"
                                            )
                                            ConversionToolType.EXCEL_TO_PDF -> arrayOf(
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                "text/csv",
                                                "text/comma-separated-values",
                                                "*/*"
                                            )
                                            else -> arrayOf(
                                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                                "*/*"
                                            )
                                        }
                                        officeFileLauncher.launch(mimes)
                                    },
                                    enabled = !state.isProcessing,
                                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("pick_office_file_button")
                                ) {
                                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Choose File & Convert")
                                }
                            }
                        }
                    }
                }

                ConversionToolType.TEXT_TO_PDF -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = docTitle,
                                onValueChange = { docTitle = it },
                                label = { Text("Document Title") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = { textFileLauncher.launch(arrayOf("text/plain", "*/*")) },
                                modifier = Modifier.height(54.dp)
                            ) {
                                Text("Import .txt")
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = textBody,
                            onValueChange = { textBody = it },
                            label = { Text("Document Text") },
                            minLines = 8,
                            maxLines = 16,
                            modifier = Modifier.fillMaxWidth().testTag("text_to_pdf_body")
                        )
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Font Size: ${textFontSize.toInt()} pt", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(12.dp))
                            Slider(
                                value = textFontSize,
                                onValueChange = { textFontSize = it },
                                valueRange = 9f..18f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        Button(
                            onClick = { viewModel.convertTextToPdf(docTitle, textBody, textFontSize) },
                            enabled = textBody.isNotBlank() && !state.isProcessing,
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("run_convert_button")
                        ) {
                            Text("Convert Text to PDF")
                        }
                    }
                }

                ConversionToolType.HTML_TO_PDF -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = docTitle,
                                onValueChange = { docTitle = it },
                                label = { Text("PDF Title") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = { textFileLauncher.launch(arrayOf("text/html", "*/*")) },
                                modifier = Modifier.height(54.dp)
                            ) {
                                Text("Import .html")
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = htmlContent,
                            onValueChange = { htmlContent = it },
                            label = { Text("HTML Markup") },
                            minLines = 9,
                            maxLines = 18,
                            modifier = Modifier.fillMaxWidth().testTag("html_to_pdf_input")
                        )
                    }
                    item {
                        Button(
                            onClick = { viewModel.convertHtmlToPdf(docTitle, htmlContent) },
                            enabled = htmlContent.isNotBlank() && !state.isProcessing,
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("run_convert_button")
                        ) {
                            Text("Render HTML to PDF")
                        }
                    }
                }

                ConversionToolType.PDF_TO_IMAGES,
                ConversionToolType.PDF_TO_WORD,
                ConversionToolType.PDF_TO_EXCEL,
                ConversionToolType.PDF_TO_PPT,
                ConversionToolType.COMPRESS_PDF,
                ConversionToolType.SPLIT_PDF -> {
                    item {
                        PdfSourcePickerSection(
                            libraryDocs = libraryDocs,
                            selectedPdf = selectedPdf,
                            onSelect = { selectedPdf = it },
                            onImportNew = { importPdfLauncher.launch(arrayOf("application/pdf")) }
                        )
                    }

                    if (tool == ConversionToolType.PDF_TO_IMAGES) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ImageExportFormat.entries.forEach { fmt ->
                                    FilterChip(
                                        selected = imageFormat == fmt,
                                        onClick = { imageFormat = fmt },
                                        label = { Text(fmt.label) }
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Text("High Res", style = MaterialTheme.typography.labelMedium)
                                Switch(checked = highResImages, onCheckedChange = { highResImages = it })
                            }
                        }
                    }

                    if (tool == ConversionToolType.COMPRESS_PDF) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Compression Quality", style = MaterialTheme.typography.titleSmall)
                                CompressionLevel.entries.forEach { lvl ->
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = if (compressionLevel == lvl) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            if (compressionLevel == lvl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.small)
                                            .clickable { compressionLevel = lvl }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(lvl.label, style = MaterialTheme.typography.titleSmall)
                                            Text(lvl.description, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (tool == ConversionToolType.SPLIT_PDF) {
                        item {
                            OutlinedTextField(
                                value = splitRange,
                                onValueChange = { splitRange = it },
                                label = { Text("Ranges (e.g. 1-2, 3-4 or \"all\")") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                val doc = selectedPdf ?: return@Button
                                when (tool) {
                                    ConversionToolType.PDF_TO_IMAGES -> viewModel.convertPdfToImages(doc, imageFormat, highResImages)
                                    ConversionToolType.PDF_TO_WORD,
                                    ConversionToolType.PDF_TO_EXCEL,
                                    ConversionToolType.PDF_TO_PPT -> viewModel.convertPdfToOffice(doc, tool)
                                    ConversionToolType.COMPRESS_PDF -> viewModel.compressPdf(doc, compressionLevel)
                                    ConversionToolType.SPLIT_PDF -> viewModel.splitPdf(doc, splitRange)
                                    else -> Unit
                                }
                            },
                            enabled = selectedPdf != null && !state.isProcessing,
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("run_convert_button")
                        ) {
                            Text(
                                when (tool) {
                                    ConversionToolType.COMPRESS_PDF -> "Compress Selected PDF"
                                    ConversionToolType.SPLIT_PDF -> "Split Selected PDF"
                                    else -> "Run ${tool.title}"
                                }
                            )
                        }
                    }
                }

                ConversionToolType.MERGE_PDF -> {
                    item {
                        OutlinedTextField(
                            value = docTitle,
                            onValueChange = { docTitle = it },
                            label = { Text("Merged PDF Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Select 2+ PDFs (${mergeQueue.size} queued)", style = MaterialTheme.typography.titleSmall)
                            OutlinedButton(onClick = { importPdfLauncher.launch(arrayOf("application/pdf")) }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Import")
                            }
                        }
                    }
                    if (mergeQueue.isNotEmpty()) {
                        item {
                            Text("Merge Order", style = MaterialTheme.typography.labelLarge)
                        }
                        itemsIndexed(mergeQueue) { idx, qDoc ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${idx + 1}. ${qDoc.title}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    IconButton(
                                        onClick = {
                                            if (idx > 0) {
                                                val item = mergeQueue.removeAt(idx)
                                                mergeQueue.add(idx - 1, item)
                                            }
                                        },
                                        enabled = idx > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(16.dp)) }
                                    IconButton(
                                        onClick = {
                                            if (idx < mergeQueue.lastIndex) {
                                                val item = mergeQueue.removeAt(idx)
                                                mergeQueue.add(idx + 1, item)
                                            }
                                        },
                                        enabled = idx < mergeQueue.lastIndex,
                                        modifier = Modifier.size(32.dp)
                                    ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(16.dp)) }
                                    IconButton(
                                        onClick = { mergeQueue.removeAt(idx) },
                                        modifier = Modifier.size(32.dp)
                                    ) { Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(16.dp)) }
                                }
                            }
                        }
                    }
                    item {
                        Text("Tap library PDFs to add/remove:", style = MaterialTheme.typography.labelMedium)
                    }
                    items(libraryDocs, key = { it.id }) { item ->
                        val checked = mergeQueue.any { it.id == item.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    if (checked) mergeQueue.removeAll { it.id == item.id } else mergeQueue.add(item)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (it) mergeQueue.add(item) else mergeQueue.removeAll { d -> d.id == item.id }
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall)
                                Text("${item.pageCount} pg • ${formatFileSize(item.fileSizeBytes)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = { viewModel.mergePdfs(mergeQueue.toList(), docTitle) },
                            enabled = mergeQueue.size >= 2 && !state.isProcessing,
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("run_convert_button")
                        ) {
                            Text("Merge ${mergeQueue.size} PDFs")
                        }
                    }
                }
            }

            if (state.isProcessing) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Text("Processing...", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }

            // Result Card
            val res = state.result
            if (res != null) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().testTag("tool_result_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    "Completed",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }

                            when (res) {
                                is ToolResult.CreatedPdf -> {
                                    Text(
                                        "${res.doc.title} (${res.doc.pageCount} pg • ${formatFileSize(res.doc.fileSizeBytes)})",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    res.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { onOpenInEditor(res.doc.id) },
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Open")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                shareFile(context, File(res.doc.filePath), "application/pdf", res.doc.title)
                                            },
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Share")
                                        }
                                    }
                                }

                                is ToolResult.CreatedMultiplePdfs -> {
                                    Text("Created ${res.docs.size} PDF files in your workspace.")
                                    res.docs.forEach { d ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(d.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                            OutlinedButton(onClick = { onOpenInEditor(d.id) }) { Text("Open") }
                                        }
                                    }
                                }

                                is ToolResult.CreatedExternalFile -> {
                                    Text("${res.displayName} (${formatFileSize(res.file.length())})")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                pendingSaveExternal = res
                                                createExternalDocLauncher.launch(res.displayName)
                                            },
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Save")
                                        }
                                        OutlinedButton(
                                            onClick = { shareFile(context, res.file, res.mimeType, res.displayName) },
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Share")
                                        }
                                    }
                                }

                                is ToolResult.CreatedImages -> {
                                    Text("Exported ${res.files.size} page image(s).")
                                    Button(
                                        onClick = { shareMultipleFiles(context, res.files, res.mimeType, "Share Exported Pages") },
                                        modifier = Modifier.fillMaxWidth().height(44.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Share All Images")
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

@Composable
private fun PdfSourcePickerSection(
    libraryDocs: List<PdfDocumentUi>,
    selectedPdf: PdfDocumentUi?,
    onSelect: (PdfDocumentUi) -> Unit,
    onImportNew: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Source PDF", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = onImportNew) {
                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Import PDF")
            }
        }
        if (libraryDocs.isEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Import a PDF from storage or create one from Home first.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(libraryDocs, key = { it.id }) { doc ->
                    FilterChip(
                        selected = selectedPdf?.id == doc.id,
                        onClick = { onSelect(doc) },
                        label = { Text("${doc.title} (${doc.pageCount}p)", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
    }
}
