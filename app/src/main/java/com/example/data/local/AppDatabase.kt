package com.example.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Database
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pdf_documents")
data class PdfDocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val filePath: String,
    val thumbnailPath: String?,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceTag: String
)

@Entity(tableName = "saved_signatures")
data class SavedSignatureEntity(
    @PrimaryKey val id: String,
    val label: String,
    val imagePath: String,
    val createdAt: Long
)

@Dao
interface PdfDocumentDao {
    @Query("SELECT * FROM pdf_documents ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PdfDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PdfDocumentEntity)

    @Query("DELETE FROM pdf_documents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM pdf_documents")
    suspend fun count(): Int
}

@Dao
interface SavedSignatureDao {
    @Query("SELECT * FROM saved_signatures ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedSignatureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavedSignatureEntity)

    @Query("DELETE FROM saved_signatures WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Database(
    entities = [PdfDocumentEntity::class, SavedSignatureEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pdfDocumentDao(): PdfDocumentDao
    abstract fun savedSignatureDao(): SavedSignatureDao
}
