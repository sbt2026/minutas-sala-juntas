package com.contexto.minutas

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.Collections

/**
 * Servicio en primer plano: mantiene la grabación y transcripción activas
 * aunque el usuario use otras apps o ventanas en el panel.
 */
class RecordingService : Service() {

    companion object {
        val segments: MutableList<String> =
            Collections.synchronizedList(mutableListOf<String>())
        @Volatile var partial = ""
        @Volatile var running = false
        @Volatile var startTime = 0L
        @Volatile var uiListener: (() -> Unit)? = null
        @Volatile var statusListener: ((String) -> Unit)? = null

        fun reset() { segments.clear(); partial = "" }
    }

    private var speech: SpeechManager? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_STICKY
        running = true
        startTime = System.currentTimeMillis()
        startForegroundNotif()

        val lang = getSharedPreferences("minutas", MODE_PRIVATE)
            .getString("idioma", "es-MX") ?: "es-MX"
        speech = SpeechManager(this, lang,
            onPartial = { p -> partial = p; notifyUi() },
            onFinal = { f -> segments.add(f); partial = ""; notifyUi() },
            onStatus = { s -> main.post { statusListener?.invoke(s) } }
        )
        speech?.start()
        return START_STICKY
    }

    private fun notifyUi() = main.post { uiListener?.invoke() }

    override fun onDestroy() {
        speech?.stop()
        speech = null
        if (partial.isNotBlank()) { segments.add(partial); partial = "" }
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNotif() {
        val chId = "grabacion"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(
                chId, "Grabación de reunión", NotificationManager.IMPORTANCE_LOW))
        }
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, chId) else @Suppress("DEPRECATION") Notification.Builder(this)
        val notif = builder
            .setContentTitle("Grabando reunión")
            .setContentText("La transcripción continúa en segundo plano")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 30)
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else
            startForeground(1, notif)
    }
}
