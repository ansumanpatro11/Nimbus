package com.sih.wearable.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Telemetry::class], version = 1)
abstract class AppDb : RoomDatabase() {
    abstract fun telemDao(): TelemetryDao
    companion object {
        @Volatile private var INSTANCE: AppDb? = null
        fun get(ctx: Context): AppDb = INSTANCE ?: synchronized(this){
            INSTANCE ?: Room.databaseBuilder(ctx, AppDb::class.java, "sih.db").build().also { INSTANCE = it }
        }
    }
}
