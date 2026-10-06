package com.motocrashguardian.data.incidents

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [IncidentEntity::class, IncidentTraceEntity::class],
    version = 2,
    exportSchema = true
)
abstract class GuardianDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE incidents ADD COLUMN countdown_deadline INTEGER")
            }
        }
    }
}
