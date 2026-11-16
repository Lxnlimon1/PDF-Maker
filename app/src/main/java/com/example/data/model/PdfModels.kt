package com.example.data.model

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    AUTO_ENHANCE("Auto Enhance"),
    COLOR_ENHANCE("Color Vivid"),
    GRAYSCALE("Grayscale"),
    BLACK_AND_WHITE("B&W Scan")
}

enum class PageSizeOption(val label: String, val widthPt: Float, val heightPt: Float) {
    A4("A4 (Standard)", 595f, 842f),
    LETTER("US Letter", 612f, 792f),
    FIT_IMAGE("Fit Content", 595f, 842f)
}

enum class CompressionLevel(
    val label: String,
    val description: String,
    val jpegQuality: Int,
    val maxImageDimension: Int
) {
    HIGH_QUALITY("Low Compression", "Best visual clarity, ~20-35% smaller", 85, 1800),
    BALANCED("Recommended", "Sharp text & photos, ~45-65% smaller", 62, 1300),
    MAXIMUM("Maximum", "Smallest file size for email/web", 38, 900)
}

enum class ImageExportFormat(val label: String, val extension: String, val mimeType: String) {
    JPG("JPG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png")
}

enum class EditorWorkspaceMode(val label: String, val badge: String) {
    CONTENT_EDIT("Content Edit", "PDF Stream"),
    ANNOTATE("Annotate & Sign", "Overlay"),
    PAGES("Organize Pages", "Structure")
}

enum class AnnotationTool(val label: String) {
    SELECT("Inspect"),
    HIGHLIGHT("Highlight"),
    UNDERLINE("Underline"),
    STRIKETHROUGH("Strike"),
    DRAW("Freehand"),
    SHAPE_RECT("Rectangle"),
    SHAPE_CIRCLE("Circle"),
    SHAPE_ARROW("Arrow"),
    TEXT_BOX("Text Box"),
    COMMENT("Comment"),
    SIGNATURE("Signature")
}

enum class ConversionToolType(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: ToolCategory
) {
    IMAGE_TO_PDF("image_to_pdf", "Image to PDF", "JPG, PNG, WEBP to PDF", ToolCategory.TO_PDF),
    WORD_TO_PDF("word_to_pdf", "Word to PDF", "Convert .docx documents", ToolCategory.TO_PDF),
    EXCEL_TO_PDF("excel_to_pdf", "Excel to PDF", "Convert .xlsx or .csv sheets", ToolCategory.TO_PDF),
    PPT_TO_PDF("ppt_to_pdf", "PowerPoint to PDF", "Convert .pptx slide decks", ToolCategory.TO_PDF),
    TEXT_TO_PDF("text_to_pdf", "Text to PDF", "Write or import .txt notes", ToolCategory.TO_PDF),
    HTML_TO_PDF("html_to_pdf", "HTML to PDF", "Render HTML markup to PDF", ToolCategory.TO_PDF),

    PDF_TO_IMAGES("pdf_to_images", "PDF to JPG / PNG", "Export pages as crisp images", ToolCategory.FROM_PDF),
    PDF_TO_WORD("pdf_to_word", "PDF to Word", "Extract editable .docx file", ToolCategory.FROM_PDF),
    PDF_TO_EXCEL("pdf_to_excel", "PDF to Excel", "Extract tables to .xlsx", ToolCategory.FROM_PDF),
    PDF_TO_PPT("pdf_to_ppt", "PDF to PowerPoint", "Convert pages to .pptx slides", ToolCategory.FROM_PDF),

    COMPRESS_PDF("compress_pdf", "Compress PDF", "Reduce PDF size at 3 levels", ToolCategory.MANAGE),
    MERGE_PDF("merge_pdf", "Merge PDFs", "Combine multiple PDFs into one", ToolCategory.MANAGE),
    SPLIT_PDF("split_pdf", "Split & Extract", "Split ranges or extract pages", ToolCategory.MANAGE)
}

enum class ToolCategory(val title: String) {
    TO_PDF("Convert to PDF"),
    FROM_PDF("Convert from PDF"),
    MANAGE("Optimize & Organize")
}

data class ScanPageItem(
    val id: String,
    val originalPath: String,
    val croppedPath: String,
    val filteredPath: String,
    val corners: List<Offset> = listOf(
        Offset(0.08f, 0.08f),
        Offset(0.92f, 0.08f),
        Offset(0.92f, 0.92f),
        Offset(0.08f, 0.92f)
    ),
    val filter: ScanFilter = ScanFilter.AUTO_ENHANCE,
    val rotationDegrees: Int = 0,
    val brightness: Float = 0f,
    val contrast: Float = 1.05f
)

data class PdfTextBlock(
    val id: String,
    val text: String,
    val normLeft: Float,
    val normTop: Float,
    val normRight: Float,
    val normBottom: Float,
    val fontSizePt: Float,
    val pdfX: Float,
    val pdfY: Float,
    val pdfWidth: Float,
    val pdfHeight: Float
)

sealed class PageOverlayItem {
    abstract val id: String
    abstract val colorArgb: Int

    data class TextMarkup(
        override val id: String,
        val kind: MarkupKind,
        val normLeft: Float,
        val normTop: Float,
        val normRight: Float,
        val normBottom: Float,
        override val colorArgb: Int
    ) : PageOverlayItem()

    data class InkStroke(
        override val id: String,
        val points: List<Offset>,
        val strokeWidthNorm: Float,
        override val colorArgb: Int
    ) : PageOverlayItem()

    data class ShapeItem(
        override val id: String,
        val kind: ShapeKind,
        val start: Offset,
        val end: Offset,
        val strokeWidthNorm: Float,
        override val colorArgb: Int
    ) : PageOverlayItem()

    data class TextBoxItem(
        override val id: String,
        val text: String,
        val normX: Float,
        val normY: Float,
        val fontSizeSp: Float,
        override val colorArgb: Int
    ) : PageOverlayItem()

    data class CommentPin(
        override val id: String,
        val comment: String,
        val normX: Float,
        val normY: Float,
        override val colorArgb: Int
    ) : PageOverlayItem()

    data class SignaturePlacement(
        override val id: String,
        val imagePath: String,
        val normLeft: Float,
        val normTop: Float,
        val normRight: Float,
        val normBottom: Float,
        override val colorArgb: Int = 0xFF000000.toInt()
    ) : PageOverlayItem()
}

enum class MarkupKind { HIGHLIGHT, UNDERLINE, STRIKETHROUGH }
enum class ShapeKind { RECTANGLE, CIRCLE, ARROW }

@Serializable
data class PdfDocumentUi(
    val id: String,
    val title: String,
    val filePath: String,
    val thumbnailPath: String?,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceTag: String
)
