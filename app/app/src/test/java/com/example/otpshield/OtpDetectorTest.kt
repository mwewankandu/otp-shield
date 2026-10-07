package com.example.otpshield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpDetectorTest {
    @Test fun detectsTypicalOtp() =
        assertTrue(OtpDetector.isOtp("Your verification code is 482915. Do not share it."))

    @Test fun detectsPin() =
        assertTrue(OtpDetector.isOtp("Use PIN 7391 to reset your password."))

    @Test fun ignoresNormalChat() =
        assertFalse(OtpDetector.isOtp("Hey, are we meeting at 14 30 today?"))

    @Test fun ignoresLongNumbers() =
        assertFalse(OtpDetector.isOtp("Your code reference is 123456789012"))

    @Test fun ignoresKeywordWithoutDigits() =
        assertFalse(OtpDetector.isOtp("Please enter the code we sent you"))

    @Test fun `detects mtn otp`() = assertTrue(
        OtpDetector.isOtp("""Your MyMTN NextGen OTP is: 4721. Do not share this OTP. MTN is not liable for incident that result from sharing your OTP. @mtnid.mtn.zm #4721""")
    )

    @Test fun `detects zamtel otp`() = assertTrue(
        OtpDetector.isOtp("""Dear Customer, your OTP is 518230.
Please do not share it with anyone. If you did not request this OTP, contact Zamtel immediately on 111.
hCBuOTpsPmQhCBuOTpsPmQ""")
    )

    @Test fun `ignores zamtel money receipt`() = assertFalse(
        OtpDetector.isOtp("""Dear Customer, you have received ZMW 1,500.00 from John Banda - 0970000000 on 03-10-2026 16:59:01 under Txn ID: 9ABC1DE3. Your account balance is now ZMW 4,722.93. Manage your money smarter download the MyZamtel App today!""")
    )
}