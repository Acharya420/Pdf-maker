package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pdf.PdfVaultManager
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGradientButton
import com.example.viewmodel.PdfToolsViewModel
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    viewModel: PdfToolsViewModel,
    vaultManager: PdfVaultManager,
    initialFilePath: String? = null,
    onBack: () -> Unit,
    onOpenOrganizer: (Uri) -> Unit
) {
    val context = LocalContext.current
    val source by viewModel.singleSource.collectAsStateWithLifecycle()
    val meta by viewModel.inspectedMeta.collectAsStateWithLifecycle()
    val pageBitmap by viewModel.viewerPageBitmap.collectAsStateWithLifecycle()
    val state by viewModel.toolState.collectAsStateWithLifecycle()

    var currentPage by remember { mutableIntStateOf(0) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }

    LaunchedEffect(initialFilePath) {
        viewModel.resetToolState()
        if (!initialFilePath.isNullOrBlank()) {
            val file = File(initialFilePath)
            if (file.exists()) {
                val uri = Uri.fromFile(file)
                viewModel.selectSinglePdf(uri)
            }
        }
    }

    LaunchedEffect(source, meta, currentPage) {
        val src = source
        val m = meta
        if (src != null && m != null && m.pageCount > 0) {
            val clamped = currentPage.coerceIn(0, m.pageCount - 1)
            currentPage = clamped
            viewModel.loadViewerPage(src.uri, clamped, src.password)
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            currentPage = 0
            zoomScale = 1f
            viewModel.selectSinglePdf(uri)
        }
    }

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(meta?.fileName ?: "PDF Viewer", maxLines = 1)
                            if (meta != null) {
                                Text(
                                    "Page ${currentPage + 1} of ${meta!!.pageCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                    actions = {
                        IconButton(onClick = { pickLauncher.launch(arrayOf("application/pdf")) }) {
                            Icon(Icons.Filled.FolderOpen, "Open PDF")
                        }
                        if (meta != null) {
                            IconButton(onClick = { showInfoDialog = true }) {
                                Icon(Icons.Filled.Info, "PDF Info")
                            }
                            if (source != null) {
                                IconButton(onClick = { onOpenOrganizer(source!!.uri) }) {
                                    Icon(Icons.Filled.GridView, "Organize Pages")
                                }
                            }
                            if (!initialFilePath.isNullOrBlank()) {
                                IconButton(onClick = {
                                    val file = File(initialFilePath)
                                    if (file.exists()) {
                                        context.startActivity(
                                            android.content.Intent.createChooser(
                                                vaultManager.createSharePdfIntent(file),
                                                "Share PDF"
                                            )
                                        )
                                    }
                                }) {
                                    Icon(Icons.Filled.Share, "Share PDF")
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 14.dp)
            ) {
                if (source == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LiquidGlassCard(elevated = true, contentPadding = PaddingValues(24.dp)) {
                            Text("Open Any PDF", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "View standard or password-protected PDFs with pinch-to-zoom and metadata inspection.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(14.dp))
                            LiquidGradientButton(
                                text = "Browse Device PDFs",
                                icon = Icons.Filled.FolderOpen,
                                onClick = { pickLauncher.launch(arrayOf("application/pdf")) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("viewer_browse_btn")
                            )
                        }
                    }
                    return@Scaffold
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.08f))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(1f, 3.5f)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (pageBitmap != null) {
                        Image(
                            bitmap = pageBitmap!!.asImageBitmap(),
                            contentDescription = "PDF Page ${currentPage + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .graphicsLayer(
                                    scaleX = zoomScale,
                                    scaleY = zoomScale
                                )
                        )
                    } else if (state.error != null) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    }
                }

                if (meta != null && meta!!.pageCount > 1) {
                    LiquidGlassCard(
                        elevated = true,
                        cornerRadius = 18.dp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (currentPage > 0) currentPage-- },
                                enabled = currentPage > 0
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous page")
                            }
                            Text(
                                "Page ${currentPage + 1} / ${meta!!.pageCount}  •  Zoom ${(zoomScale * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelLarge
                            )
                            IconButton(
                                onClick = { if (currentPage < meta!!.pageCount - 1) currentPage++ },
                                enabled = currentPage < meta!!.pageCount - 1
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next page")
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    if (showInfoDialog && meta != null) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("PDF Information") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("File: ${meta!!.fileName}")
                    Text("Pages: ${meta!!.pageCount}")
                    Text("Size: ${PdfVaultManager.formatFileSize(meta!!.fileSizeBytes)}")
                    Text("Dimensions: ${meta!!.firstPageWidthPt.roundToInt()} × ${meta!!.firstPageHeightPt.roundToInt()} pt")
                    Text("Author: ${meta!!.author}")
                    Text("Creator: ${meta!!.creator}")
                    Text("Producer: ${meta!!.producer}")
                    Text("Format: ${meta!!.pdfVersion}")
                    Text("Encrypted: ${if (meta!!.isEncrypted) "Yes" else "No"}")
                }
            },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Close") } }
        )
    }

    if (state.needsPassword && source != null) {
        PasswordUnlockDialog(
            value = passwordInput,
            onValueChange = { passwordInput = it },
            onUnlock = {
                val pw = passwordInput; passwordInput = ""
                viewModel.selectSinglePdf(source!!.uri, pw)
            },
            onDismiss = { viewModel.clearNeedsPassword() }
        )
    }
}
