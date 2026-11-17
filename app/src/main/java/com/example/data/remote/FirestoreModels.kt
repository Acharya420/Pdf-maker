package com.example.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val preferredTheme: String = "system",
    val preferredLanguage: String = "en",
    val pdfsCreatedCount: Long = 0L,
    val totalBytesProcessed: Long = 0L,
    val status: String = "active",
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PdfSyncRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fileName: String = "",
    val operation: String = "image_to_pdf",
    val pageCount: Long = 1L,
    val fileSizeBytes: Long = 0L,
    val isFavorite: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppGlobalConfig(
    @DocumentId val id: String = "global",
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "",
    val latestVersionCode: Long = 1L,
    val latestVersionName: String = "1.0",
    val minSupportedVersionCode: Long = 1L,
    val forceUpdate: Boolean = false,
    val releaseNotes: String = "",
    val enableImageToPdf: Boolean = true,
    val enableCompress: Boolean = true,
    val enableMerge: Boolean = true,
    val enableSplit: Boolean = true,
    val enableOrganizer: Boolean = true,
    val enablePasswordProtect: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Announcement(
    @DocumentId val id: String = "",
    val title: String = "",
    val message: String = "",
    val severity: String = "info",
    val isActive: Boolean = true,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserFeedback(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val category: String = "general",
    val subject: String = "",
    val message: String = "",
    val appVersion: String = "1.0",
    val status: String = "open",
    val adminReply: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
