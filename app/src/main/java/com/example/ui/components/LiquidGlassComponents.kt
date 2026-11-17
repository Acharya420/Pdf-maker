package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.local.PdfDocumentEntity
import com.example.pdf.PdfVaultManager
import com.example.ui.theme.DeepPurple
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LocalGlassTokens
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalPurple
import com.example.ui.theme.SoftIndigo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = LocalGlassTokens.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        tokens.backgroundTop,
                        tokens.backgroundMid,
                        tokens.backgroundBottom
                    )
                )
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(tokens.orbPurple, Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.14f),
                        radius = size.maxDimension * 0.45f
                    ),
                    radius = size.maxDimension * 0.45f,
                    center = Offset(size.width * 0.85f, size.height * 0.14f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(tokens.orbNavy, Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.72f),
                        radius = size.maxDimension * 0.50f
                    ),
                    radius = size.maxDimension * 0.50f,
                    center = Offset(size.width * 0.12f, size.height * 0.72f)
                )
            },
        content = content
    )
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    elevated: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalGlassTokens.current
    val shape = RoundedCornerShape(cornerRadius)
    val bgColor = if (elevated) tokens.glassSurfaceElevated else tokens.glassSurface
    val borderBrush = Brush.linearGradient(
        colors = listOf(tokens.glassBorderTop, tokens.glassBorderBottom)
    )

    val cardModifier = modifier
        .clip(shape)
        .background(bgColor)
        .border(width = 1.dp, brush = borderBrush, shape = shape)
        .let { base ->
            if (onClick != null) {
                base
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onClick)
            } else {
                base
            }
        }
        .padding(contentPadding)

    Column(modifier = cardModifier, content = content)
}

@Composable
fun LiquidGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val gradient = Brush.horizontalGradient(
        colors = if (enabled) {
            listOf(MidnightNavy, RoyalPurple, SoftIndigo)
        } else {
            listOf(Color.Gray.copy(alpha = 0.5f), Color.Gray.copy(alpha = 0.4f))
        }
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = PureWhite,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = PureWhite.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .height(52.dp)
            .minimumInteractiveComponentSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient, RoundedCornerShape(18.dp))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalGlassTokens.current
    val bg by animateColorAsState(
        targetValue = if (selected) RoyalPurple else tokens.glassSurface,
        label = "chipBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) PureWhite else MaterialTheme.colorScheme.onSurface,
        label = "chipFg"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) RoyalPurple else tokens.glassBorderBottom
        ),
        modifier = modifier.minimumInteractiveComponentSize()
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                maxLines = 1
            )
        }
    }
}

@Composable
fun PdfDocumentCard(
    document: PdfDocumentEntity,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onExportDevice: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val formattedDate = remember(document.createdAtMillis) {
        dateFormatter.format(Date(document.createdAtMillis))
    }

    LiquidGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pdf_card_${document.id}"),
        cornerRadius = 20.dp,
        onClick = onOpen,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.linearGradient(listOf(DeepPurple, RoyalPurple)),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PictureAsPdf,
                    contentDescription = "PDF file icon",
                    tint = PureWhite,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = document.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (document.isPasswordProtected) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Password protected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${PdfVaultManager.formatFileSize(document.fileSizeBytes)} • ${document.pageCount} pg • $formattedDate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.testTag("favorite_btn_${document.id}")
            ) {
                Icon(
                    imageVector = if (document.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (document.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (document.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("more_btn_${document.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open in Viewer") },
                        leadingIcon = { Icon(Icons.Filled.OpenInNew, null) },
                        onClick = { menuExpanded = false; onOpen() }
                    )
                    DropdownMenuItem(
                        text = { Text("Share PDF") },
                        leadingIcon = { Icon(Icons.Filled.Share, null) },
                        onClick = { menuExpanded = false; onShare() }
                    )
                    DropdownMenuItem(
                        text = { Text("Save to Location...") },
                        leadingIcon = { Icon(Icons.Filled.FileDownload, null) },
                        onClick = { menuExpanded = false; onExportDevice() }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                        onClick = { menuExpanded = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
fun PdfSuccessCelebrationCard(
    document: PdfDocumentEntity,
    subtitle: String = "Generated privately on your device",
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSaveLocation: () -> Unit,
    onDone: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.72f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "successScale"
    )

    LiquidGlassCard(
        elevated = true,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .testTag("pdf_success_card"),
        contentPadding = PaddingValues(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(EmeraldSuccess.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "PDF created successfully",
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = document.fileName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$subtitle • ${PdfVaultManager.formatFileSize(document.fileSizeBytes)} • ${document.pageCount} pages",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LiquidGradientButton(
                    text = "Open PDF",
                    icon = Icons.Filled.OpenInNew,
                    onClick = onOpen,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("success_open_pdf_btn")
                )
                OutlinedButton(
                    onClick = onShare,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .weight(1f)
                        .testTag("success_share_pdf_btn")
                ) {
                    Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onSaveLocation,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f)
                        .testTag("success_save_location_btn")
                ) {
                    Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save As...")
                }
                OutlinedButton(
                    onClick = onDone,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f)
                        .testTag("success_done_btn")
                ) {
                    Text("Done")
                }
            }
        }
    }
}
