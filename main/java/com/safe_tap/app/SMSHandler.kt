package com.safe_tap.app

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

class SMSHandler(private val context: Context) {

    companion object {
        const val MDRRMO_NUMBER = "+639984920502"
        private const val TAG = "SMSHandler"
    }

    fun sendEmergencySMS(
        userName: String,
        emergencyType: String,
        latitude: Double,
        longitude: Double,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val message = buildEmergencyMessage(userName, emergencyType, latitude, longitude)

        Log.d(TAG, "=== SENDING SMS ===")
        Log.d(TAG, "To: $MDRRMO_NUMBER")
        Log.d(TAG, "Message length: ${message.length} chars")
        Log.d(TAG, "Message: $message")

        try {
            // Get the default SmsManager
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // For dual SIM support
            val subscriptionId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                android.telephony.SubscriptionManager.getDefaultSmsSubscriptionId()
            } else {
                -1
            }

            Log.d(TAG, "Using subscription ID: $subscriptionId")

            // Split message if it's too long (>160 chars)
            val parts = smsManager.divideMessage(message)

            if (parts.size > 1) {
                Log.d(TAG, "Sending multipart SMS (${parts.size} parts)")

                // Send multipart message
                smsManager.sendMultipartTextMessage(
                    MDRRMO_NUMBER,
                    null,
                    parts,
                    null,
                    null
                )
            } else {
                Log.d(TAG, "Sending single SMS")

                // Send single message
                smsManager.sendTextMessage(
                    MDRRMO_NUMBER,
                    null,
                    message,
                    null,
                    null
                )
            }

            Log.d(TAG, "✅ SMS sent successfully to network")

            // Give it a moment to process
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                onSuccess()
            }, 500)

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Security Exception: ${e.message}")
            e.printStackTrace()
            onFailure("SMS permission denied - ${e.message}")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "❌ Invalid argument: ${e.message}")
            e.printStackTrace()
            onFailure("Invalid phone number or message - ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception: ${e.message}")
            e.printStackTrace()
            onFailure(e.message ?: "Failed to send SMS")
        }
    }

    private fun buildEmergencyMessage(
        userName: String,
        emergencyType: String,
        latitude: Double,
        longitude: Double
    ): String {
        val time = SimpleDateFormat("hh:mma", Locale.getDefault()).format(Date())
        val url = "https://maps.google.com/?q=$latitude,$longitude"

        // Short message format
        return "🚨$emergencyType\n$userName\n$time\n$url"
    }
}