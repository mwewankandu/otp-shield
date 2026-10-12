package com.example.otpshield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val sender = parts.firstOrNull()?.originatingAddress ?: "unknown"
        // Long messages arrive in pieces, so join them before checking
        val body = parts.joinToString("") { it.messageBody ?: "" }

        if (!OtpDetector.isOtp(body)) {
            Log.d("OtpShield", "Normal SMS from $sender")
            return
        }

        if (!isInCall(context)) {
            Log.d("OtpShield", "OTP-style SMS from $sender, no call active")
        } else if (ContactChecker.isInContacts(context, CallState.number)) {
            Log.d("OtpShield", "OTP during a call, but caller is a saved contact")
        } else {
            Log.w("OtpShield", "ALERT: OTP arrived during a call (number known: ${CallState.number != null})")
            AlertNotifier.show(context)
        }
    }

    // Asks Android directly whether a call is active right now,
    // so it still works if HiOS killed the app and our saved state was lost
    @Suppress("DEPRECATION")
    private fun isInCall(context: Context): Boolean {
        val tm = context.getSystemService(TelephonyManager::class.java)
        return tm.callState != TelephonyManager.CALL_STATE_IDLE
    }
}