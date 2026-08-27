package com.coinv.app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    private const val TAG = "CoinVCrash"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            persist(appContext, throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun persist(context: Context, throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val text = "CoinV crash $stamp\n${sw}"
            Log.e(TAG, text)

            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            File(dir, "coinv-crash-$stamp.txt").writeText(text)
            File(dir, "coinv-last-crash.txt").writeText(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist crash", e)
        }
    }
}
