package my.noveldokusha.tooling.local_server_sync.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import my.noveldokusha.tooling.local_server_sync.api.LocalServerApiService
import my.noveldokusha.tooling.local_server_sync.auth.LocalServerAuthService
import my.noveldokusha.tooling.local_server_sync.repository.LocalLibraryRepository
import my.noveldokusha.tooling.local_server_sync.repository.LocalLibraryRepositoryImpl
import my.noveldokusha.tooling.local_server_sync.repository.LocalServerSyncRepository
import my.noveldokusha.tooling.local_server_sync.storage.AuthTokenStorage
import my.noveldokusha.tooling.local_server_sync.manager.LocalServerSyncManager
import my.noveldokusha.tooling.local_server_sync.image.ImageIndexCache
import my.noveldokusha.tooling.local_server_sync.image.LocalServerImageService
import my.noveldokusha.tooling.local_server_sync.sync.WiFiSyncScheduler
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.core.AppFileResolver
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalServerSyncModule {

    @Provides
    @Singleton
    fun provideAuthTokenStorage(
        @ApplicationContext context: Context
    ): AuthTokenStorage = AuthTokenStorage(context)

    @Provides
    @Singleton
    fun provideLocalServerApiService(
        tokenStorage: AuthTokenStorage
    ): LocalServerApiService = LocalServerApiService(tokenStorage)

    @Provides
    @Singleton
    fun provideLocalServerAuthService(
        apiService: LocalServerApiService,
        tokenStorage: AuthTokenStorage
    ): LocalServerAuthService = LocalServerAuthService(apiService, tokenStorage)

    @Provides
    @Singleton
    fun provideLocalServerSyncRepository(
        apiService: LocalServerApiService
    ): LocalServerSyncRepository = LocalServerSyncRepository(apiService)

    @Provides
    @Singleton
    fun provideLocalLibraryRepository(
        database: AppDatabase
    ): LocalLibraryRepository = LocalLibraryRepositoryImpl(database)

    @Provides
    @Singleton
    fun provideLocalServerSyncManager(
        authService: LocalServerAuthService,
        syncRepository: LocalServerSyncRepository,
        localLibraryRepository: LocalLibraryRepository,
        imageService: LocalServerImageService,
        tokenStorage: AuthTokenStorage
    ): LocalServerSyncManager = LocalServerSyncManager(
        authService = authService,
        syncRepository = syncRepository,
        localLibraryRepository = localLibraryRepository,
        imageService = imageService,
        tokenStorage = tokenStorage
    )

    @Provides
    @Singleton
    fun provideLocalServerImageService(
        @ApplicationContext context: Context,
        appFileResolver: AppFileResolver,
        imageIndexCache: ImageIndexCache
    ): LocalServerImageService = LocalServerImageService(context, appFileResolver, imageIndexCache)

    @Provides
    @Singleton
    fun provideWiFiSyncScheduler(
        @ApplicationContext context: Context,
        tokenStorage: AuthTokenStorage
    ): WiFiSyncScheduler = WiFiSyncScheduler(context, tokenStorage)
}
