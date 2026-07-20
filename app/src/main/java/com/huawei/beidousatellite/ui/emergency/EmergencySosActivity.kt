package com.huawei.beidousatellite.ui.emergency

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.huawei.beidousatellite.R
import com.huawei.beidousatellite.data.hms.HmsSmcManager
import com.huawei.beidousatellite.data.model.MessagePriority
import com.huawei.beidousatellite.data.model.MessageType
import com.huawei.beidousatellite.data.model.SmcMessage
import com.huawei.beidousatellite.util.SatelliteLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class EmergencySosActivity : AppCompatActivity() {

    @Inject lateinit var hmsManager: HmsSmcManager
    @Inject lateinit var logger: SatelliteLogger

    private lateinit var countdownText: TextView
    private lateinit var locationText: TextView
    private lateinit var statusText: TextView
    private lateinit var cancelButton: Button
    private lateinit var sendNowButton: Button
    private var countDownTimer: CountDownTimer? = null
    private var currentLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_sos)

        countdownText = findViewById(R.id.countdownText)
        locationText = findViewById(R.id.locationText)
        statusText = findViewById(R.id.statusText)
        cancelButton = findViewById(R.id.cancelButton)
        sendNowButton = findViewById(R.id.sendNowButton)

        cancelButton.setOnClickListener {
            countDownTimer?.cancel()
            finish()
        }
        sendNowButton.setOnClickListener {
            countDownTimer?.cancel()
            sendSos()
        }

        getLocation()
        startCountdown()
    }

    private fun startCountdown() {
        countDownTimer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = millisUntilFinished / 1000
                countdownText.text = "Sending SOS in $sec seconds...\nTap Cancel to abort"
            }
            override fun onFinish() {
                sendSos()
            }
        }.start()
    }

    private fun getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationText.text = "Location permission not granted"
            return
        }
        val fused = LocationServices.getFusedLocationProviderClient(this)
        fused.lastLocation.addOnSuccessListener { loc ->
            currentLocation = loc
            if (loc != null) {
                locationText.text = "Lat: ${loc.latitude}\nLon: ${loc.longitude}\nAcc: ${loc.accuracy}m\nAlt: ${loc.altitude}"
            } else {
                locationText.text = "Location unavailable"
            }
        }.addOnFailureListener {
            locationText.text = "Failed to get location: ${it.message}"
        }
    }

    private fun sendSos() {
        val content = hmsManager.buildEmergencyContent(currentLocation)
        val message = SmcMessage(
            senderNumber = "self",
            recipientNumber = "110",
            content = content,
            priority = MessagePriority.EMERGENCY,
            messageType = MessageType.EMERGENCY_SOS,
            latitude = currentLocation?.latitude,
            longitude = currentLocation?.longitude,
            altitude = currentLocation?.altitude
        )
        statusText.text = "Sending SOS via BeiDou...\n$content"
        logger.i("EmergencySOS", "Sending $content")

        hmsManager.sendMessage(message) { success, finalMsg ->
            runOnUiThread {
                if (success) {
                    statusText.text = "SOS SENT via satellite!\n$content\nStatus: ${finalMsg.status}"
                    Toast.makeText(this, "SOS Sent ${finalMsg.status}", Toast.LENGTH_LONG).show()
                } else {
                    statusText.text = "Failed to send SOS\n$content"
                    Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
