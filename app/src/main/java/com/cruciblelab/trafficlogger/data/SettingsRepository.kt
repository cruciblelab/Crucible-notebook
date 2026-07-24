package com.cruciblelab.trafficlogger.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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

        val DAILY_LIMIT_MB_KEY = intPreferencesKey("daily_limit_mb")
        /** 0 means the per-app daily data limit warning is disabled. */
        const val DEFAULT_DAILY_LIMIT_MB = 0

        val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETED_KEY] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETED_KEY] = completed
        }
    }

    val retentionDays: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[RETENTION_DAYS_KEY] ?: DEFAULT_RETENTION_DAYS
    }

    suspend fun setRetentionDays(days: Int) {
        context.dataStore.edit { prefs ->
            prefs[RETENTION_DAYS_KEY] = days
        }
    }

    val dailyLimitMb: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[DAILY_LIMIT_MB_KEY] ?: DEFAULT_DAILY_LIMIT_MB
    }

    suspend fun setDailyLimitMb(mb: Int) {
        context.dataStore.edit { prefs ->
            prefs[DAILY_LIMIT_MB_KEY] = mb
        }
    }
}
