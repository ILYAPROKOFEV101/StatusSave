package com.statusvideo.statussaver.di

import com.statusvideo.statussaver.data.repository.StatusRepositoryImpl
import com.statusvideo.statussaver.domain.repository.StatusRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindStatusRepository(impl: StatusRepositoryImpl): StatusRepository
}
