package com.example.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PdfDocumentEntity
import com.example.data.model.PageOrganizeItem
import com.example.data.model.PdfDocumentMetadata
import com.example.data.model.PdfOperationType
import com.example.data.model.SplitMode
import com.example.pdf.PdfLockedException
import com.example.pdf.PdfToolsEngine
import com.example.pdf.PdfVaultManager
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SelectedPdfSource(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val password: String? = null
)

data class PdfToolExecutionState(
    val isBusy: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val resultDocuments: List<PdfDocumentEntity> = emptyList(),
    val exportedImages: List<File> = emptyList(),
    val error: String? = null,
    val needsPassword: Boolean = false,
    val compressionOriginalBytes: Long = 0L,
    val compressionResultBytes: Long = 0L
)

class PdfToolsViewModel(
    private val engine: PdfToolsEngine,
    private val vaultManager: PdfVaultManager
) : ViewModel() {

    private val _toolState = MutableStateFlow(PdfToolExecutionState())
    val toolState: StateFlow<PdfToolExecutionState> = _toolState.asStateFlow()

    private val _mergeSources = MutableStateFlow<List<SelectedPdfSource>>(emptyList())
    val mergeSources: StateFlow<List<SelectedPdfSource>> = _mergeSources.asStateFlow()

    private val _singleSource = MutableStateFlow<SelectedPdfSource?>(null)
    val singleSource: StateFlow<SelectedPdfSource?> = _singleSource.asStateFlow()

    private val _inspectedMeta = MutableStateFlow<PdfDocumentMetadata?>(null)
    val inspectedMeta: StateFlow<PdfDocumentMetadata?> = _inspectedMeta.asStateFlow()

    private val _organizerThumbnails = MutableStateFlow<List<Bitmap>>(emptyList())
    val organizerThumbnails: StateFlow<List<Bitmap>> = _organizerThumbnails.asStateFlow()

    private val _organizerItems = MutableStateFlow<List<PageOrganizeItem>>(emptyList())
    val organizerItems: StateFlow<List<PageOrganizeItem>> = _organizerItems.asStateFlow()

    private val _viewerPageBitmap = MutableStateFlow<Bitmap?>(null)
    val viewerPageBitmap: StateFlow<Bitmap?> = _viewerPageBitmap.asStateFlow()

    fun resetToolState() {
        _toolState.value = PdfToolExecutionState()
    }

    fun clearNeedsPassword() {
        _toolState.value = _toolState.value.copy(needsPassword = false, error = null)
    }

    fun addMergeSources(uris: List<Uri>) {
        val added = uris.map { uri ->
            SelectedPdfSource(
                uri = uri,
                displayName = vaultManager.resolveDisplayName(uri),
                sizeBytes = vaultManager.resolveSizeBytes(uri)
            )
        }
        _mergeSources.value = _mergeSources.value + added
    }

    fun removeMergeSource(index: Int) {
        val list = _mergeSources.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _mergeSources.value = list
        }
    }

    fun moveMergeSource(from: Int, to: Int) {
        val list = _mergeSources.value.toMutableList()
        if (from in list.indices && to in list.indices && from != to) {
            val item = list.removeAt(from)
            list.add(to, item)
            _mergeSources.value = list
        }
    }

    fun setMergeSourcePassword(index: Int, password: String) {
        _mergeSources.value = _mergeSources.value.mapIndexed { idx, src ->
            if (idx == index) src.copy(password = password) else src
        }
    }

    fun selectSinglePdf(uri: Uri, password: String? = null, loadThumbnails: Boolean = false) {
        val src = SelectedPdfSource(
            uri = uri,
            displayName = vaultManager.resolveDisplayName(uri),
            sizeBytes = vaultManager.resolveSizeBytes(uri),
            password = password
        )
        _singleSource.value = src
        _inspectedMeta.value = null
        _organizerItems.value = emptyList()
        _organizerThumbnails.value = emptyList()
        _toolState.value = _toolState.value.copy(
            isBusy = true,
            statusText = "Inspecting PDF...",
            error = null,
            needsPassword = false
        )

        viewModelScope.launch {
            engine.inspectPdf(uri, password).onSuccess { meta ->
                _inspectedMeta.value = meta
                if (loadThumbnails) {
                    engine.renderAllThumbnails(uri, password).onSuccess { bitmaps ->
                        _organizerThumbnails.value = bitmaps
                        _organizerItems.value = (0 until bitmaps.size).map { PageOrganizeItem(it) }
                    }
                }
                _toolState.value = _toolState.value.copy(isBusy = false, statusText = "")
            }.onFailure { err ->
                if (err is PdfLockedException) {
                    _toolState.value = _toolState.value.copy(
                        isBusy = false,
                        needsPassword = true,
                        error = "Enter password to unlock ${src.displayName}"
                    )
                } else {
                    _toolState.value = _toolState.value.copy(
                        isBusy = false,
                        error = err.localizedMessage ?: "Failed to read PDF"
                    )
                }
            }
        }
    }

    fun loadViewerPage(uri: Uri, pageIndex: Int, password: String?) {
        viewModelScope.launch {
            engine.renderPageBitmap(uri, pageIndex, password).onSuccess { bmp ->
                _viewerPageBitmap.value = bmp
            }.onFailure { err ->
                if (err is PdfLockedException) {
                    _toolState.value = _toolState.value.copy(needsPassword = true, error = err.message)
                } else {
                    _toolState.value = _toolState.value.copy(error = err.localizedMessage)
                }
            }
        }
    }

    fun rotateOrganizerPage(position: Int) {
        _organizerItems.value = _organizerItems.value.mapIndexed { idx, item ->
            if (idx == position) {
                item.copy(rotationDegrees = (item.rotationDegrees + 90) % 360)
            } else {
                item
            }
        }
    }

    fun toggleDeleteOrganizerPage(position: Int) {
        _organizerItems.value = _organizerItems.value.mapIndexed { idx, item ->
            if (idx == position) item.copy(isDeleted = !item.isDeleted) else item
        }
    }

    fun moveOrganizerPage(from: Int, to: Int) {
        val list = _organizerItems.value.toMutableList()
        if (from in list.indices && to in list.indices && from != to) {
            val item = list.removeAt(from)
            list.add(to, item)
            _organizerItems.value = list
        }
    }

    fun executeMerge(outputName: String, onSaved: (PdfDocumentEntity) -> Unit) {
        val sources = _mergeSources.value
        if (sources.size < 2) {
            _toolState.value = _toolState.value.copy(error = "Select at least 2 PDFs to merge.")
            return
        }
        viewModelScope.launch {
            _toolState.value = PdfToolExecutionState(isBusy = true, statusText = "Merging ${sources.size} PDFs...")
            val outFile = vaultManager.createUniquePdfFile(outputName)
            val pwMap = sources.mapIndexedNotNull { idx, src -> src.password?.let { idx to it } }.toMap()
            engine.mergePdfs(sources.map { it.uri }, pwMap, outFile) { p ->
                _toolState.value = _toolState.value.copy(progress = p)
            }.onSuccess { (file, pages) ->
                val doc = buildEntity(file, PdfOperationType.MERGE.key, pages, false)
                onSaved(doc)
                _toolState.value = PdfToolExecutionState(resultDocuments = listOf(doc))
            }.onFailure { err ->
                _toolState.value = PdfToolExecutionState(
                    needsPassword = err is PdfLockedException,
                    error = err.localizedMessage ?: "Merge failed"
                )
            }
        }
    }

    fun executeSplit(mode: SplitMode, outputBase: String, onSaved: (PdfDocumentEntity) -> Unit) {
        val src = _singleSource.value ?: return
        viewModelScope.launch {
            _toolState.value = PdfToolExecutionState(isBusy = true, statusText = "Splitting PDF...")
            engine.splitPdf(src.uri, src.password, mode, outputBase) { p ->
                _toolState.value = _toolState.value.copy(progress = p)
            }.onSuccess { files ->
                val entities = files.map { (file, pages) ->
                    buildEntity(file, PdfOperationType.SPLIT.key, pages, false).also(onSaved)
                }
                _toolState.value = PdfToolExecutionState(resultDocuments = entities)
            }.onFailure { err ->
                _toolState.value = PdfToolExecutionState(
                    needsPassword = err is PdfLockedException,
                    error = err.localizedMessage ?: "Split failed"
                )
            }
        }
    }

    fun executeCompress(
        jpegQuality: Int,
        dpi: Int,
        outputName: String,
        onSaved: (PdfDocumentEntity) -> Unit
    ) {
        val src = _singleSource.value ?: return
        val origSize = _inspectedMeta.value?.fileSizeBytes ?: src.sizeBytes
        viewModelScope.launch {
            _toolState.value = PdfToolExecutionState(isBusy = true, statusText = "Compressing PDF pages...")
            val outFile = vaultManager.createUniquePdfFile(outputName)
            engine.compressPdf(src.uri, src.password, jpegQuality, dpi, outFile) { p ->
                _toolState.value = _toolState.value.copy(progress = p)
            }.onSuccess { (file, pages) ->
                val doc = buildEntity(file, PdfOperationType.COMPRESS.key, pages, false)
                onSaved(doc)
                _toolState.value = PdfToolExecutionState(
                    resultDocuments = listOf(doc),
                    compressionOriginalBytes = origSize,
                    compressionResultBytes = file.length()
                )
            }.onFailure { err ->
                _toolState.value = PdfToolExecutionState(
                    needsPassword = err is PdfLockedException,
                    error = err.localizedMessage ?: "Compression failed"
                )
            }
        }
    }

    fun executeOrganize(outputName: String, onSaved: (PdfDocumentEntity) -> Unit) {
        val src = _singleSource.value ?: return
        val items = _organizerItems.value
        viewModelScope.launch {
            _toolState.value = PdfToolExecutionState(isBusy = true, statusText = "Rebuilding PDF pages...")
            val outFile = vaultManager.createUniquePdfFile(outputName)
            engine.organizePages(src.uri, src.password, items, outFile)
                .onSuccess { (file, pages) ->
                    val doc = buildEntity(file, PdfOperationType.ORGANIZE.key, pages, false)
                    onSaved(doc)
                    _toolState.value = PdfToolExecutionState(resultDocuments = listOf(doc))
                }
                .onFailure { err ->
                    _toolState.value = PdfToolExecutionState(error = err.localizedMessage ?: "Organize failed")
                }
        }
    }

    fun executeRotateAll(deltaDegrees: Int, outputName: String, onSaved: (PdfDocumentEntity) -> Unit) {
        val src = _singleSource.value ?: return
        viewModelScope.launch {
            _toolState.value = PdfToolExecutionState(isBusy = true, statusText = "Rotating pages...")
            val outFile = vaultManager.createUniquePdfFile(outputName)
            engine.rotatePdf(src.uri, src.password, deltaDegrees, outFile)
                .onSuccess { (file, pages) ->
                    val doc = buildEntity(file, PdfOperationType.ROTATE.key, pages, false)
                    onSaved(doc)
                    _toolState.value = PdfToolExecutionState(resultDocuments = listOf(doc))
                }
                .onFailure { err ->
                    _toolState.value = PdfToolExecutionState(error = err.localizedMessage ?: "Rotate failed")
                }
        }
    }

    fun executeConvertToImages(asPng: Boolean, quality: Int, baseName: String) {
        val src = _singleSource.value ?: return
        viewModelScope.launch {
            _toolState.value = PdfToolExecutionState(isBusy = true, statusText = "Exporting pages as images...")
            engine.convertPdfToImages(src.uri, src.password, asPng, quality, baseName) { p ->
                _toolState.value = _toolState.value.copy(progress = p)
            }.onSuccess { files ->
                _toolState.value = PdfToolExecutionState(exportedImages = files)
            }.onFailure { err ->
                _toolState.value = PdfToolExecutionState(error = err.localizedMessage ?: "Conversion failed")
            }
        }
    }

    fun executeProtectOrUnlock(newPassword: String?, outputName: String, onSaved: (PdfDocumentEntity) -> Unit) {
        val src = _singleSource.value ?: return
        viewModelScope.launch {
            _toolState.value = PdfToolExecutionState(
                isBusy = true,
                statusText = if (newPassword.isNullOrBlank()) "Removing protection..." else "Encrypting PDF..."
            )
            val outFile = vaultManager.createUniquePdfFile(outputName)
            engine.protectOrUnlockPdf(src.uri, src.password, newPassword, outFile)
                .onSuccess { (file, pages) ->
                    val doc = buildEntity(
                        file = file,
                        op = PdfOperationType.PROTECT.key,
                        pages = pages,
                        isProtected = !newPassword.isNullOrBlank()
                    )
                    onSaved(doc)
                    _toolState.value = PdfToolExecutionState(resultDocuments = listOf(doc))
                }
                .onFailure { err ->
                    _toolState.value = PdfToolExecutionState(
                        needsPassword = err is PdfLockedException,
                        error = err.localizedMessage ?: "Security update failed"
                    )
                }
        }
    }

    private fun buildEntity(
        file: File,
        op: String,
        pages: Int,
        isProtected: Boolean
    ): PdfDocumentEntity {
        val now = System.currentTimeMillis()
        return PdfDocumentEntity(
            id = UUID.randomUUID().toString(),
            fileName = file.name,
            filePath = file.absolutePath,
            operation = op,
            pageCount = pages.coerceAtLeast(1),
            fileSizeBytes = file.length(),
            isFavorite = false,
            isPasswordProtected = isProtected,
            createdAtMillis = now,
            updatedAtMillis = now
        )
    }
}
