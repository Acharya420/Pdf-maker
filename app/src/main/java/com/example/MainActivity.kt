package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.remote.attemptAutoSignIn
import com.example.pdf.ImageToPdfEngine
import com.example.pdf.PdfToolsEngine
import com.example.pdf.PdfVaultManager
import com.example.ui.PdfMakerRoot
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.stringsForLanguage
import com.example.ui.theme.PdfMakerTheme
import com.example.viewmodel.ImageToPdfViewModel
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.PdfToolsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val mainViewModel: MainViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) as PdfMakerApp
                        MainViewModel(
                            historyRepo = app.pdfHistoryRepository,
                            prefsRepo = app.userPreferencesRepository,
                            cloudRepo = app.cloudSyncRepository
                        )
                    }
                }
            )

            val vaultManager = remember(applicationContext) { PdfVaultManager(applicationContext) }

            val imageToPdfViewModel: ImageToPdfViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) as PdfMakerApp
                        ImageToPdfViewModel(
                            engine = ImageToPdfEngine(app.applicationContext),
                            vaultManager = PdfVaultManager(app.applicationContext)
                        )
                    }
                }
            )

            val pdfToolsViewModel: PdfToolsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) as PdfMakerApp
                        val vmVault = PdfVaultManager(app.applicationContext)
                        PdfToolsViewModel(
                            engine = PdfToolsEngine(app.applicationContext, vmVault),
                            vaultManager = vmVault
                        )
                    }
                }
            )

            val scope = rememberCoroutineScope()
            val credentialManager = remember(this) { CredentialManager.create(this) }
            LaunchedEffect(Unit) {
                attemptAutoSignIn(
                    context = this@MainActivity,
                    credentialManager = credentialManager,
                    onAuthSuccess = {},
                    onUnauthenticated = {},
                    scope = scope
                )
            }

            val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
            val appStrings = remember(preferences.languageCode) {
                stringsForLanguage(preferences.languageCode)
            }

            CompositionLocalProvider(LocalAppStrings provides appStrings) {
                PdfMakerTheme(themeMode = preferences.themeMode) {
                    PdfMakerRoot(
                        mainViewModel = mainViewModel,
                        imageToPdfViewModel = imageToPdfViewModel,
                        pdfToolsViewModel = pdfToolsViewModel,
                        vaultManager = vaultManager
                    )
                }
            }
        }
    }
}
