package com.paeffgen.plexcloud.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
    private val photoBackupKey = booleanPreferencesKey("backup_photos")
    private val videoBackupKey = booleanPreferencesKey("backup_videos")
    private val photoSinceKey = longPreferencesKey("backup_photos_since")
    private val foldersKey = stringSetPreferencesKey("backup_folders")
    private val lastBackupKey = longPreferencesKey("backup_last_run")
    private val lastBackupCountKey = longPreferencesKey("backup_last_count")
    private val albumsKey = stringSetPreferencesKey("backup_albums")

    /** Akzentfarbe als ARGB */
    val accent: Flow<Long> = context.dataStore.data.map { it[accentKey] ?: DEFAULT_ACCENT }
    /** Fotos und Dateien nur im WLAN hochladen */
    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { it[wifiOnlyKey] ?: true }
    val autoUpdate: Flow<Boolean> = context.dataStore.data.map { it[autoUpdateKey] ?: true }
    val notifiedUpdateVersion: Flow<String> = context.dataStore.data.map { it[notifiedKey].orEmpty() }

    // ── Automatische Sicherung ──
    val photoBackup: Flow<Boolean> = context.dataStore.data.map { it[photoBackupKey] ?: false }
    val videoBackup: Flow<Boolean> = context.dataStore.data.map { it[videoBackupKey] ?: false }
    /** Fotos ab diesem Zeitpunkt (MediaStore DATE_ADDED, Sekunden) werden gesichert */
    val photoSince: Flow<Long> = context.dataStore.data.map { it[photoSinceKey] ?: 0L }
    /** Gesicherte Ordner mit Stand: "<ms>|<tree-uri>" */
    val folders: Flow<List<BackupFolder>> = context.dataStore.data.map { p -> (p[foldersKey] ?: emptySet()).mapNotNull(BackupFolder::decode).sortedBy { it.uri } }
    /** Gewählte Alben (MediaStore BUCKET_ID); leer = alle */
    val albums: Flow<Set<String>> = context.dataStore.data.map { it[albumsKey] ?: emptySet() }
    suspend fun setAlbums(ids: Set<String>) = context.dataStore.edit { it[albumsKey] = ids }
    val lastBackup: Flow<Pair<Long, Long>> = context.dataStore.data.map { (it[lastBackupKey] ?: 0L) to (it[lastBackupCountKey] ?: 0L) }

    suspend fun setPhotoBackup(on: Boolean, sinceSec: Long?) = context.dataStore.edit { p ->
        p[photoBackupKey] = on
        if (sinceSec != null) p[photoSinceKey] = sinceSec
    }
    suspend fun setVideoBackup(on: Boolean) = context.dataStore.edit { it[videoBackupKey] = on }
    suspend fun setPhotoSince(sec: Long) = context.dataStore.edit { it[photoSinceKey] = sec }
    suspend fun putFolder(f: BackupFolder) = context.dataStore.edit { p ->
        p[foldersKey] = (p[foldersKey] ?: emptySet()).filterNot { BackupFolder.decode(it)?.uri == f.uri }.toSet() + f.encode()
    }
    suspend fun removeFolder(uri: String) = context.dataStore.edit { p ->
        p[foldersKey] = (p[foldersKey] ?: emptySet()).filterNot { BackupFolder.decode(it)?.uri == uri }.toSet()
    }
    suspend fun setLastBackup(atMs: Long, count: Long) = context.dataStore.edit { it[lastBackupKey] = atMs; it[lastBackupCountKey] = count }

    suspend fun setAccent(argb: Long) = context.dataStore.edit { it[accentKey] = argb }
    suspend fun setWifiOnly(on: Boolean) = context.dataStore.edit { it[wifiOnlyKey] = on }
    suspend fun setAutoUpdate(on: Boolean) = context.dataStore.edit { it[autoUpdateKey] = on }
    suspend fun setNotifiedUpdateVersion(v: String) = context.dataStore.edit { it[notifiedKey] = v }
}

/** Ein gesicherter Ordner (Speicherzugriff über den System-Dateiauswähler) und bis wann er gesichert ist */
data class BackupFolder(val uri: String, val sinceMs: Long) {
    fun encode() = "$sinceMs|$uri"

    companion object {
        fun decode(s: String): BackupFolder? {
            val i = s.indexOf('|')
            if (i <= 0) return null
            return BackupFolder(s.substring(i + 1).ifEmpty { return null }, s.substring(0, i).toLongOrNull() ?: return null)
        }
    }
}
