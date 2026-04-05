package com.quantumbox.gemma4poc.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class, MessageEntity::class],
    version = 1,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
