package com.example.otpshield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val sender = parts.firstOrNull()?.originatingAddress ?: "unknown"
        // Long messages arrive in pieces, so join them before checking
        val body = parts.joinToString("") { it.messageBody ?: "" }

        if (OtpDetector.isOtp(body)) {
            Log.d("OtpShield", "OTP-style SMS detected from $sender")
        } else {
            Log.d("OtpShield", "Normal SMS from $sender")
        }
    }
}