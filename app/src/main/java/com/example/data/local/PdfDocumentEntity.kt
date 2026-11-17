package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_documents")
data class PdfDocumentEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val filePath: String,
    val operation: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val isFavorite: Boolean = false,
    val isPasswordProtected: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)
