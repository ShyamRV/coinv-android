package com.coinv.app.di

import com.coinv.app.voice.HeadsetMediaController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HeadsetEntryPoint {
    fun headsetMediaController(): HeadsetMediaController
}
