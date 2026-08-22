package my.noveldokusha.tooling.quick_setup.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.tooling.quick_setup.transfer.QuickSetupExporter
import my.noveldokusha.tooling.quick_setup.transfer.QuickSetupImporter
import my.noveldoksuha.data.AppRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object QuickSetupModule {

    @Provides
    @Singleton
    fun provideQuickSetupExporter(
        @ApplicationContext context: Context,
        appDatabase: AppDatabase,
        appFileResolver: AppFileResolver,
        appPreferences: AppPreferences,
    ): QuickSetupExporter = QuickSetupExporter(
        context = context,
        appDatabase = appDatabase,
        appFileResolver = appFileResolver,
        appPreferences = appPreferences,
    )

    @Provides
    @Singleton
    fun provideQuickSetupImporter(
        @ApplicationContext context: Context,
        appRepository: AppRepository,
        appFileResolver: AppFileResolver,
        appPreferences: AppPreferences,
    ): QuickSetupImporter = QuickSetupImporter(
        context = context,
        appRepository = appRepository,
        appFileResolver = appFileResolver,
        appPreferences = appPreferences,
    )
}
