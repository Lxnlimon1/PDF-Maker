package com.example.data.repository

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.PdfDocumentDao
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.SavedSignatureDao
import com.example.data.local.SavedSignatureEntity
import com.example.data.model.PdfDocumentUi
import com.example.engine.PdfContentEngine
import com.example.engine.PdfRenderEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class PdfLibraryRepository(
    private val context: Context,
    private val docDao: PdfDocumentDao,
    private val signatureDao: SavedSignatureDao
) {
    val pdfDir: File = File(context.filesDir, "pdfs").apply { mkdirs() }
    val thumbDir: File = File(context.filesDir, "thumbnails").apply { mkdirs() }
    val scanDir: File = File(context.filesDir, "scans").apply { mkdirs() }
    val signatureDir: File = File(context.filesDir, "signatures").apply { mkdirs() }
    val exportDir: File = File(context.filesDir, "exports").apply { mkdirs() }

    val documentsFlow: Flow<List<PdfDocumentUi>> = docDao.observeAll().map { list ->
        list.filter { File(it.filePath).exists() }.map { it.toUi() }
    }

    val signaturesFlow: Flow<List<SavedSignatureEntity>> = signatureDao.observeAll().map { list ->
        list.filter { File(it.imagePath).exists() }
    }

    suspend fun getDocumentById(id: String): PdfDocumentUi? = withContext(Dispatchers.IO) {
        docDao.getById(id)?.toUi()
    }

    suspend fun importPdfUri(uri: Uri): PdfDocumentUi? = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri)?.removeSuffix(".pdf")?.removeSuffix(".PDF")
            ?: "Imported_${System.currentTimeMillis()}"
        val id = UUID.randomUUID().toString()
        val destFile = File(pdfDir, "$id.pdf")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.exists() && destFile.length() > 0
        }.getOrDefault(false)

        if (!copied) return@withContext null
        registerExistingPdf(destFile, displayName, sourceTag = "Imported", id = id)
    }

    suspend fun createBlankDocument(title: String): PdfDocumentUi? = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val destFile = File(pdfDir, "$id.pdf")
        PdfContentEngine.createBlankPdf(destFile, title)
        registerExistingPdf(destFile, title, sourceTag = "Created", id = id)
    }

    suspend fun createSampleStarterDocument(): PdfDocumentUi? = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val destFile = File(pdfDir, "$id.pdf")
        PdfContentEngine.createStarterSamplePdf(destFile)
        registerExistingPdf(destFile, "Project Proposal Sample", sourceTag = "Template", id = id)
    }

    suspend fun registerExistingPdf(
        file: File,
        title: String,
        sourceTag: String,
        id: String = UUID.randomUUID().toString()
    ): PdfDocumentUi? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext null
        val targetFile = if (file.parentFile == pdfDir) {
            file
        } else {
            val copy = File(pdfDir, "$id.pdf")
            file.copyTo(copy, overwrite = true)
            copy
        }
        val pageCount = PdfRenderEngine.getPageCount(targetFile).coerceAtLeast(1)
        val thumbFile = File(thumbDir, "$id.jpg")
        val hasThumb = PdfRenderEngine.generateFirstPageThumbnail(targetFile, thumbFile)
        val now = System.currentTimeMillis()
        val entity = PdfDocumentEntity(
            id = id,
            title = title,
            filePath = targetFile.absolutePath,
            thumbnailPath = if (hasThumb) thumbFile.absolutePath else null,
            pageCount = pageCount,
            fileSizeBytes = targetFile.length(),
            createdAt = now,
            updatedAt = now,
            sourceTag = sourceTag
        )
        docDao.upsert(entity)
        entity.toUi()
    }

    suspend fun refreshDocumentMetadata(id: String) = withContext(Dispatchers.IO) {
        val existing = docDao.getById(id) ?: return@withContext
        val file = File(existing.filePath)
        if (!file.exists()) return@withContext
        val pageCount = PdfRenderEngine.getPageCount(file).coerceAtLeast(1)
        val thumbFile = File(thumbDir, "$id.jpg")
        val hasThumb = PdfRenderEngine.generateFirstPageThumbnail(file, thumbFile)
        docDao.upsert(
            existing.copy(
                pageCount = pageCount,
                fileSizeBytes = file.length(),
                thumbnailPath = if (hasThumb) thumbFile.absolutePath else existing.thumbnailPath,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun renameDocument(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        val existing = docDao.getById(id) ?: return@withContext
        if (newTitle.isBlank()) return@withContext
        docDao.upsert(existing.copy(title = newTitle.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteDocument(id: String) = withContext(Dispatchers.IO) {
        val existing = docDao.getById(id)
        docDao.deleteById(id)
        if (existing != null) {
            File(existing.filePath).delete()
            existing.thumbnailPath?.let { File(it).delete() }
        }
    }

    suspend fun saveSignature(label: String, pngFile: File): SavedSignatureEntity =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val entity = SavedSignatureEntity(
                id = id,
                label = label.ifBlank { "Signature" },
                imagePath = pngFile.absolutePath,
                createdAt = System.currentTimeMillis()
            )
            signatureDao.upsert(entity)
            entity
        }

    suspend fun deleteSignature(id: String, imagePath: String) = withContext(Dispatchers.IO) {
        signatureDao.deleteById(id)
        File(imagePath).delete()
    }

    fun resolveDisplayName(uri: Uri): String? {
        var name: String? = null
        val cursor: Cursor? = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        }.getOrNull()
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = it.getString(idx)
            }
        }
        return name ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun PdfDocumentEntity.toUi() = PdfDocumentUi(
        id = id,
        title = title,
        filePath = filePath,
        thumbnailPath = thumbnailPath,
        pageCount = pageCount,
        fileSizeBytes = fileSizeBytes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sourceTag = sourceTag
    )
}
