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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PdfDocumentEntity
import com.example.data.model.SplitMode
import com.example.pdf.PdfVaultManager
import com.example.ui.components.GlassChip
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGradientButton
import com.example.ui.components.PdfDocumentCard
import com.example.ui.components.PdfSuccessCelebrationCard
import com.example.ui.theme.RoyalPurple
import com.example.viewmodel.PdfToolsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergePdfScreen(
    viewModel: PdfToolsViewModel,
    onBack: () -> Unit,
    onDocumentSaved: (PdfDocumentEntity) -> Unit,
    onOpenDocument: (PdfDocumentEntity) -> Unit,
    onShareDocument: (PdfDocumentEntity) -> Unit,
    onSaveCopyToUri: (PdfDocumentEntity, Uri) -> Unit
) {
    val sources by viewModel.mergeSources.collectAsStateWithLifecycle()
    val state by viewModel.toolState.collectAsStateWithLifecycle()
    var outputName by remember { mutableStateOf("Merged_${System.currentTimeMillis() / 1000}") }
    var pendingSafDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }

    LaunchedEffect(Unit) { viewModel.resetToolState() }

    val pickPdfsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.addMergeSources(uris)
    }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
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
                    title = { Text("Merge PDFs") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                    .padding(horizontal = 16.dp)
            ) {
                val created = state.resultDocuments.firstOrNull()
                if (created != null) {
                    Spacer(Modifier.height(16.dp))
                    PdfSuccessCelebrationCard(
                        document = created,
                        subtitle = "Merged ${sources.size} PDFs offline",
                        onOpen = { onOpenDocument(created) },
                        onShare = { onShareDocument(created) },
                        onSaveLocation = {
                            pendingSafDoc = created
                            safLauncher.launch(created.fileName)
                        },
                        onDone = {
                            viewModel.resetToolState()
                            onBack()
                        }
                    )
                    return@Scaffold
                }

                OutlinedButton(
                    onClick = { pickPdfsLauncher.launch(arrayOf("application/pdf")) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("merge_pick_pdfs_btn")
                ) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Select PDF Files to Merge")
                }

                Spacer(Modifier.height(10.dp))

                if (state.error != null) {
                    Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                if (state.isBusy) {
                    LinearProgressIndicator(
                        progress = { state.progress.coerceAtLeast(0.1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(sources) { idx, src ->
                        LiquidGlassCard(
                            cornerRadius = 18.dp,
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, null, tint = RoyalPurple)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${idx + 1}. ${src.displayName}",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        PdfVaultManager.formatFileSize(src.sizeBytes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.moveMergeSource(idx, idx - 1) },
                                    enabled = idx > 0
                                ) {
                                    Icon(Icons.Filled.ArrowUpward, "Move up")
                                }
                                IconButton(
                                    onClick = { viewModel.moveMergeSource(idx, idx + 1) },
                                    enabled = idx < sources.lastIndex
                                ) {
                                    Icon(Icons.Filled.ArrowDownward, "Move down")
                                }
                                IconButton(onClick = { viewModel.removeMergeSource(idx) }) {
                                    Icon(Icons.Filled.Close, "Remove")
                                }
                            }
                        }
                    }
                }

                LiquidGlassCard(
                    elevated = true,
                    cornerRadius = 22.dp,
                    contentPadding = PaddingValues(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                ) {
                    OutlinedTextField(
                        value = outputName,
                        onValueChange = { outputName = it },
                        label = { Text("Merged PDF Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    LiquidGradientButton(
                        text = "Merge ${sources.size} PDFs",
                        icon = Icons.AutoMirrored.Filled.MergeType,
                        enabled = sources.size >= 2 && !state.isBusy,
                        onClick = { viewModel.executeMerge(outputName, onDocumentSaved) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("merge_execute_btn")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SplitPdfScreen(
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

    var modeIndex by remember { mutableIntStateOf(0) } // 0=Range, 1=Every N, 2=All
    var rangeText by remember { mutableStateOf("1-2") }
    var everyNText by remember { mutableStateOf("2") }
    var baseName by remember { mutableStateOf("Split_${System.currentTimeMillis() / 1000}") }
    var passwordPrompt by remember { mutableStateOf("") }
    var pendingSafDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }

    LaunchedEffect(Unit) { viewModel.resetToolState() }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.selectSinglePdf(uri)
    }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
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
                    title = { Text("Split PDF") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    OutlinedButton(
                        onClick = { pickLauncher.launch(arrayOf("application/pdf")) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("split_pick_pdf_btn")
                    ) {
                        Icon(Icons.Filled.FolderOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text(source?.displayName ?: "Choose PDF File")
                    }
                }

                if (meta != null) {
                    item {
                        LiquidGlassCard(cornerRadius = 18.dp, contentPadding = PaddingValues(14.dp)) {
                            Text(meta!!.fileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${meta!!.pageCount} pages • ${PdfVaultManager.formatFileSize(meta!!.fileSizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = RoyalPurple
                            )
                        }
                    }

                    item {
                        LiquidGlassCard(elevated = true, cornerRadius = 20.dp, contentPadding = PaddingValues(16.dp)) {
                            Text("Split Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassChip("Page Range", modeIndex == 0, { modeIndex = 0 })
                                GlassChip("Every N Pages", modeIndex == 1, { modeIndex = 1 })
                                GlassChip("Extract All Pages", modeIndex == 2, { modeIndex = 2 })
                            }
                            Spacer(Modifier.height(12.dp))
                            when (modeIndex) {
                                0 -> OutlinedTextField(
                                    value = rangeText,
                                    onValueChange = { rangeText = it },
                                    label = { Text("Pages (e.g. 1-3, 5)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                1 -> OutlinedTextField(
                                    value = everyNText,
                                    onValueChange = { everyNText = it },
                                    label = { Text("Pages per split file") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                else -> Text(
                                    "Creates ${meta!!.pageCount} individual single-page PDFs.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = baseName,
                                onValueChange = { baseName = it },
                                label = { Text("Output Base Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(14.dp))
                            LiquidGradientButton(
                                text = if (state.isBusy) "Splitting..." else "Split PDF",
                                icon = Icons.AutoMirrored.Filled.CallSplit,
                                enabled = !state.isBusy,
                                onClick = {
                                    val mode = when (modeIndex) {
                                        0 -> SplitMode.PageRange(rangeText)
                                        1 -> SplitMode.EveryNPages(everyNText.toIntOrNull() ?: 1)
                                        else -> SplitMode.ExtractAllPages
                                    }
                                    viewModel.executeSplit(mode, baseName, onDocumentSaved)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("split_execute_btn")
                            )
                        }
                    }
                }

                if (state.error != null) {
                    item {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    }
                }

                if (state.resultDocuments.isNotEmpty()) {
                    item {
                        Text(
                            "Created ${state.resultDocuments.size} split file(s):",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    itemsIndexed(state.resultDocuments) { _, doc ->
                        PdfDocumentCard(
                            document = doc,
                            onOpen = { onOpenDocument(doc) },
                            onShare = { onShareDocument(doc) },
                            onExportDevice = {
                                pendingSafDoc = doc
                                safLauncher.launch(doc.fileName)
                            },
                            onToggleFavorite = {},
                            onRename = {},
                            onDelete = {}
                        )
                    }
                }
            }
        }
    }

    if (state.needsPassword && source != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearNeedsPassword() },
            title = { Text("Unlock PDF") },
            text = {
                OutlinedTextField(
                    value = passwordPrompt,
                    onValueChange = { passwordPrompt = it },
                    label = { Text("Enter PDF Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearNeedsPassword() }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pw = passwordPrompt
                    passwordPrompt = ""
                    viewModel.selectSinglePdf(source!!.uri, pw)
                }) { Text("Unlock") }
            }
        )
    }
}
