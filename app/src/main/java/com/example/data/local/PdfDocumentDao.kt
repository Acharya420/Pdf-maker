package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDocumentDao {
    @Query("SELECT * FROM pdf_documents ORDER BY updatedAtMillis DESC")
    fun observeAllDocuments(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE isFavorite = 1 ORDER BY updatedAtMillis DESC")
    fun observeFavorites(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PdfDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: PdfDocumentEntity)

    @Update
    suspend fun update(document: PdfDocumentEntity)

    @Query("UPDATE pdf_documents SET isFavorite = :isFavorite, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE pdf_documents SET fileName = :newName, filePath = :newPath, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, newName: String, newPath: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM pdf_documents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COALESCE(SUM(fileSizeBytes), 0) FROM pdf_documents")
    fun observeTotalStorageBytes(): Flow<Long>
}
