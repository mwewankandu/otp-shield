package com.example.otpshield

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

object ContactChecker {
    fun isInContacts(context: Context, number: String?): Boolean {
        if (number.isNullOrBlank()) return false

        // PhoneLookup matches numbers in different formats (+260..., 097...)
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        return try {
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null
            )?.use { it.count > 0 } ?: false
        } catch (e: SecurityException) {
            false // permission not granted, so treat as unknown and warn
        }
    }
}