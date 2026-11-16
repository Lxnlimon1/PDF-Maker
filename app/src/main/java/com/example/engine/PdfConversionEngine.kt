package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Xml
import com.example.data.model.ImageExportFormat
import com.example.data.model.PageSizeOption
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object PdfConversionEngine {

    private fun sanitize(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when {
                ch.code in 32..126 -> sb.append(ch)
                ch == '\u2013' || ch == '\u2014' -> sb.append('-')
                ch == '\u2018' || ch == '\u2019' -> sb.append('\'')
                ch == '\u201C' || ch == '\u201D' -> sb.append('"')
                ch == '\u2022' -> sb.append('*')
                ch.code in 160..255 -> sb.append(ch)
                ch == '\t' -> sb.append("    ")
                else -> if (ch != '\r' && ch != '\n') sb.append(' ')
            }
        }
        return sb.toString()
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    // -------------------------------------------------------------------------
    // 1. IMAGE TO PDF
    // -------------------------------------------------------------------------
    suspend fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        pageSize: PageSizeOption,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            for (uri in imageUris) {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, boundsOpts)
                }
                var sample = 1
                while (maxOf(boundsOpts.outWidth, boundsOpts.outHeight) / sample > 2200) {
                    sample *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                }
                if (bmp != null) bitmaps.add(bmp)
            }
            PdfContentEngine.buildPdfFromBitmaps(bitmaps, pageSize, 18f, outputFile)
        } finally {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    // -------------------------------------------------------------------------
    // 2. TEXT TO PDF
    // -------------------------------------------------------------------------
    suspend fun textToPdf(
        title: String,
        bodyText: String,
        fontSizePt: Float = 11.5f,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val paragraphs = mutableListOf<StyledParagraph>()
        if (title.isNotBlank()) {
            paragraphs.add(StyledParagraph(title.trim(), isHeading = true, level = 1))
        }
        bodyText.split('\n').forEach { line ->
            paragraphs.add(StyledParagraph(line))
        }
        writeStyledParagraphsToPdf(paragraphs, outputFile, fontSizePt)
    }

    // -------------------------------------------------------------------------
    // 3. HTML TO PDF
    // -------------------------------------------------------------------------
    suspend fun htmlToPdf(
        htmlSource: String,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val paragraphs = parseHtmlToParagraphs(htmlSource)
        writeStyledParagraphsToPdf(paragraphs, outputFile, 11.5f)
    }

    private fun parseHtmlToParagraphs(html: String): List<StyledParagraph> {
        val cleaned = html
            .replace(Regex("(?is)<style.*?</style>"), "")
            .replace(Regex("(?is)<script.*?</script>"), "")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|tr|section|article|header|footer)>"), "\n")
            .replace(Regex("(?i)<h1[^>]*>"), "\n[[H1]]")
            .replace(Regex("(?i)</h1>"), "\n")
            .replace(Regex("(?i)<h2[^>]*>"), "\n[[H2]]")
            .replace(Regex("(?i)</h2>"), "\n")
            .replace(Regex("(?i)<h3[^>]*>"), "\n[[H3]]")
            .replace(Regex("(?i)</h3>"), "\n")
            .replace(Regex("(?i)<li[^>]*>"), "\n* ")
            .replace(Regex("(?i)</(li|td|th)>"), "  ")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

        val out = mutableListOf<StyledParagraph>()
        for (rawLine in cleaned.lines()) {
            val line = rawLine.trim()
            when {
                line.startsWith("[[H1]]") -> out.add(
                    StyledParagraph(line.removePrefix("[[H1]]").trim(), isHeading = true, level = 1)
                )
                line.startsWith("[[H2]]") -> out.add(
                    StyledParagraph(line.removePrefix("[[H2]]").trim(), isHeading = true, level = 2)
                )
                line.startsWith("[[H3]]") -> out.add(
                    StyledParagraph(line.removePrefix("[[H3]]").trim(), isHeading = true, level = 3)
                )
                line.isNotEmpty() -> out.add(StyledParagraph(line))
                else -> if (out.isNotEmpty() && out.last().text.isNotEmpty()) {
                    out.add(StyledParagraph(""))
                }
            }
        }
        if (out.isEmpty()) out.add(StyledParagraph("(Empty HTML Document)"))
        return out
    }

    // -------------------------------------------------------------------------
    // 4. WORD (.DOCX) TO PDF
    // -------------------------------------------------------------------------
    suspend fun docxToPdf(
        inputStream: InputStream,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            var documentXml: ByteArray? = null
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.equals("word/document.xml", ignoreCase = true)) {
                        documentXml = zis.readBytes()
                        break
                    }
                    entry = zis.nextEntry
                }
            }
            val xmlBytes = documentXml ?: return@runCatching false
            val paragraphs = parseDocxXml(xmlBytes)
            writeStyledParagraphsToPdf(paragraphs, outputFile, 11.5f)
        }.getOrDefault(false)
    }

    private fun parseDocxXml(xmlBytes: ByteArray): List<StyledParagraph> {
        val result = mutableListOf<StyledParagraph>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var inParagraph = false
        var isHeading = false
        var headingLevel = 1
        var isBold = false
        val currentText = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    when {
                        name == "p" || name.endsWith(":p") -> {
                            inParagraph = true
                            isHeading = false
                            headingLevel = 1
                            isBold = false
                            currentText.clear()
                        }
                        inParagraph && (name == "pStyle" || name.endsWith(":pStyle")) -> {
                            val styleVal = parser.getAttributeValue(null, "w:val")
                                ?: parser.getAttributeValue(null, "val") ?: ""
                            if (styleVal.contains("Heading", ignoreCase = true) ||
                                styleVal.contains("Title", ignoreCase = true)
                            ) {
                                isHeading = true
                                headingLevel = when {
                                    styleVal.contains("2") -> 2
                                    styleVal.contains("3") -> 3
                                    else -> 1
                                }
                            }
                        }
                        inParagraph && (name == "b" || name.endsWith(":b")) -> {
                            isBold = true
                        }
                        inParagraph && (name == "t" || name.endsWith(":t")) -> {
                            currentText.append(parser.nextText())
                        }
                        inParagraph && (name == "tab" || name.endsWith(":tab")) -> {
                            currentText.append("    ")
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    if ((name == "p" || name.endsWith(":p")) && inParagraph) {
                        result.add(
                            StyledParagraph(
                                text = currentText.toString(),
                                isHeading = isHeading,
                                level = headingLevel,
                                isBold = isBold
                            )
                        )
                        inParagraph = false
                    }
                }
            }
            event = parser.next()
        }
        if (result.isEmpty()) result.add(StyledParagraph("(No readable text in Word document)"))
        return result
    }

    // -------------------------------------------------------------------------
    // 5. EXCEL (.XLSX / .CSV) TO PDF
    // -------------------------------------------------------------------------
    suspend fun spreadsheetToPdf(
        inputStream: InputStream,
        isCsv: Boolean,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val sheets: List<Pair<String, List<List<String>>>> = if (isCsv) {
                val text = inputStream.bufferedReader().readText()
                val rows = text.lines()
                    .filter { it.isNotBlank() }
                    .map { parseCsvLine(it) }
                listOf("Sheet 1" to rows)
            } else {
                parseXlsxSheets(inputStream)
            }
            if (sheets.isEmpty()) return@runCatching false

            PDDocument().use { doc ->
                val pageRect = PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width) // Landscape for tables
                val margin = 36f
                val usableW = pageRect.width - margin * 2

                for ((sheetName, rows) in sheets) {
                    if (rows.isEmpty()) continue
                    val colCount = rows.maxOfOrNull { it.size }?.coerceIn(1, 10) ?: 1
                    val colWidth = usableW / colCount
                    val rowHeight = 20f

                    var page = PDPage(pageRect)
                    doc.addPage(page)
                    var cs = PDPageContentStream(doc, page)
                    var y = pageRect.height - margin

                    // Sheet title
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 14f)
                    cs.setNonStrokingColor(19, 27, 46)
                    cs.newLineAtOffset(margin, y)
                    cs.showText(sanitize(sheetName))
                    cs.endText()
                    y -= 24f

                    rows.forEachIndexed { rowIdx, row ->
                        if (y - rowHeight < margin) {
                            cs.close()
                            page = PDPage(pageRect)
                            doc.addPage(page)
                            cs = PDPageContentStream(doc, page)
                            y = pageRect.height - margin
                        }

                        if (rowIdx == 0) {
                            cs.setNonStrokingColor(232, 237, 248)
                            cs.addRect(margin, y - rowHeight + 4f, usableW, rowHeight)
                            cs.fill()
                        } else if (rowIdx % 2 == 1) {
                            cs.setNonStrokingColor(248, 249, 252)
                            cs.addRect(margin, y - rowHeight + 4f, usableW, rowHeight)
                            cs.fill()
                        }

                        cs.setStrokingColor(214, 219, 230)
                        cs.setLineWidth(0.5f)
                        cs.addRect(margin, y - rowHeight + 4f, usableW, rowHeight)
                        cs.stroke()

                        for (c in 0 until colCount) {
                            val cellText = sanitize(row.getOrNull(c).orEmpty())
                            val maxChars = (colWidth / 5.5f).toInt().coerceAtLeast(4)
                            val clipped = if (cellText.length > maxChars) cellText.take(maxChars - 1) + "." else cellText
                            cs.beginText()
                            cs.setFont(
                                if (rowIdx == 0) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA,
                                9f
                            )
                            cs.setNonStrokingColor(19, 27, 46)
                            cs.newLineAtOffset(margin + c * colWidth + 4f, y - 10f)
                            cs.showText(clipped)
                            cs.endText()
                        }
                        y -= rowHeight
                    }
                    cs.close()
                }
                outputFile.parentFile?.mkdirs()
                doc.save(outputFile)
            }
            outputFile.exists() && outputFile.length() > 0
        }.getOrDefault(false)
    }

    private fun parseCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    cells.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        cells.add(sb.toString().trim())
        return cells
    }

    private fun parseXlsxSheets(inputStream: InputStream): List<Pair<String, List<List<String>>>> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zis.readBytes()
                }
                entry = zis.nextEntry
            }
        }

        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheetFiles = entries.keys
            .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .sortedBy { name ->
                Regex("\\d+").find(name)?.value?.toIntOrNull() ?: 0
            }

        return sheetFiles.mapIndexed { idx, key ->
            val rows = parseWorksheetXml(entries[key]!!, sharedStrings)
            "Sheet ${idx + 1}" to rows
        }
    }

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
        var inSi = false
        val current = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") {
                        inSi = true
                        current.clear()
                    } else if (inSi && parser.name == "t") {
                        current.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "si" && inSi) {
                        strings.add(current.toString())
                        inSi = false
                    }
                }
            }
            event = parser.next()
        }
        return strings
    }

    private fun parseWorksheetXml(xmlBytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var currentRow = mutableListOf<String>()
        var inRow = false
        var cellType = ""
        var cellValue = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            inRow = true
                            currentRow = mutableListOf()
                        }
                        "c" -> {
                            cellType = parser.getAttributeValue(null, "t") ?: ""
                            cellValue = ""
                        }
                        "v", "t" -> {
                            if (inRow) cellValue = parser.nextText()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "c" -> {
                            if (inRow) {
                                val resolved = if (cellType == "s") {
                                    val idx = cellValue.toIntOrNull()
                                    if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
                                } else {
                                    cellValue
                                }
                                currentRow.add(resolved)
                            }
                        }
                        "row" -> {
                            if (inRow && currentRow.any { it.isNotBlank() }) {
                                rows.add(currentRow)
                            }
                            inRow = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    // -------------------------------------------------------------------------
    // 6. POWERPOINT (.PPTX) TO PDF
    // -------------------------------------------------------------------------
    suspend fun pptxToPdf(
        inputStream: InputStream,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val slideEntries = mutableMapOf<String, ByteArray>()
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                        slideEntries[entry.name] = zis.readBytes()
                    }
                    entry = zis.nextEntry
                }
            }
            val orderedSlides = slideEntries.keys.sortedBy {
                Regex("\\d+").find(it)?.value?.toIntOrNull() ?: 0
            }
            if (orderedSlides.isEmpty()) return@runCatching false

            PDDocument().use { doc ->
                val slideRect = PDRectangle(720f, 405f) // 16:9 widescreen
                orderedSlides.forEachIndexed { slideIdx, key ->
                    val texts = parsePptxSlideTexts(slideEntries[key]!!)
                    val page = PDPage(slideRect)
                    doc.addPage(page)
                    PDPageContentStream(doc, page).use { cs ->
                        // Clean studio slide header bar
                        cs.setNonStrokingColor(19, 27, 46)
                        cs.addRect(0f, 345f, 720f, 60f)
                        cs.fill()

                        val titleText = texts.firstOrNull() ?: "Slide ${slideIdx + 1}"
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA_BOLD, 18f)
                        cs.setNonStrokingColor(255, 255, 255)
                        cs.newLineAtOffset(36f, 368f)
                        cs.showText(sanitize(titleText).take(68))
                        cs.endText()

                        var y = 310f
                        for (bullet in texts.drop(1)) {
                            val wrapped = wrapLine(sanitize(bullet), PDType1Font.HELVETICA, 13f, 640f)
                            for ((lineIdx, wLine) in wrapped.withIndex()) {
                                if (y < 36f) break
                                cs.beginText()
                                cs.setFont(PDType1Font.HELVETICA, 13f)
                                cs.setNonStrokingColor(30, 38, 56)
                                cs.newLineAtOffset(36f, y)
                                cs.showText(if (lineIdx == 0) "* $wLine" else "  $wLine")
                                cs.endText()
                                y -= 20f
                            }
                            y -= 6f
                        }

                        // Slide footer
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA, 9f)
                        cs.setNonStrokingColor(130, 140, 158)
                        cs.newLineAtOffset(640f, 18f)
                        cs.showText("Slide ${slideIdx + 1} / ${orderedSlides.size}")
                        cs.endText()
                    }
                }
                outputFile.parentFile?.mkdirs()
                doc.save(outputFile)
            }
            outputFile.exists() && outputFile.length() > 0
        }.getOrDefault(false)
    }

    private fun parsePptxSlideTexts(xmlBytes: ByteArray): List<String> {
        val texts = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
        val para = StringBuilder()
        var inP = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    if (name == "p" || name.endsWith(":p")) {
                        inP = true
                        para.clear()
                    } else if (inP && (name == "t" || name.endsWith(":t"))) {
                        para.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    if ((name == "p" || name.endsWith(":p")) && inP) {
                        val s = para.toString().trim()
                        if (s.isNotEmpty()) texts.add(s)
                        inP = false
                    }
                }
            }
            event = parser.next()
        }
        return texts
    }

    // -------------------------------------------------------------------------
    // 7. PDF TO JPG / PNG IMAGES
    // -------------------------------------------------------------------------
    suspend fun pdfToImages(
        pdfFile: File,
        format: ImageExportFormat,
        widthPx: Int = 1600,
        outputDir: File,
        baseName: String
    ): List<File> = withContext(Dispatchers.IO) {
        val count = PdfRenderEngine.getPageCount(pdfFile)
        if (count == 0) return@withContext emptyList()
        outputDir.mkdirs()
        val files = mutableListOf<File>()
        val compressFmt = if (format == ImageExportFormat.PNG) {
            Bitmap.CompressFormat.PNG
        } else {
            Bitmap.CompressFormat.JPEG
        }
        for (i in 0 until count) {
            val bmp = PdfRenderEngine.renderPage(pdfFile, i, widthPx) ?: continue
            val outFile = File(outputDir, "${baseName}_page_${i + 1}.${format.extension}")
            FileOutputStream(outFile).use { fos ->
                bmp.compress(compressFmt, 92, fos)
            }
            bmp.recycle()
            files.add(outFile)
        }
        files
    }

    // -------------------------------------------------------------------------
    // 8. PDF TO WORD (.DOCX)
    // -------------------------------------------------------------------------
    suspend fun pdfToDocx(pdfFile: File, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val pageTexts = extractTextPerPage(pdfFile)
            outputFile.parentFile?.mkdirs()

            val bodyXml = buildString {
                pageTexts.forEachIndexed { idx, text ->
                    for (line in text.lines()) {
                        append("<w:p><w:r><w:t xml:space=\"preserve\">")
                        append(escapeXml(line))
                        append("</w:t></w:r></w:p>")
                    }
                    if (idx < pageTexts.lastIndex) {
                        append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
                    }
                }
            }

            val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>$bodyXml<w:sectPr/></w:body>
</w:document>"""

            val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

            val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

            ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                writeZipTextEntry(zos, "[Content_Types].xml", contentTypes)
                writeZipTextEntry(zos, "_rels/.rels", rels)
                writeZipTextEntry(zos, "word/document.xml", documentXml)
            }
            outputFile.exists() && outputFile.length() > 0
        }.getOrDefault(false)
    }

    // -------------------------------------------------------------------------
    // 9. PDF TO EXCEL (.XLSX)
    // -------------------------------------------------------------------------
    suspend fun pdfToXlsx(pdfFile: File, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val pageTexts = extractTextPerPage(pdfFile)
            outputFile.parentFile?.mkdirs()

            val sheetRows = buildString {
                var rowNum = 1
                pageTexts.forEachIndexed { pageIdx, text ->
                    append("<row r=\"$rowNum\"><c r=\"A$rowNum\" t=\"inlineStr\"><is><t>Page ${pageIdx + 1}</t></is></c></row>")
                    rowNum++
                    for (line in text.lines()) {
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) continue
                        val cols = trimmed.split(Regex("\\s{2,}|\\t|\\|")).filter { it.isNotBlank() }
                        append("<row r=\"$rowNum\">")
                        cols.take(20).forEachIndexed { colIdx, cell ->
                            val colLetter = colName(colIdx)
                            append("<c r=\"$colLetter$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(cell)}</t></is></c>")
                        }
                        append("</row>")
                        rowNum++
                    }
                }
            }

            val sheetXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>$sheetRows</sheetData>
</worksheet>"""

            val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="PDF Extract" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

            val wbRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""

            val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

            val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

            ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                writeZipTextEntry(zos, "[Content_Types].xml", contentTypes)
                writeZipTextEntry(zos, "_rels/.rels", rootRels)
                writeZipTextEntry(zos, "xl/workbook.xml", workbookXml)
                writeZipTextEntry(zos, "xl/_rels/workbook.xml.rels", wbRels)
                writeZipTextEntry(zos, "xl/worksheets/sheet1.xml", sheetXml)
            }
            outputFile.exists() && outputFile.length() > 0
        }.getOrDefault(false)
    }

    private fun colName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A'.code + (n % 26)).toChar())
            n = n / 26 - 1
        }
        return sb.toString()
    }

    // -------------------------------------------------------------------------
    // 10. PDF TO POWERPOINT (.PPTX)
    // -------------------------------------------------------------------------
    suspend fun pdfToPptx(pdfFile: File, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val pageTexts = extractTextPerPage(pdfFile)
            val pageCount = PdfRenderEngine.getPageCount(pdfFile).coerceAtLeast(pageTexts.size)
            if (pageCount == 0) return@runCatching false

            outputFile.parentFile?.mkdirs()
            val slideIdsXml = buildString {
                for (i in 1..pageCount) {
                    append("<p:sldId id=\"${255 + i}\" r:id=\"rId$i\"/>")
                }
            }
            val presRelsXml = buildString {
                for (i in 1..pageCount) {
                    append("<Relationship Id=\"rId$i\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide$i.xml\"/>")
                }
                append("<Relationship Id=\"rId${pageCount + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>")
            }
            val contentTypesXml = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
                append("""<Default Extension="xml" ContentType="application/xml"/>""")
                append("""<Default Extension="jpg" ContentType="image/jpeg"/>""")
                append("""<Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>""")
                append("""<Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>""")
                append("""<Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>""")
                for (i in 1..pageCount) {
                    append("""<Override PartName="/ppt/slides/slide$i.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
                }
                append("</Types>")
            }

            ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                writeZipTextEntry(zos, "[Content_Types].xml", contentTypesXml)
                writeZipTextEntry(
                    zos,
                    "_rels/.rels",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/></Relationships>"""
                )
                writeZipTextEntry(
                    zos,
                    "ppt/presentation.xml",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId${pageCount + 1}"/></p:sldMasterIdLst><p:sldIdLst>$slideIdsXml</p:sldIdLst><p:sldSz cx="9144000" cy="6858000" type="screen4x3"/><p:notesSz cx="6858000" cy="9144000"/></p:presentation>"""
                )
                writeZipTextEntry(
                    zos,
                    "ppt/_rels/presentation.xml.rels",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$presRelsXml</Relationships>"""
                )
                writeZipTextEntry(
                    zos,
                    "ppt/slideMasters/slideMaster1.xml",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld><p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst></p:sldMaster>"""
                )
                writeZipTextEntry(
                    zos,
                    "ppt/slideMasters/_rels/slideMaster1.xml.rels",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/></Relationships>"""
                )
                writeZipTextEntry(
                    zos,
                    "ppt/slideLayouts/slideLayout1.xml",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld></p:sldLayout>"""
                )
                writeZipTextEntry(
                    zos,
                    "ppt/slideLayouts/_rels/slideLayout1.xml.rels",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/></Relationships>"""
                )

                for (i in 0 until pageCount) {
                    val slideNo = i + 1
                    val bmp = PdfRenderEngine.renderPage(pdfFile, i, 1280)
                    if (bmp != null) {
                        val imgBytes = ByteArrayOutputStream().use { baos ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 88, baos)
                            baos.toByteArray()
                        }
                        bmp.recycle()
                        zos.putNextEntry(ZipEntry("ppt/media/image$slideNo.jpg"))
                        zos.write(imgBytes)
                        zos.closeEntry()
                    }
                    val pageText = escapeXml(pageTexts.getOrNull(i)?.take(600) ?: "Slide $slideNo")
                    val slideXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:spTree>
      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
      <p:grpSpPr/>
      <p:pic>
        <p:nvPicPr><p:cNvPr id="2" name="PageImage$slideNo"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>
        <p:blipFill><a:blip r:embed="rId2"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>
        <p:spPr><a:xfrm><a:off x="457200" y="228600"/><a:ext cx="8229600" cy="6400800"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr>
      </p:pic>
      <p:sp>
        <p:nvSpPr><p:cNvPr id="3" name="ExtractedText$slideNo"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr>
        <p:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="100" cy="100"/></a:xfrm></p:spPr>
        <p:txBody><a:bodyPr/><a:p><a:r><a:t>$pageText</a:t></a:r></a:p></p:txBody>
      </p:sp>
    </p:spTree>
  </p:cSld>
</p:sld>"""
                    writeZipTextEntry(zos, "ppt/slides/slide$slideNo.xml", slideXml)
                    writeZipTextEntry(
                        zos,
                        "ppt/slides/_rels/slide$slideNo.xml.rels",
                        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image$slideNo.jpg"/></Relationships>"""
                    )
                }
            }
            outputFile.exists() && outputFile.length() > 0
        }.getOrDefault(false)
    }

    private fun writeZipTextEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private suspend fun extractTextPerPage(pdfFile: File): List<String> = withContext(Dispatchers.IO) {
        if (!pdfFile.exists()) return@withContext emptyList()
        runCatching {
            PDDocument.load(pdfFile).use { doc ->
                val stripper = PDFTextStripper()
                (1..doc.numberOfPages).map { pageNum ->
                    stripper.startPage = pageNum
                    stripper.endPage = pageNum
                    stripper.getText(doc).trim()
                }
            }
        }.getOrDefault(emptyList())
    }

    private data class StyledParagraph(
        val text: String,
        val isHeading: Boolean = false,
        val level: Int = 1,
        val isBold: Boolean = false
    )

    private fun writeStyledParagraphsToPdf(
        paragraphs: List<StyledParagraph>,
        outputFile: File,
        baseFontSize: Float
    ): Boolean {
        return runCatching {
            PDDocument().use { doc ->
                val pageRect = PDRectangle.A4
                val margin = 48f
                val maxWidth = pageRect.width - margin * 2f

                var page = PDPage(pageRect)
                doc.addPage(page)
                var cs = PDPageContentStream(doc, page)
                var y = pageRect.height - margin

                for (para in paragraphs) {
                    if (para.text.isBlank()) {
                        y -= baseFontSize * 0.8f
                        continue
                    }
                    val fontSize = when {
                        para.isHeading && para.level == 1 -> (baseFontSize + 7f).coerceAtMost(24f)
                        para.isHeading && para.level == 2 -> (baseFontSize + 4f).coerceAtMost(20f)
                        para.isHeading -> baseFontSize + 2f
                        else -> baseFontSize
                    }
                    val font: PDFont = if (para.isHeading || para.isBold) {
                        PDType1Font.HELVETICA_BOLD
                    } else {
                        PDType1Font.HELVETICA
                    }
                    val lineHeight = fontSize * 1.42f
                    val wrappedLines = wrapLine(sanitize(para.text), font, fontSize, maxWidth)

                    if (para.isHeading) y -= 6f
                    for (line in wrappedLines) {
                        if (y - lineHeight < margin) {
                            cs.close()
                            page = PDPage(pageRect)
                            doc.addPage(page)
                            cs = PDPageContentStream(doc, page)
                            y = pageRect.height - margin
                        }
                        cs.beginText()
                        cs.setFont(font, fontSize)
                        cs.setNonStrokingColor(19, 27, 46)
                        cs.newLineAtOffset(margin, y)
                        cs.showText(line)
                        cs.endText()
                        y -= lineHeight
                    }
                    y -= if (para.isHeading) 4f else 2f
                }
                cs.close()
                outputFile.parentFile?.mkdirs()
                doc.save(outputFile)
            }
            outputFile.exists() && outputFile.length() > 0
        }.getOrDefault(false)
    }

    private fun wrapLine(text: String, font: PDFont, fontSize: Float, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        val current = StringBuilder()

        for (word in words) {
            val candidate = if (current.isEmpty()) word else "${current} $word"
            val w = runCatching { font.getStringWidth(candidate) / 1000f * fontSize }.getOrDefault(candidate.length * fontSize * 0.55f)
            if (w <= maxWidth) {
                current.clear()
                current.append(candidate)
            } else {
                if (current.isNotEmpty()) {
                    lines.add(current.toString())
                    current.clear()
                }
                current.append(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }
}
