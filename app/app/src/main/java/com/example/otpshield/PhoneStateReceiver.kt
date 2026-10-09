package com.example.otpshield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class PhoneStateReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING,
            TelephonyManager.EXTRA_STATE_OFFHOOK -> CallState.active = true
            TelephonyManager.EXTRA_STATE_IDLE -> {
                CallState.active = false
                CallState.number = null
            }
        }

        // Android often sends this broadcast twice, once without the number,
        // so only overwrite when a number is present
        intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            ?.let { CallState.number = it }

        Log.d("OtpShield", "Call active=${CallState.active}, number known=${CallState.number != null}")
    }
}