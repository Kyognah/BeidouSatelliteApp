package com.huawei.beidousatellite.ui.message

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.huawei.beidousatellite.R
import com.huawei.beidousatellite.data.hms.HmsSmcManager
import com.huawei.beidousatellite.data.model.MessagePriority
import com.huawei.beidousatellite.data.model.MessageType
import com.huawei.beidousatellite.data.model.SmcMessage
import com.huawei.beidousatellite.data.repository.SatelliteRepository
import com.huawei.beidousatellite.util.SatelliteLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class ComposeMessageActivity : AppCompatActivity() {

    @Inject lateinit var hmsManager: HmsSmcManager
    @Inject lateinit var repository: SatelliteRepository
    @Inject lateinit var logger: SatelliteLogger

    private lateinit var recipientInput: EditText
    private lateinit var contentInput: EditText
    private lateinit var prioritySpinner: Spinner
    private lateinit var includeLocationCheck: CheckBox
    private lateinit var locationText: TextView
    private lateinit var sendButton: Button
    private lateinit var statusText: TextView
    private lateinit var charCountText: TextView

    private var currentLocation: Location? = null
    private var selectedPriority = MessagePriority.NORMAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compose_message)

        recipientInput = findViewById(R.id.recipientInput)
        contentInput = findViewById(R.id.contentInput)
        prioritySpinner = findViewById(R.id.prioritySpinner)
        includeLocationCheck = findViewById(R.id.includeLocationCheck)
        locationText = findViewById(R.id.locationText)
        sendButton = findViewById(R.id.sendButton)
        statusText = findViewById(R.id.statusText)
        charCountText = findViewById(R.id.charCountText)

        // Default recipient - user asked for specific number
        recipientInput.setText("+989121234567") // example IR number, user can change

        // Priority spinner
        val priorities = MessagePriority.values().map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, priorities)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        prioritySpinner.adapter = adapter
        prioritySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                selectedPriority = MessagePriority.values()[pos]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Char count - BDS-3 max 140 chars (Chinese) / 78 chars latin? For test use 140
        contentInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val len = s?.length ?: 0
                charCountText.text = "$len/140 (BeiDou limit)"
                charCountText.setTextColor(if (len > 140) 0xFFFF0000.toInt() else 0xFF666666.toInt())
            }
        })

        includeLocationCheck.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) getLocation() else locationText.text = "Location not included"
        }

        sendButton.setOnClickListener {
            sendMessage()
        }

        // Pre-fill content for test
        contentInput.setText("سلام via BeiDou! Test ${System.currentTimeMillis()}")

        getLocation()
        observeHms()

        statusText.text = "Ready. Test Mode: ${hmsManager.connectionState.value} Capability searchMode=${hmsManager.capability.value.searchMode}"
    }

    private fun getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationText.text = "Location permission needed"
            return
        }
        val fused = LocationServices.getFusedLocationProviderClient(this)
        fused.lastLocation.addOnSuccessListener { loc ->
            currentLocation = loc
            if (loc != null) {
                locationText.text = "📍 Lat: ${loc.latitude}\n📍 Lon: ${loc.longitude}\n📈 Alt: ${loc.altitude}m Acc: ${loc.accuracy}m"
            } else {
                locationText.text = "Location unavailable - will send without location"
            }
        }.addOnFailureListener {
            locationText.text = "Location failed: ${it.message}"
        }
    }

    private fun observeHms() {
        lifecycleScope.launch {
            hmsManager.searchStatus.collect { status ->
                statusText.text = "Search: $status | Conn: ${hmsManager.connectionState.value} | Mode: ${hmsManager.capability.value.searchMode}"
            }
        }
    }

    private fun sendMessage() {
        val recipient = recipientInput.text.toString().trim()
        val content = contentInput.text.toString().trim()

        if (recipient.isEmpty()) {
            Toast.makeText(this, "شماره گیرنده را وارد کن", Toast.LENGTH_SHORT).show()
            return
        }
        if (content.isEmpty()) {
            Toast.makeText(this, "متن پیام خالی است", Toast.LENGTH_SHORT).show()
            return
        }
        if (content.length > 140) {
            Toast.makeText(this, "پیام بیشتر از 140 کاراکتر است - BeiDou limit", Toast.LENGTH_LONG).show()
            return
        }

        // Validate phone? Basic check
        // Allow +xxx numbers

        val message = SmcMessage(
            senderNumber = "self",
            recipientNumber = recipient,
            content = content,
            priority = selectedPriority,
            messageType = MessageType.TEXT,
            latitude = if (includeLocationCheck.isChecked) currentLocation?.latitude else null,
            longitude = if (includeLocationCheck.isChecked) currentLocation?.longitude else null,
            altitude = if (includeLocationCheck.isChecked) currentLocation?.altitude else null,
            utcTime = Instant.now()
        )

        sendButton.isEnabled = false
        statusText.text = "⏳ Sending to $recipient...\nStatus: QUEUED"

        lifecycleScope.launch {
            repository.saveMessage(message.copy(status = com.huawei.beidousatellite.data.model.MessageStatus.QUEUED))
        }

        logger.message("User sending to $recipient: $content")

        hmsManager.sendMessage(message) { success ->
            runOnUiThread {
                sendButton.isEnabled = true
                if (success) {
                    statusText.text = "✅ SENT via BeiDou (simulated in test mode)!\nTo: $recipient\nContent: $content\nPriority: $selectedPriority\n\nNote: In TEST MODE, message is simulated locally and saved to DB. On real Huawei hardware with bypass, it would go via satellite."
                    lifecycleScope.launch {
                        repository.saveMessage(message.copy(status = com.huawei.beidousatellite.data.model.MessageStatus.SENT, sendTime = Instant.now()))
                    }
                    Toast.makeText(this, "پیام ارسال شد (شبیه سازی)", Toast.LENGTH_LONG).show()
                } else {
                    statusText.text = "❌ Failed to send to $recipient"
                    Toast.makeText(this, "ارسال ناموفق", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
