package com.paeffgen.plexcloud.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

/** Standard-Akzent: Plexora-Orange */
const val DEFAULT_ACCENT = 0xFFFF7A2FL

class SettingsRepository(private val context: Context) {
    private val accentKey = longPreferencesKey("accent")
    private val wifiOnlyKey = booleanPreferencesKey("wifi_only")
    private val autoUpdateKey = booleanPreferencesKey("auto_update")
    private val notifiedKey = stringPreferencesKey("notified_update")

    /** Akzentfarbe als ARGB */
    val accent: Flow<Long> = context.dataStore.data.map { it[accentKey] ?: DEFAULT_ACCENT }
    /** Fotos und Dateien nur im WLAN hochladen */
    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { it[wifiOnlyKey] ?: true }
    val autoUpdate: Flow<Boolean> = context.dataStore.data.map { it[autoUpdateKey] ?: true }
    val notifiedUpdateVersion: Flow<String> = context.dataStore.data.map { it[notifiedKey].orEmpty() }

    suspend fun setAccent(argb: Long) = context.dataStore.edit { it[accentKey] = argb }
    suspend fun setWifiOnly(on: Boolean) = context.dataStore.edit { it[wifiOnlyKey] = on }
    suspend fun setAutoUpdate(on: Boolean) = context.dataStore.edit { it[autoUpdateKey] = on }
    suspend fun setNotifiedUpdateVersion(v: String) = context.dataStore.edit { it[notifiedKey] = v }
}
