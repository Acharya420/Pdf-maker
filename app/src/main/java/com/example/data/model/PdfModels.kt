package com.example.data.model

import java.util.UUID

enum class PdfOperationType(val key: String, val label: String) {
    IMAGE_TO_PDF("image_to_pdf", "Image to PDF"),
    MERGE("merge", "Merge PDF"),
    SPLIT("split", "Split PDF"),
    COMPRESS("compress", "Compress PDF"),
    ORGANIZE("organize", "Organize Pages"),
    ROTATE("rotate", "Rotate PDF"),
    CONVERT("convert", "PDF Converter"),
    PROTECT("protect", "Protect / Unlock")
}

enum class PageSizePreset(
    val label: String,
    val widthPt: Float,
    val heightPt: Float
) {
    A4("A4 (210 × 297 mm)", 595f, 842f),
    A3("A3 (297 × 420 mm)", 842f, 1191f),
    A5("A5 (148 × 210 mm)", 420f, 595f),
    LETTER("Letter (8.5 × 11 in)", 612f, 792f),
    LEGAL("Legal (8.5 × 14 in)", 612f, 1008f),
    CUSTOM("Custom Size", 595f, 842f)
}

enum class PageOrientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class MarginPreset(
    val label: String,
    val topPt: Float,
    val rightPt: Float,
    val bottomPt: Float,
    val leftPt: Float
) {
    NONE("No Margin", 0f, 0f, 0f, 0f),
    SMALL("Small (12 pt)", 12f, 12f, 12f, 12f),
    MEDIUM("Medium (28 pt)", 28f, 28f, 28f, 28f),
    LARGE("Large (48 pt)", 48f, 48f, 48f, 48f),
    CUSTOM("Custom Margins", 24f, 24f, 24f, 24f)
}

enum class ImageLayoutMode(val label: String, val description: String) {
    FIT_PAGE("Fit to Page", "Scales proportionally inside margins"),
    FILL_PAGE("Fill Page", "Crops/fills entire printable area"),
    ORIGINAL_RATIO("Original Ratio", "Keeps native aspect & pixel bounds"),
    CUSTOM_POSITION("Custom Position", "Custom scale & X/Y alignment")
}

enum class PdfQualityPreset(val label: String, val dpi: Int, val jpegQuality: Int) {
    LOW("Low (Fast & Compact)", 72, 48),
    MEDIUM("Medium (Balanced)", 150, 76),
    HIGH("High (Print Ready)", 300, 92),
    CUSTOM_DPI("Custom DPI", 200, 84)
}

enum class CompressionTarget(val label: String) {
    AUTO("Automatic"),
    LIGHT("Light (90% quality)"),
    BALANCED("Balanced (72% quality)"),
    MAXIMUM("Maximum (45% quality)"),
    TARGET_KB("Target File Size (KB)")
}

data class NormalizedCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val isFull: Boolean get() = left <= 0.01f && top <= 0.01f && right >= 0.99f && bottom >= 0.99f
}

data class ImageItem(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val displayName: String,
    val rotationDegrees: Int = 0,
    val cropRect: NormalizedCropRect = NormalizedCropRect(),
    val qualityOverride: Int? = null,
    val sourceSizeBytes: Long = 0L
)

data class PdfCreationConfig(
    val fileName: String = "Document_${System.currentTimeMillis() / 1000}",
    val pageSizePreset: PageSizePreset = PageSizePreset.A4,
    val customWidthMm: Float = 210f,
    val customHeightMm: Float = 297f,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val marginPreset: MarginPreset = MarginPreset.SMALL,
    val customMarginTopPt: Float = 24f,
    val customMarginRightPt: Float = 24f,
    val customMarginBottomPt: Float = 24f,
    val customMarginLeftPt: Float = 24f,
    val imageLayoutMode: ImageLayoutMode = ImageLayoutMode.FIT_PAGE,
    val customScalePercent: Float = 85f,
    val customOffsetXPercent: Float = 0f,
    val customOffsetYPercent: Float = 0f,
    val qualityPreset: PdfQualityPreset = PdfQualityPreset.MEDIUM,
    val customDpi: Int = 150,
    val customJpegQuality: Int = 78,
    val compressionTarget: CompressionTarget = CompressionTarget.AUTO,
    val targetFileSizeKb: Int = 1024,
    val password: String = ""
) {
    fun resolvePageDimensionsPt(): Pair<Float, Float> {
        val (baseW, baseH) = if (pageSizePreset == PageSizePreset.CUSTOM) {
            (customWidthMm * 72f / 25.4f).coerceIn(100f, 3000f) to
                (customHeightMm * 72f / 25.4f).coerceIn(100f, 3000f)
        } else {
            pageSizePreset.widthPt to pageSizePreset.heightPt
        }
        return if (orientation == PageOrientation.LANDSCAPE) {
            maxOf(baseW, baseH) to minOf(baseW, baseH)
        } else {
            minOf(baseW, baseH) to maxOf(baseW, baseH)
        }
    }

    fun resolveMarginsPt(): List<Float> = if (marginPreset == MarginPreset.CUSTOM) {
        listOf(customMarginTopPt, customMarginRightPt, customMarginBottomPt, customMarginLeftPt)
    } else {
        listOf(marginPreset.topPt, marginPreset.rightPt, marginPreset.bottomPt, marginPreset.leftPt)
    }

    fun resolveEffectiveDpi(): Int = if (qualityPreset == PdfQualityPreset.CUSTOM_DPI) {
        customDpi.coerceIn(50, 600)
    } else {
        qualityPreset.dpi
    }

    fun resolveEffectiveJpegQuality(): Int = when (compressionTarget) {
        CompressionTarget.LIGHT -> 90
        CompressionTarget.BALANCED -> 72
        CompressionTarget.MAXIMUM -> 45
        CompressionTarget.AUTO, CompressionTarget.TARGET_KB ->
            if (qualityPreset == PdfQualityPreset.CUSTOM_DPI) customJpegQuality.coerceIn(20, 100)
            else qualityPreset.jpegQuality
    }
}

data class PdfGenerationProgress(
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val statusMessage: String = ""
)

data class PdfDocumentMetadata(
    val fileName: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val title: String,
    val author: String,
    val creator: String,
    val producer: String,
    val pdfVersion: String,
    val isEncrypted: Boolean,
    val firstPageWidthPt: Float,
    val firstPageHeightPt: Float
)

data class PageOrganizeItem(
    val originalPageIndex: Int,
    val rotationDegrees: Int = 0,
    val isDeleted: Boolean = false
)

sealed interface SplitMode {
    data class PageRange(val rangeExpression: String) : SplitMode
    data class EveryNPages(val chunkSize: Int) : SplitMode
    data object ExtractAllPages : SplitMode
}
