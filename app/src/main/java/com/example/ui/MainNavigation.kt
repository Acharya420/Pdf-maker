package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.BuildConfig
import com.example.data.local.PdfDocumentEntity
import com.example.pdf.PdfVaultManager
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.LiquidGlassCard
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.screens.CompressPdfScreen
import com.example.ui.screens.FilesLibraryScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.HomeToolDestination
import com.example.ui.screens.ImageToPdfScreen
import com.example.ui.screens.MergePdfScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PageOrganizerScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RotateConvertProtectScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.SplitPdfScreen
import com.example.ui.screens.StudioToolRoute
import com.example.ui.screens.ToolsHubScreen
import com.example.ui.theme.LocalGlassTokens
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalPurple
import com.example.viewmodel.ImageToPdfViewModel
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.PdfToolsViewModel
import java.io.File
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object FilesRoute
@Serializable object ToolsRoute
@Serializable object ProfileRoute
@Serializable object ImageToPdfRoute
@Serializable object MergeRoute
@Serializable object SplitRoute
@Serializable object CompressRoute
@Serializable data class OrganizerRoute(val uriString: String? = null)
@Serializable object RotateRoute
@Serializable object ConvertRoute
@Serializable object ProtectInfoRoute
@Serializable data class ViewerRoute(val filePath: String? = null)

private data class TopLevelDestination(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun PdfMakerRoot(
    mainViewModel: MainViewModel,
    imageToPdfViewModel: ImageToPdfViewModel,
    pdfToolsViewModel: PdfToolsViewModel,
    vaultManager: PdfVaultManager
) {
    var showSplash by remember { mutableStateOf(true) }
    val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()

    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
        return
    }

    if (!preferences.onboardingCompleted) {
        OnboardingScreen(
            onContinueAsGuest = { mainViewModel.completeOnboarding() },
            onSignedIn = { mainViewModel.completeOnboarding() }
        )
        return
    }

    MainAppShell(
        mainViewModel = mainViewModel,
        imageToPdfViewModel = imageToPdfViewModel,
        pdfToolsViewModel = pdfToolsViewModel,
        vaultManager = vaultManager
    )
}

@Composable
private fun MainAppShell(
    mainViewModel: MainViewModel,
    imageToPdfViewModel: ImageToPdfViewModel,
    pdfToolsViewModel: PdfToolsViewModel,
    vaultManager: PdfVaultManager
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val tokens = LocalGlassTokens.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
    val allDocs by mainViewModel.allDocuments.collectAsStateWithLifecycle()
    val favoriteDocs by mainViewModel.favoriteDocuments.collectAsStateWithLifecycle()
    val totalStorageBytes by mainViewModel.totalStorageBytes.collectAsStateWithLifecycle()
    val currentUser by mainViewModel.currentUser.collectAsStateWithLifecycle()
    val globalConfig by mainViewModel.globalConfig.collectAsStateWithLifecycle()
    val announcements by mainViewModel.announcements.collectAsStateWithLifecycle()
    val myFeedback by mainViewModel.myFeedback.collectAsStateWithLifecycle()
    val statusBanner by mainViewModel.statusBanner.collectAsStateWithLifecycle()

    var renamingDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var pendingExportDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var maintenanceDismissed by remember(globalConfig.maintenanceMode) { mutableStateOf(false) }

    val exportSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        val doc = pendingExportDoc
        if (destUri != null && doc != null) {
            vaultManager.copyFileToUri(File(doc.filePath), destUri)
                .onSuccess { mainViewModel.showBanner("Saved copy of ${doc.fileName}") }
                .onFailure { mainViewModel.showBanner(it.localizedMessage ?: "Export failed") }
        }
        pendingExportDoc = null
    }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            mainViewModel.clearStatusBanner()
        }
    }

    val topDestinations = remember(strings) {
        listOf(
            TopLevelDestination(HomeRoute, strings.navHome, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
            TopLevelDestination(FilesRoute, strings.navFiles, Icons.Filled.Folder, Icons.Outlined.Folder, "nav_files"),
            TopLevelDestination(ToolsRoute, strings.navTools, Icons.Filled.Build, Icons.Outlined.Build, "nav_tools"),
            TopLevelDestination(ProfileRoute, strings.navProfile, Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    val isTopLevel = currentDest?.let { dest ->
        dest.hasRoute<HomeRoute>() ||
            dest.hasRoute<FilesRoute>() ||
            dest.hasRoute<ToolsRoute>() ||
            dest.hasRoute<ProfileRoute>()
    } ?: true

    val shareDoc: (PdfDocumentEntity) -> Unit = { doc ->
        val file = File(doc.filePath)
        if (file.exists()) {
            context.startActivity(
                Intent.createChooser(vaultManager.createSharePdfIntent(file), "Share PDF")
            )
        } else {
            mainViewModel.showBanner("File no longer exists on disk.")
        }
    }

    val exportDoc: (PdfDocumentEntity) -> Unit = { doc ->
        pendingExportDoc = doc
        exportSafLauncher.launch(doc.fileName)
    }

    val saveCopyToUri: (PdfDocumentEntity, Uri) -> Unit = { doc, destUri ->
        vaultManager.copyFileToUri(File(doc.filePath), destUri)
            .onSuccess { mainViewModel.showBanner("Saved ${doc.fileName} to selected location") }
            .onFailure { mainViewModel.showBanner(it.localizedMessage ?: "Failed to save copy") }
    }

    val openDocInViewer: (PdfDocumentEntity) -> Unit = { doc ->
        navController.navigate(ViewerRoute(filePath = doc.filePath))
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 640.dp

        LiquidGlassBackground {
            Row(modifier = Modifier.fillMaxSize()) {
                if (isWideScreen && isTopLevel) {
                    NavigationRail(
                        containerColor = tokens.navBarSurface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        Spacer(Modifier.height(16.dp))
                        topDestinations.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(navController, item.route) },
                                icon = { Icon(if (selected) item.selectedIcon else item.unselectedIcon, item.label) },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (!isWideScreen && isTopLevel) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                NavigationBar(
                                    containerColor = tokens.navBarSurface,
                                    tonalElevation = 0.dp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(26.dp))
                                        .border(
                                            1.dp,
                                            Brush.linearGradient(listOf(tokens.glassBorderTop, tokens.glassBorderBottom)),
                                            RoundedCornerShape(26.dp)
                                        )
                                ) {
                                    topDestinations.forEach { item ->
                                        val selected = currentDest?.hasRoute(item.route::class) == true
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { navigateTopLevel(navController, item.route) },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                    contentDescription = item.label
                                                )
                                            },
                                            label = { Text(item.label) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = RoyalPurple,
                                                selectedTextColor = RoyalPurple,
                                                indicatorColor = RoyalPurple.copy(alpha = 0.14f)
                                            ),
                                            modifier = Modifier.testTag(item.testTag)
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .let { m -> if (isWideScreen) m.widthIn(max = 960.dp) else m }
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = HomeRoute,
                            enterTransition = {
                                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(240)) + fadeIn(tween(220))
                            },
                            exitTransition = {
                                fadeOut(tween(180))
                            },
                            popEnterTransition = {
                                fadeIn(tween(200))
                            },
                            popExitTransition = {
                                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(240)) + fadeOut(tween(180))
                            }
                        ) {
                            composable<HomeRoute> {
                                HomeDashboardScreen(
                                    displayName = currentUser?.displayName?.takeIf { it.isNotBlank() }
                                        ?: preferences.guestDisplayName,
                                    isSignedIn = currentUser != null,
                                    recentDocs = allDocs,
                                    favoriteDocs = favoriteDocs,
                                    totalStorageBytes = totalStorageBytes,
                                    globalConfig = globalConfig,
                                    announcements = announcements,
                                    onToolSelected = { dest ->
                                        when (dest) {
                                            HomeToolDestination.IMAGE_TO_PDF -> navController.navigate(ImageToPdfRoute)
                                            HomeToolDestination.COMPRESS -> navController.navigate(CompressRoute)
                                            HomeToolDestination.MERGE -> navController.navigate(MergeRoute)
                                            HomeToolDestination.SPLIT -> navController.navigate(SplitRoute)
                                            HomeToolDestination.VIEWER -> navController.navigate(ViewerRoute())
                                            HomeToolDestination.ORGANIZER -> navController.navigate(OrganizerRoute())
                                            HomeToolDestination.ROTATE -> navController.navigate(RotateRoute)
                                            HomeToolDestination.CONVERTER -> navController.navigate(ConvertRoute)
                                        }
                                    },
                                    onOpenDocument = openDocInViewer,
                                    onShareDocument = shareDoc,
                                    onExportDocument = exportDoc,
                                    onToggleFavorite = { mainViewModel.toggleFavorite(it) },
                                    onRenameDocument = { renamingDoc = it },
                                    onDeleteDocument = { mainViewModel.deleteDocument(it) }
                                )
                            }

                            composable<FilesRoute> {
                                FilesLibraryScreen(
                                    allDocs = allDocs,
                                    totalStorageBytes = totalStorageBytes,
                                    onOpenDocument = openDocInViewer,
                                    onShareDocument = shareDoc,
                                    onExportDocument = exportDoc,
                                    onToggleFavorite = { mainViewModel.toggleFavorite(it) },
                                    onRenameDocument = { renamingDoc = it },
                                    onDeleteDocument = { mainViewModel.deleteDocument(it) }
                                )
                            }

                            composable<ToolsRoute> {
                                ToolsHubScreen(
                                    onOpenTool = { route ->
                                        when (route) {
                                            StudioToolRoute.IMAGE_TO_PDF -> navController.navigate(ImageToPdfRoute)
                                            StudioToolRoute.MERGE -> navController.navigate(MergeRoute)
                                            StudioToolRoute.SPLIT -> navController.navigate(SplitRoute)
                                            StudioToolRoute.COMPRESS -> navController.navigate(CompressRoute)
                                            StudioToolRoute.ORGANIZE -> navController.navigate(OrganizerRoute())
                                            StudioToolRoute.ROTATE -> navController.navigate(RotateRoute)
                                            StudioToolRoute.CONVERT -> navController.navigate(ConvertRoute)
                                            StudioToolRoute.PROTECT_INFO -> navController.navigate(ProtectInfoRoute)
                                            StudioToolRoute.VIEWER -> navController.navigate(ViewerRoute())
                                        }
                                    }
                                )
                            }

                            composable<ProfileRoute> {
                                ProfileScreen(
                                    currentUser = currentUser,
                                    preferences = preferences,
                                    docCount = allDocs.size,
                                    totalStorageBytes = totalStorageBytes,
                                    myFeedback = myFeedback,
                                    onThemeChanged = { mainViewModel.setThemeMode(it) },
                                    onLanguageChanged = { mainViewModel.setLanguageCode(it) },
                                    onGuestNameChanged = { mainViewModel.setGuestDisplayName(it) },
                                    onSubmitFeedback = { cat, subj, msg, ver ->
                                        mainViewModel.submitFeedback(cat, subj, msg, ver)
                                    }
                                )
                            }

                            composable<ImageToPdfRoute> {
                                ImageToPdfScreen(
                                    viewModel = imageToPdfViewModel,
                                    onBack = { navController.popBackStack() },
                                    onDocumentCreated = { mainViewModel.recordCreatedPdf(it) },
                                    onOpenDocument = openDocInViewer,
                                    onShareDocument = shareDoc,
                                    onSaveCopyToUri = saveCopyToUri
                                )
                            }

                            composable<MergeRoute> {
                                MergePdfScreen(
                                    viewModel = pdfToolsViewModel,
                                    onBack = { navController.popBackStack() },
                                    onDocumentSaved = { mainViewModel.recordCreatedPdf(it) },
                                    onOpenDocument = openDocInViewer,
                                    onShareDocument = shareDoc,
                                    onSaveCopyToUri = saveCopyToUri
                                )
                            }

                            composable<SplitRoute> {
                                SplitPdfScreen(
                                    viewModel = pdfToolsViewModel,
                                    onBack = { navController.popBackStack() },
                                    onDocumentSaved = { mainViewModel.recordCreatedPdf(it) },
                                    onOpenDocument = openDocInViewer,
                                    onShareDocument = shareDoc,
                                    onSaveCopyToUri = saveCopyToUri
                                )
                            }

                            composable<CompressRoute> {
                                CompressPdfScreen(
                                    viewModel = pdfToolsViewModel,
                                    onBack = { navController.popBackStack() },
                                    onDocumentSaved = { mainViewModel.recordCreatedPdf(it) },
                                    onOpenDocument = openDocInViewer,
                                    onShareDocument = shareDoc,
                                    onSaveCopyToUri = saveCopyToUri
                                )
                            }

                            composable<OrganizerRoute> { entry ->
                                val args = entry.toRoute<OrganizerRoute>()
                                PageOrganizerScreen(
                                    viewModel = pdfToolsViewModel,
                                    initialPdfUri = args.uriString?.let { Uri.parse(it) },
                                    onBack = { navController.popBackStack() },
                                    onDocumentSaved = { mainViewModel.recordCreatedPdf(it) },
                                    onOpenDocument = openDocInViewer,
                                    onShareDocument = shareDoc,
                                    onSaveCopyToUri = saveCopyToUri
                                )
                            }

                            composable<RotateRoute> {
                                RotateConvertProtectScreen(
                                    mode = StudioToolRoute.ROTATE,
                                    viewModel = pdfToolsViewModel,
                                    onBack = { navController.popBackStack() },
                                    onDocumentSaved = { mainViewModel.recordCreatedPdf(it) },
                                    onOpenDocument = openDocInViewer,
                                    onShareDocument = shareDoc,
                                    onSaveCopyToUri = saveCopyToUri
                                )
                            }

                            composable<ConvertRoute> {
                                RotateConvertProtectScreen(
                                    mode = StudioToolRoute.CONVERT,
                                    viewModel = pdfToolsViewModel,
                                    onBack = { navController.popBackStack() },
                                    onDocumentSaved = { mainViewModel.recordCreatedPdf(it) },
                                    onOpenDocument = openDocInViewer,
                                    onShareDocument = shareDoc,
                                    onSaveCopyToUri = saveCopyToUri
                                )
                            }

                            composable<ProtectInfoRoute> {
                                RotateConvertProtectScreen(
                                    mode = StudioToolRoute.PROTECT_INFO,
                                    viewModel = pdfToolsViewModel,
                                    onBack = { navController.popBackStack() },
                                    onDocumentSaved = { mainViewModel.recordCreatedPdf(it) },
                                    onOpenDocument = openDocInViewer,
                                    onShareDocument = shareDoc,
                                    onSaveCopyToUri = saveCopyToUri
                                )
                            }

                            composable<ViewerRoute> { entry ->
                                val args = entry.toRoute<ViewerRoute>()
                                PdfViewerScreen(
                                    viewModel = pdfToolsViewModel,
                                    vaultManager = vaultManager,
                                    initialFilePath = args.filePath,
                                    onBack = { navController.popBackStack() },
                                    onOpenOrganizer = { uri ->
                                        navController.navigate(OrganizerRoute(uriString = uri.toString()))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (globalConfig.maintenanceMode && currentUser != null && !maintenanceDismissed) {
        AlertDialog(
            onDismissRequest = { maintenanceDismissed = true },
            title = { Text("Scheduled Maintenance") },
            text = {
                Text(
                    globalConfig.maintenanceMessage.ifBlank {
                        "Cloud sync is temporarily undergoing maintenance. Offline local PDF features remain available."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        maintenanceDismissed = true
                        mainViewModel.showBanner("Offline mode active")
                    }
                ) {
                    Text("Continue Offline")
                }
            }
        )
    }

    if (globalConfig.forceUpdate && BuildConfig.VERSION_CODE < globalConfig.minSupportedVersionCode) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Update Available (${globalConfig.latestVersionName})") },
            text = {
                Text(
                    globalConfig.releaseNotes.ifBlank {
                        "A newer version of PDF Maker is available with improvements and security fixes."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { mainViewModel.showBanner("Check Google Play for v${globalConfig.latestVersionName}") }) {
                    Text("Got it")
                }
            }
        )
    }

    renamingDoc?.let { target ->
        var newName by remember(target.id) { mutableStateOf(target.fileName.removeSuffix(".pdf")) }
        AlertDialog(
            onDismissRequest = { renamingDoc = null },
            title = { Text("Rename PDF") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("File Name") },
                    singleLine = true
                )
            },
            dismissButton = { TextButton(onClick = { renamingDoc = null }) { Text("Cancel") } },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        mainViewModel.renameDocument(target, newName)
                        renamingDoc = null
                    }
                ) { Text("Rename") }
            }
        )
    }
}

private fun navigateTopLevel(navController: NavHostController, route: Any) {
    navController.navigate(route) {
        popUpTo<HomeRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
