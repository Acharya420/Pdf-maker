package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import coil.compose.rememberAsyncImagePainter
import com.example.BuildConfig
import com.example.data.local.UserPreferences
import com.example.data.remote.UserFeedback
import com.example.data.remote.onGoogleSignInClicked
import com.example.data.remote.signOutUser
import com.example.pdf.PdfVaultManager
import com.example.ui.components.GlassChip
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGradientButton
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.DeepPurple
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalPurple
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    currentUser: FirebaseUser?,
    preferences: UserPreferences,
    docCount: Int,
    totalStorageBytes: Long,
    myFeedback: List<UserFeedback>,
    onThemeChanged: (String) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onGuestNameChanged: (String) -> Unit,
    onSubmitFeedback: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var showEditName by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var legalDialogTitle by remember { mutableStateOf<String?>(null) }
    var legalDialogBody by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_screen_list"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(strings.navProfile, style = MaterialTheme.typography.headlineLarge)
        }

        // Identity Card
        item {
            LiquidGlassCard(elevated = true, cornerRadius = 24.dp, contentPadding = PaddingValues(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val photoUrl = currentUser?.photoUrl?.toString()
                    if (!photoUrl.isNullOrBlank()) {
                        Image(
                            painter = rememberAsyncImagePainter(photoUrl),
                            contentDescription = "Profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .background(Brush.linearGradient(listOf(DeepPurple, RoyalPurple)), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Person, "Default profile avatar", tint = PureWhite, modifier = Modifier.size(32.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: preferences.guestDisplayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currentUser?.email ?: "${strings.guestModeLabel} • Offline Vault",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (currentUser == null) {
                        IconButton(onClick = { showEditName = true }) {
                            Icon(Icons.Filled.Edit, "Edit guest name")
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (authError != null) {
                    Text(authError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                if (currentUser == null) {
                    LiquidGradientButton(
                        text = if (isSigningIn) "Signing in..." else "Sign in with Google",
                        enabled = !isSigningIn,
                        onClick = {
                            isSigningIn = true
                            authError = null
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = { isSigningIn = false },
                                onAuthError = { msg -> isSigningIn = false; authError = msg },
                                scope = scope,
                                onAuthCancelled = { isSigningIn = false }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_google_signin_btn")
                    )
                } else {
                    OutlinedButton(
                        onClick = { signOutUser(credentialManager, scope) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("profile_signout_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        }

        // Theme & Language Settings
        item {
            LiquidGlassCard(cornerRadius = 22.dp, contentPadding = PaddingValues(16.dp)) {
                Text(strings.themeLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("System", preferences.themeMode == "system", { onThemeChanged("system") })
                    GlassChip("Light", preferences.themeMode == "light", { onThemeChanged("light") })
                    GlassChip("Dark", preferences.themeMode == "dark", { onThemeChanged("dark") })
                }
                Spacer(Modifier.height(14.dp))
                Text(strings.languageLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("en" to "English", "es" to "Español", "hi" to "हिन्दी", "fr" to "Français", "de" to "Deutsch")
                        .forEach { (code, name) ->
                            GlassChip(name, preferences.languageCode == code, { onLanguageChanged(code) })
                        }
                }
            }
        }

        // Storage Info
        item {
            LiquidGlassCard(cornerRadius = 22.dp, contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Storage, null, tint = RoyalPurple)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(strings.storageLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "$docCount local PDFs • ${PdfVaultManager.formatFileSize(totalStorageBytes)} used on device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Feedback & Legal
        item {
            LiquidGlassCard(cornerRadius = 22.dp, contentPadding = PaddingValues(8.dp)) {
                ProfileLinkRow(
                    icon = Icons.Filled.Feedback,
                    title = strings.sendFeedback,
                    subtitle = if (currentUser == null) "Sign in with Google to send feedback" else "${myFeedback.size} submitted",
                    onClick = { showFeedbackDialog = true }
                )
                ProfileLinkRow(
                    icon = Icons.Filled.PrivacyTip,
                    title = strings.privacyPolicy,
                    subtitle = "100% local PDF & photo processing",
                    onClick = {
                        legalDialogTitle = strings.privacyPolicy
                        legalDialogBody = "PDF Maker processes all images and PDF documents locally on your Android device. Private photos and PDF contents are never uploaded to any server. If you optionally sign in with Google, only your display name, email, app preferences, and non-content PDF activity counts/filenames are stored in Firebase Firestore under strict per-user security rules."
                    }
                )
                ProfileLinkRow(
                    icon = Icons.Filled.Gavel,
                    title = strings.termsConditions,
                    subtitle = "Offline studio license & usage terms",
                    onClick = {
                        legalDialogTitle = strings.termsConditions
                        legalDialogBody = "PDF Maker is provided as a local document utility. You retain 100% ownership and responsibility for documents you create, merge, split, compress, or encrypt. Keep backups of PDF passwords, as encrypted PDFs cannot be recovered without the password."
                    }
                )
                ProfileLinkRow(
                    icon = Icons.Filled.Info,
                    title = strings.aboutApp,
                    subtitle = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                    onClick = {
                        legalDialogTitle = strings.aboutApp
                        legalDialogBody = "PDF Maker v${BuildConfig.VERSION_NAME}\n\nModern Liquid Glass PDF Creation & Editing Studio built with Jetpack Compose, Material 3, Room, DataStore, Android Photo Picker, Storage Access Framework, and Apache PDFBox."
                    }
                )
            }
        }
    }

    if (showEditName) {
        var nameInput by remember { mutableStateOf(preferences.guestDisplayName) }
        AlertDialog(
            onDismissRequest = { showEditName = false },
            title = { Text("Edit Guest Name") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Display Name") },
                    singleLine = true
                )
            },
            dismissButton = { TextButton(onClick = { showEditName = false }) { Text("Cancel") } },
            confirmButton = {
                TextButton(onClick = {
                    onGuestNameChanged(nameInput)
                    showEditName = false
                }) { Text("Save") }
            }
        )
    }

    if (showFeedbackDialog) {
        var category by remember { mutableStateOf("feature") }
        var subject by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text(strings.sendFeedback) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("bug", "feature", "general").forEach { cat ->
                            GlassChip(cat.replaceFirstChar { it.uppercase() }, category == cat, { category = cat })
                        }
                    }
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Message") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            dismissButton = { TextButton(onClick = { showFeedbackDialog = false }) { Text("Cancel") } },
            confirmButton = {
                TextButton(
                    enabled = subject.isNotBlank() && message.isNotBlank(),
                    onClick = {
                        onSubmitFeedback(category, subject, message, BuildConfig.VERSION_NAME)
                        showFeedbackDialog = false
                    }
                ) { Text("Submit") }
            }
        )
    }

    if (legalDialogTitle != null) {
        AlertDialog(
            onDismissRequest = { legalDialogTitle = null },
            title = { Text(legalDialogTitle!!) },
            text = { Text(legalDialogBody, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { legalDialogTitle = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun ProfileLinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = RoyalPurple)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
