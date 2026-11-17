package com.family.grocery

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Запоминает последнюю ошибку, чтобы её можно было увидеть в приложении. */
object Diag {
    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "last_error.txt")

    fun record(ctx: Context, where: String, t: Throwable) {
        Log.e("Grocery", where, t)
        runCatching {
            val time = SimpleDateFormat("dd.MM HH:mm:ss", Locale.US).format(Date())
            file(ctx).writeText("$time [$where]\n" + Log.getStackTraceString(t).take(6000))
        }
    }

    fun read(ctx: Context): String? = runCatching {
        file(ctx).takeIf { it.exists() }?.readText()
    }.getOrNull()

    fun clear(ctx: Context) {
        runCatching { file(ctx).delete() }
    }

    fun installCrashHandler(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { th, e ->
            record(app, "crash in ${th.name}", e)
            prev?.uncaughtException(th, e)
        }
    }
}
