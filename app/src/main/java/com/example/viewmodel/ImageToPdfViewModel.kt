package com.example.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PdfDocumentEntity
import com.example.data.model.ImageItem
import com.example.data.model.NormalizedCropRect
import com.example.data.model.PdfCreationConfig
import com.example.data.model.PdfGenerationProgress
import com.example.data.model.PdfOperationType
import com.example.pdf.ImageToPdfEngine
import com.example.pdf.PdfVaultManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ImageToPdfViewModel(
    private val engine: ImageToPdfEngine,
    private val vaultManager: PdfVaultManager
) : ViewModel() {

    private val _images = MutableStateFlow<List<ImageItem>>(emptyList())
    val images: StateFlow<List<ImageItem>> = _images.asStateFlow()

    private val _config = MutableStateFlow(
        PdfCreationConfig(
            fileName = "Scan_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}"
        )
    )
    val config: StateFlow<PdfCreationConfig> = _config.asStateFlow()

    private val _progress = MutableStateFlow(PdfGenerationProgress())
    val progress: StateFlow<PdfGenerationProgress> = _progress.asStateFlow()

    private val _createdDocument = MutableStateFlow<PdfDocumentEntity?>(null)
    val createdDocument: StateFlow<PdfDocumentEntity?> = _createdDocument.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val estimatedSizeBytes: StateFlow<Long> = combine(_images, _config) { imgs, cfg ->
        engine.estimateOutputSizeBytes(imgs, cfg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val newItems = uris.map { uri ->
            ImageItem(
                uriString = uri.toString(),
                displayName = vaultManager.resolveDisplayName(uri),
                sourceSizeBytes = vaultManager.resolveSizeBytes(uri)
            )
        }
        _images.value = _images.value + newItems
    }

    fun removeImage(id: String) {
        _images.value = _images.value.filterNot { it.id == id }
    }

    fun moveImage(fromIndex: Int, toIndex: Int) {
        val list = _images.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _images.value = list
    }

    fun rotateImage(id: String, deltaDegrees: Int = 90) {
        _images.value = _images.value.map { item ->
            if (item.id == id) {
                item.copy(rotationDegrees = ((item.rotationDegrees + deltaDegrees) % 360 + 360) % 360)
            } else {
                item
            }
        }
    }

    fun updateImageCrop(id: String, cropRect: NormalizedCropRect) {
        _images.value = _images.value.map { item ->
            if (item.id == id) item.copy(cropRect = cropRect) else item
        }
    }

    fun updateImageQualityOverride(id: String, quality: Int?) {
        _images.value = _images.value.map { item ->
            if (item.id == id) item.copy(qualityOverride = quality) else item
        }
    }

    fun updateConfig(transform: (PdfCreationConfig) -> PdfCreationConfig) {
        _config.value = transform(_config.value)
    }

    fun clearCreatedDocument() {
        _createdDocument.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearWorkspace() {
        _images.value = emptyList()
        _createdDocument.value = null
        _progress.value = PdfGenerationProgress()
        _config.value = PdfCreationConfig(
            fileName = "Scan_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}"
        )
    }

    fun generatePdf(onPersisted: (PdfDocumentEntity) -> Unit) {
        val currentImages = _images.value
        if (currentImages.isEmpty()) {
            _errorMessage.value = "Add at least one image first."
            return
        }
        val currentConfig = _config.value
        val outputFile = vaultManager.createUniquePdfFile(currentConfig.fileName)

        viewModelScope.launch {
            _errorMessage.value = null
            _progress.value = PdfGenerationProgress(
                isRunning = true,
                progress = 0.05f,
                currentPage = 1,
                totalPages = currentImages.size,
                statusMessage = "Preparing images..."
            )
            val result = engine.generatePdf(
                items = currentImages,
                config = currentConfig,
                outputFile = outputFile,
                onProgress = { _progress.value = it }
            )
            result.onSuccess { file ->
                val now = System.currentTimeMillis()
                val entity = PdfDocumentEntity(
                    id = UUID.randomUUID().toString(),
                    fileName = file.name,
                    filePath = file.absolutePath,
                    operation = PdfOperationType.IMAGE_TO_PDF.key,
                    pageCount = currentImages.size,
                    fileSizeBytes = file.length(),
                    isFavorite = false,
                    isPasswordProtected = currentConfig.password.isNotBlank(),
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
                _createdDocument.value = entity
                onPersisted(entity)
            }.onFailure { err ->
                _progress.value = PdfGenerationProgress()
                _errorMessage.value = err.localizedMessage ?: "Failed to generate PDF."
            }
        }
    }
}
