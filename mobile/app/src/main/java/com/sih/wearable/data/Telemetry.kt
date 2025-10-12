package com.sih.wearable.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telemetry")
data class Telemetry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val team: String,
    val did: String,
    val src: String,
    val ts: Long,
    val batt: Double,
    val hr: Double,
    val spo2: Double,
    val temp: Double,
    val raw: String
)
