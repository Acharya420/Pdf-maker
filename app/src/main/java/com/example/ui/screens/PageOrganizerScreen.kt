package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.RotateRight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PdfDocumentEntity
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGradientButton
import com.example.ui.components.PdfSuccessCelebrationCard
import com.example.viewmodel.PdfToolsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageOrganizerScreen(
    viewModel: PdfToolsViewModel,
    initialPdfUri: Uri? = null,
    onBack: () -> Unit,
    onDocumentSaved: (PdfDocumentEntity) -> Unit,
    onOpenDocument: (PdfDocumentEntity) -> Unit,
    onShareDocument: (PdfDocumentEntity) -> Unit,
    onSaveCopyToUri: (PdfDocumentEntity, Uri) -> Unit
) {
    val source by viewModel.singleSource.collectAsStateWithLifecycle()
    val thumbnails by viewModel.organizerThumbnails.collectAsStateWithLifecycle()
    val items by viewModel.organizerItems.collectAsStateWithLifecycle()
    val state by viewModel.toolState.collectAsStateWithLifecycle()

    var outputName by remember { mutableStateOf("Organized_${System.currentTimeMillis() / 1000}") }
    var unlockPrompt by remember { mutableStateOf("") }
    var pendingSafDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }

    LaunchedEffect(initialPdfUri) {
        viewModel.resetToolState()
        if (initialPdfUri != null) {
            viewModel.selectSinglePdf(initialPdfUri, loadThumbnails = true)
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.selectSinglePdf(uri, loadThumbnails = true)
    }
    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val doc = pendingSafDoc
        if (uri != null && doc != null) onSaveCopyToUri(doc, uri)
        pendingSafDoc = null
    }

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = { Text("PDF Page Organizer") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                val created = state.resultDocuments.firstOrNull()
                if (created != null) {
                    Spacer(Modifier.height(12.dp))
                    PdfSuccessCelebrationCard(
                        document = created,
                        subtitle = "Pages reorganized & saved offline",
                        onOpen = { onOpenDocument(created) },
                        onShare = { onShareDocument(created) },
                        onSaveLocation = {
                            pendingSafDoc = created
                            safLauncher.launch(created.fileName)
                        },
                        onDone = { viewModel.resetToolState(); onBack() }
                    )
                    return@Scaffold
                }

                OutlinedButton(
                    onClick = { pickLauncher.launch(arrayOf("application/pdf")) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("organizer_pick_pdf_btn")
                ) {
                    Icon(Icons.Filled.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text(source?.displayName ?: "Select PDF to Organize")
                }

                if (state.isBusy) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Spacer(Modifier.height(10.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(items) { position, pageItem ->
                        val bmp = thumbnails.getOrNull(pageItem.originalPageIndex)
                        LiquidGlassCard(
                            cornerRadius = 16.dp,
                            contentPadding = PaddingValues(8.dp),
                            modifier = Modifier.alpha(if (pageItem.isDeleted) 0.4f else 1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Page ${pageItem.originalPageIndex + 1}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .rotate(pageItem.rotationDegrees.toFloat())
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Page ${pageItem.originalPageIndex + 1}" + if (pageItem.isDeleted) " (Removed)" else "",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = { viewModel.moveOrganizerPage(position, position - 1) },
                                    enabled = position > 0,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Move earlier")
                                }
                                IconButton(
                                    onClick = { viewModel.rotateOrganizerPage(position) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Filled.RotateRight, "Rotate 90°")
                                }
                                IconButton(
                                    onClick = { viewModel.toggleDeleteOrganizerPage(position) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        if (pageItem.isDeleted) Icons.Filled.RestoreFromTrash else Icons.Filled.DeleteOutline,
                                        "Delete or restore",
                                        tint = if (pageItem.isDeleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.moveOrganizerPage(position, position + 1) },
                                    enabled = position < items.lastIndex,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Move later")
                                }
                            }
                        }
                    }
                }

                if (items.isNotEmpty()) {
                    LiquidGlassCard(
                        elevated = true,
                        cornerRadius = 22.dp,
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = outputName,
                            onValueChange = { outputName = it },
                            label = { Text("Output PDF Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        val activeCount = items.count { !it.isDeleted }
                        LiquidGradientButton(
                            text = "Save Reorganized PDF ($activeCount pg)",
                            icon = Icons.Filled.Check,
                            enabled = activeCount > 0 && !state.isBusy,
                            onClick = { viewModel.executeOrganize(outputName, onDocumentSaved) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("organizer_save_btn")
                        )
                    }
                }
            }
        }
    }

    if (state.needsPassword && source != null) {
        PasswordUnlockDialog(
            value = unlockPrompt,
            onValueChange = { unlockPrompt = it },
            onUnlock = {
                val pw = unlockPrompt; unlockPrompt = ""
                viewModel.selectSinglePdf(source!!.uri, pw, loadThumbnails = true)
            },
            onDismiss = { viewModel.clearNeedsPassword() }
        )
    }
}
