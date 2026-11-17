package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pdf_maker_prefs")

data class UserPreferences(
    val themeMode: String = "system",
    val languageCode: String = "en",
    val guestDisplayName: String = "Guest Creator",
    val onboardingCompleted: Boolean = false,
    val defaultPageSize: String = "A4",
    val defaultQuality: String = "MEDIUM"
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val GUEST_NAME = stringPreferencesKey("guest_name")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DEFAULT_PAGE_SIZE = stringPreferencesKey("default_page_size")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[Keys.THEME_MODE] ?: "system",
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "en",
            guestDisplayName = prefs[Keys.GUEST_NAME] ?: "Guest Creator",
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            defaultPageSize = prefs[Keys.DEFAULT_PAGE_SIZE] ?: "A4",
            defaultQuality = prefs[Keys.DEFAULT_QUALITY] ?: "MEDIUM"
        )
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setLanguageCode(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE_CODE] = code }
    }

    suspend fun setGuestDisplayName(name: String) {
        context.dataStore.edit { it[Keys.GUEST_NAME] = name.trim().ifBlank { "Guest Creator" } }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setDefaultPageSize(size: String) {
        context.dataStore.edit { it[Keys.DEFAULT_PAGE_SIZE] = size }
    }

    suspend fun setDefaultQuality(quality: String) {
        context.dataStore.edit { it[Keys.DEFAULT_QUALITY] = quality }
    }
}
