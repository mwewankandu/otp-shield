
package com.example.otpshield

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val caller = intent.getStringExtra("caller")

        setContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFB00020))
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "STOP",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Do NOT read this code to the caller.",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Scammers may start a real login on your account, " +
                            "then trick you into reading the code aloud. Hang up.",
                    fontSize = 16.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                if (!caller.isNullOrBlank()) {
                    Spacer(Modifier.height(24.dp))

                    Text(
                        text = "Caller: $caller",
                        fontSize = 18.sp,
                        color = Color.White
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            Thread {
                                val success = try {
                                    ReportClient.report(caller)
                                } catch (_: Exception) {
                                    false
                                }

                                runOnUiThread {
                                    Toast.makeText(
                                        this@AlertActivity,
                                        if (success) {
                                            "Reported. Thank you."
                                        } else {
                                            "Could not reach the server"
                                        },
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }.start()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFFB00020)
                        )
                    ) {
                        Text("Report this number")
                    }
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        try {
                            val dialIntent = Intent(
                                Intent.ACTION_DIAL,
                                Uri.parse("tel:${Uri.encode("*707#")}")
                            )
                            startActivity(dialIntent)
                        } catch (_: Exception) {
                            Toast.makeText(
                                this@AlertActivity,
                                "Unable to open the phone dialer",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFFB00020)
                    )
                ) {
                    Text("Report to ZICTA (*707#)")
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { finish() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("I understand")
                }
            }
        }
    }
}
