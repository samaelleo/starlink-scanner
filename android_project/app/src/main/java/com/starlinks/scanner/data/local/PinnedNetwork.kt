package com.starlinks.scanner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pinned_networks")
data class PinnedNetwork(
    @PrimaryKey
    val bssid: String,
    val ssid: String,
    val comment: String = "",
    val signalStrength: Int,
    val channel: Int,
    val frequency: Int,
    val score: Int,
    val vendorIEFound: Boolean = false,
    val vendorIEOUI: String? = null,
    val htCapable: Boolean = false,
    val vhtCapable: Boolean = false,
    val heCapable: Boolean = false,
    val pinnedAt: Long = System.currentTimeMillis()
)

@Dao
interface PinnedNetworkDao {
    @Query("SELECT * FROM pinned_networks ORDER BY pinnedAt DESC")
    fun getAllPinned(): Flow<List<PinnedNetwork>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPinned(pinned: PinnedNetwork)

    @Query("DELETE FROM pinned_networks WHERE bssid = :bssid")
    suspend fun deletePinned(bssid: String)

    @Query("UPDATE pinned_networks SET comment = :comment WHERE bssid = :bssid")
    suspend fun updateComment(bssid: String, comment: String)

    @Query("SELECT EXISTS(SELECT 1 FROM pinned_networks WHERE bssid = :bssid)")
    suspend fun isPinned(bssid: String): Boolean
}
