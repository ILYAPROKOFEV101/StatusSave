package com.statusvideo.statussaver.data.repository

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "status_saver_prefs")

@Singleton
class SafUriStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val safUriKey = stringPreferencesKey("saf_whatsapp_uri")

    fun getTreeUri(): Uri? = runBlocking {
        context.dataStore.data
            .map { prefs -> prefs[safUriKey] }
            .firstOrNull()
            ?.let { Uri.parse(it) }
    }

    suspend fun saveTreeUri(uri: Uri) {
        context.dataStore.edit { prefs ->
            prefs[safUriKey] = uri.toString()
        }
    }

    suspend fun clearTreeUri() {
        context.dataStore.edit { prefs ->
            prefs.remove(safUriKey)
        }
    }
}

enum class ThemeMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun from(value: String?): ThemeMode = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

@Singleton
class UserPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val languageTagKey = stringPreferencesKey("language_tag")
    private val backgroundAutoSaveKey = booleanPreferencesKey("background_auto_save_enabled")

    fun getThemeMode(): ThemeMode = runBlocking {
        context.dataStore.data
            .map { prefs -> ThemeMode.from(prefs[themeModeKey]) }
            .firstOrNull() ?: ThemeMode.SYSTEM
    }

    fun getLanguageTag(): String = runBlocking {
        context.dataStore.data
            .map { prefs -> prefs[languageTagKey] ?: "" }
            .firstOrNull().orEmpty()
    }

    fun isBackgroundAutoSaveEnabled(): Boolean = runBlocking {
        context.dataStore.data
            .map { prefs -> prefs[backgroundAutoSaveKey] ?: false }
            .firstOrNull() ?: false
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeModeKey] = mode.value
        }
    }

    suspend fun saveLanguageTag(languageTag: String) {
        context.dataStore.edit { prefs ->
            prefs[languageTagKey] = languageTag
        }
    }

    suspend fun saveBackgroundAutoSaveEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[backgroundAutoSaveKey] = enabled
        }
    }
}
