package com.blacksingularities.opencalc.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifier {
    const val CHANNEL_UPDATES = "updates"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_UPDATES, "Aktualizacje", NotificationManager.IMPORTANCE_LOW))
    }
}
