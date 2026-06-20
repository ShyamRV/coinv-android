package com.coinv.app

import android.app.Application
import com.coinv.app.data.local.ProfileBootstrap
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CoinVApplication : Application() {

    @Inject lateinit var bootstrap: ProfileBootstrap

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { bootstrap.ensureProfile() }
    }
}
