package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.data.model.PageOrganizeItem
import com.example.data.model.PdfDocumentMetadata
import com.example.data.model.SplitMode
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfLockedException(message: String = "PDF requires a valid password.") : Exception(message)

class PdfToolsEngine(
    private val context: Context,
    private val vaultManager: PdfVaultManager
) {

    private fun openStream(uri: Uri): InputStream {
        return context.contentResolver.openInputStream(uri)
            ?: error("Unable to open PDF input stream.")
    }

    private fun loadPdDocument(uri: Uri, password: String?): PDDocument {
        return try {
            openStream(uri).use { stream ->
                if (password.isNullOrEmpty()) {
                    PDDocument.load(stream)
                } else {
                    PDDocument.load(stream, password)
                }
            }
        } catch (e: InvalidPasswordException) {
            throw PdfLockedException("This PDF is password protected or the password is incorrect.")
        }
    }

    private fun copyUriToTempFile(uri: Uri, password: String?): Pair<File, Boolean> {
        val tempIn = File(context.cacheDir, "tmp_in_${System.nanoTime()}.pdf")
        openStream(uri).use { input ->
            tempIn.outputStream().use { out -> input.copyTo(out) }
        }
        if (password.isNullOrEmpty()) {
            // Verify not encrypted before PdfRenderer
            try {
                PDDocument.load(tempIn).use { doc ->
                    if (doc.isEncrypted) {
                        val unlocked = File(context.cacheDir, "tmp_dec_${System.nanoTime()}.pdf")
                        doc.isAllSecurityToBeRemoved = true
                        doc.save(unlocked)
                        tempIn.delete()
                        return unlocked to true
                    }
                }
            } catch (e: InvalidPasswordException) {
                tempIn.delete()
                throw PdfLockedException()
            }
            return tempIn to false
        } else {
            val doc = try {
                PDDocument.load(tempIn, password)
            } catch (e: InvalidPasswordException) {
                tempIn.delete()
                throw PdfLockedException()
            }
            val unlocked = File(context.cacheDir, "tmp_dec_${System.nanoTime()}.pdf")
            doc.use {
                it.isAllSecurityToBeRemoved = true
                it.save(unlocked)
            }
            tempIn.delete()
            return unlocked to true
        }
    }

    suspend fun inspectPdf(uri: Uri, password: String? = null): Result<PdfDocumentMetadata> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fileName = vaultManager.resolveDisplayName(uri)
                val sizeBytes = vaultManager.resolveSizeBytes(uri).let {
                    if (it > 0L) it else openStream(uri).use { s -> s.readBytes().size.toLong() }
                }
                loadPdDocument(uri, password).use { doc ->
                    val info = doc.documentInformation
                    val firstBox = if (doc.numberOfPages > 0) doc.getPage(0).mediaBox else PDRectangle.A4
                    PdfDocumentMetadata(
                        fileName = fileName,
                        pageCount = doc.numberOfPages,
                        fileSizeBytes = sizeBytes,
                        title = info.title?.takeIf { it.isNotBlank() } ?: fileName.removeSuffix(".pdf"),
                        author = info.author ?: "Unknown",
                        creator = info.creator ?: "PDF Maker",
                        producer = info.producer ?: "PDFBox Engine",
                        pdfVersion = "PDF ${doc.version}",
                        isEncrypted = doc.isEncrypted,
                        firstPageWidthPt = firstBox.width,
                        firstPageHeightPt = firstBox.height
                    )
                }
            }
        }

    suspend fun renderPageBitmap(
        uri: Uri,
        pageIndex: Int,
        password: String? = null,
        targetWidthPx: Int = 1080
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val (renderFile, _) = copyUriToTempFile(uri, password)
            try {
                ParcelFileDescriptor.open(renderFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        require(pageIndex in 0 until renderer.pageCount) {
                            "Page index $pageIndex out of bounds"
                        }
                        renderer.openPage(pageIndex).use { page ->
                            val aspect = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                            val width = targetWidthPx.coerceIn(200, 2200)
                            val height = (width * aspect).roundToInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            } finally {
                renderFile.delete()
            }
        }
    }

    suspend fun renderAllThumbnails(
        uri: Uri,
        password: String? = null,
        maxPages: Int = 60,
        thumbnailWidthPx: Int = 360
    ): Result<List<Bitmap>> = withContext(Dispatchers.IO) {
        runCatching {
            val (renderFile, _) = copyUriToTempFile(uri, password)
            val bitmaps = mutableListOf<Bitmap>()
            try {
                ParcelFileDescriptor.open(renderFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val count = minOf(renderer.pageCount, maxPages)
                        for (i in 0 until count) {
                            renderer.openPage(i).use { page ->
                                val aspect = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                                val w = thumbnailWidthPx.coerceIn(140, 720)
                                val h = (w * aspect).roundToInt().coerceAtLeast(1)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmaps.add(bmp)
                            }
                        }
                    }
                }
            } finally {
                renderFile.delete()
            }
            bitmaps
        }
    }

    suspend fun mergePdfs(
        uris: List<Uri>,
        passwords: Map<Int, String>,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<Pair<File, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            require(uris.size >= 2) { "Select at least 2 PDF files to merge." }
            val tempUnlocked = mutableListOf<File>()
            try {
                val merger = PDFMergerUtility()
                outputFile.parentFile?.mkdirs()
                merger.destinationFileName = outputFile.absolutePath
                var totalPages = 0

                uris.forEachIndexed { idx, uri ->
                    onProgress((idx.toFloat() / uris.size.toFloat()) * 0.8f)
                    val (file, _) = copyUriToTempFile(uri, passwords[idx])
                    tempUnlocked.add(file)
                    PDDocument.load(file).use { totalPages += it.numberOfPages }
                    merger.addSource(file)
                }

                merger.mergeDocuments(null)
                onProgress(1f)
                outputFile to totalPages.coerceAtLeast(1)
            } finally {
                tempUnlocked.forEach { it.delete() }
            }
        }
    }

    suspend fun splitPdf(
        uri: Uri,
        password: String?,
        mode: SplitMode,
        baseName: String,
        onProgress: (Float) -> Unit = {}
    ): Result<List<Pair<File, Int>>> = withContext(Dispatchers.IO) {
        runCatching {
            val outputs = mutableListOf<Pair<File, Int>>()
            loadPdDocument(uri, password).use { sourceDoc ->
                val total = sourceDoc.numberOfPages
                require(total > 0) { "Source PDF has 0 pages." }

                val chunks: List<List<Int>> = when (mode) {
                    is SplitMode.ExtractAllPages -> (0 until total).map { listOf(it) }
                    is SplitMode.EveryNPages -> {
                        val size = mode.chunkSize.coerceIn(1, total)
                        (0 until total).chunked(size)
                    }
                    is SplitMode.PageRange -> {
                        val indices = parsePageRange(mode.rangeExpression, total)
                        require(indices.isNotEmpty()) {
                            "Invalid range. Example: 1-3, 5 (max $total)."
                        }
                        listOf(indices)
                    }
                }

                chunks.forEachIndexed { idx, pageIndices ->
                    onProgress((idx + 1).toFloat() / chunks.size.toFloat())
                    val partName = if (chunks.size == 1) {
                        "${baseName}_split"
                    } else {
                        "${baseName}_part${idx + 1}"
                    }
                    val outFile = vaultManager.createUniquePdfFile(partName)
                    PDDocument().use { destDoc ->
                        pageIndices.forEach { pIdx ->
                            destDoc.importPage(sourceDoc.getPage(pIdx))
                        }
                        destDoc.save(outFile)
                    }
                    outputs.add(outFile to pageIndices.size)
                }
            }
            outputs
        }
    }

    suspend fun compressPdf(
        uri: Uri,
        password: String?,
        jpegQuality: Int,
        dpi: Int,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<Pair<File, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val (renderFile, _) = copyUriToTempFile(uri, password)
            try {
                var pageCount = 0
                ParcelFileDescriptor.open(renderFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount
                        require(pageCount > 0) { "Source PDF has no pages." }
                        PDDocument().use { outDoc ->
                            for (i in 0 until pageCount) {
                                onProgress((i + 1).toFloat() / pageCount.toFloat())
                                renderer.openPage(i).use { page ->
                                    val ptW = (page.width * 72f / 72f).coerceAtLeast(100f)
                                    val ptH = (page.height * 72f / 72f).coerceAtLeast(100f)
                                    val targetW = ((ptW / 72f) * dpi.coerceIn(60, 300)).roundToInt().coerceIn(320, 2400)
                                    val targetH = ((ptH / 72f) * dpi.coerceIn(60, 300)).roundToInt().coerceIn(320, 3200)
                                    val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                                    bmp.eraseColor(android.graphics.Color.WHITE)
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                    val jpegBytes = ByteArrayOutputStream().use { baos ->
                                        bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(20, 95), baos)
                                        baos.toByteArray()
                                    }
                                    bmp.recycle()

                                    val newPage = PDPage(PDRectangle(ptW, ptH))
                                    outDoc.addPage(newPage)
                                    val pdImg = JPEGFactory.createFromByteArray(outDoc, jpegBytes)
                                    PDPageContentStream(outDoc, newPage).use { cs ->
                                        cs.drawImage(pdImg, 0f, 0f, ptW, ptH)
                                    }
                                }
                            }
                            outputFile.parentFile?.mkdirs()
                            outDoc.save(outputFile)
                        }
                    }
                }
                outputFile to pageCount
            } finally {
                renderFile.delete()
            }
        }
    }

    suspend fun organizePages(
        uri: Uri,
        password: String?,
        items: List<PageOrganizeItem>,
        outputFile: File
    ): Result<Pair<File, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val active = items.filter { !it.isDeleted }
            require(active.isNotEmpty()) { "At least one page must remain in the PDF." }
            loadPdDocument(uri, password).use { src ->
                PDDocument().use { dest ->
                    active.forEach { item ->
                        val imported = dest.importPage(src.getPage(item.originalPageIndex))
                        val nextRotation = (imported.rotation + item.rotationDegrees) % 360
                        imported.rotation = (nextRotation + 360) % 360
                    }
                    outputFile.parentFile?.mkdirs()
                    dest.save(outputFile)
                }
            }
            outputFile to active.size
        }
    }

    suspend fun rotatePdf(
        uri: Uri,
        password: String?,
        deltaDegrees: Int,
        outputFile: File
    ): Result<Pair<File, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            loadPdDocument(uri, password).use { doc ->
                val count = doc.numberOfPages
                for (i in 0 until count) {
                    val page = doc.getPage(i)
                    page.rotation = ((page.rotation + deltaDegrees) % 360 + 360) % 360
                }
                doc.isAllSecurityToBeRemoved = true
                outputFile.parentFile?.mkdirs()
                doc.save(outputFile)
                outputFile to count
            }
        }
    }

    suspend fun convertPdfToImages(
        uri: Uri,
        password: String?,
        asPng: Boolean,
        jpegQuality: Int,
        baseName: String,
        onProgress: (Float) -> Unit = {}
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val (renderFile, _) = copyUriToTempFile(uri, password)
            val outFiles = mutableListOf<File>()
            try {
                val exportDir = vaultManager.exportsDir
                ParcelFileDescriptor.open(renderFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val count = renderer.pageCount
                        for (i in 0 until count) {
                            onProgress((i + 1).toFloat() / count.coerceAtLeast(1).toFloat())
                            renderer.openPage(i).use { page ->
                                val w = (page.width * 2).coerceIn(600, 2200)
                                val aspect = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                                val h = (w * aspect).roundToInt().coerceAtLeast(1)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val ext = if (asPng) "png" else "jpg"
                                val cleanBase = baseName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
                                val imgFile = File(exportDir, "${cleanBase}_page_${i + 1}.$ext")
                                imgFile.outputStream().use { os ->
                                    val format = if (asPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                                    bmp.compress(format, jpegQuality.coerceIn(50, 100), os)
                                }
                                bmp.recycle()
                                outFiles.add(imgFile)
                            }
                        }
                    }
                }
            } finally {
                renderFile.delete()
            }
            outFiles
        }
    }

    suspend fun protectOrUnlockPdf(
        uri: Uri,
        currentPassword: String?,
        newPassword: String?,
        outputFile: File
    ): Result<Pair<File, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            loadPdDocument(uri, currentPassword).use { doc ->
                val pageCount = doc.numberOfPages
                if (newPassword.isNullOrBlank()) {
                    doc.isAllSecurityToBeRemoved = true
                } else {
                    val access = AccessPermission()
                    val policy = StandardProtectionPolicy(newPassword, newPassword, access)
                    policy.encryptionKeyLength = 128
                    policy.permissions = access
                    doc.protect(policy)
                }
                outputFile.parentFile?.mkdirs()
                doc.save(outputFile)
                outputFile to pageCount
            }
        }
    }

    private fun parsePageRange(expression: String, maxPages: Int): List<Int> {
        val result = linkedSetOf<Int>()
        expression.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { token ->
            if (token.contains("-")) {
                val parts = token.split("-").mapNotNull { it.trim().toIntOrNull() }
                if (parts.size == 2) {
                    val start = minOf(parts[0], parts[1]).coerceIn(1, maxPages)
                    val end = maxOf(parts[0], parts[1]).coerceIn(1, maxPages)
                    for (p in start..end) result.add(p - 1)
                }
            } else {
                token.toIntOrNull()?.let { p ->
                    if (p in 1..maxPages) result.add(p - 1)
                }
            }
        }
        return result.toList()
    }
}
