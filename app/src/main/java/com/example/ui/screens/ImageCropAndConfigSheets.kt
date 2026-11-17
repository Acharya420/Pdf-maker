package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.CompressionTarget
import com.example.data.model.ImageItem
import com.example.data.model.ImageLayoutMode
import com.example.data.model.MarginPreset
import com.example.data.model.NormalizedCropRect
import com.example.data.model.PageOrientation
import com.example.data.model.PageSizePreset
import com.example.data.model.PdfCreationConfig
import com.example.data.model.PdfQualityPreset
import com.example.pdf.PdfVaultManager
import com.example.ui.components.GlassChip
import com.example.ui.components.LiquidGradientButton
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfCustomizationDialog(
    config: PdfCreationConfig,
    estimatedSizeBytes: Long,
    onUpdateConfig: ((PdfCreationConfig) -> PdfCreationConfig) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("PDF Customization", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Estimated output: ~${PdfVaultManager.formatFileSize(estimatedSizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Page Size
                SectionTitle("Page Size")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PageSizePreset.entries.forEach { preset ->
                        GlassChip(
                            label = preset.name,
                            selected = config.pageSizePreset == preset,
                            onClick = { onUpdateConfig { it.copy(pageSizePreset = preset) } },
                            modifier = Modifier.testTag("page_size_${preset.name.lowercase()}")
                        )
                    }
                }
                if (config.pageSizePreset == PageSizePreset.CUSTOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = config.customWidthMm.roundToInt().toString(),
                            onValueChange = { v ->
                                v.toFloatOrNull()?.let { w -> onUpdateConfig { it.copy(customWidthMm = w) } }
                            },
                            label = { Text("Width (mm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = config.customHeightMm.roundToInt().toString(),
                            onValueChange = { v ->
                                v.toFloatOrNull()?.let { h -> onUpdateConfig { it.copy(customHeightMm = h) } }
                            },
                            label = { Text("Height (mm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Orientation
                SectionTitle("Page Orientation")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageOrientation.entries.forEach { orientation ->
                        GlassChip(
                            label = orientation.label,
                            selected = config.orientation == orientation,
                            onClick = { onUpdateConfig { it.copy(orientation = orientation) } },
                            modifier = Modifier.testTag("orientation_${orientation.name.lowercase()}")
                        )
                    }
                }

                // Margins
                SectionTitle("Page Margins")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MarginPreset.entries.forEach { margin ->
                        GlassChip(
                            label = margin.label,
                            selected = config.marginPreset == margin,
                            onClick = { onUpdateConfig { it.copy(marginPreset = margin) } }
                        )
                    }
                }
                if (config.marginPreset == MarginPreset.CUSTOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = config.customMarginTopPt.roundToInt().toString(),
                            onValueChange = { v -> v.toFloatOrNull()?.let { x -> onUpdateConfig { it.copy(customMarginTopPt = x) } } },
                            label = { Text("Top") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = config.customMarginBottomPt.roundToInt().toString(),
                            onValueChange = { v -> v.toFloatOrNull()?.let { x -> onUpdateConfig { it.copy(customMarginBottomPt = x) } } },
                            label = { Text("Bottom") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = config.customMarginLeftPt.roundToInt().toString(),
                            onValueChange = { v -> v.toFloatOrNull()?.let { x -> onUpdateConfig { it.copy(customMarginLeftPt = x) } } },
                            label = { Text("Left") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = config.customMarginRightPt.roundToInt().toString(),
                            onValueChange = { v -> v.toFloatOrNull()?.let { x -> onUpdateConfig { it.copy(customMarginRightPt = x) } } },
                            label = { Text("Right") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Image Layout
                SectionTitle("Image Layout")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ImageLayoutMode.entries.forEach { layout ->
                        GlassChip(
                            label = layout.label,
                            selected = config.imageLayoutMode == layout,
                            onClick = { onUpdateConfig { it.copy(imageLayoutMode = layout) } }
                        )
                    }
                }
                if (config.imageLayoutMode == ImageLayoutMode.CUSTOM_POSITION) {
                    Text("Scale: ${config.customScalePercent.roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = config.customScalePercent,
                        onValueChange = { s -> onUpdateConfig { it.copy(customScalePercent = s) } },
                        valueRange = 25f..100f
                    )
                    Text("Horizontal Offset: ${config.customOffsetXPercent.roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = config.customOffsetXPercent,
                        onValueChange = { x -> onUpdateConfig { it.copy(customOffsetXPercent = x) } },
                        valueRange = -50f..50f
                    )
                    Text("Vertical Offset: ${config.customOffsetYPercent.roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = config.customOffsetYPercent,
                        onValueChange = { y -> onUpdateConfig { it.copy(customOffsetYPercent = y) } },
                        valueRange = -50f..50f
                    )
                }

                // Quality / DPI
                SectionTitle("PDF Quality & DPI")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PdfQualityPreset.entries.forEach { q ->
                        GlassChip(
                            label = q.label,
                            selected = config.qualityPreset == q,
                            onClick = { onUpdateConfig { it.copy(qualityPreset = q) } }
                        )
                    }
                }
                if (config.qualityPreset == PdfQualityPreset.CUSTOM_DPI) {
                    Text("DPI: ${config.customDpi}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = config.customDpi.toFloat(),
                        onValueChange = { d -> onUpdateConfig { it.copy(customDpi = d.roundToInt()) } },
                        valueRange = 72f..600f
                    )
                    Text("JPEG Quality: ${config.customJpegQuality}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = config.customJpegQuality.toFloat(),
                        onValueChange = { q -> onUpdateConfig { it.copy(customJpegQuality = q.roundToInt()) } },
                        valueRange = 25f..100f
                    )
                }

                // Target File Size / Compression
                SectionTitle("PDF File Size & Compression")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompressionTarget.entries.forEach { target ->
                        GlassChip(
                            label = target.label,
                            selected = config.compressionTarget == target,
                            onClick = { onUpdateConfig { it.copy(compressionTarget = target) } }
                        )
                    }
                }
                if (config.compressionTarget == CompressionTarget.TARGET_KB) {
                    OutlinedTextField(
                        value = config.targetFileSizeKb.toString(),
                        onValueChange = { v ->
                            v.toIntOrNull()?.let { kb -> onUpdateConfig { it.copy(targetFileSizeKb = kb) } }
                        },
                        label = { Text("Target Size (KB, min 30)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Password Protection
                SectionTitle("Password Protection (Optional)")
                OutlinedTextField(
                    value = config.password,
                    onValueChange = { pw -> onUpdateConfig { it.copy(password = pw) } },
                    label = { Text("Set PDF open password (leave blank for none)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            LiquidGradientButton(
                text = "Apply",
                onClick = onDismiss,
                modifier = Modifier.width(120.dp)
            )
        }
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageEditDialog(
    item: ImageItem,
    onRotate: () -> Unit,
    onSave: (NormalizedCropRect, Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var left by remember(item.id) { mutableFloatStateOf(item.cropRect.left) }
    var top by remember(item.id) { mutableFloatStateOf(item.cropRect.top) }
    var right by remember(item.id) { mutableFloatStateOf(item.cropRect.right) }
    var bottom by remember(item.id) { mutableFloatStateOf(item.cropRect.bottom) }
    var quality by remember(item.id) { mutableFloatStateOf((item.qualityOverride ?: 80).toFloat()) }
    var useCustomQuality by remember(item.id) { mutableStateOf(item.qualityOverride != null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crop, Rotate & Quality", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(item.displayName, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Rotate +90°", false, onRotate)
                    GlassChip("Full Image", left == 0f && top == 0f && right == 1f && bottom == 1f, {
                        left = 0f; top = 0f; right = 1f; bottom = 1f
                    })
                    GlassChip("Center 1:1", false, {
                        left = 0.1f; top = 0.15f; right = 0.9f; bottom = 0.85f
                    })
                }
                Text("Left Trim: ${(left * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                Slider(value = left, onValueChange = { left = it.coerceAtMost(right - 0.1f) }, valueRange = 0f..0.8f)

                Text("Right Trim: ${(right * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                Slider(value = right, onValueChange = { right = it.coerceAtLeast(left + 0.1f) }, valueRange = 0.2f..1f)

                Text("Top Trim: ${(top * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                Slider(value = top, onValueChange = { top = it.coerceAtMost(bottom - 0.1f) }, valueRange = 0f..0.8f)

                Text("Bottom Trim: ${(bottom * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                Slider(value = bottom, onValueChange = { bottom = it.coerceAtLeast(top + 0.1f) }, valueRange = 0.2f..1f)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Global Quality", !useCustomQuality, { useCustomQuality = false })
                    GlassChip("Custom Quality", useCustomQuality, { useCustomQuality = true })
                }
                if (useCustomQuality) {
                    Text("Image JPEG Quality: ${quality.roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = quality, onValueChange = { quality = it }, valueRange = 25f..100f)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            LiquidGradientButton(
                text = "Save",
                onClick = {
                    onSave(
                        NormalizedCropRect(left, top, right, bottom),
                        if (useCustomQuality) quality.roundToInt() else null
                    )
                },
                modifier = Modifier.width(110.dp)
            )
        }
    )
}
