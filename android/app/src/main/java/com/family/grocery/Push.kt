package com.family.grocery

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Push.init(this)
        Sync.schedulePeriodic(this)
    }
}

/**
 * Firebase настраивается не через google-services.json, а настройками,
 * которые телефон получает с вашего сервера. Поэтому в APK нет ничего секретного.
 */
object Push {
    fun init(context: Context) {
        val json = Repo.firebaseJson ?: return
        val topic = Repo.topic ?: return
        try {
            val o = JSONObject(json)
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(o.getString("applicationId"))
                    .setApiKey(o.getString("apiKey"))
                    .setProjectId(o.getString("projectId"))
                    .setGcmSenderId(o.getString("senderId"))
                    .build()
                FirebaseApp.initializeApp(context.applicationContext, options)
            }
            FirebaseMessaging.getInstance().subscribeToTopic(topic)
        } catch (e: Exception) {
            Log.w("Push", "Firebase init failed", e)
        }
    }
}

/** Сервер присылает «список изменился» — скачиваем его и обновляем виджет. */
class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val ctx = applicationContext
        Repo.init(ctx)
        val ok = runBlocking {
            withTimeoutOrNull(15_000) { runCatching { Repo.refresh(ctx) }.isSuccess }
        } == true
        if (!ok) Sync.syncSoon(ctx)
    }

    override fun onNewToken(token: String) {
        Repo.init(applicationContext)
        Push.init(applicationContext)
    }
}
