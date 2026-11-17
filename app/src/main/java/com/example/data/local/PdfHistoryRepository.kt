package com.example.data.local

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PdfHistoryRepository(private val dao: PdfDocumentDao) {

    val allDocuments: Flow<List<PdfDocumentEntity>> = dao.observeAllDocuments()
    val favoriteDocuments: Flow<List<PdfDocumentEntity>> = dao.observeFavorites()
    val totalStorageBytes: Flow<Long> = dao.observeTotalStorageBytes()

    suspend fun getDocumentById(id: String): PdfDocumentEntity? = dao.getById(id)

    suspend fun saveDocument(document: PdfDocumentEntity) {
        dao.upsert(document)
    }

    suspend fun toggleFavorite(id: String, currentFavorite: Boolean) {
        dao.setFavorite(id, !currentFavorite)
    }

    suspend fun renameDocument(document: PdfDocumentEntity, newBaseName: String): Result<PdfDocumentEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sanitized = newBaseName.trim()
                    .replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
                    .removeSuffix(".pdf")
                    .ifBlank { "Document" }
                val finalName = "$sanitized.pdf"
                val currentFile = File(document.filePath)
                val targetFile = File(currentFile.parentFile ?: currentFile.absoluteFile.parentFile!!, finalName)
                if (currentFile.exists() && currentFile.absolutePath != targetFile.absolutePath) {
                    val uniqueTarget = if (targetFile.exists()) {
                        File(targetFile.parentFile, "${sanitized}_${System.currentTimeMillis() % 10000}.pdf")
                    } else {
                        targetFile
                    }
                    check(currentFile.renameTo(uniqueTarget)) { "Failed to rename PDF on disk" }
                    val updated = document.copy(
                        fileName = uniqueTarget.name,
                        filePath = uniqueTarget.absolutePath,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                    dao.rename(document.id, updated.fileName, updated.filePath, updated.updatedAtMillis)
                    updated
                } else {
                    val updated = document.copy(
                        fileName = finalName,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                    dao.rename(document.id, finalName, document.filePath, updated.updatedAtMillis)
                    updated
                }
            }
        }

    suspend fun deleteDocument(document: PdfDocumentEntity) {
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(document.filePath)
                if (file.exists()) file.delete()
            }
            dao.deleteById(document.id)
        }
    }
}
