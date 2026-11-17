package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.data.local.PdfDocumentEntity
import com.example.data.model.ImageItem
import com.example.pdf.PdfVaultManager
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGradientButton
import com.example.ui.components.PdfSuccessCelebrationCard
import com.example.ui.theme.RoyalPurple
import com.example.viewmodel.ImageToPdfViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToPdfScreen(
    viewModel: ImageToPdfViewModel,
    onBack: () -> Unit,
    onDocumentCreated: (PdfDocumentEntity) -> Unit,
    onOpenDocument: (PdfDocumentEntity) -> Unit,
    onShareDocument: (PdfDocumentEntity) -> Unit,
    onSaveCopyToUri: (PdfDocumentEntity, Uri) -> Unit
) {
    val images by viewModel.images.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val createdDoc by viewModel.createdDocument.collectAsStateWithLifecycle()
    val errorMsg by viewModel.errorMessage.collectAsStateWithLifecycle()
    val estimatedSize by viewModel.estimatedSizeBytes.collectAsStateWithLifecycle()

    var showCustomizeSheet by remember { mutableStateOf(false) }
    var editingImage by remember { mutableStateOf<ImageItem?>(null) }
    var pendingSafExportDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }

    // Android Zero-Permission Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris ->
        viewModel.addImages(uris)
    }

    // Android Storage Access Framework File Manager Picker
    val fileManagerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.addImages(uris)
    }

    // SAF Save As Location Picker
    val safSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        val doc = pendingSafExportDoc
        if (destUri != null && doc != null) {
            onSaveCopyToUri(doc, destUri)
        }
        pendingSafExportDoc = null
    }

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = { Text("Image to PDF Maker") },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("img2pdf_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showCustomizeSheet = true },
                            modifier = Modifier.testTag("img2pdf_customize_btn")
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = "Customize PDF settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                if (createdDoc != null) {
                    Spacer(Modifier.height(16.dp))
                    PdfSuccessCelebrationCard(
                        document = createdDoc!!,
                        onOpen = { onOpenDocument(createdDoc!!) },
                        onShare = { onShareDocument(createdDoc!!) },
                        onSaveLocation = {
                            pendingSafExportDoc = createdDoc
                            safSaveLauncher.launch(createdDoc!!.fileName)
                        },
                        onDone = {
                            viewModel.clearWorkspace()
                            onBack()
                        }
                    )
                    return@Scaffold
                }

                // Image Source Pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pick_gallery_btn")
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Gallery", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { fileManagerLauncher.launch(arrayOf("image/*")) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pick_file_manager_btn")
                    ) {
                        Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("File Manager", maxLines = 1)
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (errorMsg != null) {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (progress.isRunning) {
                    LiquidGlassCard(
                        elevated = true,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Text(progress.statusMessage, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Page ${progress.currentPage} of ${progress.totalPages}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // Selected images list with long-press drag reorder + accessible arrows
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("selected_images_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    if (images.isEmpty()) {
                        item {
                            LiquidGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(24.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = RoyalPurple,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text("Select Photos or Scans", style = MaterialTheme.typography.titleLarge)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Pick multiple images from Android Gallery or File Manager. Long-press & drag to reorder, crop, rotate, or customize layout.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        itemsIndexed(images, key = { _, item -> item.id }) { index, item ->
                            var dragOffsetY by remember { mutableFloatStateOf(0f) }
                            LiquidGlassCard(
                                cornerRadius = 18.dp,
                                contentPadding = PaddingValues(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { translationY = dragOffsetY }
                                    .pointerInput(item.id, images.size) {
                                        detectDragGesturesAfterLongPress(
                                            onDragEnd = { dragOffsetY = 0f },
                                            onDragCancel = { dragOffsetY = 0f },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetY += dragAmount.y
                                                if (dragOffsetY > 90f && index < images.lastIndex) {
                                                    viewModel.moveImage(index, index + 1)
                                                    dragOffsetY = 0f
                                                } else if (dragOffsetY < -90f && index > 0) {
                                                    viewModel.moveImage(index, index - 1)
                                                    dragOffsetY = 0f
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DragIndicator,
                                        contentDescription = "Drag to reorder",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(62.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.Black.copy(alpha = 0.08f))
                                    ) {
                                        Image(
                                            painter = rememberAsyncImagePainter(Uri.parse(item.uriString)),
                                            contentDescription = item.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .rotate(item.rotationDegrees.toFloat())
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "#${index + 1} • ${item.displayName}",
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = buildString {
                                                append("Rot ${item.rotationDegrees}°")
                                                if (!item.cropRect.isFull) append(" • Cropped")
                                                if (item.qualityOverride != null) append(" • Q${item.qualityOverride}%")
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row {
                                            IconButton(
                                                onClick = { viewModel.rotateImage(item.id, 90) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Filled.RotateRight, "Rotate 90°", modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = { editingImage = item },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Filled.Crop, "Crop & quality", modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = { viewModel.moveImage(index, index - 1) },
                                                enabled = index > 0,
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Filled.ArrowUpward, "Move up", modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = { viewModel.moveImage(index, index + 1) },
                                                enabled = index < images.lastIndex,
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.Filled.ArrowDownward, "Move down", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                    IconButton(onClick = { viewModel.removeImage(item.id) }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Remove image")
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Export & Customization Bar
                LiquidGlassCard(
                    elevated = true,
                    cornerRadius = 24.dp,
                    contentPadding = PaddingValues(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                ) {
                    OutlinedTextField(
                        value = config.fileName,
                        onValueChange = { name -> viewModel.updateConfig { it.copy(fileName = name) } },
                        label = { Text("Output PDF Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("img2pdf_filename_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${config.pageSizePreset.name} • ${config.orientation.label} • ${config.imageLayoutMode.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (images.isEmpty()) "Add images to estimate size"
                                else "Est. size: ~${PdfVaultManager.formatFileSize(estimatedSize)} (${images.size} pg)",
                                style = MaterialTheme.typography.labelSmall,
                                color = RoyalPurple
                            )
                        }
                        OutlinedButton(
                            onClick = { showCustomizeSheet = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("img2pdf_options_btn")
                        ) {
                            Icon(Icons.Filled.Tune, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Options")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LiquidGradientButton(
                        text = if (progress.isRunning) "Creating PDF..." else "Create PDF (${images.size})",
                        icon = Icons.Filled.PictureAsPdf,
                        enabled = images.isNotEmpty() && !progress.isRunning,
                        onClick = { viewModel.generatePdf(onDocumentCreated) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_pdf_submit_btn")
                    )
                }
            }
        }
    }

    if (showCustomizeSheet) {
        PdfCustomizationDialog(
            config = config,
            estimatedSizeBytes = estimatedSize,
            onUpdateConfig = { viewModel.updateConfig(it) },
            onDismiss = { showCustomizeSheet = false }
        )
    }

    editingImage?.let { target ->
        ImageEditDialog(
            item = target,
            onRotate = { viewModel.rotateImage(target.id, 90) },
            onSave = { cropRect, qualityOverride ->
                viewModel.updateImageCrop(target.id, cropRect)
                viewModel.updateImageQualityOverride(target.id, qualityOverride)
                editingImage = null
            },
            onDismiss = { editingImage = null }
        )
    }
}
