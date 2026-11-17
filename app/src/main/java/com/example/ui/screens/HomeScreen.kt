package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.PdfDocumentEntity
import com.example.data.remote.Announcement
import com.example.data.remote.AppGlobalConfig
import com.example.pdf.PdfVaultManager
import com.example.ui.components.GlassChip
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGradientButton
import com.example.ui.components.PdfDocumentCard
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.DeepPurple
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.SoftIndigo

enum class HomeToolDestination {
    IMAGE_TO_PDF,
    COMPRESS,
    MERGE,
    SPLIT,
    VIEWER,
    ORGANIZER,
    ROTATE,
    CONVERTER
}

private data class DashboardToolItem(
    val destination: HomeToolDestination,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val enabled: Boolean = true
)

@Composable
fun HomeDashboardScreen(
    displayName: String,
    isSignedIn: Boolean,
    recentDocs: List<PdfDocumentEntity>,
    favoriteDocs: List<PdfDocumentEntity>,
    totalStorageBytes: Long,
    globalConfig: AppGlobalConfig,
    announcements: List<Announcement>,
    onToolSelected: (HomeToolDestination) -> Unit,
    onOpenDocument: (PdfDocumentEntity) -> Unit,
    onShareDocument: (PdfDocumentEntity) -> Unit,
    onExportDocument: (PdfDocumentEntity) -> Unit,
    onToggleFavorite: (PdfDocumentEntity) -> Unit,
    onRenameDocument: (PdfDocumentEntity) -> Unit,
    onDeleteDocument: (PdfDocumentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Recent, 1 = Favorites

    val tools = remember(globalConfig) {
        listOf(
            DashboardToolItem(
                HomeToolDestination.IMAGE_TO_PDF,
                "Image to PDF",
                "Photos & scans",
                Icons.Filled.AddPhotoAlternate,
                RoyalPurple,
                globalConfig.enableImageToPdf
            ),
            DashboardToolItem(
                HomeToolDestination.COMPRESS,
                "Compress PDF",
                "Reduce file size",
                Icons.Filled.Compress,
                Color(0xFF0EA5E9),
                globalConfig.enableCompress
            ),
            DashboardToolItem(
                HomeToolDestination.MERGE,
                "Merge PDF",
                "Combine files",
                Icons.AutoMirrored.Filled.MergeType,
                SoftIndigo,
                globalConfig.enableMerge
            ),
            DashboardToolItem(
                HomeToolDestination.SPLIT,
                "Split PDF",
                "Extract pages",
                Icons.AutoMirrored.Filled.CallSplit,
                Color(0xFFEC4899),
                globalConfig.enableSplit
            ),
            DashboardToolItem(
                HomeToolDestination.VIEWER,
                "PDF Viewer",
                "Read & inspect",
                Icons.Filled.PictureAsPdf,
                Color(0xFF10B981),
                true
            ),
            DashboardToolItem(
                HomeToolDestination.ORGANIZER,
                "Page Organizer",
                "Reorder & delete",
                Icons.Filled.GridView,
                Color(0xFFF59E0B),
                globalConfig.enableOrganizer
            ),
            DashboardToolItem(
                HomeToolDestination.ROTATE,
                "Rotate PDF",
                "Fix orientation",
                Icons.Filled.RotateRight,
                Color(0xFF6366F1),
                true
            ),
            DashboardToolItem(
                HomeToolDestination.CONVERTER,
                "PDF Converter",
                "PDF to PNG/JPG",
                Icons.Filled.Image,
                DeepPurple,
                true
            )
        )
    }

    val displayedDocs = if (selectedTab == 0) recentDocs.take(10) else favoriteDocs

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_dashboard_list"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hello, $displayName",
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = strings.heroSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = RoyalPurple.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = RoyalPurple,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isSignedIn) "Synced" else strings.offlineBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = RoyalPurple
                        )
                    }
                }
            }
        }

        if (announcements.isNotEmpty()) {
            item {
                val ann = announcements.first()
                LiquidGlassCard(
                    elevated = true,
                    cornerRadius = 18.dp,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Campaign,
                            contentDescription = "Announcement",
                            tint = RoyalPurple
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ann.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                ann.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            // Hero Liquid Glass Card for Image to PDF
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(MidnightNavy, DeepPurple, RoyalPurple)
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "IMAGE TO PDF STUDIO",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFDDD6FE)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Turn Photos into Print-Ready PDFs",
                                style = MaterialTheme.typography.headlineMedium,
                                color = PureWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${recentDocs.size} PDFs in vault • ${PdfVaultManager.formatFileSize(totalStorageBytes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureWhite.copy(alpha = 0.82f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .background(PureWhite.copy(alpha = 0.16f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddPhotoAlternate,
                                contentDescription = null,
                                tint = PureWhite,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    LiquidGradientButton(
                        text = strings.createPdfCta,
                        icon = Icons.Filled.AddPhotoAlternate,
                        onClick = { onToolSelected(HomeToolDestination.IMAGE_TO_PDF) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hero_image_to_pdf_btn")
                    )
                }
            }
        }

        item {
            Text(
                text = strings.quickTools,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tools.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { tool ->
                            LiquidGlassCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tool_card_${tool.destination.name.lowercase()}"),
                                cornerRadius = 20.dp,
                                onClick = if (tool.enabled) {
                                    { onToolSelected(tool.destination) }
                                } else null,
                                contentPadding = PaddingValues(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(tool.accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (tool.enabled) tool.icon else Icons.Filled.Lock,
                                            contentDescription = tool.title,
                                            tint = tool.accent,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = tool.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (tool.enabled) tool.subtitle else "Disabled",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassChip(
                    label = "${strings.recentFiles} (${recentDocs.size})",
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.testTag("home_tab_recent")
                )
                GlassChip(
                    label = "${strings.favorites} (${favoriteDocs.size})",
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.testTag("home_tab_favorites")
                )
            }
        }

        if (displayedDocs.isEmpty()) {
            item {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_documents),
                            contentDescription = "No documents yet",
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(22.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = strings.emptyFilesTitle,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = strings.emptyFilesHint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(displayedDocs, key = { it.id }) { doc ->
                AnimatedVisibility(visible = true) {
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
}
