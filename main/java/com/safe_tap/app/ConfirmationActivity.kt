package com.safe_tap.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class ConfirmationActivity : ComponentActivity() {
    private lateinit var tvCountdown: TextView
    private lateinit var tvEmergencyType: TextView
    private lateinit var btnCancel: Button
    private var countDownTimer: CountDownTimer? = null
    private var emergencyType: String = ""

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var sessionManager: SessionManager
    private lateinit var smsHandler: SMSHandler
    private lateinit var locationHandler: LocationHandler

    companion object {
        private const val TAG = "ConfirmationActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirmation)

        Log.d(TAG, "ConfirmationActivity started")

        // Check permissions immediately
        if (!checkRequiredPermissions()) {
            Toast.makeText(
                this,
                "SMS and Location permissions are required. Please grant them in Dashboard.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        // Initialize managers
        dbHelper = DatabaseHelper(this)
        sessionManager = SessionManager(this)
        smsHandler = SMSHandler(this)
        locationHandler = LocationHandler(this)

        // Initialize views
        tvCountdown = findViewById(R.id.tvCountdown)
        tvEmergencyType = findViewById(R.id.tvEmergencyType)
        btnCancel = findViewById(R.id.btnCancel)

        emergencyType = intent.getStringExtra("EMERGENCY_TYPE") ?: "Unknown"
        tvEmergencyType.text = emergencyType

        Log.d(TAG, "Emergency Type: $emergencyType")

        startCountdown()

        btnCancel.setOnClickListener {
            Log.d(TAG, "Alert cancelled by user")
            countDownTimer?.cancel()
            Toast.makeText(this, "Alert cancelled", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun checkRequiredPermissions(): Boolean {
        val hasSMS = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val hasLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "Permission check - SMS: $hasSMS, Location: $hasLocation")

        return hasSMS && hasLocation
    }

    private fun startCountdown() {
        Log.d(TAG, "Starting 5 second countdown...")

        countDownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000).toInt()
                tvCountdown.text = secondsLeft.toString()
                Log.d(TAG, "Countdown: $secondsLeft seconds")
            }

            override fun onFinish() {
                Log.d(TAG, "Countdown finished - sending emergency alert")
                tvCountdown.text = "0"
                sendEmergencyAlert()
            }
        }.start()
    }

    private fun sendEmergencyAlert() {
        val userId = sessionManager.getUserId()
        val userName = sessionManager.getUserName() ?: "Unknown User"

        Log.d(TAG, "Sending emergency alert for user: $userName (ID: $userId)")

        // Show loading message
        Toast.makeText(this, "Getting your location...", Toast.LENGTH_SHORT).show()

        // Get current location
        locationHandler.getCurrentLocation(
            onSuccess = { latitude, longitude ->
                Log.d(TAG, "Location obtained: $latitude, $longitude")

                // Get current timestamp
                val currentTime = SimpleDateFormat("MMM dd, yyyy hh:mm:ss a", Locale.getDefault()).format(Date())

                // Save to database
                val reportId = dbHelper.saveEmergencyReport(
                    userId,
                    emergencyType,
                    locationHandler.formatLocation(latitude, longitude)
                )

                Log.d(TAG, "Emergency report saved to DB with ID: $reportId")

                Toast.makeText(this, "Sending alert to MDRRMO...", Toast.LENGTH_SHORT).show()

                // Send SMS
                smsHandler.sendEmergencySMS(
                    userName,
                    emergencyType,
                    latitude,
                    longitude,
                    onSuccess = {
                        Log.d(TAG, "SMS sent successfully!")
                        runOnUiThread {
                            // Navigate to success screen with real data
                            val intent = Intent(this, AlertSentActivity::class.java)
                            intent.putExtra("EMERGENCY_TYPE", emergencyType)
                            intent.putExtra("LATITUDE", latitude)
                            intent.putExtra("LONGITUDE", longitude)
                            intent.putExtra("TIME_SENT", currentTime)
                            intent.putExtra("USER_NAME", userName)
                            startActivity(intent)
                            finish()
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to send SMS: $error")
                        runOnUiThread {
                            Toast.makeText(this, "Failed to send SMS: $error", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    }
                )
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to get location: $error")
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Unable to get location: $error. Please ensure GPS is enabled.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        Log.d(TAG, "ConfirmationActivity destroyed")
    }
}