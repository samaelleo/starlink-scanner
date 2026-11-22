package com.starlinks.scanner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

data class ScoringWeights(
    var ouiMatch: Int = 50,
    var vendorIE: Int = 25,
    var htVht: Int = 20,
    var he: Int = 10,
    var hidden: Int = 10,
    var starlinkChannel: Int = 10,
    var cluster: Int = 10,
    var clusterSizeMin: Int = 3
)

@Database(
    entities = [SavedStarlink::class, PinnedNetwork::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedStarlinkDao(): SavedStarlinkDao
    abstract fun pinnedNetworkDao(): PinnedNetworkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "starlink_scanner.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
