package com.example.platecollector.data


import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlateDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: PlateRecord): Long

    @Query("UPDATE plates SET lastSeen = :now, count = count + 1 WHERE plate = :plate")
    suspend fun touch(plate: String, now: Long)

    @Query("SELECT * FROM plates ORDER BY firstSeen DESC")
    fun all(): Flow<List<PlateRecord>>

    @Query("SELECT * FROM plates WHERE plate LIKE '%' || :q || '%' ORDER BY firstSeen DESC")
    suspend fun search(q: String): List<PlateRecord>

    @Query("SELECT EXISTS(SELECT 1 FROM plates WHERE plate = :plate)")
    suspend fun exists(plate: String): Boolean

    @Query("SELECT * FROM plates ORDER BY firstSeen DESC")
    suspend fun getAllOnce(): List<PlateRecord>
    @Update
    suspend fun update(record: PlateRecord)

    @Delete
    suspend fun delete(record: PlateRecord)

}