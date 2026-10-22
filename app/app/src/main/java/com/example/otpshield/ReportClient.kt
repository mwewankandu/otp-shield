package com.example.otpshield

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ReportClient {
    private const val BASE_URL = "http://127.0.0.1:8000"

    fun report(number: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("$BASE_URL/reports").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true

            val body = JSONObject()
                .put("number", number)
                .put("scam_type", "otp_prize")
                .toString()
            conn.outputStream.use { it.write(body.toByteArray()) }

            val code = conn.responseCode
            Log.d("OtpShield", "Report response code: $code")
            code == 201
        } catch (e: Exception) {
            Log.e("OtpShield", "Report failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }
}