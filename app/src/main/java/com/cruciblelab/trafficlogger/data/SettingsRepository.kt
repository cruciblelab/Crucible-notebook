package com.cruciblelab.trafficlogger.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

        /** Aktif ağ profilinin id'si (bkz. NetworkProfile). null/boş = profil kapalı, normal mod. */
        val ACTIVE_PROFILE_ID_KEY = stringPreferencesKey("active_profile_id")

        /**
         * Faz 2 - "DoH'u tamamen bloklama": açıldığında bilinen genel DoH sunucularına
         * (bkz. DohProviders) giden TÜM bağlantılar, hangi uygulamadan geldiğine
         * bakılmaksızın engellenir. Varsayılan kapalı - bazı tarayıcılar/uygulamalar DoH'u
         * her zaman kullanır, bu yüzden kullanıcı bilerek açmalı.
         */
        val BLOCK_KNOWN_DOH_KEY = booleanPreferencesKey("block_known_doh")
    }

    /** null = hiçbir kısıtlama profili aktif değil (normal, tam erişim modu). */
    val activeProfileId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ACTIVE_PROFILE_ID_KEY]?.takeIf { it.isNotBlank() }
    }

    suspend fun setActiveProfileId(id: String?) {
        context.dataStore.edit { prefs ->
            if (id.isNullOrBlank()) prefs.remove(ACTIVE_PROFILE_ID_KEY) else prefs[ACTIVE_PROFILE_ID_KEY] = id
        }
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

    val blockKnownDoh: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BLOCK_KNOWN_DOH_KEY] ?: false
    }

    suspend fun setBlockKnownDoh(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[BLOCK_KNOWN_DOH_KEY] = enabled
        }
    }
}
