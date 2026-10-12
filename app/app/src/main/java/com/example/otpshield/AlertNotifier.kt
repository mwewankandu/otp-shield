package com.example.otpshield

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import android.provider.Settings

object AlertNotifier {
    private const val CHANNEL_ID = "otp_alert"

    fun show(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "OTP scam alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Warns you when a code arrives during a call"
                enableVibration(true)
            }
        )

        val intent = Intent(context, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Do NOT read this code aloud")
            .setContentText("The caller may be trying to take over your account.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(pending, true)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        nm.notify(1, notification)
        if (Settings.canDrawOverlays(context)) {
            context.startActivity(intent)
        }
    }
}