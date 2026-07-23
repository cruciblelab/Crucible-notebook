package com.cruciblelab.trafficlogger

import android.app.Application
import com.cruciblelab.trafficlogger.data.AppDatabase
import com.cruciblelab.trafficlogger.data.SettingsRepository
import com.cruciblelab.trafficlogger.data.TrafficRepository

class TrafficLoggerApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val trafficRepository by lazy { TrafficRepository(database.trafficDao()) }
    val settingsRepository by lazy { SettingsRepository(this) }
}
