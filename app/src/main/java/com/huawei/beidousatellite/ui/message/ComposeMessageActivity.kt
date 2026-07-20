package com.huawei.beidousatellite.ui.message

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.huawei.beidousatellite.R
import com.huawei.beidousatellite.data.model.MessagePriority
import com.huawei.beidousatellite.data.model.MessageType
import com.huawei.beidousatellite.data.model.SmcMessage
import com.huawei.beidousatellite.data.repository.SatelliteRepository
import com.huawei.beidousatellite.ui.satellite.SatelliteSearchActivity
import com.huawei.beidousatellite.util.SatelliteLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class ComposeMessageActivity : AppCompatActivity() {

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

        recipientInput.setText("+989121234567")
        contentInput.setText("سلام via BeiDou! Test ${System.currentTimeMillis()} - این پیام از طریق اپ Beidou Satellite Messenger ارسال شده")

        val priorities = MessagePriority.values().map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, priorities)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        prioritySpinner.adapter = adapter
        prioritySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                selectedPriority = MessagePriority.values()[pos]
                logger.d("Compose", "Priority selected $selectedPriority")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

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
            logger.d("Compose", "Include location $isChecked")
            if (isChecked) getLocation() else locationText.text = "Location not included"
        }

        sendButton.setOnClickListener {
            logger.i("Compose", "Send button clicked - will go to satellite search flow")
            prepareAndGoToSatelliteSearch()
        }

        getLocation()
        statusText.text = "مرحله 1: شماره و پیام را وارد کن\nمرحله 2: دکمه ارسال را بزن تا به صفحه جستجوی ماهواره بروی\nمرحله 3: گوشی را به سمت ماهواره بگیر تا وقتی TRACKING شد پیام اتوماتیک ارسال شود\n\nTest Mode روشن باشد شبیه سازی میشود، خاموش باشد روی هواوی واقعی via satellite میرود"
    }

    private fun getLocation() {
        logger.d("Compose", "getLocation called")
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationText.text = "Location permission needed"
            logger.w("Compose", "Location permission not granted")
            return
        }
        val fused = LocationServices.getFusedLocationProviderClient(this)
        fused.lastLocation.addOnSuccessListener { loc ->
            currentLocation = loc
            if (loc != null) {
                locationText.text = "📍 Lat: ${loc.latitude}\n📍 Lon: ${loc.longitude}\n📈 Alt: ${loc.altitude}m Acc: ${loc.accuracy}m"
                logger.i("Compose", "Location obtained: ${loc.latitude},${loc.longitude}")
            } else {
                locationText.text = "Location unavailable - will send without location"
                logger.w("Compose", "Location unavailable")
            }
        }.addOnFailureListener {
            locationText.text = "Location failed: ${it.message}"
            logger.e("Compose", "Location failed", it)
        }
    }

    private fun prepareAndGoToSatelliteSearch() {
        val recipient = recipientInput.text.toString().trim()
        val content = contentInput.text.toString().trim()

        logger.i("Compose", "Preparing message to $recipient content=$content priority=$selectedPriority")

        if (recipient.isEmpty()) {
            Toast.makeText(this, "شماره گیرنده را وارد کن", Toast.LENGTH_SHORT).show()
            logger.w("Compose", "Recipient empty")
            return
        }
        if (content.isEmpty()) {
            Toast.makeText(this, "متن پیام خالی است", Toast.LENGTH_SHORT).show()
            logger.w("Compose", "Content empty")
            return
        }
        if (content.length > 140) {
            Toast.makeText(this, "پیام بیشتر از 140 کاراکتر است", Toast.LENGTH_LONG).show()
            logger.w("Compose", "Content too long ${content.length}")
            return
        }

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

        logger.i("Compose", "Saving message ${message.messageId} as PENDING and launching satellite search")
        lifecycleScope.launch {
            try {
                repository.saveMessage(message.copy(status = com.huawei.beidousatellite.data.model.MessageStatus.PENDING))
                logger.message("Saved PENDING message ${message.messageId} to $recipient: $content")

                // Go to satellite search with messageId - new flow as user requested
                val intent = Intent(this@ComposeMessageActivity, SatelliteSearchActivity::class.java)
                intent.putExtra("messageId", message.messageId)
                intent.putExtra("recipient", recipient)
                intent.putExtra("content", content)
                intent.putExtra("priority", selectedPriority.name)
                intent.putExtra("latitude", message.latitude ?: 0.0)
                intent.putExtra("longitude", message.longitude ?: 0.0)
                startActivity(intent)
                logger.i("Compose", "Launched SatelliteSearchActivity with messageId ${message.messageId}")
            } catch (e: Throwable) {
                logger.e("Compose", "Failed to save and launch search", e)
                Toast.makeText(this@ComposeMessageActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
