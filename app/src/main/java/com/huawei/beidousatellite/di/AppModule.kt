package com.huawei.beidousatellite.di

import android.content.Context
import androidx.room.Room
import com.huawei.beidousatellite.data.local.SatelliteDatabase
import com.huawei.beidousatellite.data.repository.SatelliteRepository
import com.huawei.beidousatellite.data.region.RegionBypassManager
import com.huawei.beidousatellite.data.hms.HmsSmcManager
import com.huawei.beidousatellite.util.SatelliteLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideLogger(
        @ApplicationContext context: Context
    ): SatelliteLogger = SatelliteLogger(context)

    @Provides
    @Singleton
    fun provideRegionBypassManager(
        @ApplicationContext context: Context,
        logger: SatelliteLogger
    ): RegionBypassManager = RegionBypassManager(context, logger)

    @Provides
    @Singleton
    fun provideHmsSmcManager(
        @ApplicationContext context: Context,
        regionManager: RegionBypassManager,
        logger: SatelliteLogger
    ): HmsSmcManager = HmsSmcManager(context, regionManager, logger)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): SatelliteDatabase {
        return Room.databaseBuilder(
            context,
            SatelliteDatabase::class.java,
            "beidou_satellite.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideRepository(
        db: SatelliteDatabase,
        logger: SatelliteLogger
    ): SatelliteRepository = SatelliteRepository(db, logger)
}
