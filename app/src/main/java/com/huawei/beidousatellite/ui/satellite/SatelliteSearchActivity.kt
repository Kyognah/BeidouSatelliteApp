package com.huawei.beidousatellite.ui.satellite

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.huawei.beidousatellite.R
import com.huawei.beidousatellite.data.model.MessagePriority
import com.huawei.beidousatellite.data.model.MessageType
import com.huawei.beidousatellite.data.model.SatelliteSearchStatus
import com.huawei.beidousatellite.data.model.SmcMessage
import com.huawei.beidousatellite.data.repository.SatelliteRepository
import com.huawei.beidousatellite.util.SatelliteLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class SatelliteSearchActivity : AppCompatActivity() {

    private val viewModel: SatelliteSearchViewModel by viewModels()
    private lateinit var compassView: CompassView
    private lateinit var statusText: TextView
    private lateinit var calibrationText: TextView
    private lateinit var signalText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var sendButton: Button
    private lateinit var messageInfoText: TextView

    @Inject lateinit var repository: SatelliteRepository
    @Inject lateinit var logger: SatelliteLogger

    private var pendingMessage: SmcMessage? = null
    private var messageId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_satellite_search)

        compassView = findViewById(R.id.compassView)
        statusText = findViewById(R.id.statusText)
        calibrationText = findViewById(R.id.calibrationText)
        signalText = findViewById(R.id.signalText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        sendButton = findViewById(R.id.sendButton)
        messageInfoText = findViewById(R.id.messageInfoText)

        logger.i("SatSearchActivity", "onCreate: intent extras=${intent.extras}")

        // Check if we have a pending message from Compose flow
        messageId = intent.getStringExtra("messageId")
        if (messageId != null) {
            val recipient = intent.getStringExtra("recipient") ?: "unknown"
            val content = intent.getStringExtra("content") ?: ""
            val priorityName = intent.getStringExtra("priority") ?: "NORMAL"
            val lat = intent.getDoubleExtra("latitude", 0.0).takeIf { it != 0.0 }
            val lon = intent.getDoubleExtra("longitude", 0.0).takeIf { it != 0.0 }

            pendingMessage = SmcMessage(
                messageId = messageId!!,
                senderNumber = "self",
                recipientNumber = recipient,
                content = content,
                priority = try { MessagePriority.valueOf(priorityName) } catch (_: Exception) { MessagePriority.NORMAL },
                messageType = MessageType.TEXT,
                latitude = lat,
                longitude = lon,
                utcTime = Instant.now()
            )
            messageInfoText.text = "📨 Pending message:\nTo: $recipient\nContent: $content\nPriority: $priorityName\n\nPoint phone to satellite (hold to open sky) to send.\nWhen TRACKING, Send button enables."
            messageInfoText.visibility = android.view.View.VISIBLE
            sendButton.text = "🚀 Send to $recipient"
            sendButton.isEnabled = false
            logger.i("SatSearch", "Pending message loaded: to $recipient content=$content id=$messageId")
        } else {
            messageInfoText.text = "No pending message - searching satellite for testing. Enable Test Mode for simulation."
            messageInfoText.visibility = android.view.View.VISIBLE
            sendButton.text = "No message to send"
            sendButton.isEnabled = false
            logger.i("SatSearch", "No pending message, just searching")
        }

        startButton.setOnClickListener { 
            logger.i("SatSearch", "Start button clicked")
            viewModel.start() 
        }
        stopButton.setOnClickListener { 
            logger.i("SatSearch", "Stop button clicked")
            viewModel.stop() 
        }
        sendButton.setOnClickListener {
            pendingMessage?.let { msg ->
                logger.i("SatSearch", "Send button clicked for message ${msg.messageId}")
                sendPendingMessage(msg)
            }
        }

        lifecycleScope.launch {
            viewModel.deviceAzimuth.collect { azimuth ->
                statusText.text = "Device Azimuth: %.1f°\nSearch: ${viewModel.searchStatus.value}\nConn: ${viewModel.hmsManager.connectionState.value} Cap: ${viewModel.hmsManager.capability.value.searchMode}".format(azimuth)
            }
        }
        lifecycleScope.launch {
            viewModel.calibrationStatus.collect { cal ->
                calibrationText.text = "Calibration: $cal\nDo figure-8 motion to calibrate compass. Test mode simulates movement even without moving phone."
                logger.d("SatSearch", "Calibration: $cal")
            }
        }
        lifecycleScope.launch {
            viewModel.signalInfo.collect { info ->
                info?.let {
                    signalText.text = "PRN ${it.satelliteId}\nSNR %.1f dB\nEl %.1f° Az %.1f°\nQuality: %s\nUsable: %s\n\nPoint phone so satellite dot is at center (top)".format(it.snrDb, it.elevationDeg, it.azimuthDeg, it.signalQuality, it.isUsable)
                    compassView.updateCompass(
                        viewModel.deviceAzimuth.value,
                        it.azimuthDeg.toFloat(),
                        it.elevationDeg.toFloat(),
                        it.snrDb.toFloat(),
                        it.satelliteId,
                        viewModel.hmsManager.capability.value.searchMode
                    )
                }
            }
        }
        lifecycleScope.launch {
            viewModel.searchStatus.collect { status ->
                logger.i("SatSearch", "Search status changed to $status")
                statusText.text = "Device Azimuth: %.1f°\nSearch: $status\nConn: ${viewModel.hmsManager.connectionState.value}".format(viewModel.deviceAzimuth.value)
                // Enable send button when tracking and we have pending message
                if (status == SatelliteSearchStatus.TRACKING && pendingMessage != null) {
                    sendButton.isEnabled = true
                    sendButton.text = "✅ Satellite Found! Send to ${pendingMessage?.recipientNumber}"
                    logger.i("SatSearch", "TRACKING - enabling send button")
                } else if (status == SatelliteSearchStatus.TRACKING) {
                    sendButton.text = "Tracking - no pending message"
                }
            }
        }

        viewModel.start()
        logger.i("SatSearch", "Activity created and start() called")
    }

    private fun sendPendingMessage(msg: SmcMessage) {
        sendButton.isEnabled = false
        sendButton.text = "Sending..."
        messageInfoText.text = "Sending to ${msg.recipientNumber}...\n${msg.content}"
        logger.i("SatSearch", "Sending pending message ${msg.messageId} to ${msg.recipientNumber}")

        lifecycleScope.launch {
            try {
                repository.saveMessage(msg.copy(status = com.huawei.beidousatellite.data.model.MessageStatus.SENDING))
            } catch (e: Throwable) {
                logger.e("SatSearch", "Failed to save SENDING", e)
            }
        }

        viewModel.hmsManager.sendMessage(msg) { success, finalMsg ->
            runOnUiThread {
                if (success) {
                    messageInfoText.text = "✅ SENT!\nTo: ${finalMsg.recipientNumber}\nContent: ${finalMsg.content}\nStatus: ${finalMsg.status}\n\nIn Test Mode, this is simulated and saved to DB. On real Huawei hardware, it goes via BeiDou satellite."
                    sendButton.text = "✅ Sent! Check History"
                    sendButton.isEnabled = false
                    Toast.makeText(this, "پیام ارسال شد ${finalMsg.status}", Toast.LENGTH_LONG).show()
                    logger.message("Message sent success ${finalMsg.messageId} status=${finalMsg.status}")

                    lifecycleScope.launch {
                        try {
                            repository.saveMessage(finalMsg)
                            logger.message("Saved final message ${finalMsg.messageId} status=${finalMsg.status}")
                        } catch (e: Throwable) {
                            logger.e("SatSearch", "Failed to save final", e)
                        }
                    }
                } else {
                    messageInfoText.text = "❌ Failed to send to ${msg.recipientNumber}\nTry again with Test Mode enabled."
                    sendButton.isEnabled = true
                    sendButton.text = "Retry Send"
                    Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
                    logger.w("SatSearch", "Send failed for ${msg.messageId}")
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        logger.i("SatSearch", "onPause - stopping search but keeping simulation if test mode")
        viewModel.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        logger.i("SatSearch", "onDestroy")
    }
}
