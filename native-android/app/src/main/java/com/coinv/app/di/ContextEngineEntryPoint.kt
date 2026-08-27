package com.coinv.app.di

import com.coinv.app.engine.ContextEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ContextEngineEntryPoint {
    fun contextEngine(): ContextEngine
}
