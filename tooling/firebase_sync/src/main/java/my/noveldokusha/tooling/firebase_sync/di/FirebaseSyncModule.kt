package my.noveldokusha.tooling.firebase_sync.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import my.noveldokusha.tooling.firebase_sync.repository.LocalLibraryRepository
import my.noveldokusha.tooling.firebase_sync.repository.LocalLibraryRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseSyncModule {

    @Binds
    abstract fun bindLocalLibraryRepository(
        localLibraryRepositoryImpl: LocalLibraryRepositoryImpl
    ): LocalLibraryRepository
}
