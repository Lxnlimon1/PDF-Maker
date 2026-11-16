package com.example.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import com.example.data.model.PageSizeOption
import com.example.data.model.ScanFilter
import com.example.data.model.ScanPageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object ScannerProcessor {

    /**
     * Pure-Kotlin edge-energy & quad corner detector for document scanning.
     * Computes grayscale luminance + Sobel gradient magnitude on a downscaled buffer,
     * identifies the primary rectangular paper boundary, and returns 4 normalized corners
     * ordered [TopLeft, TopRight, BottomRight, BottomLeft].
     */
    suspend fun detectDocumentCorners(bitmap: Bitmap): List<Offset> = withContext(Dispatchers.Default) {
        val targetW = 160
        val aspect = bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1).toFloat()
        val targetH = (targetW * aspect).toInt().coerceIn(80, 260)
        val small = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)

        val pixels = IntArray(targetW * targetH)
        small.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        if (small !== bitmap) small.recycle()

        val lum = IntArray(targetW * targetH)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            lum[i] = (r * 30 + g * 59 + b * 11) / 100
        }

        val edge = IntArray(targetW * targetH)
        var maxEdge = 1
        for (y in 2 until targetH - 2) {
            for (x in 2 until targetW - 2) {
                val idx = y * targetW + x
                val gx = -lum[idx - targetW - 1] + lum[idx - targetW + 1] -
                    2 * lum[idx - 1] + 2 * lum[idx + 1] -
                    lum[idx + targetW - 1] + lum[idx + targetW + 1]
                val gy = -lum[idx - targetW - 1] - 2 * lum[idx - targetW] - lum[idx - targetW + 1] +
                    lum[idx + targetW - 1] + 2 * lum[idx + targetW] + lum[idx + targetW + 1]
                val mag = abs(gx) + abs(gy)
                edge[idx] = mag
                if (mag > maxEdge) maxEdge = mag
            }
        }

        val threshold = (maxEdge * 0.24f).toInt().coerceAtLeast(45)
        var tlBest = Offset(0.08f, 0.08f)
        var trBest = Offset(0.92f, 0.08f)
        var brBest = Offset(0.92f, 0.92f)
        var blBest = Offset(0.08f, 0.92f)

        var minSum = Float.MAX_VALUE
        var maxSum = Float.MIN_VALUE
        var minDiff = Float.MAX_VALUE
        var maxDiff = Float.MIN_VALUE
        var hitCount = 0

        val marginX = (targetW * 0.03f).toInt()
        val marginY = (targetH * 0.03f).toInt()

        for (y in marginY until targetH - marginY) {
            for (x in marginX until targetW - marginX) {
                if (edge[y * targetW + x] >= threshold) {
                    hitCount++
                    val nx = x.toFloat() / targetW
                    val ny = y.toFloat() / targetH
                    val sum = nx + ny
                    val diff = nx - ny
                    if (sum < minSum) {
                        minSum = sum
                        tlBest = Offset(nx, ny)
                    }
                    if (sum > maxSum) {
                        maxSum = sum
                        brBest = Offset(nx, ny)
                    }
                    if (diff > maxDiff) {
                        maxDiff = diff
                        trBest = Offset(nx, ny)
                    }
                    if (diff < minDiff) {
                        minDiff = diff
                        blBest = Offset(nx, ny)
                    }
                }
            }
        }

        // Validate detected quad has reasonable area; otherwise return balanced default frame
        val widthEstimate = hypot((trBest.x - tlBest.x).toDouble(), (trBest.y - tlBest.y).toDouble())
        val heightEstimate = hypot((blBest.x - tlBest.x).toDouble(), (blBest.y - tlBest.y).toDouble())
        if (hitCount < 25 || widthEstimate < 0.25 || heightEstimate < 0.25) {
            listOf(
                Offset(0.08f, 0.08f),
                Offset(0.92f, 0.08f),
                Offset(0.92f, 0.92f),
                Offset(0.08f, 0.92f)
            )
        } else {
            listOf(
                Offset(tlBest.x.coerceIn(0.02f, 0.48f), tlBest.y.coerceIn(0.02f, 0.48f)),
                Offset(trBest.x.coerceIn(0.52f, 0.98f), trBest.y.coerceIn(0.02f, 0.48f)),
                Offset(brBest.x.coerceIn(0.52f, 0.98f), brBest.y.coerceIn(0.52f, 0.98f)),
                Offset(blBest.x.coerceIn(0.02f, 0.48f), blBest.y.coerceIn(0.52f, 0.98f))
            )
        }
    }

    /**
     * Performs 4-point projective perspective correction using Matrix.setPolyToPoly.
     */
    suspend fun perspectiveCrop(
        source: Bitmap,
        corners: List<Offset>,
        rotationDegrees: Int = 0
    ): Bitmap = withContext(Dispatchers.Default) {
        val pts = if (corners.size == 4) corners else listOf(
            Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f)
        )
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val tl = Offset(pts[0].x * w, pts[0].y * h)
        val tr = Offset(pts[1].x * w, pts[1].y * h)
        val br = Offset(pts[2].x * w, pts[2].y * h)
        val bl = Offset(pts[3].x * w, pts[3].y * h)

        val topW = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble()).toFloat()
        val botW = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble()).toFloat()
        val leftH = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble()).toFloat()
        val rightH = hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble()).toFloat()

        val outW = max(topW, botW).toInt().coerceIn(240, 2400)
        val outH = max(leftH, rightH).toInt().coerceIn(240, 3200)

        val srcPoly = floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
        val dstPoly = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoly, 0, dstPoly, 0, 4)

        val warped = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(warped)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)

        if (rotationDegrees % 360 != 0) {
            val rotMatrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(warped, 0, 0, warped.width, warped.height, rotMatrix, true)
            if (rotated !== warped) warped.recycle()
            rotated
        } else {
            warped
        }
    }

    suspend fun applyScanFilter(
        source: Bitmap,
        filter: ScanFilter,
        brightness: Float = 0f,
        contrast: Float = 1.0f
    ): Bitmap = withContext(Dispatchers.Default) {
        if (filter == ScanFilter.ORIGINAL && abs(brightness) < 0.01f && abs(contrast - 1f) < 0.01f) {
            return@withContext source.copy(Bitmap.Config.ARGB_8888, false)
        }

        if (filter == ScanFilter.BLACK_AND_WHITE) {
            return@withContext applyAdaptiveDocumentThreshold(source, brightness)
        }

        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix()

        when (filter) {
            ScanFilter.ORIGINAL -> Unit
            ScanFilter.GRAYSCALE -> {
                cm.setSaturation(0f)
                val c = 1.12f * contrast
                val t = 128f * (1f - c) + brightness * 45f
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            c, 0f, 0f, 0f, t,
                            0f, c, 0f, 0f, t,
                            0f, 0f, c, 0f, t,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            ScanFilter.AUTO_ENHANCE -> {
                val c = 1.24f * contrast
                val t = 128f * (1f - c) + 12f + brightness * 45f
                cm.set(
                    floatArrayOf(
                        c, 0f, 0f, 0f, t,
                        0f, c, 0f, 0f, t,
                        0f, 0f, c, 0f, t,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            ScanFilter.COLOR_ENHANCE -> {
                cm.setSaturation(1.38f)
                val c = 1.18f * contrast
                val t = 128f * (1f - c) + 10f + brightness * 45f
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            c, 0f, 0f, 0f, t,
                            0f, c, 0f, 0f, t,
                            0f, 0f, c, 0f, t,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            ScanFilter.BLACK_AND_WHITE -> Unit
        }

        if (filter == ScanFilter.ORIGINAL) {
            val c = contrast
            val t = 128f * (1f - c) + brightness * 45f
            cm.set(
                floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        out
    }

    private fun applyAdaptiveDocumentThreshold(source: Bitmap, brightnessShift: Float): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val lum = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            lum[i] = (r * 30 + g * 59 + b * 11) / 100
        }

        // Integral image for fast local adaptive mean thresholding
        val integral = LongArray((w + 1) * (h + 1))
        val stride = w + 1
        for (y in 0 until h) {
            var rowSum = 0L
            for (x in 0 until w) {
                rowSum += lum[y * w + x]
                integral[(y + 1) * stride + (x + 1)] = integral[y * stride + (x + 1)] + rowSum
            }
        }

        val radius = max(10, min(w, h) / 28)
        val bias = (11f - brightnessShift * 18f).toInt()
        val outPixels = IntArray(w * h)

        for (y in 0 until h) {
            val y1 = max(0, y - radius)
            val y2 = min(h - 1, y + radius)
            for (x in 0 until w) {
                val x1 = max(0, x - radius)
                val x2 = min(w - 1, x + radius)
                val area = (x2 - x1 + 1) * (y2 - y1 + 1)
                val sum = integral[(y2 + 1) * stride + (x2 + 1)] -
                    integral[y1 * stride + (x2 + 1)] -
                    integral[(y2 + 1) * stride + x1] +
                    integral[y1 * stride + x1]
                val localMean = (sum / area).toInt()
                val v = lum[y * w + x]
                outPixels[y * w + x] = if (v < localMean - bias) Color.rgb(22, 24, 30) else Color.WHITE
            }
        }

        return Bitmap.createBitmap(outPixels, w, h, Bitmap.Config.ARGB_8888)
    }

    suspend fun saveBitmapToFile(bitmap: Bitmap, outFile: File, quality: Int = 92): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
                }
                true
            }.getOrDefault(false)
        }

    suspend fun exportScansToPdf(
        scanPages: List<ScanPageItem>,
        pageSize: PageSizeOption,
        highResolution: Boolean,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val maxDim = if (highResolution) 2100 else 1280
        val bitmaps = mutableListOf<Bitmap>()
        try {
            for (page in scanPages) {
                val srcFile = File(page.filteredPath).takeIf { it.exists() }
                    ?: File(page.croppedPath).takeIf { it.exists() }
                    ?: File(page.originalPath)
                if (!srcFile.exists()) continue
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(srcFile.absolutePath, opts)
                var sample = 1
                while (max(opts.outWidth, opts.outHeight) / sample > maxDim * 2) {
                    sample *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = BitmapFactory.decodeFile(srcFile.absolutePath, decodeOpts) ?: continue
                bitmaps.add(bmp)
            }
            PdfContentEngine.buildPdfFromBitmaps(
                bitmaps = bitmaps,
                pageSize = pageSize,
                marginPt = 12f,
                outputFile = outputFile
            )
        } finally {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }
}
