package com.cruciblelab.trafficlogger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TrafficEntry::class, IpInfoCache::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trafficDao(): TrafficDao
    abstract fun ipInfoDao(): IpInfoDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "traffic_logger.db"
                )
                    // Pre-1.0 app: no need to migrate existing local caches.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
        }
    }
}
