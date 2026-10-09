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
            if (CallState.active) {
                Log.w("OtpShield", "ALERT: OTP arrived during a call (number known: ${CallState.number != null})")
            } else {
                Log.d("OtpShield", "OTP-style SMS from $sender, no call active")
            }
        } else {
            Log.d("OtpShield", "Normal SMS from $sender")
        }
    }
}