package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.PdfDocumentEntity
import com.example.data.model.PdfOperationType
import com.example.pdf.PdfVaultManager
import com.example.ui.components.GlassChip
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.PdfDocumentCard
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.DeepPurple
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.SoftIndigo

@Composable
fun FilesLibraryScreen(
    allDocs: List<PdfDocumentEntity>,
    totalStorageBytes: Long,
    onOpenDocument: (PdfDocumentEntity) -> Unit,
    onShareDocument: (PdfDocumentEntity) -> Unit,
    onExportDocument: (PdfDocumentEntity) -> Unit,
    onToggleFavorite: (PdfDocumentEntity) -> Unit,
    onRenameDocument: (PdfDocumentEntity) -> Unit,
    onDeleteDocument: (PdfDocumentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var searchQuery by remember { mutableStateOf("") }
    var filterKey by remember { mutableStateOf("all") }

    val filteredDocs = remember(allDocs, searchQuery, filterKey) {
        allDocs.filter { doc ->
            val matchesQuery = searchQuery.isBlank() ||
                doc.fileName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (filterKey) {
                "all" -> true
                "favorites" -> doc.isFavorite
                else -> doc.operation == filterKey
            }
            matchesQuery && matchesFilter
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("files_library_list"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.allFiles,
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = "${allDocs.size} files • ${PdfVaultManager.formatFileSize(totalStorageBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search PDFs by name...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("files_search_input")
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    GlassChip("All", filterKey == "all", { filterKey = "all" })
                }
                item {
                    GlassChip("Favorites", filterKey == "favorites", { filterKey = "favorites" })
                }
                items(PdfOperationType.entries) { op ->
                    GlassChip(op.label, filterKey == op.key, { filterKey = op.key })
                }
            }
        }

        if (filteredDocs.isEmpty()) {
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_documents),
                            contentDescription = "No matching PDFs",
                            modifier = Modifier
                                .size(104.dp)
                                .clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(strings.emptyFilesTitle, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (searchQuery.isNotBlank()) "Try clearing your search filter." else strings.emptyFilesHint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredDocs, key = { it.id }) { doc ->
                PdfDocumentCard(
                    document = doc,
                    onOpen = { onOpenDocument(doc) },
                    onShare = { onShareDocument(doc) },
                    onExportDevice = { onExportDocument(doc) },
                    onToggleFavorite = { onToggleFavorite(doc) },
                    onRename = { onRenameDocument(doc) },
                    onDelete = { onDeleteDocument(doc) }
                )
            }
        }
    }
}

enum class StudioToolRoute {
    IMAGE_TO_PDF,
    MERGE,
    SPLIT,
    COMPRESS,
    ORGANIZE,
    ROTATE,
    CONVERT,
    PROTECT_INFO,
    VIEWER
}

private data class StudioToolEntry(
    val route: StudioToolRoute,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun ToolsHubScreen(
    onOpenTool: (StudioToolRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = remember {
        listOf(
            StudioToolEntry(StudioToolRoute.IMAGE_TO_PDF, "Image to PDF Maker", "Select photos, reorder, crop, rotate & customize page size, margins, DPI & target KB.", Icons.Filled.AddPhotoAlternate, RoyalPurple),
            StudioToolEntry(StudioToolRoute.MERGE, "Merge PDFs", "Combine two or more PDFs into one clean document in any order.", Icons.AutoMirrored.Filled.MergeType, SoftIndigo),
            StudioToolEntry(StudioToolRoute.SPLIT, "Split PDF", "Extract page ranges, split every N pages, or extract all individual pages.", Icons.AutoMirrored.Filled.CallSplit, Color(0xFFEC4899)),
            StudioToolEntry(StudioToolRoute.COMPRESS, "Compress PDF", "Reduce PDF file size using customizable DPI and JPEG compression levels.", Icons.Filled.Compress, Color(0xFF0EA5E9)),
            StudioToolEntry(StudioToolRoute.ORGANIZE, "PDF Page Organizer", "Preview thumbnails, drag/move to reorder, rotate individual pages, or delete pages.", Icons.Filled.GridView, Color(0xFFF59E0B)),
            StudioToolEntry(StudioToolRoute.ROTATE, "Rotate PDF", "Quickly rotate all PDF pages 90°, 180°, or 270° and save.", Icons.Filled.RotateRight, Color(0xFF6366F1)),
            StudioToolEntry(StudioToolRoute.CONVERT, "PDF to Images Converter", "Render and export PDF pages as high-resolution PNG or JPEG images.", Icons.Filled.Image, DeepPurple),
            StudioToolEntry(StudioToolRoute.PROTECT_INFO, "PDF Security & Info", "Add or remove password protection and inspect document metadata.", Icons.Filled.Lock, Color(0xFF10B981)),
            StudioToolEntry(StudioToolRoute.VIEWER, "PDF Viewer", "Open any PDF from your device or vault with zoom, page scrubber & unlock prompt.", Icons.Filled.Info, Color(0xFF0F172A))
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("tools_hub_list"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "PDF Studio Tools",
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = "All operations execute 100% offline on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(entries) { entry ->
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hub_tool_${entry.route.name.lowercase()}"),
                cornerRadius = 22.dp,
                onClick = { onOpenTool(entry.route) },
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(entry.color.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(entry.icon, contentDescription = entry.title, tint = entry.color)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            entry.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
