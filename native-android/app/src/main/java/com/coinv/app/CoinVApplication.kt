package com.coinv.app

import android.app.Application
import com.coinv.app.data.local.ProfileBootstrap
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class CoinVApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        // Hilt requires super.onCreate() before EntryPoints.
        super.onCreate()
        CrashLogger.install(this)
        LaunchProbe.mark(this, "application_onCreate")

        appScope.launch {
            try {
                LaunchProbe.mark(this@CoinVApplication, "bootstrap_start")
                val bootstrap = EntryPointAccessors.fromApplication(
                    this@CoinVApplication,
                    BootstrapEntryPoint::class.java
                ).profileBootstrap()
                bootstrap.ensureProfile()
                LaunchProbe.mark(this@CoinVApplication, "bootstrap_ok")
            } catch (t: Throwable) {
                CrashLogger.persist(this@CoinVApplication, t)
                LaunchProbe.mark(this@CoinVApplication, "bootstrap_fail:${t.javaClass.simpleName}")
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootstrapEntryPoint {
        fun profileBootstrap(): ProfileBootstrap
    }
}
