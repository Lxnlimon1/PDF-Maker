package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CompressionLevel
import com.example.data.model.PageSizeOption
import com.example.data.model.ScanFilter
import com.example.engine.PdfContentEngine
import com.example.engine.PdfConversionEngine
import com.example.engine.ScannerProcessor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches PDF Master`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PDF Master", appName)
    }

    @Test
    fun `genuine pdf content creation and stream text edit works`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(context)
        val pdfFile = File(context.cacheDir, "test_stream.pdf")
        PdfContentEngine.createBlankPdf(pdfFile, "Original Heading")
        assertTrue(pdfFile.exists() && pdfFile.length() > 0)

        val blocks = PdfContentEngine.extractTextBlocks(pdfFile, 0)
        assertTrue(blocks.any { it.text.contains("Original Heading") })

        val target = blocks.first { it.text.contains("Original Heading") }
        val edited = PdfContentEngine.editExistingTextInStream(pdfFile, 0, target, "Updated Heading")
        assertTrue(edited)

        val updatedBlocks = PdfContentEngine.extractTextBlocks(pdfFile, 0)
        assertTrue(updatedBlocks.any { it.text.contains("Updated Heading") })
        pdfFile.delete()
    }

    @Test
    fun `conversions html text and office outputs are valid`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(context)

        val htmlPdf = File(context.cacheDir, "test_html.pdf")
        assertTrue(PdfConversionEngine.htmlToPdf("<h1>Hello</h1><p>World</p>", htmlPdf))
        assertTrue(htmlPdf.length() > 0)

        val docxOut = File(context.cacheDir, "test_out.docx")
        assertTrue(PdfConversionEngine.pdfToDocx(htmlPdf, docxOut))
        assertTrue(docxOut.length() > 0)

        val roundTripPdf = File(context.cacheDir, "test_docx_rt.pdf")
        assertTrue(docxOut.inputStream().use { PdfConversionEngine.docxToPdf(it, roundTripPdf) })
        assertTrue(roundTripPdf.length() > 0)

        val xlsxOut = File(context.cacheDir, "test_out.xlsx")
        assertTrue(PdfConversionEngine.pdfToXlsx(htmlPdf, xlsxOut))
        assertTrue(xlsxOut.length() > 0)

        val bmp = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val bw = ScannerProcessor.applyScanFilter(bmp, ScanFilter.BLACK_AND_WHITE)
        val scanPdf = File(context.cacheDir, "test_scan.pdf")
        assertTrue(PdfContentEngine.buildPdfFromBitmaps(listOf(bw), PageSizeOption.A4, 12f, scanPdf))
        val compPdf = File(context.cacheDir, "test_comp.pdf")
        val stats = PdfContentEngine.compressPdf(scanPdf, compPdf, CompressionLevel.BALANCED)
        assertTrue(stats != null && compPdf.length() > 0)

        bmp.recycle()
        bw.recycle()
        listOf(htmlPdf, docxOut, roundTripPdf, xlsxOut, scanPdf, compPdf).forEach { it.delete() }
    }
}
