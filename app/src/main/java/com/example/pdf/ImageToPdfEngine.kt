package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.example.data.model.CompressionTarget
import com.example.data.model.ImageItem
import com.example.data.model.ImageLayoutMode
import com.example.data.model.NormalizedCropRect
import com.example.data.model.PdfCreationConfig
import com.example.data.model.PdfGenerationProgress
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageToPdfEngine(private val context: Context) {

    fun estimateOutputSizeBytes(items: List<ImageItem>, config: PdfCreationConfig): Long {
        if (items.isEmpty()) return 0L
        if (config.compressionTarget == CompressionTarget.TARGET_KB) {
            return config.targetFileSizeKb.coerceAtLeast(30).toLong() * 1024L
        }
        val (pageW, pageH) = config.resolvePageDimensionsPt()
        val dpi = config.resolveEffectiveDpi()
        val quality = config.resolveEffectiveJpegQuality()
        val pixelWidth = (pageW / 72f) * dpi
        val pixelHeight = (pageH / 72f) * dpi
        val pixelsPerPage = pixelWidth * pixelHeight
        // Empirical JPEG bytes-per-pixel heuristic scaled by quality
        val bpp = 0.04f + (quality / 100f) * 0.22f
        val perPageBytes = (pixelsPerPage * bpp).toLong().coerceIn(22_000L, 2_800_000L)
        return (perPageBytes * items.size) + 4096L
    }

    suspend fun loadProcessedBitmap(
        uri: Uri,
        rotationDegrees: Int,
        cropRect: NormalizedCropRect,
        maxDimensionPx: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val exifOrientation = resolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        var sampleSize = 1
        val maxSide = max(bounds.outWidth, bounds.outHeight)
        while (maxSide / sampleSize > maxDimensionPx * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return@withContext null

        val exifAngle = when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val totalAngle = (exifAngle + rotationDegrees) % 360f

        val rotated = if (totalAngle != 0f) {
            val matrix = Matrix().apply { postRotate(totalAngle) }
            val result = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            if (result !== raw) raw.recycle()
            result
        } else {
            raw
        }

        val cropped = if (!cropRect.isFull) {
            val x = (cropRect.left.coerceIn(0f, 0.95f) * rotated.width).roundToInt()
            val y = (cropRect.top.coerceIn(0f, 0.95f) * rotated.height).roundToInt()
            val w = ((cropRect.right - cropRect.left).coerceIn(0.05f, 1f) * rotated.width)
                .roundToInt().coerceAtMost(rotated.width - x).coerceAtLeast(1)
            val h = ((cropRect.bottom - cropRect.top).coerceIn(0.05f, 1f) * rotated.height)
                .roundToInt().coerceAtMost(rotated.height - y).coerceAtLeast(1)
            val result = Bitmap.createBitmap(rotated, x, y, w, h)
            if (result !== rotated) rotated.recycle()
            result
        } else {
            rotated
        }

        val currentMax = max(cropped.width, cropped.height)
        if (currentMax > maxDimensionPx) {
            val scale = maxDimensionPx.toFloat() / currentMax.toFloat()
            val targetW = (cropped.width * scale).roundToInt().coerceAtLeast(1)
            val targetH = (cropped.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
            if (scaled !== cropped) cropped.recycle()
            scaled
        } else {
            cropped
        }
    }

    suspend fun generatePdf(
        items: List<ImageItem>,
        config: PdfCreationConfig,
        outputFile: File,
        onProgress: (PdfGenerationProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(items.isNotEmpty()) { "Please select at least one image." }
            val total = items.size
            val (pageW, pageH) = config.resolvePageDimensionsPt()
            val dpi = config.resolveEffectiveDpi()
            val baseMaxDim = ((max(pageW, pageH) / 72f) * dpi).roundToInt().coerceIn(500, 3200)
            var currentQuality = config.resolveEffectiveJpegQuality()
            var scaleDownFactor = 1.0f

            val maxAttempts = if (config.compressionTarget == CompressionTarget.TARGET_KB) 4 else 1
            val targetBytes = config.targetFileSizeKb.coerceAtLeast(30).toLong() * 1024L

            for (attempt in 0 until maxAttempts) {
                val effectiveMaxDim = (baseMaxDim * scaleDownFactor).roundToInt().coerceIn(360, 3200)
                val document = PDDocument()
                try {
                    val margins = config.resolveMarginsPt()
                    val mTop = margins[0].coerceAtMost(pageH * 0.35f)
                    val mRight = margins[1].coerceAtMost(pageW * 0.35f)
                    val mBottom = margins[2].coerceAtMost(pageH * 0.35f)
                    val mLeft = margins[3].coerceAtMost(pageW * 0.35f)
                    val contentW = (pageW - mLeft - mRight).coerceAtLeast(36f)
                    val contentH = (pageH - mTop - mBottom).coerceAtLeast(36f)

                    items.forEachIndexed { index, item ->
                        val baseProgress = index.toFloat() / total.toFloat()
                        onProgress(
                            PdfGenerationProgress(
                                isRunning = true,
                                progress = baseProgress.coerceIn(0.02f, 0.95f),
                                currentPage = index + 1,
                                totalPages = total,
                                statusMessage = if (attempt == 0) {
                                    "Rendering page ${index + 1} of $total..."
                                } else {
                                    "Optimizing size (pass ${attempt + 1})..."
                                }
                            )
                        )

                        val uri = Uri.parse(item.uriString)
                        val bitmap = loadProcessedBitmap(
                            uri = uri,
                            rotationDegrees = item.rotationDegrees,
                            cropRect = item.cropRect,
                            maxDimensionPx = effectiveMaxDim
                        ) ?: error("Unable to decode image: ${item.displayName}")

                        val itemQuality = (item.qualityOverride ?: currentQuality).coerceIn(20, 100)
                        val jpegBytes = ByteArrayOutputStream().use { baos ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, itemQuality, baos)
                            baos.toByteArray()
                        }
                        val bmpW = bitmap.width.toFloat()
                        val bmpH = bitmap.height.toFloat()
                        bitmap.recycle()

                        val page = PDPage(PDRectangle(pageW, pageH))
                        document.addPage(page)

                        val pdImage = JPEGFactory.createFromByteArray(document, jpegBytes)
                        PDPageContentStream(document, page).use { stream ->
                            val (drawX, drawY, drawW, drawH) = computePlacement(
                                imgW = bmpW,
                                imgH = bmpH,
                                areaX = mLeft,
                                areaY = mBottom,
                                areaW = contentW,
                                areaH = contentH,
                                layoutMode = config.imageLayoutMode,
                                customScalePercent = config.customScalePercent,
                                customOffsetXPercent = config.customOffsetXPercent,
                                customOffsetYPercent = config.customOffsetYPercent
                            )
                            stream.drawImage(pdImage, drawX, drawY, drawW, drawH)
                        }
                    }

                    if (config.password.isNotBlank()) {
                        val access = AccessPermission()
                        val policy = StandardProtectionPolicy(config.password, config.password, access)
                        policy.encryptionKeyLength = 128
                        policy.permissions = access
                        document.protect(policy)
                    }

                    outputFile.parentFile?.mkdirs()
                    document.save(outputFile)
                } finally {
                    document.close()
                }

                if (config.compressionTarget != CompressionTarget.TARGET_KB ||
                    outputFile.length() <= targetBytes ||
                    attempt == maxAttempts - 1
                ) {
                    break
                }

                val ratio = targetBytes.toFloat() / outputFile.length().coerceAtLeast(1L).toFloat()
                currentQuality = (currentQuality * ratio.coerceIn(0.45f, 0.85f)).roundToInt().coerceIn(22, 90)
                scaleDownFactor = (scaleDownFactor * 0.78f).coerceAtLeast(0.45f)
            }

            onProgress(
                PdfGenerationProgress(
                    isRunning = false,
                    progress = 1f,
                    currentPage = total,
                    totalPages = total,
                    statusMessage = "PDF ready!"
                )
            )
            outputFile
        }
    }

    private fun computePlacement(
        imgW: Float,
        imgH: Float,
        areaX: Float,
        areaY: Float,
        areaW: Float,
        areaH: Float,
        layoutMode: ImageLayoutMode,
        customScalePercent: Float,
        customOffsetXPercent: Float,
        customOffsetYPercent: Float
    ): FloatArray {
        val imgRatio = imgW / imgH.coerceAtLeast(1f)
        val areaRatio = areaW / areaH.coerceAtLeast(1f)

        return when (layoutMode) {
            ImageLayoutMode.FIT_PAGE -> {
                val (w, h) = if (imgRatio > areaRatio) {
                    areaW to (areaW / imgRatio)
                } else {
                    (areaH * imgRatio) to areaH
                }
                val x = areaX + (areaW - w) / 2f
                val y = areaY + (areaH - h) / 2f
                floatArrayOf(x, y, w, h)
            }
            ImageLayoutMode.FILL_PAGE -> {
                val (w, h) = if (imgRatio > areaRatio) {
                    (areaH * imgRatio) to areaH
                } else {
                    areaW to (areaW / imgRatio)
                }
                val x = areaX + (areaW - w) / 2f
                val y = areaY + (areaH - h) / 2f
                floatArrayOf(x, y, w, h)
            }
            ImageLayoutMode.ORIGINAL_RATIO -> {
                val nativeW = (imgW * 72f / 150f)
                val nativeH = (imgH * 72f / 150f)
                val scale = min(1f, min(areaW / nativeW, areaH / nativeH))
                val w = nativeW * scale
                val h = nativeH * scale
                val x = areaX + (areaW - w) / 2f
                val y = areaY + (areaH - h) / 2f
                floatArrayOf(x, y, w, h)
            }
            ImageLayoutMode.CUSTOM_POSITION -> {
                val fitScale = min(areaW / imgW, areaH / imgH)
                val factor = (customScalePercent.coerceIn(20f, 100f) / 100f)
                val w = imgW * fitScale * factor
                val h = imgH * fitScale * factor
                val freeX = (areaW - w).coerceAtLeast(0f)
                val freeY = (areaH - h).coerceAtLeast(0f)
                val normX = ((customOffsetXPercent.coerceIn(-50f, 50f) + 50f) / 100f)
                val normY = ((50f - customOffsetYPercent.coerceIn(-50f, 50f)) / 100f)
                val x = areaX + freeX * normX
                val y = areaY + freeY * normY
                floatArrayOf(x, y, w, h)
            }
        }
    }
}
