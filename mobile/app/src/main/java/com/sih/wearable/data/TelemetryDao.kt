package com.sih.wearable.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(t: Telemetry)

    @Query("SELECT * FROM telemetry ORDER BY ts DESC LIMIT 1")
    suspend fun getLatest(): Telemetry?

    @Query("SELECT * FROM telemetry WHERE id > :lastId ORDER BY id ASC LIMIT :limit")
    suspend fun nextBatch(lastId: Long, limit: Int): List<Telemetry>

    @Query("DELETE FROM telemetry WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
