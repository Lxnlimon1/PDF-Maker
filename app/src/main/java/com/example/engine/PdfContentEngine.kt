package com.example.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.example.data.model.CompressionLevel
import com.example.data.model.MarkupKind
import com.example.data.model.PageOverlayItem
import com.example.data.model.PageSizeOption
import com.example.data.model.PdfTextBlock
import com.example.data.model.ShapeKind
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLine
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object PdfContentEngine {

    private fun sanitizeWinAnsi(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when {
                ch.code in 32..126 -> sb.append(ch)
                ch == '\u2013' || ch == '\u2014' -> sb.append('-')
                ch == '\u2018' || ch == '\u2019' -> sb.append('\'')
                ch == '\u201C' || ch == '\u201D' -> sb.append('"')
                ch == '\u2022' -> sb.append('*')
                ch.code in 160..255 -> sb.append(ch)
                ch == '\n' || ch == '\r' || ch == '\t' -> sb.append(' ')
                else -> sb.append('?')
            }
        }
        return sb.toString()
    }

    private fun argbToRgbComponents(argb: Int): Triple<Int, Int, Int> {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return Triple(r, g, b)
    }

    private fun argbToPdColor(argb: Int): PDColor {
        val (r, g, b) = argbToRgbComponents(argb)
        return PDColor(floatArrayOf(r / 255f, g / 255f, b / 255f), PDDeviceRGB.INSTANCE)
    }

    suspend fun createBlankPdf(outputFile: File, title: String = "Untitled Document") =
        withContext(Dispatchers.IO) {
            PDDocument().use { doc ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 20f)
                    cs.setNonStrokingColor(19, 27, 46)
                    cs.newLineAtOffset(56f, 760f)
                    cs.showText(sanitizeWinAnsi(title))
                    cs.endText()

                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 12f)
                    cs.setNonStrokingColor(86, 96, 118)
                    cs.newLineAtOffset(56f, 730f)
                    cs.showText("Created in PDF Master. Tap any text in Content Edit mode to modify or delete.")
                    cs.endText()
                }
                outputFile.parentFile?.mkdirs()
                doc.save(outputFile)
            }
        }

    suspend fun createStarterSamplePdf(outputFile: File) = withContext(Dispatchers.IO) {
        PDDocument().use { doc ->
            val p1 = PDPage(PDRectangle.A4)
            doc.addPage(p1)
            PDPageContentStream(doc, p1).use { cs ->
                cs.setNonStrokingColor(19, 27, 46)
                cs.addRect(0f, 752f, 595f, 90f)
                cs.fill()

                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA_BOLD, 22f)
                cs.setNonStrokingColor(255, 255, 255)
                cs.newLineAtOffset(48f, 796f)
                cs.showText("PROJECT PROPOSAL & BRIEF")
                cs.endText()

                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 11f)
                cs.setNonStrokingColor(217, 48, 54)
                cs.newLineAtOffset(48f, 774f)
                cs.showText("Q4 Studio Deliverable  |  Editable PDF Stream Sample")
                cs.endText()

                val lines = listOf(
                    48f to 700f to ("1. Executive Summary" to true),
                    48f to 676f to ("This PDF contains real editable text runs in its content stream." to false),
                    48f to 656f to ("Switch to Content Edit mode to tap and edit any paragraph directly," to false),
                    48f to 636f to ("or switch to Annotate & Sign to highlight, draw, and place signatures." to false),
                    48f to 588f to ("2. Key Deliverables" to true),
                    48f to 562f to ("- High-resolution camera document scanning with perspective crop" to false),
                    48f to 542f to ("- Genuine PDF content stream editing, merging, and splitting" to false),
                    48f to 522f to ("- Multi-format conversions across Word, Excel, PowerPoint, and HTML" to false),
                    48f to 472f to ("3. Budget & Timeline Overview" to true),
                    48f to 446f to ("Phase 1: Discovery & Architecture     $12,400     Completed" to false),
                    48f to 426f to ("Phase 2: Core Engine & UI Polish       $18,900     In Progress" to false),
                    48f to 406f to ("Phase 3: Release & QA Verification     $7,200      Scheduled" to false)
                )
                for (entry in lines) {
                    val (coords, content) = entry
                    val (x, y) = coords
                    val (txt, bold) = content
                    cs.beginText()
                    cs.setFont(if (bold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA, if (bold) 14f else 11.5f)
                    cs.setNonStrokingColor(19, 27, 46)
                    cs.newLineAtOffset(x, y)
                    cs.showText(txt)
                    cs.endText()
                }
            }

            val p2 = PDPage(PDRectangle.A4)
            doc.addPage(p2)
            PDPageContentStream(doc, p2).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA_BOLD, 18f)
                cs.setNonStrokingColor(19, 27, 46)
                cs.newLineAtOffset(48f, 770f)
                cs.showText("Approval & Sign-Off Sheet")
                cs.endText()

                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.setNonStrokingColor(86, 96, 118)
                cs.newLineAtOffset(48f, 738f)
                cs.showText("Use the Signature tool to sign inside the box below.")
                cs.endText()

                cs.setStrokingColor(190, 198, 214)
                cs.setLineWidth(1.2f)
                cs.addRect(48f, 560f, 499f, 130f)
                cs.stroke()

                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 10f)
                cs.setNonStrokingColor(120, 130, 148)
                cs.newLineAtOffset(60f, 575f)
                cs.showText("Authorized Signature")
                cs.endText()
            }

            outputFile.parentFile?.mkdirs()
            doc.save(outputFile)
        }
    }

    suspend fun extractTextBlocks(pdfFile: File, pageIndex: Int): List<PdfTextBlock> =
        withContext(Dispatchers.IO) {
            if (!pdfFile.exists()) return@withContext emptyList()
            runCatching {
                PDDocument.load(pdfFile).use { doc ->
                    if (pageIndex !in 0 until doc.numberOfPages) return@runCatching emptyList()
                    val page = doc.getPage(pageIndex)
                    val cropBox = page.cropBox ?: page.mediaBox
                    val pageW = cropBox.width.coerceAtLeast(1f)
                    val pageH = cropBox.height.coerceAtLeast(1f)

                    val blocks = mutableListOf<PdfTextBlock>()
                    val stripper = object : PDFTextStripper() {
                        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                            if (text.isNullOrBlank() || textPositions.isNullOrEmpty()) return
                            var minX = Float.MAX_VALUE
                            var maxX = Float.MIN_VALUE
                            var minY = Float.MAX_VALUE
                            var maxY = Float.MIN_VALUE
                            var fontSum = 0f
                            for (tp in textPositions) {
                                val x = tp.xDirAdj
                                val w = tp.widthDirAdj
                                val baseY = tp.yDirAdj
                                val h = tp.heightDir.coerceAtLeast(8f)
                                minX = min(minX, x)
                                maxX = max(maxX, x + w)
                                minY = min(minY, baseY - h * 1.05f)
                                maxY = max(maxY, baseY + h * 0.25f)
                                fontSum += tp.fontSizeInPt.takeIf { it > 0f } ?: h
                            }
                            if (minX < maxX && minY < maxY) {
                                val normL = (minX / pageW).coerceIn(0f, 1f)
                                val normR = (maxX / pageW).coerceIn(normL + 0.01f, 1f)
                                val normT = (minY / pageH).coerceIn(0f, 1f)
                                val normB = (maxY / pageH).coerceIn(normT + 0.008f, 1f)
                                val avgFont = (fontSum / textPositions.size).coerceIn(7f, 48f)
                                val pdfX = cropBox.lowerLeftX + minX
                                val pdfBaselineY = cropBox.upperRightY - maxY + avgFont * 0.22f
                                blocks.add(
                                    PdfTextBlock(
                                        id = UUID.randomUUID().toString(),
                                        text = text.trim(),
                                        normLeft = normL,
                                        normTop = normT,
                                        normRight = normR,
                                        normBottom = normB,
                                        fontSizePt = avgFont,
                                        pdfX = pdfX,
                                        pdfY = pdfBaselineY,
                                        pdfWidth = (maxX - minX).coerceAtLeast(10f),
                                        pdfHeight = (maxY - minY).coerceAtLeast(10f)
                                    )
                                )
                            }
                        }
                    }
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    stripper.writeText(doc, StringWriter())
                    blocks
                }
            }.getOrDefault(emptyList())
        }

    /**
     * Genuine PDF content-stream editing:
     * Attempts direct COSString/TJ token replacement in the page's content stream first;
     * if the text uses subsetted CID encoding or split glyphs, strips/masks the original text run
     * inside the content stream and writes native PDF text operators (`BT ... ET`) at the exact coordinates.
     */
    suspend fun editExistingTextInStream(
        pdfFile: File,
        pageIndex: Int,
        block: PdfTextBlock,
        newText: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!pdfFile.exists()) return@withContext false
        runCatching {
            val tempOut = File(pdfFile.parentFile, "${pdfFile.nameWithoutExtension}_edit_tmp.pdf")
            PDDocument.load(pdfFile).use { doc ->
                if (pageIndex !in 0 until doc.numberOfPages) return@runCatching false
                val page = doc.getPage(pageIndex)
                val targetTrim = block.text.trim()
                val safeNew = sanitizeWinAnsi(newText)
                val replacedDirectly = tryReplaceStreamStringTokens(doc, page, targetTrim, safeNew)

                if (!replacedDirectly) {
                    // Remove any matching fragments if present and rewrite the text block cleanly
                    // directly in the PDF content stream so the PDF stream itself carries the updated text.
                    val cropBox = page.cropBox ?: page.mediaBox
                    val boxX = cropBox.lowerLeftX + block.normLeft * cropBox.width - 2f
                    val boxW = ((block.normRight - block.normLeft) * cropBox.width + 6f).coerceAtLeast(12f)
                    val boxH = ((block.normBottom - block.normTop) * cropBox.height + 4f).coerceAtLeast(12f)
                    val boxY = cropBox.upperRightY - block.normBottom * cropBox.height - 1f

                    tryRemoveStreamStringTokens(doc, page, targetTrim)
                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        // Clear glyph area in content stream
                        cs.setNonStrokingColor(255, 255, 255)
                        cs.addRect(boxX, boxY, boxW, boxH)
                        cs.fill()

                        if (safeNew.isNotBlank()) {
                            cs.beginText()
                            cs.setFont(PDType1Font.HELVETICA, block.fontSizePt.coerceIn(7f, 36f))
                            cs.setNonStrokingColor(19, 27, 46)
                            cs.newLineAtOffset(block.pdfX, block.pdfY)
                            cs.showText(safeNew)
                            cs.endText()
                        }
                    }
                }

                doc.save(tempOut)
            }
            if (tempOut.exists() && tempOut.length() > 0) {
                tempOut.copyTo(pdfFile, overwrite = true)
                tempOut.delete()
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private fun tryReplaceStreamStringTokens(
        doc: PDDocument,
        page: PDPage,
        target: String,
        replacement: String
    ): Boolean {
        if (target.isEmpty()) return false
        val parser = PDFStreamParser(page)
        parser.parse()
        val tokens = parser.tokens
        var modified = false

        for (i in tokens.indices) {
            val token = tokens[i]
            if (token is COSString) {
                val str = token.string ?: continue
                if (str.contains(target)) {
                    val updated = str.replace(target, replacement)
                    tokens[i] = COSString(updated)
                    modified = true
                    break
                }
            } else if (token is COSArray) {
                val joined = buildString {
                    for (item in token) {
                        if (item is COSString) append(item.string ?: "")
                    }
                }
                if (joined.contains(target)) {
                    val updated = joined.replace(target, replacement)
                    val newArr = COSArray()
                    newArr.add(COSString(updated))
                    tokens[i] = newArr
                    modified = true
                    break
                }
            }
        }

        if (modified) {
            val updatedStream = PDStream(doc)
            updatedStream.createOutputStream(COSName.FLATE_DECODE).use { os ->
                ContentStreamWriter(os).writeTokens(tokens)
            }
            page.setContents(updatedStream)
        }
        return modified
    }

    private fun tryRemoveStreamStringTokens(
        doc: PDDocument,
        page: PDPage,
        target: String
    ): Boolean {
        if (target.isEmpty()) return false
        val parser = PDFStreamParser(page)
        parser.parse()
        val tokens = parser.tokens
        var modified = false

        for (i in tokens.indices) {
            val token = tokens[i]
            if (token is COSString) {
                val str = token.string ?: continue
                if (str.contains(target) || target.contains(str.trim()) && str.trim().length > 3) {
                    tokens[i] = COSString("")
                    modified = true
                }
            } else if (token is COSArray) {
                val joined = buildString {
                    for (item in token) {
                        if (item is COSString) append(item.string ?: "")
                    }
                }
                if (joined.contains(target)) {
                    val newArr = COSArray()
                    newArr.add(COSString(joined.replace(target, "")))
                    tokens[i] = newArr
                    modified = true
                }
            }
        }

        if (modified) {
            val updatedStream = PDStream(doc)
            updatedStream.createOutputStream(COSName.FLATE_DECODE).use { os ->
                ContentStreamWriter(os).writeTokens(tokens)
            }
            page.setContents(updatedStream)
        }
        return modified
    }

    suspend fun insertPermanentText(
        pdfFile: File,
        pageIndex: Int,
        text: String,
        normX: Float,
        normY: Float,
        fontSizePt: Float,
        colorArgb: Int
    ): Boolean = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || text.isBlank()) return@withContext false
        runCatching {
            val tempOut = File(pdfFile.parentFile, "${pdfFile.nameWithoutExtension}_txt_tmp.pdf")
            PDDocument.load(pdfFile).use { doc ->
                val page = doc.getPage(pageIndex)
                val cropBox = page.cropBox ?: page.mediaBox
                val pdfX = cropBox.lowerLeftX + normX.coerceIn(0.02f, 0.92f) * cropBox.width
                val pdfY = cropBox.upperRightY - normY.coerceIn(0.04f, 0.96f) * cropBox.height
                val (r, g, b) = argbToRgbComponents(colorArgb)

                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    val lines = sanitizeWinAnsi(text).split('\n')
                    var yCursor = pdfY
                    for (line in lines) {
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA, fontSizePt.coerceIn(8f, 48f))
                        cs.setNonStrokingColor(r, g, b)
                        cs.newLineAtOffset(pdfX, yCursor)
                        cs.showText(line)
                        cs.endText()
                        yCursor -= fontSizePt * 1.3f
                    }
                }
                doc.save(tempOut)
            }
            tempOut.copyTo(pdfFile, overwrite = true)
            tempOut.delete()
            true
        }.getOrDefault(false)
    }

    suspend fun insertPermanentImage(
        pdfFile: File,
        pageIndex: Int,
        bitmap: Bitmap,
        normLeft: Float,
        normTop: Float,
        normRight: Float,
        normBottom: Float
    ): Boolean = withContext(Dispatchers.IO) {
        if (!pdfFile.exists()) return@withContext false
        runCatching {
            val tempOut = File(pdfFile.parentFile, "${pdfFile.nameWithoutExtension}_img_tmp.pdf")
            PDDocument.load(pdfFile).use { doc ->
                val page = doc.getPage(pageIndex)
                val cropBox = page.cropBox ?: page.mediaBox
                val x = cropBox.lowerLeftX + normLeft * cropBox.width
                val w = ((normRight - normLeft) * cropBox.width).coerceAtLeast(16f)
                val h = ((normBottom - normTop) * cropBox.height).coerceAtLeast(16f)
                val y = cropBox.upperRightY - normBottom * cropBox.height

                val pdImage = if (bitmap.hasAlpha()) {
                    LosslessFactory.createFromImage(doc, bitmap)
                } else {
                    JPEGFactory.createFromImage(doc, bitmap, 0.88f)
                }

                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.drawImage(pdImage, x, y, w, h)
                }
                doc.save(tempOut)
            }
            tempOut.copyTo(pdfFile, overwrite = true)
            tempOut.delete()
            true
        }.getOrDefault(false)
    }

    suspend fun commitAnnotationsAndOverlays(
        pdfFile: File,
        pageIndex: Int,
        overlays: List<PageOverlayItem>
    ): Boolean = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || overlays.isEmpty()) return@withContext false
        runCatching {
            val tempOut = File(pdfFile.parentFile, "${pdfFile.nameWithoutExtension}_ann_tmp.pdf")
            PDDocument.load(pdfFile).use { doc ->
                val page = doc.getPage(pageIndex)
                val cropBox = page.cropBox ?: page.mediaBox
                val pw = cropBox.width
                val ph = cropBox.height

                // Separate real PDF annotation objects vs vector/signature stream overlays
                for (item in overlays) {
                    when (item) {
                        is PageOverlayItem.TextMarkup -> {
                            val subType = when (item.kind) {
                                MarkupKind.HIGHLIGHT -> PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT
                                MarkupKind.UNDERLINE -> PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE
                                MarkupKind.STRIKETHROUGH -> PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT
                            }
                            val x1 = cropBox.lowerLeftX + item.normLeft * pw
                            val x2 = cropBox.lowerLeftX + item.normRight * pw
                            val yTop = cropBox.upperRightY - item.normTop * ph
                            val yBot = cropBox.upperRightY - item.normBottom * ph

                            val ann = PDAnnotationTextMarkup(subType)
                            ann.rectangle = PDRectangle(x1, yBot, (x2 - x1).coerceAtLeast(8f), (yTop - yBot).coerceAtLeast(8f))
                            ann.quadPoints = floatArrayOf(x1, yTop, x2, yTop, x1, yBot, x2, yBot)
                            ann.color = argbToPdColor(item.colorArgb)
                            ann.constantOpacity = if (item.kind == MarkupKind.HIGHLIGHT) 0.35f else 0.9f
                            page.annotations.add(ann)

                            // Also draw crisp fallback visual in stream for maximum viewer compatibility
                            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                                val (r, g, b) = argbToRgbComponents(item.colorArgb)
                                cs.setStrokingColor(r, g, b)
                                when (item.kind) {
                                    MarkupKind.HIGHLIGHT -> {
                                        // Draw translucent-looking tint frame or soft underline if viewer ignores AP
                                        cs.setLineWidth(1.0f)
                                        cs.addRect(x1, yBot, (x2 - x1).coerceAtLeast(8f), (yTop - yBot).coerceAtLeast(8f))
                                        cs.stroke()
                                    }
                                    MarkupKind.UNDERLINE -> {
                                        cs.setLineWidth(2.0f)
                                        cs.moveTo(x1, yBot + 2f)
                                        cs.lineTo(x2, yBot + 2f)
                                        cs.stroke()
                                    }
                                    MarkupKind.STRIKETHROUGH -> {
                                        val midY = (yTop + yBot) / 2f
                                        cs.setLineWidth(2.0f)
                                        cs.moveTo(x1, midY)
                                        cs.lineTo(x2, midY)
                                        cs.stroke()
                                    }
                                }
                            }
                        }

                        is PageOverlayItem.CommentPin -> {
                            val cx = cropBox.lowerLeftX + item.normX * pw
                            val cy = cropBox.upperRightY - item.normY * ph
                            val ann = PDAnnotationText()
                            ann.rectangle = PDRectangle(cx - 10f, cy - 10f, 20f, 20f)
                            ann.contents = item.comment
                            ann.name = PDAnnotationText.NAME_COMMENT
                            ann.color = argbToPdColor(item.colorArgb)
                            page.annotations.add(ann)

                            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                                val (r, g, b) = argbToRgbComponents(item.colorArgb)
                                cs.setNonStrokingColor(r, g, b)
                                cs.addRect(cx - 8f, cy - 8f, 16f, 16f)
                                cs.fill()
                                cs.beginText()
                                cs.setFont(PDType1Font.HELVETICA_BOLD, 9f)
                                cs.setNonStrokingColor(255, 255, 255)
                                cs.newLineAtOffset(cx - 3f, cy - 3f)
                                cs.showText("!")
                                cs.endText()
                            }
                        }

                        is PageOverlayItem.InkStroke -> {
                            if (item.points.size >= 2) {
                                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                                    val (r, g, b) = argbToRgbComponents(item.colorArgb)
                                    cs.setStrokingColor(r, g, b)
                                    cs.setLineWidth((item.strokeWidthNorm * pw).coerceIn(1.2f, 12f))
                                    cs.setLineCapStyle(1)
                                    cs.setLineJoinStyle(1)
                                    val first = item.points.first()
                                    cs.moveTo(cropBox.lowerLeftX + first.x * pw, cropBox.upperRightY - first.y * ph)
                                    for (pt in item.points.drop(1)) {
                                        cs.lineTo(cropBox.lowerLeftX + pt.x * pw, cropBox.upperRightY - pt.y * ph)
                                    }
                                    cs.stroke()
                                }
                            }
                        }

                        is PageOverlayItem.ShapeItem -> {
                            val sx = cropBox.lowerLeftX + item.start.x * pw
                            val sy = cropBox.upperRightY - item.start.y * ph
                            val ex = cropBox.lowerLeftX + item.end.x * pw
                            val ey = cropBox.upperRightY - item.end.y * ph
                            val (r, g, b) = argbToRgbComponents(item.colorArgb)

                            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                                cs.setStrokingColor(r, g, b)
                                cs.setLineWidth((item.strokeWidthNorm * pw).coerceIn(1.5f, 10f))
                                when (item.kind) {
                                    ShapeKind.RECTANGLE -> {
                                        val rx = min(sx, ex)
                                        val ry = min(sy, ey)
                                        val rw = kotlin.math.abs(ex - sx).coerceAtLeast(6f)
                                        val rh = kotlin.math.abs(ey - sy).coerceAtLeast(6f)
                                        cs.addRect(rx, ry, rw, rh)
                                        cs.stroke()
                                    }
                                    ShapeKind.CIRCLE -> {
                                        val cx = (sx + ex) / 2f
                                        val cy = (sy + ey) / 2f
                                        val radX = (kotlin.math.abs(ex - sx) / 2f).coerceAtLeast(6f)
                                        val radY = (kotlin.math.abs(ey - sy) / 2f).coerceAtLeast(6f)
                                        drawEllipse(cs, cx, cy, radX, radY)
                                        cs.stroke()
                                    }
                                    ShapeKind.ARROW -> {
                                        cs.moveTo(sx, sy)
                                        cs.lineTo(ex, ey)
                                        val angle = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                                        val headLen = 14.0
                                        val a1 = angle - Math.PI / 6.0
                                        val a2 = angle + Math.PI / 6.0
                                        cs.moveTo(ex, ey)
                                        cs.lineTo((ex - headLen * cos(a1)).toFloat(), (ey - headLen * sin(a1)).toFloat())
                                        cs.moveTo(ex, ey)
                                        cs.lineTo((ex - headLen * cos(a2)).toFloat(), (ey - headLen * sin(a2)).toFloat())
                                        cs.stroke()
                                    }
                                }
                            }
                        }

                        is PageOverlayItem.TextBoxItem -> {
                            val tx = cropBox.lowerLeftX + item.normX * pw
                            val ty = cropBox.upperRightY - item.normY * ph
                            val (r, g, b) = argbToRgbComponents(item.colorArgb)
                            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                                val lines = sanitizeWinAnsi(item.text).split('\n')
                                var yCur = ty
                                for (line in lines) {
                                    cs.beginText()
                                    cs.setFont(PDType1Font.HELVETICA_BOLD, item.fontSizeSp.coerceIn(9f, 36f))
                                    cs.setNonStrokingColor(r, g, b)
                                    cs.newLineAtOffset(tx, yCur)
                                    cs.showText(line)
                                    cs.endText()
                                    yCur -= item.fontSizeSp * 1.25f
                                }
                            }
                        }

                        is PageOverlayItem.SignaturePlacement -> {
                            val sigFile = File(item.imagePath)
                            if (sigFile.exists()) {
                                val bmp = BitmapFactory.decodeFile(sigFile.absolutePath)
                                if (bmp != null) {
                                    val sx = cropBox.lowerLeftX + item.normLeft * pw
                                    val sw = ((item.normRight - item.normLeft) * pw).coerceAtLeast(32f)
                                    val sh = ((item.normBottom - item.normTop) * ph).coerceAtLeast(18f)
                                    val sy = cropBox.upperRightY - item.normBottom * ph
                                    val pdImg = LosslessFactory.createFromImage(doc, bmp)
                                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                                        cs.drawImage(pdImg, sx, sy, sw, sh)
                                    }
                                    bmp.recycle()
                                }
                            }
                        }
                    }
                }
                doc.save(tempOut)
            }
            tempOut.copyTo(pdfFile, overwrite = true)
            tempOut.delete()
            true
        }.getOrDefault(false)
    }

    private fun drawEllipse(cs: PDPageContentStream, cx: Float, cy: Float, rx: Float, ry: Float) {
        val k = 0.5522847498f
        val ox = rx * k
        val oy = ry * k
        cs.moveTo(cx - rx, cy)
        cs.curveTo(cx - rx, cy + oy, cx - ox, cy + ry, cx, cy + ry)
        cs.curveTo(cx + ox, cy + ry, cx + rx, cy + oy, cx + rx, cy)
        cs.curveTo(cx + rx, cy - oy, cx + ox, cy - ry, cx, cy - ry)
        cs.curveTo(cx - ox, cy - ry, cx - rx, cy - oy, cx - rx, cy)
    }

    suspend fun rotatePage(pdfFile: File, pageIndex: Int, deltaDegrees: Int = 90): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val tempOut = File(pdfFile.parentFile, "${pdfFile.nameWithoutExtension}_rot_tmp.pdf")
                PDDocument.load(pdfFile).use { doc ->
                    val page = doc.getPage(pageIndex)
                    page.rotation = (page.rotation + deltaDegrees + 360) % 360
                    doc.save(tempOut)
                }
                tempOut.copyTo(pdfFile, overwrite = true)
                tempOut.delete()
                true
            }.getOrDefault(false)
        }

    suspend fun duplicatePage(pdfFile: File, pageIndex: Int): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val tempOut = File(pdfFile.parentFile, "${pdfFile.nameWithoutExtension}_dup_tmp.pdf")
                PDDocument.load(pdfFile).use { doc ->
                    if (pageIndex !in 0 until doc.numberOfPages) return@runCatching false
                    val srcPage = doc.getPage(pageIndex)
                    val imported = doc.importPage(srcPage)
                    // Move imported (at end) right after pageIndex
                    val tree = doc.pages
                    if (doc.numberOfPages > pageIndex + 2) {
                        tree.remove(imported)
                        tree.insertAfter(imported, srcPage)
                    }
                    doc.save(tempOut)
                }
                tempOut.copyTo(pdfFile, overwrite = true)
                tempOut.delete()
                true
            }.getOrDefault(false)
        }

    suspend fun deletePage(pdfFile: File, pageIndex: Int): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val tempOut = File(pdfFile.parentFile, "${pdfFile.nameWithoutExtension}_del_tmp.pdf")
                PDDocument.load(pdfFile).use { doc ->
                    if (doc.numberOfPages <= 1 || pageIndex !in 0 until doc.numberOfPages) {
                        return@runCatching false
                    }
                    doc.removePage(pageIndex)
                    doc.save(tempOut)
                }
                tempOut.copyTo(pdfFile, overwrite = true)
                tempOut.delete()
                true
            }.getOrDefault(false)
        }

    suspend fun reorderPages(pdfFile: File, newOrder: List<Int>): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val tempOut = File(pdfFile.parentFile, "${pdfFile.nameWithoutExtension}_reord_tmp.pdf")
                PDDocument.load(pdfFile).use { src ->
                    if (newOrder.size != src.numberOfPages) return@runCatching false
                    PDDocument().use { dst ->
                        for (idx in newOrder) {
                            dst.importPage(src.getPage(idx))
                        }
                        dst.save(tempOut)
                    }
                }
                tempOut.copyTo(pdfFile, overwrite = true)
                tempOut.delete()
                true
            }.getOrDefault(false)
        }

    suspend fun mergePdfs(inputFiles: List<File>, outputFile: File): Boolean =
        withContext(Dispatchers.IO) {
            if (inputFiles.isEmpty()) return@withContext false
            runCatching {
                outputFile.parentFile?.mkdirs()
                val merger = PDFMergerUtility()
                merger.destinationFileName = outputFile.absolutePath
                for (file in inputFiles) {
                    if (file.exists()) merger.addSource(file)
                }
                merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
                outputFile.exists() && outputFile.length() > 0
            }.getOrDefault(false)
        }

    suspend fun extractPages(
        inputFile: File,
        selectedIndices: List<Int>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        if (!inputFile.exists() || selectedIndices.isEmpty()) return@withContext false
        runCatching {
            PDDocument.load(inputFile).use { src ->
                PDDocument().use { dst ->
                    for (idx in selectedIndices) {
                        if (idx in 0 until src.numberOfPages) {
                            dst.importPage(src.getPage(idx))
                        }
                    }
                    if (dst.numberOfPages == 0) return@runCatching false
                    outputFile.parentFile?.mkdirs()
                    dst.save(outputFile)
                }
            }
            outputFile.exists() && outputFile.length() > 0
        }.getOrDefault(false)
    }

    suspend fun splitPdfByRanges(
        inputFile: File,
        rangeSpec: String,
        outputDir: File,
        baseTitle: String
    ): List<File> = withContext(Dispatchers.IO) {
        if (!inputFile.exists()) return@withContext emptyList()
        val created = mutableListOf<File>()
        runCatching {
            PDDocument.load(inputFile).use { src ->
                val total = src.numberOfPages
                val groups = parsePageRanges(rangeSpec, total)
                outputDir.mkdirs()
                groups.forEachIndexed { idx, pages ->
                    if (pages.isNotEmpty()) {
                        val outFile = File(outputDir, "${baseTitle}_part_${idx + 1}_${System.currentTimeMillis()}.pdf")
                        PDDocument().use { dst ->
                            for (p in pages) {
                                dst.importPage(src.getPage(p))
                            }
                            dst.save(outFile)
                        }
                        created.add(outFile)
                    }
                }
            }
        }
        created
    }

    private fun parsePageRanges(spec: String, totalPages: Int): List<List<Int>> {
        val trimmed = spec.trim()
        if (trimmed.isEmpty() || trimmed.equals("all", ignoreCase = true)) {
            return (0 until totalPages).map { listOf(it) }
        }
        val groups = mutableListOf<List<Int>>()
        for (part in trimmed.split(',')) {
            val token = part.trim()
            if (token.contains('-')) {
                val bounds = token.split('-')
                val start = (bounds.getOrNull(0)?.trim()?.toIntOrNull() ?: 1).coerceIn(1, totalPages)
                val end = (bounds.getOrNull(1)?.trim()?.toIntOrNull() ?: totalPages).coerceIn(start, totalPages)
                groups.add(((start - 1) until end).toList())
            } else {
                val single = token.toIntOrNull()
                if (single != null && single in 1..totalPages) {
                    groups.add(listOf(single - 1))
                }
            }
        }
        return groups
    }

    suspend fun compressPdf(
        inputFile: File,
        outputFile: File,
        level: CompressionLevel
    ): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        if (!inputFile.exists()) return@withContext null
        val beforeBytes = inputFile.length()
        val ok = runCatching {
            PDDocument.load(inputFile).use { doc ->
                for (page in doc.pages) {
                    val resources = page.resources ?: continue
                    for (name in resources.xObjectNames) {
                        val xObj = runCatching { resources.getXObject(name) }.getOrNull()
                        if (xObj is PDImageXObject) {
                            val bmp = runCatching { xObj.image }.getOrNull() ?: continue
                            val scaled = downscaleBitmapIfNeeded(bmp, level.maxImageDimension)
                            val compressedJpeg = JPEGFactory.createFromImage(
                                doc,
                                scaled,
                                level.jpegQuality / 100f
                            )
                            resources.put(name, compressedJpeg)
                            if (scaled !== bmp) scaled.recycle()
                            bmp.recycle()
                        }
                    }
                }
                outputFile.parentFile?.mkdirs()
                doc.save(outputFile)
            }
            true
        }.getOrDefault(false)

        if (!ok || !outputFile.exists()) return@withContext null
        // If input had only vector text and compression slightly grew header overhead, keep smaller copy
        if (outputFile.length() > beforeBytes && beforeBytes > 0) {
            // Re-render pages at target DPI as compressed JPEG pages if user chose Maximum
            if (level == CompressionLevel.MAXIMUM) {
                val rendered = PdfRenderEngine.renderAllThumbnails(inputFile, thumbWidthPx = level.maxImageDimension)
                if (rendered.isNotEmpty()) {
                    PDDocument().use { reDoc ->
                        for (bmp in rendered) {
                            val page = PDPage(PDRectangle.A4)
                            reDoc.addPage(page)
                            val img = JPEGFactory.createFromImage(reDoc, bmp, level.jpegQuality / 100f)
                            PDPageContentStream(reDoc, page).use { cs ->
                                cs.drawImage(img, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
                            }
                            bmp.recycle()
                        }
                        reDoc.save(outputFile)
                    }
                }
            }
        }
        beforeBytes to outputFile.length()
    }

    private fun downscaleBitmapIfNeeded(bmp: Bitmap, maxDim: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val largest = max(w, h)
        if (largest <= maxDim) return bmp
        val scale = maxDim.toFloat() / largest.toFloat()
        val nw = (w * scale).toInt().coerceAtLeast(64)
        val nh = (h * scale).toInt().coerceAtLeast(64)
        return Bitmap.createScaledBitmap(bmp, nw, nh, true)
    }

    suspend fun buildPdfFromBitmaps(
        bitmaps: List<Bitmap>,
        pageSize: PageSizeOption,
        marginPt: Float = 16f,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        if (bitmaps.isEmpty()) return@withContext false
        runCatching {
            PDDocument().use { doc ->
                for (bmp in bitmaps) {
                    val pageRect = if (pageSize == PageSizeOption.FIT_IMAGE) {
                        val scale = 595f / bmp.width.coerceAtLeast(1)
                        PDRectangle(595f, (bmp.height * scale).coerceIn(200f, 1600f))
                    } else {
                        PDRectangle(pageSize.widthPt, pageSize.heightPt)
                    }
                    val page = PDPage(pageRect)
                    doc.addPage(page)
                    val availW = (pageRect.width - marginPt * 2f).coerceAtLeast(50f)
                    val availH = (pageRect.height - marginPt * 2f).coerceAtLeast(50f)
                    val imgRatio = bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()
                    val boxRatio = availW / availH
                    val drawW: Float
                    val drawH: Float
                    if (imgRatio > boxRatio) {
                        drawW = availW
                        drawH = availW / imgRatio
                    } else {
                        drawH = availH
                        drawW = availH * imgRatio
                    }
                    val x = (pageRect.width - drawW) / 2f
                    val y = (pageRect.height - drawH) / 2f
                    val pdImage = JPEGFactory.createFromImage(doc, bmp, 0.90f)
                    PDPageContentStream(doc, page).use { cs ->
                        cs.drawImage(pdImage, x, y, drawW, drawH)
                    }
                }
                outputFile.parentFile?.mkdirs()
                doc.save(outputFile)
            }
            outputFile.exists() && outputFile.length() > 0
        }.getOrDefault(false)
    }
}
