package net.alan.jupiter.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.alan.jupiter.data.remote.UpdateManager

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdateManagerEntryPoint {
    fun updateManager(): UpdateManager
}