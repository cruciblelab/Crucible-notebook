package com.cruciblelab.trafficlogger.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val RETENTION_DAYS_KEY = intPreferencesKey("retention_days")
        const val DEFAULT_RETENTION_DAYS = 7
    }

    val retentionDays: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[RETENTION_DAYS_KEY] ?: DEFAULT_RETENTION_DAYS
    }

    suspend fun setRetentionDays(days: Int) {
        context.dataStore.edit { prefs ->
            prefs[RETENTION_DAYS_KEY] = days
        }
    }
}
