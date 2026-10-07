package com.example.otpshield

object OtpDetector {
    // 4-8 digits that aren't part of a longer number
    private val codeRegex = Regex("""(?<!\d)\d{4,8}(?!\d)""")

    // whole-word keywords, so "pin" won't match "spinning"
    private val keywordRegex = Regex(
        """\b(otp|code|pin|verification|verify|password|one[- ]time)\b""",
        RegexOption.IGNORE_CASE
    )

    fun isOtp(message: String): Boolean =
        codeRegex.containsMatchIn(message) && keywordRegex.containsMatchIn(message)
}