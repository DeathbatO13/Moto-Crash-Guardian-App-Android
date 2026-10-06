package com.motocrashguardian.di

import android.content.Context
import androidx.room.Room
import com.motocrashguardian.data.incidents.GuardianDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideGuardianDatabase(
        @ApplicationContext context: Context
    ): GuardianDatabase = Room.databaseBuilder(
        context,
        GuardianDatabase::class.java,
        "guardian.db"
    ).addMigrations(GuardianDatabase.MIGRATION_1_2)
        .build()
}
