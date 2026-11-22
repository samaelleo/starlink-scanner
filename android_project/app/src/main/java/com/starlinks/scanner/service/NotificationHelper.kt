package com.starlinks.scanner.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.starlinks.scanner.MainActivity

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_SCAN_ID = "starlink_scan"
        const val CHANNEL_ALERT_ID = "starlink_alert"
        const val NOTIF_SCAN_ID = 1001
        const val NOTIF_ALERT_ID = 1002
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val scanChannel = NotificationChannel(
                CHANNEL_SCAN_ID,
                "سرویس اسکن پس‌زمینه",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "نمایش وضعیت پایش مداوم شبکه‌ها"
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "هشدارهای شناسایی استارلینک",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "هشدار هنگام کشف روتر استارلینک"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(scanChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    fun buildScanNotification(scanCount: Int, networkCount: Int, starlinkCount: Int): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val content = if (starlinkCount > 0) {
            "🛰 کشف $starlinkCount شبکه استارلینک! (اسکن #$scanCount)"
        } else {
            "در حال اسکن... $networkCount شبکه شناسایی شد (اسکن #$scanCount)"
        }

        return NotificationCompat.Builder(context, CHANNEL_SCAN_ID)
            .setContentTitle("Starlink Scanner Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
    }

    fun sendDetectionAlert(ssid: String, bssid: String, score: Int) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
            .setContentTitle("روتر استارلینک شناسایی شد!")
            .setContentText("$ssid ($bssid) • امتیاز: $score%")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIF_ALERT_ID + (bssid.hashCode() % 100), notif)
    }
}
