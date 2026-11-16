package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfRenderEngine {
    private val renderMutex = Mutex()

    suspend fun getPageCount(pdfFile: File): Int = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return@withContext 0
        renderMutex.withLock {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            }
        }
    }

    suspend fun renderPage(
        pdfFile: File,
        pageIndex: Int,
        targetWidthPx: Int = 1240
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return@withContext null
        renderMutex.withLock {
            runCatching {
                ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageIndex !in 0 until renderer.pageCount) return@runCatching null
                        renderer.openPage(pageIndex).use { page ->
                            val aspect = if (page.width > 0) page.height.toFloat() / page.width.toFloat() else 1.414f
                            val width = targetWidthPx.coerceAtLeast(160)
                            val height = (width * aspect).toInt().coerceAtLeast(160)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    }
                }
            }.getOrNull()
        }
    }

    suspend fun renderAllThumbnails(
        pdfFile: File,
        thumbWidthPx: Int = 320
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return@withContext emptyList()
        renderMutex.withLock {
            runCatching {
                val results = mutableListOf<Bitmap>()
                ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { page ->
                                val aspect = if (page.width > 0) page.height.toFloat() / page.width.toFloat() else 1.414f
                                val w = thumbWidthPx.coerceAtLeast(100)
                                val h = (w * aspect).toInt().coerceAtLeast(100)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                Canvas(bmp).drawColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                results.add(bmp)
                            }
                        }
                    }
                }
                results
            }.getOrDefault(emptyList())
        }
    }

    suspend fun generateFirstPageThumbnail(pdfFile: File, outThumbFile: File): Boolean =
        withContext(Dispatchers.IO) {
            val bmp = renderPage(pdfFile, 0, targetWidthPx = 420) ?: return@withContext false
            runCatching {
                outThumbFile.parentFile?.mkdirs()
                FileOutputStream(outThumbFile).use { fos ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 86, fos)
                }
                bmp.recycle()
                true
            }.getOrDefault(false)
        }
}
