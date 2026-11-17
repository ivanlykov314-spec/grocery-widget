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
        runCatching { events(ctx).delete() }
    }

    // ---- Журнал событий виджета (временно, для поиска «кнопки не работают») ----
    private fun events(ctx: Context) = File(ctx.applicationContext.filesDir, "widget_events.txt")

    /** Добавить строку в журнал; хранятся последние 40 строк. */
    @Synchronized
    fun event(ctx: Context, msg: String) {
        Log.i("Grocery", msg)
        runCatching {
            val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            val f = events(ctx)
            val old = if (f.exists()) f.readLines() else emptyList()
            f.writeText((old + "$time $msg").takeLast(40).joinToString("\n"))
        }
    }

    fun readEvents(ctx: Context): String? = runCatching {
        events(ctx).takeIf { it.exists() }?.readText()
    }.getOrNull()

    fun installCrashHandler(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { th, e ->
            record(app, "crash in ${th.name}", e)
            prev?.uncaughtException(th, e)
        }
    }
}
