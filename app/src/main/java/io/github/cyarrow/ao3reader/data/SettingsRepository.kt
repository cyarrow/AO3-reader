package io.github.cyarrow.ao3reader.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val SKIPPED_VERSION = stringPreferencesKey("skipped_version")
    }

    val autoCheckUpdates: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AUTO_CHECK_UPDATES] ?: true }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CHECK_UPDATES] = enabled }
    }

    suspend fun lastUpdateCheck(): Long =
        context.dataStore.data.first()[Keys.LAST_UPDATE_CHECK] ?: 0L

    suspend fun setLastUpdateCheck(time: Long) {
        context.dataStore.edit { it[Keys.LAST_UPDATE_CHECK] = time }
    }

    suspend fun skippedVersion(): String? =
        context.dataStore.data.first()[Keys.SKIPPED_VERSION]

    suspend fun setSkippedVersion(version: String) {
        context.dataStore.edit { it[Keys.SKIPPED_VERSION] = version }
    }
}
