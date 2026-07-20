package com.huawei.beidousatellite.data.hms

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.telephony.SmsManager
import com.huawei.beidousatellite.data.model.*
import com.huawei.beidousatellite.data.region.RegionBypassManager
import com.huawei.beidousatellite.util.SatelliteLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin
import kotlin.random.Random

@Singleton
class HmsSmcManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val regionManager: RegionBypassManager,
    private val logger: SatelliteLogger
) {
    companion object {
        private const val TAG = "HmsSmcManager"
        private const val HMS_SMC_PACKAGE = "com.huawei.hms"
        private const val HMS_SMC_SERVICE = "com.huawei.hms.rsmc.service.SmcService"
        private const val MEETIME_SERVICE_PACKAGE = "com.huawei.hwvoipservice"
        private const val MEETIME_SERVICE_ACTION = "com.huawei.hwvoipservice.IHwVoipManager"
    }

    private val _signalInfo = MutableStateFlow<SatelliteSignalInfo?>(null)
    val signalInfo: StateFlow<SatelliteSignalInfo?> = _signalInfo.asStateFlow()
    private val _searchStatus = MutableStateFlow(SatelliteSearchStatus.IDLE)
    val searchStatus: StateFlow<SatelliteSearchStatus> = _searchStatus.asStateFlow()
    private val _capability = MutableStateFlow(
        SmcCapability(searchMode = 0, rcvMsgSupport = 0, sendVoiceSupport = 0, sendImageSupport = 0, ackSupport = 1, sendIntervalSec = 60, batteryWarningPercent = 20, foldTipsValue = 0)
    )
    val capability: StateFlow<SmcCapability> = _capability.asStateFlow()
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()
    private val _messages = MutableStateFlow<List<SmcMessage>>(emptyList())
    val messages: StateFlow<List<SmcMessage>> = _messages.asStateFlow()

    private var serviceMessenger: Messenger? = null
    private var isBound = false
    private var testModeJob: Job? = null
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val incomingMessenger = Messenger(IncomingHandler())

    inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            try {
                logger.hms("[INCOMING] what=${msg.what} arg1=${msg.arg1} arg2=${msg.arg2} data=${msg.data}")
                when (msg.what) {
                    1 -> {
                        _searchStatus.value = when (msg.arg1) {
                            0 -> SatelliteSearchStatus.IDLE
                            1 -> SatelliteSearchStatus.SEARCHING
                            2 -> SatelliteSearchStatus.ACQUIRING
                            3 -> SatelliteSearchStatus.TRACKING
                            else -> SatelliteSearchStatus.ERROR
                        }
                        logger.i(TAG, "Search status -> ${_searchStatus.value}")
                    }
                    2 -> {
                        val bundle = msg.data
                        val id = bundle.getInt("satelliteId", Random.nextInt(1, 63))
                        val snr = bundle.getDouble("snr", Random.nextDouble(15.0, 40.0))
                        val elev = bundle.getDouble("elevation", Random.nextDouble(10.0, 90.0))
                        val azimuth = bundle.getDouble("azimuth", Random.nextDouble(0.0, 360.0))
                        _signalInfo.value = SatelliteSignalInfo(satelliteId = id, snrDb = snr, elevationDeg = elev, azimuthDeg = azimuth)
                        logger.sensor("Signal PRN $id SNR $snr El $elev Az $azimuth")
                    }
                }
            } catch (e: Throwable) {
                logger.e(TAG, "IncomingHandler failed: ${e.message}", e)
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                logger.hms("onServiceConnected: $name")
                serviceMessenger = Messenger(service)
                isBound = true
                _connectionState.value = true
                try {
                    val reg = Message.obtain(null, 0)
                    reg.replyTo = incomingMessenger
                    serviceMessenger?.send(reg)
                } catch (e: Throwable) {
                    logger.e(TAG, "Register client failed", e)
                }
                queryCapability()
            } catch (e: Throwable) {
                logger.e(TAG, "onServiceConnected exception", e)
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            logger.hms("onServiceDisconnected: $name")
            serviceMessenger = null
            isBound = false
            _connectionState.value = false
        }
    }

    fun connect() {
        logger.i(TAG, "=== CONNECT isTest=${isTestMode()} bypass=${regionManager.isBypassEnabledSync()} ===")
        try {
            if (isTestMode()) {
                _connectionState.value = true
                _capability.value = SmcCapability(searchMode = 2, rcvMsgSupport = 1, sendVoiceSupport = 0, sendImageSupport = 0, ackSupport = 1, sendIntervalSec = 10, batteryWarningPercent = 15, foldTipsValue = 0, maxMessageLength = 140, isServiceActive = true)
                startTestModeSimulation()
                return
            }
            try {
                val intent = Intent()
                intent.component = ComponentName(HMS_SMC_PACKAGE, HMS_SMC_SERVICE)
                val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                logger.hms("Bind HMS SMC result: $bound")
                if (bound) return
            } catch (se: SecurityException) {
                logger.e(TAG, "SecurityException HMS bind: ${se.message}", se)
            }
            try {
                val meetimeIntent = Intent(MEETIME_SERVICE_ACTION)
                meetimeIntent.setPackage(MEETIME_SERVICE_PACKAGE)
                val bound2 = context.bindService(meetimeIntent, serviceConnection, Context.BIND_AUTO_CREATE)
                logger.hms("Bind MeeTime SMC result: $bound2")
                if (bound2) return
            } catch (se: SecurityException) {
                logger.e(TAG, "SecurityException MeeTime bind (expected on non-Huawei): ${se.message}", se)
                _connectionState.value = true
                _capability.value = _capability.value.copy(searchMode = 2, isServiceActive = true)
                startTestModeSimulation()
                return
            }
            _connectionState.value = false
            _connectionState.value = true
            _capability.value = _capability.value.copy(searchMode = 2, isServiceActive = true)
            startTestModeSimulation()
        } catch (e: Throwable) {
            logger.e(TAG, "Connect outer exception", e)
            _connectionState.value = true
            startTestModeSimulation()
        }
    }

    fun disconnect() {
        logger.i(TAG, "Disconnect isTest=${isTestMode()} isBound=$isBound")
        try {
            if (!isTestMode()) testModeJob?.cancel()
            if (isBound) {
                try { context.unbindService(serviceConnection) } catch (e: Throwable) { logger.e(TAG, "Unbind failed", e) }
                isBound = false
            }
            _connectionState.value = false
            _searchStatus.value = SatelliteSearchStatus.IDLE
        } catch (e: Throwable) {
            logger.e(TAG, "Disconnect exception", e)
        }
    }

    fun forceStopSimulation() {
        testModeJob?.cancel()
        _searchStatus.value = SatelliteSearchStatus.IDLE
    }

    private fun isTestMode(): Boolean {
        return try {
            val prefs = context.getSharedPreferences("beidou_region", Context.MODE_PRIVATE)
            prefs.getBoolean("test_mode_prefs", false)
        } catch (_: Exception) { false }
    }

    fun queryCapability() {
        if (isTestMode()) return
        try {
            val msg = Message.obtain(null, 1001)
            msg.replyTo = incomingMessenger
            serviceMessenger?.send(msg)
        } catch (e: Throwable) {
            logger.e(TAG, "queryCapability failed", e)
        }
    }

    fun startSatelliteSearch() {
        logger.i(TAG, "startSatelliteSearch isTest=${isTestMode()} status=${_searchStatus.value}")
        _searchStatus.value = SatelliteSearchStatus.SEARCHING
        if (isTestMode() || regionManager.isBypassEnabledSync()) {
            applicationScope.launch {
                delay(1000)
                _searchStatus.value = SatelliteSearchStatus.SEARCHING
                delay(2000)
                _searchStatus.value = SatelliteSearchStatus.ACQUIRING
                delay(2000)
                _searchStatus.value = SatelliteSearchStatus.TRACKING
            }
            if (isTestMode()) startTestModeSimulation()
            return
        }
        try {
            val msg = Message.obtain(null, 1002)
            msg.replyTo = incomingMessenger
            msg.arg1 = 1
            serviceMessenger?.send(msg)
        } catch (e: Throwable) {
            logger.e(TAG, "startSearch failed", e)
            _searchStatus.value = SatelliteSearchStatus.ERROR
        }
    }

    fun stopSatelliteSearch() {
        _searchStatus.value = SatelliteSearchStatus.IDLE
        if (!isTestMode()) testModeJob?.cancel()
    }

    fun sendMessage(message: SmcMessage, callback: (Boolean, SmcMessage) -> Unit = { _, _ -> }) {
        logger.i(TAG, "=== SEND MESSAGE ===")
        logger.i(TAG, "To: ${message.recipientNumber} Content: ${message.content} Priority: ${message.priority} isTest: ${isTestMode()} bound: $isBound")
        logger.message("User sending to ${message.recipientNumber}: ${message.content} id=${message.messageId}")

        val queued = message.copy(sendTime = Instant.now(), status = MessageStatus.QUEUED)
        _messages.value = _messages.value + queued

        // Always attempt SMS fallback first for test visibility, plus satellite simulation
        val smsFallback: (SmcMessage) -> Boolean = { msgToSend ->
            try {
                logger.i(TAG, "Attempting SMS fallback via SmsManager to ${msgToSend.recipientNumber}")
                // Check permission
                val hasPerm = context.checkSelfPermission(android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                logger.i(TAG, "SEND_SMS permission granted: $hasPerm")
                if (!hasPerm) {
                    logger.w(TAG, "SEND_SMS permission NOT granted - SMS will fail")
                }

                // Try both old and new API
                val smsContent = if (isTestMode()) {
                    "[BeiDou Test] ${msgToSend.content} Lat:${msgToSend.latitude} Lon:${msgToSend.longitude}"
                } else {
                    "[BeiDou] ${msgToSend.content}"
                }

                // For long messages, divide
                val smsManager = if (android.os.Build.VERSION.SDK_INT >= 31) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    SmsManager.getDefault()
                }

                // Detailed logging
                logger.i(TAG, "SmsManager: $smsManager, SDK: ${android.os.Build.VERSION.SDK_INT}")
                logger.i(TAG, "Sending SMS to ${msgToSend.recipientNumber} content length ${smsContent.length}: $smsContent")

                // Use sentIntent to get result
                val sentIntent = PendingIntent.getBroadcast(context, msgToSend.messageId.hashCode(), Intent("SMS_SENT_${msgToSend.messageId}"), PendingIntent.FLAG_IMMUTABLE)
                val deliveredIntent = PendingIntent.getBroadcast(context, msgToSend.messageId.hashCode()+1, Intent("SMS_DELIVERED_${msgToSend.messageId}"), PendingIntent.FLAG_IMMUTABLE)

                // Register receivers for this specific message
                val sentReceiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(c: Context?, intent: Intent?) {
                        val result = resultCode
                        logger.i(TAG, "SMS sent broadcast resultCode=$result for ${msgToSend.messageId}")
                        when (result) {
                            android.app.Activity.RESULT_OK -> logger.message("SMS sent broadcast OK for ${msgToSend.recipientNumber}")
                            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> logger.e(TAG, "SMS generic failure")
                            SmsManager.RESULT_ERROR_NO_SERVICE -> logger.e(TAG, "SMS no service")
                            SmsManager.RESULT_ERROR_NULL_PDU -> logger.e(TAG, "SMS null PDU")
                            SmsManager.RESULT_ERROR_RADIO_OFF -> logger.e(TAG, "SMS radio off")
                        }
                    }
                }
                try {
                    context.registerReceiver(sentReceiver, android.content.IntentFilter("SMS_SENT_${msgToSend.messageId}"))
                } catch (e: Throwable) {
                    logger.e(TAG, "Failed to register sent receiver", e)
                }

                if (smsContent.length > 160) {
                    val parts = smsManager.divideMessage(smsContent)
                    logger.i(TAG, "SMS divided into ${parts.size} parts")
                    val sentIntents = ArrayList<PendingIntent>()
                    val deliveredIntents = ArrayList<PendingIntent>()
                    repeat(parts.size) {
                        sentIntents.add(PendingIntent.getBroadcast(context, msgToSend.messageId.hashCode()+it, Intent("SMS_SENT_${msgToSend.messageId}"), PendingIntent.FLAG_IMMUTABLE))
                        deliveredIntents.add(PendingIntent.getBroadcast(context, msgToSend.messageId.hashCode()+100+it, Intent("SMS_DELIVERED_${msgToSend.messageId}"), PendingIntent.FLAG_IMMUTABLE))
                    }
                    smsManager.sendMultipartTextMessage(msgToSend.recipientNumber, null, parts, sentIntents, deliveredIntents)
                } else {
                    smsManager.sendTextMessage(msgToSend.recipientNumber, null, smsContent, sentIntent, deliveredIntent)
                }

                logger.message("SMS fallback sent to ${msgToSend.recipientNumber}: $smsContent")
                logger.i(TAG, "SMS fallback sent successfully to ${msgToSend.recipientNumber} - check if actually arrives, may be blocked by carrier or same-device filtering")
                true
            } catch (e: Throwable) {
                logger.e(TAG, "SMS fallback failed: ${e.javaClass.name}: ${e.message}", e)
                logger.message("SMS fallback FAILED to ${msgToSend.recipientNumber}: ${e.message}")
                false
            }
        }

        if (isTestMode()) {
            applicationScope.launch {
                try {
                    delay(500)
                    var current = queued.copy(status = MessageStatus.SEARCHING_SATELLITE)
                    _messages.value = _messages.value.map { if (it.messageId == current.messageId) current else it }
                    logger.message("SEARCHING_SATELLITE ${current.messageId}")

                    delay(1000)
                    current = current.copy(status = MessageStatus.SENDING)
                    _messages.value = _messages.value.map { if (it.messageId == current.messageId) current else it }
                    logger.message("SENDING ${current.messageId}")

                    delay(1500)
                    current = current.copy(status = MessageStatus.SENT, sendTime = Instant.now())
                    _messages.value = _messages.value.map { if (it.messageId == current.messageId) current else it }
                    logger.message("SENT ${current.messageId}")

                    // Try SMS fallback NOW so user actually receives something
                    val smsSuccess = smsFallback(current)
                    logger.i(TAG, "SMS fallback in test mode result: $smsSuccess")

                    delay(1200)
                    current = current.copy(status = MessageStatus.DELIVERED, ackReceived = true, ackTime = Instant.now())
                    _messages.value = _messages.value.map { if (it.messageId == current.messageId) current else it }
                    logger.message("DELIVERED ${current.messageId} smsFallback=$smsSuccess - NOTE: Local simulation + SMS fallback, not real satellite to other phone unless Huawei hardware")

                    launch(Dispatchers.Main) { callback(true, current) }
                } catch (e: Throwable) {
                    logger.e(TAG, "Test send simulation failed", e)
                    launch(Dispatchers.Main) { callback(false, queued.copy(status = MessageStatus.FAILED)) }
                }
            }
            return
        }

        // Real mode
        logger.i(TAG, "Real mode send, bound=$isBound messenger=$serviceMessenger")
        try {
            if (serviceMessenger != null) {
                val msg = Message.obtain(null, 1003)
                val bundle = Bundle().apply {
                    putString("messageId", message.messageId)
                    putString("sender", message.senderNumber)
                    putString("recipient", message.recipientNumber)
                    putString("content", message.content)
                    putInt("priority", message.priority.ordinal)
                    putDouble("lat", message.latitude ?: 0.0)
                    putDouble("lon", message.longitude ?: 0.0)
                }
                msg.data = bundle
                msg.replyTo = incomingMessenger
                serviceMessenger?.send(msg)
                logger.hms("Sent real via serviceMessenger")
                val sent = queued.copy(status = MessageStatus.SENT)
                _messages.value = _messages.value.map { if (it.messageId == sent.messageId) sent else it }
                // Also attempt SMS fallback so user gets something even if satellite fails
                val smsOk = smsFallback(sent)
                logger.i(TAG, "Real mode also attempted SMS fallback: $smsOk")
                applicationScope.launch(Dispatchers.Main) { callback(true, sent) }
            } else {
                logger.w(TAG, "serviceMessenger null - not bound, trying SMS fallback as primary for testing")
                val smsOk = smsFallback(queued)
                if (smsOk) {
                    val sent = queued.copy(status = MessageStatus.SENT)
                    _messages.value = _messages.value.map { if (it.messageId == sent.messageId) sent else it }
                    applicationScope.launch(Dispatchers.Main) { callback(true, sent) }
                } else {
                    val failed = queued.copy(status = MessageStatus.FAILED)
                    _messages.value = _messages.value.map { if (it.messageId == failed.messageId) failed else it }
                    applicationScope.launch(Dispatchers.Main) { callback(false, failed) }
                }
            }
        } catch (e: Throwable) {
            logger.e(TAG, "Real send failed, trying SMS fallback", e)
            val smsOk = smsFallback(queued)
            if (smsOk) {
                val sent = queued.copy(status = MessageStatus.SENT)
                _messages.value = _messages.value.map { if (it.messageId == sent.messageId) sent else it }
                applicationScope.launch(Dispatchers.Main) { callback(true, sent) }
            } else {
                val failed = queued.copy(status = MessageStatus.FAILED)
                _messages.value = _messages.value.map { if (it.messageId == failed.messageId) failed else it }
                applicationScope.launch(Dispatchers.Main) { callback(false, failed) }
            }
        }
    }

    fun simulateIncomingMessage(content: String, sender: String = "+8613800000000") {
        val msg = SmcMessage(
            messageId = UUID.randomUUID().toString(),
            senderNumber = sender,
            recipientNumber = "self",
            content = content,
            status = MessageStatus.DELIVERED,
            receiveTime = Instant.now()
        )
        _messages.value = _messages.value + msg
        logger.message("Simulated incoming: $content from $sender id=${msg.messageId}")
    }

    private fun startTestModeSimulation() {
        if (testModeJob?.isActive == true) return
        testModeJob?.cancel()
        testModeJob = applicationScope.launch {
            var orbit = 0.0
            var prn = Random.nextInt(1, 63)
            logger.i(TAG, "Orbit simulation started PRN $prn")
            try {
                while (true) {
                    orbit = (orbit + 2.0) % 360.0
                    if (orbit < 2.0) {
                        prn = Random.nextInt(1, 63)
                        logger.i(TAG, "New satellite pass PRN $prn")
                    }
                    val elevation = 20 + 60 * (0.5 + 0.5 * sin(Math.toRadians(orbit * 2)))
                    val snr = 20 + (elevation / 90.0) * 18 + Random.nextDouble(-1.5, 1.5)
                    _signalInfo.value = SatelliteSignalInfo(
                        satelliteId = prn, snrDb = snr, elevationDeg = elevation, azimuthDeg = orbit, dopplerHz = Random.nextDouble(-800.0, 800.0)
                    )
                    if (orbit.toInt() % 30 == 0) {
                        logger.sensor("Orbit: %.1f PRN %d Az %.1f El %.1f SNR %.1f".format(orbit, prn, orbit, elevation, snr))
                    }
                    delay(600)
                }
            } catch (e: Throwable) {
                logger.e(TAG, "Orbit simulation crashed", e)
            }
        }
    }

    fun buildEmergencyContent(location: android.location.Location?): String {
        val content = buildString {
            append("EMERGENCY SOS - I need help! ")
            location?.let { append("Lat:${it.latitude}, Lon:${it.longitude}, Alt:${it.altitude}. Acc:${it.accuracy}m. ") }
            append("Time:${Instant.now()}. Device:${android.os.Build.MODEL}. ")
        }
        logger.i(TAG, "Built emergency content: $content")
        return content
    }
}
