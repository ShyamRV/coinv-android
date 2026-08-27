package com.coinv.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "coinv_settings")

data class AppSettings(
    val theme: String = "dark",
    val wakeWordEnabled: Boolean = false,
    val continuousListening: Boolean = false,
    val privacyAnalytics: Boolean = true,
    val voiceMode: String = "push_to_talk",
    val notificationsEnabled: Boolean = true,
    val memoryRetentionDays: Int = 365,
    val currentAppMode: String = "idle",
    val monitoringEnabled: Boolean = true,
    val localOnlyProcessing: Boolean = true
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val themeKey = stringPreferencesKey("theme")
    private val wakeWordKey = booleanPreferencesKey("wake_word")
    private val continuousKey = booleanPreferencesKey("continuous_listening")
    private val privacyKey = booleanPreferencesKey("privacy_analytics")
    private val voiceModeKey = stringPreferencesKey("voice_mode")
    private val notificationsKey = booleanPreferencesKey("notifications")
    private val retentionKey = intPreferencesKey("memory_retention_days")
    private val currentAppModeKey = stringPreferencesKey("current_app_mode")
    private val monitoringEnabledKey = booleanPreferencesKey("monitoring_enabled")
    private val localOnlyKey = booleanPreferencesKey("local_only_processing")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[themeKey] ?: "dark",
            wakeWordEnabled = prefs[wakeWordKey] ?: false,
            continuousListening = prefs[continuousKey] ?: false,
            privacyAnalytics = prefs[privacyKey] ?: true,
            voiceMode = prefs[voiceModeKey] ?: "push_to_talk",
            notificationsEnabled = prefs[notificationsKey] ?: true,
            memoryRetentionDays = prefs[retentionKey] ?: 365,
            currentAppMode = prefs[currentAppModeKey] ?: "idle",
            monitoringEnabled = prefs[monitoringEnabledKey] ?: true,
            localOnlyProcessing = prefs[localOnlyKey] ?: true
        )
    }

    suspend fun setTheme(theme: String) = context.settingsDataStore.edit { it[themeKey] = theme }
    suspend fun setWakeWord(enabled: Boolean) = context.settingsDataStore.edit { it[wakeWordKey] = enabled }
    suspend fun setContinuousListening(enabled: Boolean) = context.settingsDataStore.edit { it[continuousKey] = enabled }
    suspend fun setPrivacyAnalytics(enabled: Boolean) = context.settingsDataStore.edit { it[privacyKey] = enabled }
    suspend fun setVoiceMode(mode: String) = context.settingsDataStore.edit { it[voiceModeKey] = mode }
    suspend fun setNotifications(enabled: Boolean) = context.settingsDataStore.edit { it[notificationsKey] = enabled }
    suspend fun setMemoryRetention(days: Int) = context.settingsDataStore.edit { it[retentionKey] = days }
    suspend fun setCurrentAppMode(mode: String) = context.settingsDataStore.edit { it[currentAppModeKey] = mode }
    suspend fun setMonitoringEnabled(enabled: Boolean) = context.settingsDataStore.edit { it[monitoringEnabledKey] = enabled }
    suspend fun setLocalOnlyProcessing(enabled: Boolean) = context.settingsDataStore.edit { it[localOnlyKey] = enabled }

    suspend fun clearAll() = context.settingsDataStore.edit { it.clear() }
}
