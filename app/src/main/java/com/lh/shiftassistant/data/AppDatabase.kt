package com.lh.shiftassistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PersonEntity::class,
        ShiftTypeEntity::class,
        LocationEntity::class,
        ScheduleEntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun shiftTypeDao(): ShiftTypeDao
    abstract fun locationDao(): LocationDao
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        const val NAME = "schedule.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}
