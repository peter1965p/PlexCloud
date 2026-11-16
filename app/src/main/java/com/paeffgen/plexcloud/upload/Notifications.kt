package com.paeffgen.plexcloud.upload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.paeffgen.plexcloud.MainActivity
import com.paeffgen.plexcloud.R

object Notifications {
    const val UPLOADS = "uploads"
    const val UPDATES = "updates"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(UPLOADS, "Uploads", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(UPDATES, "App-Updates", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun showUpdate(context: Context, version: String) {
        ensureChannels(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_CHECK_UPDATE, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, UPDATES)
            .setSmallIcon(R.drawable.ic_cloud)
            .setContentTitle("PlexCloud $version ist da")
            .setContentText("Tippen zum Installieren")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(4711, n) } // ohne Benachrichtigungsrecht: still
    }
}
