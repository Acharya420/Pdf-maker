package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PdfDocumentEntity
import com.example.pdf.PdfVaultManager
import com.example.ui.components.GlassChip
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGradientButton
import com.example.ui.components.PdfSuccessCelebrationCard
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoyalPurple
import com.example.viewmodel.PdfToolsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CompressPdfScreen(
    viewModel: PdfToolsViewModel,
    onBack: () -> Unit,
    onDocumentSaved: (PdfDocumentEntity) -> Unit,
    onOpenDocument: (PdfDocumentEntity) -> Unit,
    onShareDocument: (PdfDocumentEntity) -> Unit,
    onSaveCopyToUri: (PdfDocumentEntity, Uri) -> Unit
) {
    val source by viewModel.singleSource.collectAsStateWithLifecycle()
    val meta by viewModel.inspectedMeta.collectAsStateWithLifecycle()
    val state by viewModel.toolState.collectAsStateWithLifecycle()

    var presetIdx by remember { mutableIntStateOf(1) } // 0=Extreme, 1=Balanced, 2=High
    var customQuality by remember { mutableFloatStateOf(65f) }
    var customDpi by remember { mutableFloatStateOf(130f) }
    var outputName by remember { mutableStateOf("Compressed_${System.currentTimeMillis() / 1000}") }
    var passwordPrompt by remember { mutableStateOf("") }
    var pendingSafDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }

    LaunchedEffect(Unit) { viewModel.resetToolState() }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.selectSinglePdf(uri)
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
                    title = { Text("Compress PDF") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val created = state.resultDocuments.firstOrNull()
                if (created != null) {
                    item {
                        val orig = state.compressionOriginalBytes.coerceAtLeast(1L)
                        val comp = state.compressionResultBytes
                        val savedPct = (((orig - comp).toDouble() / orig.toDouble()) * 100).roundToInt()
                        val subtitle = if (savedPct > 0) {
                            "Reduced by $savedPct% (${PdfVaultManager.formatFileSize(orig)} → ${PdfVaultManager.formatFileSize(comp)})"
                        } else {
                            "Optimized PDF (${PdfVaultManager.formatFileSize(comp)})"
                        }
                        PdfSuccessCelebrationCard(
                            document = created,
                            subtitle = subtitle,
                            onOpen = { onOpenDocument(created) },
                            onShare = { onShareDocument(created) },
                            onSaveLocation = {
                                pendingSafDoc = created
                                safLauncher.launch(created.fileName)
                            },
                            onDone = { viewModel.resetToolState(); onBack() }
                        )
                    }
                    return@LazyColumn
                }

                item {
                    OutlinedButton(
                        onClick = { pickLauncher.launch(arrayOf("application/pdf")) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("compress_pick_pdf_btn")
                    ) {
                        Icon(Icons.Filled.FolderOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text(source?.displayName ?: "Choose PDF to Compress")
                    }
                }

                if (meta != null) {
                    item {
                        LiquidGlassCard(elevated = true, cornerRadius = 20.dp, contentPadding = PaddingValues(16.dp)) {
                            Text(
                                "Original: ${PdfVaultManager.formatFileSize(meta!!.fileSizeBytes)} • ${meta!!.pageCount} pages",
                                style = MaterialTheme.typography.titleMedium,
                                color = RoyalPurple
                            )
                            Spacer(Modifier.height(10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassChip("Max Compression", presetIdx == 0, {
                                    presetIdx = 0; customQuality = 42f; customDpi = 90f
                                })
                                GlassChip("Balanced", presetIdx == 1, {
                                    presetIdx = 1; customQuality = 68f; customDpi = 135f
                                })
                                GlassChip("High Clarity", presetIdx == 2, {
                                    presetIdx = 2; customQuality = 85f; customDpi = 190f
                                })
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Quality: ${customQuality.roundToInt()}% • DPI: ${customDpi.roundToInt()}", style = MaterialTheme.typography.bodyMedium)
                            Slider(value = customQuality, onValueChange = { customQuality = it }, valueRange = 25f..95f)
                            Slider(value = customDpi, onValueChange = { customDpi = it }, valueRange = 72f..240f)
                            OutlinedTextField(
                                value = outputName,
                                onValueChange = { outputName = it },
                                label = { Text("Output PDF Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            if (state.isBusy) {
                                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                            }
                            LiquidGradientButton(
                                text = if (state.isBusy) "Compressing..." else "Compress PDF",
                                icon = Icons.Filled.Compress,
                                enabled = !state.isBusy,
                                onClick = {
                                    viewModel.executeCompress(
                                        jpegQuality = customQuality.roundToInt(),
                                        dpi = customDpi.roundToInt(),
                                        outputName = outputName,
                                        onSaved = onDocumentSaved
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("compress_execute_btn")
                            )
                        }
                    }
                }
                if (state.error != null) {
                    item { Text(state.error!!, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (state.needsPassword && source != null) {
        PasswordUnlockDialog(
            value = passwordPrompt,
            onValueChange = { passwordPrompt = it },
            onUnlock = {
                val pw = passwordPrompt; passwordPrompt = ""
                viewModel.selectSinglePdf(source!!.uri, pw)
            },
            onDismiss = { viewModel.clearNeedsPassword() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RotateConvertProtectScreen(
    mode: StudioToolRoute,
    viewModel: PdfToolsViewModel,
    onBack: () -> Unit,
    onDocumentSaved: (PdfDocumentEntity) -> Unit,
    onOpenDocument: (PdfDocumentEntity) -> Unit,
    onShareDocument: (PdfDocumentEntity) -> Unit,
    onSaveCopyToUri: (PdfDocumentEntity, Uri) -> Unit
) {
    val context = LocalContext.current
    val source by viewModel.singleSource.collectAsStateWithLifecycle()
    val meta by viewModel.inspectedMeta.collectAsStateWithLifecycle()
    val state by viewModel.toolState.collectAsStateWithLifecycle()

    var rotateAngle by remember { mutableIntStateOf(90) }
    var asPng by remember { mutableStateOf(true) }
    var newPassword by remember { mutableStateOf("") }
    var unlockPrompt by remember { mutableStateOf("") }
    var outputName by remember { mutableStateOf("Studio_${System.currentTimeMillis() / 1000}") }
    var pendingSafDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }

    LaunchedEffect(mode) { viewModel.resetToolState() }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.selectSinglePdf(uri)
    }
    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val doc = pendingSafDoc
        if (uri != null && doc != null) onSaveCopyToUri(doc, uri)
        pendingSafDoc = null
    }

    val title = when (mode) {
        StudioToolRoute.ROTATE -> "Rotate PDF"
        StudioToolRoute.CONVERT -> "PDF to Images"
        else -> "PDF Info & Security"
    }

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val created = state.resultDocuments.firstOrNull()
                if (created != null) {
                    item {
                        PdfSuccessCelebrationCard(
                            document = created,
                            onOpen = { onOpenDocument(created) },
                            onShare = { onShareDocument(created) },
                            onSaveLocation = {
                                pendingSafDoc = created
                                safLauncher.launch(created.fileName)
                            },
                            onDone = { viewModel.resetToolState(); onBack() }
                        )
                    }
                    return@LazyColumn
                }

                item {
                    OutlinedButton(
                        onClick = { pickLauncher.launch(arrayOf("application/pdf")) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("tool_pick_pdf_btn")
                    ) {
                        Icon(Icons.Filled.FolderOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text(source?.displayName ?: "Select PDF File")
                    }
                }

                if (meta != null) {
                    item {
                        LiquidGlassCard(cornerRadius = 18.dp, contentPadding = PaddingValues(14.dp)) {
                            Text("PDF Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("Title: ${meta!!.title}", style = MaterialTheme.typography.bodyMedium)
                            Text("Pages: ${meta!!.pageCount} • Size: ${PdfVaultManager.formatFileSize(meta!!.fileSizeBytes)}", style = MaterialTheme.typography.bodyMedium)
                            Text("Dimensions: ${meta!!.firstPageWidthPt.roundToInt()} × ${meta!!.firstPageHeightPt.roundToInt()} pt", style = MaterialTheme.typography.bodyMedium)
                            Text("Version: ${meta!!.pdfVersion} • Encrypted: ${if (meta!!.isEncrypted) "Yes" else "No"}", style = MaterialTheme.typography.bodyMedium)
                            Text("Producer: ${meta!!.producer}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    item {
                        LiquidGlassCard(elevated = true, cornerRadius = 20.dp, contentPadding = PaddingValues(16.dp)) {
                            when (mode) {
                                StudioToolRoute.ROTATE -> {
                                    Text("Rotation Angle (All Pages)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        GlassChip("90° CW", rotateAngle == 90, { rotateAngle = 90 })
                                        GlassChip("180°", rotateAngle == 180, { rotateAngle = 180 })
                                        GlassChip("90° CCW", rotateAngle == 270, { rotateAngle = 270 })
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedTextField(value = outputName, onValueChange = { outputName = it }, label = { Text("Output Name") }, modifier = Modifier.fillMaxWidth())
                                    Spacer(Modifier.height(12.dp))
                                    LiquidGradientButton(
                                        text = "Rotate & Save PDF",
                                        icon = Icons.Filled.RotateRight,
                                        enabled = !state.isBusy,
                                        onClick = { viewModel.executeRotateAll(rotateAngle, outputName, onDocumentSaved) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                StudioToolRoute.CONVERT -> {
                                    Text("Export Format", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        GlassChip("PNG (Lossless)", asPng, { asPng = true })
                                        GlassChip("JPEG (Compact)", !asPng, { asPng = false })
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedTextField(value = outputName, onValueChange = { outputName = it }, label = { Text("Image Prefix") }, modifier = Modifier.fillMaxWidth())
                                    Spacer(Modifier.height(12.dp))
                                    LiquidGradientButton(
                                        text = if (state.isBusy) "Converting..." else "Convert ${meta!!.pageCount} Pages to Images",
                                        icon = Icons.Filled.Image,
                                        enabled = !state.isBusy,
                                        onClick = { viewModel.executeConvertToImages(asPng, 92, outputName) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                else -> {
                                    Text("Password Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = newPassword,
                                        onValueChange = { newPassword = it },
                                        label = { Text("New Password (leave blank to unlock/remove)") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedTextField(value = outputName, onValueChange = { outputName = it }, label = { Text("Output Name") }, modifier = Modifier.fillMaxWidth())
                                    Spacer(Modifier.height(12.dp))
                                    LiquidGradientButton(
                                        text = if (newPassword.isBlank()) "Save Unlocked PDF" else "Encrypt & Save PDF",
                                        icon = Icons.Filled.Lock,
                                        enabled = !state.isBusy,
                                        onClick = { viewModel.executeProtectOrUnlock(newPassword.ifBlank { null }, outputName, onDocumentSaved) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.exportedImages.isNotEmpty()) {
                    item {
                        LiquidGlassCard(elevated = true, cornerRadius = 20.dp, contentPadding = PaddingValues(16.dp)) {
                            Text(
                                "Exported ${state.exportedImages.size} image(s)!",
                                style = MaterialTheme.typography.titleLarge,
                                color = EmeraldSuccess,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            state.exportedImages.take(6).forEach { f ->
                                Text("• ${f.name} (${PdfVaultManager.formatFileSize(f.length())})", style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(10.dp))
                            LiquidGradientButton(
                                text = "Share First Image",
                                onClick = {
                                    val first = state.exportedImages.first()
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", first)
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "image/*"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Share Image"))
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (state.error != null) {
                    item { Text(state.error!!, color = MaterialTheme.colorScheme.error) }
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
                viewModel.selectSinglePdf(source!!.uri, pw)
            },
            onDismiss = { viewModel.clearNeedsPassword() }
        )
    }
}

@Composable
fun PasswordUnlockDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onUnlock: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Password Protected PDF") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Enter Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = onUnlock) { Text("Unlock") } }
    )
}
