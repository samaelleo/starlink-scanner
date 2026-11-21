package com.starlink.scanner.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "saved_starlinks",
    indices = [Index(value = ["bssid"], unique = true)]
)
data class SavedStarlink(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bssid: String,
    val ssid: String,
    val score: Int,
    val signalStrength: Int,
    val channel: Int,
    val frequency: Int,
    val vendorIEFound: Boolean = false,
    val vendorIEOUI: String? = null,
    val htCapable: Boolean = false,
    val vhtCapable: Boolean = false,
    val heCapable: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val comment: String = ""
)

@Dao
interface SavedStarlinkDao {
    @Query("SELECT * FROM saved_starlinks ORDER BY timestamp DESC")
    fun getAllStarlinks(): Flow<List<SavedStarlink>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(starlink: SavedStarlink)

    @Query("UPDATE saved_starlinks SET comment = :comment WHERE bssid = :bssid")
    suspend fun updateComment(bssid: String, comment: String)

    @Query("DELETE FROM saved_starlinks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM saved_starlinks")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM saved_starlinks")
    fun getStarlinkCount(): Flow<Int>
}
