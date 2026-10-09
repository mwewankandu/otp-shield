package com.example.otpshield


object CallState {
    @Volatile var active = false
    @Volatile var number: String? = null
}