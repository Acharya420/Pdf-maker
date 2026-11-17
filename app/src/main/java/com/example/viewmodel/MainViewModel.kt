package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.PdfHistoryRepository
import com.example.data.local.UserPreferences
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.Announcement
import com.example.data.remote.AppGlobalConfig
import com.example.data.remote.CloudSyncRepository
import com.example.data.remote.UserFeedback
import com.example.data.remote.UserProfile
import com.example.data.remote.authStateFlow
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val historyRepo: PdfHistoryRepository,
    private val prefsRepo: UserPreferencesRepository,
    private val cloudRepo: CloudSyncRepository
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val allDocuments: StateFlow<List<PdfDocumentEntity>> = historyRepo.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteDocuments: StateFlow<List<PdfDocumentEntity>> = historyRepo.favoriteDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalStorageBytes: StateFlow<Long> = historyRepo.totalStorageBytes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val currentUser: StateFlow<FirebaseUser?> = Firebase.auth.authStateFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Firebase.auth.currentUser)

    val globalConfig: StateFlow<AppGlobalConfig> = currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(AppGlobalConfig())
            } else {
                cloudRepo.observeGlobalConfig().catch { e ->
                    Log.w(TAG, "Global config observation fallback", e)
                    emit(AppGlobalConfig())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppGlobalConfig())

    val announcements: StateFlow<List<Announcement>> = currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                cloudRepo.observeActiveAnnouncements().catch { e ->
                    Log.w(TAG, "Announcements observation failed", e)
                    emit(emptyList())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfile: StateFlow<UserProfile?> = currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(null)
            } else {
                cloudRepo.observeUserProfile(user.uid).catch { e ->
                    Log.w(TAG, "Profile observation failed", e)
                    emit(null)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val myFeedback: StateFlow<List<UserFeedback>> = currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyList())
            } else {
                cloudRepo.observeMyFeedback(user.uid).catch { e ->
                    Log.w(TAG, "Feedback observation failed", e)
                    emit(emptyList())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    val prefs = prefsRepo.preferencesFlow.first()
                    cloudRepo.ensureUserProfile(
                        displayName = user.displayName ?: prefs.guestDisplayName,
                        email = user.email,
                        photoUrl = user.photoUrl?.toString(),
                        preferredTheme = prefs.themeMode,
                        preferredLanguage = prefs.languageCode
                    )
                }
            }
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun showBanner(message: String) {
        _statusBanner.value = message
    }

    fun recordCreatedPdf(document: PdfDocumentEntity) {
        viewModelScope.launch {
            historyRepo.saveDocument(document)
            if (Firebase.auth.currentUser != null) {
                cloudRepo.recordPdfActivity(
                    recordId = document.id,
                    fileName = document.fileName,
                    operation = document.operation,
                    pageCount = document.pageCount,
                    fileSizeBytes = document.fileSizeBytes,
                    isFavorite = document.isFavorite
                )
            }
        }
    }

    fun toggleFavorite(doc: PdfDocumentEntity) {
        viewModelScope.launch {
            historyRepo.toggleFavorite(doc.id, doc.isFavorite)
        }
    }

    fun renameDocument(doc: PdfDocumentEntity, newName: String) {
        viewModelScope.launch {
            historyRepo.renameDocument(doc, newName)
                .onSuccess { _statusBanner.value = "Renamed to ${it.fileName}" }
                .onFailure { _statusBanner.value = it.message ?: "Rename failed" }
        }
    }

    fun deleteDocument(doc: PdfDocumentEntity) {
        viewModelScope.launch {
            historyRepo.deleteDocument(doc)
            _statusBanner.value = "Deleted ${doc.fileName}"
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            prefsRepo.setThemeMode(mode)
            if (Firebase.auth.currentUser != null) {
                val p = preferences.value
                cloudRepo.updateUserPreferences(mode, p.languageCode, Firebase.auth.currentUser?.displayName ?: p.guestDisplayName)
            }
        }
    }

    fun setLanguageCode(code: String) {
        viewModelScope.launch {
            prefsRepo.setLanguageCode(code)
            if (Firebase.auth.currentUser != null) {
                val p = preferences.value
                cloudRepo.updateUserPreferences(p.themeMode, code, Firebase.auth.currentUser?.displayName ?: p.guestDisplayName)
            }
        }
    }

    fun setGuestDisplayName(name: String) {
        viewModelScope.launch {
            prefsRepo.setGuestDisplayName(name)
        }
    }

    fun setDefaultPageSize(size: String) {
        viewModelScope.launch { prefsRepo.setDefaultPageSize(size) }
    }

    fun setDefaultQuality(quality: String) {
        viewModelScope.launch { prefsRepo.setDefaultQuality(quality) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { prefsRepo.setOnboardingCompleted(true) }
    }

    fun submitFeedback(category: String, subject: String, message: String, appVersion: String) {
        viewModelScope.launch {
            if (Firebase.auth.currentUser == null) {
                _statusBanner.value = "Sign in with Google in Profile to send feedback."
                return@launch
            }
            cloudRepo.submitFeedback(category, subject, message, appVersion)
                .onSuccess { _statusBanner.value = "Feedback submitted. Thank you!" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to submit feedback." }
        }
    }

    private companion object {
        const val TAG = "MainViewModel"
    }
}
