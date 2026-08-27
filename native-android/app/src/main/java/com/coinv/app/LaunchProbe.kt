package com.coinv.app

import android.content.Context
import android.util.Log
import java.io.File

/** Writes boot breadcrumbs so we can diagnose launch crashes without ADB. */
object LaunchProbe {
    private const val TAG = "CoinVBoot"
    private const val FILE = "coinv-boot.txt"

    fun mark(context: Context, step: String) {
        val line = "${System.currentTimeMillis()} $step"
        Log.i(TAG, line)
        try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            File(dir, FILE).appendText("$line\n")
        } catch (_: Exception) {
        }
    }
}
