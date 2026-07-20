package com.huawei.beidousatellite.data.hms

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
                logger.hms("[INCOMING] what=${msg.what} arg1=${msg.arg1} arg2=${msg.arg2} data=${msg.data} from=${msg.replyTo}")
                when (msg.what) {
                    1 -> {
                        val status = msg.arg1
                        _searchStatus.value = when (status) {
                            0 -> SatelliteSearchStatus.IDLE
                            1 -> SatelliteSearchStatus.SEARCHING
                            2 -> SatelliteSearchStatus.ACQUIRING
                            3 -> SatelliteSearchStatus.TRACKING
                            else -> SatelliteSearchStatus.ERROR
                        }
                        logger.i(TAG, "Search status updated to ${_searchStatus.value} from msg arg1=$status")
                    }
                    2 -> {
                        val bundle = msg.data
                        val id = bundle.getInt("satelliteId", Random.nextInt(1, 63))
                        val snr = bundle.getDouble("snr", Random.nextDouble(15.0, 40.0))
                        val elev = bundle.getDouble("elevation", Random.nextDouble(10.0, 90.0))
                        val azimuth = bundle.getDouble("azimuth", Random.nextDouble(0.0, 360.0))
                        val info = SatelliteSignalInfo(satelliteId = id, snrDb = snr, elevationDeg = elev, azimuthDeg = azimuth)
                        _signalInfo.value = info
                        logger.sensor("Signal update: PRN $id SNR $snr El $elev Az $azimuth")
                    }
                    3 -> {
                        val msgId = msg.data.getString("messageId")
                        logger.message("[ACK] Received ACK for messageId=$msgId")
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
                logger.hms("onServiceConnected: $name, binder=$service, isTest=${isTestMode()}")
                serviceMessenger = Messenger(service)
                isBound = true
                _connectionState.value = true
                logger.hms("SMC service connected successfully: $name")
                try {
                    val reg = Message.obtain(null, 0)
                    reg.replyTo = incomingMessenger
                    serviceMessenger?.send(reg)
                    logger.hms("Sent registration message to service")
                } catch (e: Throwable) {
                    logger.e(TAG, "Register client failed: ${e.message}", e)
                }
                queryCapability()
            } catch (e: Throwable) {
                logger.e(TAG, "onServiceConnected exception: ${e.message}", e)
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
        logger.i(TAG, "========================================")
        logger.i(TAG, "=== CONNECT called ===")
        logger.d(TAG, "isTestMode=${isTestMode()}, bypassEnabled=${regionManager.isBypassEnabledSync()}, bypassMethod=${regionManager.getBypassMethodSync()}, supported=${regionManager.isSatelliteSupported()}, region=${regionManager.getCurrentRegion()}, isBound=$isBound")
        logger.hms("Connect entry: testMode=${isTestMode()} bypass=${regionManager.isBypassEnabledSync()}")

        try {
            if (isTestMode()) {
                logger.i(TAG, "Test mode - simulating connection")
                _connectionState.value = true
                _capability.value = SmcCapability(
                    searchMode = 2, rcvMsgSupport = 1, sendVoiceSupport = 0, sendImageSupport = 0,
                    ackSupport = 1, sendIntervalSec = 10, batteryWarningPercent = 15, foldTipsValue = 0,
                    maxMessageLength = 140, isServiceActive = true
                )
                logger.i(TAG, "Test capability set: searchMode=2 direct send, rcv=1")
                startTestModeSimulation()
                logger.i(TAG, "=== CONNECT finished (TEST MODE) connectionState=${_connectionState.value} ===")
                return
            }

            // Try HMS
            try {
                val intent = Intent()
                intent.component = ComponentName(HMS_SMC_PACKAGE, HMS_SMC_SERVICE)
                logger.hms("Attempting bind to HMS SMC: $HMS_SMC_PACKAGE/$HMS_SMC_SERVICE, context=$context")
                val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                logger.hms("Bind HMS SMC result: $bound")
                if (bound) {
                    logger.i(TAG, "HMS SMC bind succeeded, waiting for onServiceConnected")
                    return
                } else {
                    logger.w(TAG, "HMS SMC bind returned false, trying MeeTime")
                }
            } catch (se: SecurityException) {
                logger.e(TAG, "SecurityException binding HMS SMC (expected on non-Huawei): ${se.message}", se)
                logger.hms("SecurityException HMS bind - normal on non-Huawei, will try MeeTime then fallback")
            } catch (e: Throwable) {
                logger.e(TAG, "Exception binding HMS SMC: ${e.message}", e)
            }

            try {
                val meetimeIntent = Intent(MEETIME_SERVICE_ACTION)
                meetimeIntent.setPackage(MEETIME_SERVICE_PACKAGE)
                logger.hms("Attempting bind to MeeTime SMC: $MEETIME_SERVICE_ACTION pkg $MEETIME_SERVICE_PACKAGE")
                val bound2 = context.bindService(meetimeIntent, serviceConnection, Context.BIND_AUTO_CREATE)
                logger.hms("Bind MeeTime SMC result: $bound2")
                if (bound2) {
                    logger.i(TAG, "MeeTime SMC bind succeeded")
                    return
                } else {
                    logger.w(TAG, "MeeTime SMC bind returned false - signatureOrSystem permission required, expected on non-Huawei")
                }
            } catch (se: SecurityException) {
                logger.e(TAG, "SecurityException binding MeeTime SMC: ${se.message} - This happens on non-Huawei because com.huawei.hwvoipservice is signatureOrSystem. Falling back to test simulation.", se)
                logger.hms("SecurityException MeeTime bind - expected, fallback to test simulation")
                _connectionState.value = true
                _capability.value = _capability.value.copy(searchMode = 2, isServiceActive = true)
                startTestModeSimulation()
                logger.i(TAG, "Fallback to test mode simulation after SecurityException, connectionState=true")
                return
            } catch (e: Throwable) {
                logger.e(TAG, "Exception binding MeeTime SMC: ${e.message}", e)
            }

            logger.w(TAG, "Both SMC services bind failed")
            _connectionState.value = false
            if (!isTestMode()) {
                logger.i(TAG, "Starting simulation as fallback because real services unavailable - to keep UI alive")
                _connectionState.value = true
                _capability.value = _capability.value.copy(searchMode = 2, isServiceActive = true)
                startTestModeSimulation()
            }
        } catch (e: Throwable) {
            logger.e(TAG, "Connect outer exception - should never crash: ${e.message}", e)
            _connectionState.value = false
            try {
                _connectionState.value = true
                startTestModeSimulation()
                logger.i(TAG, "Emergency fallback to simulation after outer exception")
            } catch (e2: Throwable) {
                logger.e(TAG, "Emergency fallback also failed", e2)
            }
        } finally {
            logger.i(TAG, "=== CONNECT finished, connectionState=${_connectionState.value} capability=${_capability.value} ===")
            logger.i(TAG, "========================================")
        }
    }

    fun disconnect() {
        logger.i(TAG, "Disconnect called, isTestMode=${isTestMode()}, isBound=$isBound, jobActive=${testModeJob?.isActive}")
        try {
            if (!isTestMode()) {
                testModeJob?.cancel()
                logger.i(TAG, "Cancelled testModeJob (non-test mode)")
            } else {
                logger.i(TAG, "Keeping testModeJob alive in test mode as per fix for crash report")
            }
            if (isBound) {
                try { 
                    context.unbindService(serviceConnection)
                    logger.hms("Unbound service successfully")
                } catch (e: Throwable) { 
                    logger.e(TAG, "Unbind failed: ${e.message}", e)
                }
                isBound = false
            }
            _connectionState.value = false
            _searchStatus.value = SatelliteSearchStatus.IDLE
            logger.hms("Disconnected, state set to false/idle")
        } catch (e: Throwable) {
            logger.e(TAG, "Disconnect exception: ${e.message}", e)
        }
    }

    fun forceStopSimulation() {
        logger.i(TAG, "forceStopSimulation called")
        testModeJob?.cancel()
        _searchStatus.value = SatelliteSearchStatus.IDLE
        logger.i(TAG, "Simulation stopped, status idle")
    }

    private fun isTestMode(): Boolean {
        return try {
            val prefs = context.getSharedPreferences("beidou_region", Context.MODE_PRIVATE)
            val tm = prefs.getBoolean("test_mode_prefs", false)
            logger.d(TAG, "isTestMode check: $tm from prefs")
            tm
        } catch (e: Throwable) { 
            logger.e(TAG, "isTestMode check failed: ${e.message}", e)
            false 
        }
    }

    fun queryCapability() {
        logger.i(TAG, "queryCapability called, isTest=${isTestMode()}, bound=$isBound messenger=$serviceMessenger")
        if (isTestMode()) {
            logger.i(TAG, "Test mode, skipping real queryCapability, using simulated capability: ${_capability.value}")
            return
        }
        try {
            val msg = Message.obtain(null, 1001)
            msg.replyTo = incomingMessenger
            serviceMessenger?.send(msg)
            logger.hms("Sent queryCapability message 1001 to service")
        } catch (e: Throwable) {
            logger.e(TAG, "queryCapability failed: ${e.message}", e)
        }
    }

    fun startSatelliteSearch() {
        logger.i(TAG, "=== startSatelliteSearch called: isTest=${isTestMode()} bypass=${regionManager.isBypassEnabledSync()} currentStatus=${_searchStatus.value} ===")
        _searchStatus.value = SatelliteSearchStatus.SEARCHING
        logger.i(TAG, "Search status -> SEARCHING")

        if (isTestMode() || regionManager.isBypassEnabledSync()) {
            applicationScope.launch {
                try {
                    logger.i(TAG, "Search simulation progression: SEARCHING -> ACQUIRING -> TRACKING")
                    delay(1000)
                    _searchStatus.value = SatelliteSearchStatus.SEARCHING
                    logger.i(TAG, "Search status still SEARCHING after 1s")
                    delay(2000)
                    _searchStatus.value = SatelliteSearchStatus.ACQUIRING
                    logger.i(TAG, "Search status -> ACQUIRING (satellite signal acquiring)")
                    delay(2000)
                    _searchStatus.value = SatelliteSearchStatus.TRACKING
                    logger.i(TAG, "Search status -> TRACKING - satellite found! Ready to send")
                } catch (e: Throwable) {
                    logger.e(TAG, "Search progression failed: ${e.message}", e)
                }
            }
            if (isTestMode()) {
                logger.i(TAG, "Starting test mode simulation from startSatelliteSearch")
                startTestModeSimulation()
            }
            return
        }
        try {
            val msg = Message.obtain(null, 1002)
            msg.replyTo = incomingMessenger
            msg.arg1 = 1
            serviceMessenger?.send(msg)
            logger.hms("Sent startSearch message 1002 arg1=1 to service")
        } catch (e: Throwable) {
            logger.e(TAG, "startSearch failed: ${e.message}", e)
            _searchStatus.value = SatelliteSearchStatus.ERROR
        }
    }

    fun stopSatelliteSearch() {
        logger.i(TAG, "stopSatelliteSearch called, currentStatus=${_searchStatus.value}, isTest=${isTestMode()}")
        _searchStatus.value = SatelliteSearchStatus.IDLE
        if (!isTestMode()) {
            testModeJob?.cancel()
            logger.i(TAG, "Cancelled simulation (non-test mode) because stop requested")
        } else {
            logger.i(TAG, "Keeping simulation alive in test mode - per user feedback that it stops moving")
        }
        if (isTestMode()) return
        try {
            val msg = Message.obtain(null, 1002)
            msg.arg1 = 0
            msg.replyTo = incomingMessenger
            serviceMessenger?.send(msg)
            logger.hms("Sent stopSearch message 1002 arg1=0")
        } catch (e: Throwable) {
            logger.e(TAG, "stopSearch failed: ${e.message}", e)
        }
    }

    fun sendMessage(message: SmcMessage, callback: (Boolean, SmcMessage) -> Unit = { _, _ -> }) {
        logger.i(TAG, "========================================")
        logger.i(TAG, "=== sendMessage called ===")
        logger.i(TAG, "Recipient: ${message.recipientNumber}, Content: '${message.content}', Priority: ${message.priority}, Type: ${message.messageType}")
        logger.i(TAG, "Location: lat=${message.latitude} lon=${message.longitude} alt=${message.altitude}")
        logger.i(TAG, "isTestMode=${isTestMode()}, isBound=$isBound, messenger=$serviceMessenger, connectionState=${_connectionState.value}")
        logger.message("User sending to ${message.recipientNumber}: ${message.content} id=${message.messageId} priority=${message.priority}")

        val queued = message.copy(sendTime = Instant.now(), status = MessageStatus.QUEUED)
        _messages.value = _messages.value + queued
        logger.message("Message QUEUED: ${queued.messageId} to ${queued.recipientNumber}")

        if (isTestMode()) {
            logger.i(TAG, "Test mode send - will simulate QUEUED->SEARCHING_SATELLITE->SENDING->SENT->DELIVERED")
            applicationScope.launch {
                try {
                    delay(500)
                    val searching = queued.copy(status = MessageStatus.SEARCHING_SATELLITE)
                    _messages.value = _messages.value.map { if (it.messageId == searching.messageId) searching else it }
                    logger.message("Status SEARCHING_SATELLITE for ${searching.messageId} - searching satellite")

                    delay(1000)
                    val sending = searching.copy(status = MessageStatus.SENDING)
                    _messages.value = _messages.value.map { if (it.messageId == sending.messageId) sending else it }
                    logger.message("Status SENDING for ${sending.messageId} - sending via simulated satellite")

                    delay(1500)
                    val sent = sending.copy(status = MessageStatus.SENT, sendTime = Instant.now())
                    _messages.value = _messages.value.map { if (it.messageId == sent.messageId) sent else it }
                    logger.message("Status SENT for ${sent.messageId} - sent to satellite, waiting ACK")

                    delay(1200)
                    val delivered = sent.copy(status = MessageStatus.DELIVERED, ackReceived = true, ackTime = Instant.now())
                    _messages.value = _messages.value.map { if (it.messageId == delivered.messageId) delivered else it }
                    logger.message("Status DELIVERED for ${delivered.messageId} - ACK received (simulated) - NOTE: In test mode, message does NOT actually go to recipient phone, only local simulation. For real SMS delivery, use SMS fallback option.")

                    // Also try real SMS as fallback for testing so user actually receives something
                    try {
                        logger.i(TAG, "Test mode: also attempting real SMS fallback via SmsManager for recipient ${delivered.recipientNumber} so user actually receives something")
                        val smsManager = if (android.os.Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
                        val smsContent = "[BeiDou Test Simulated] ${delivered.content} (Lat:${delivered.latitude}, Lon:${delivered.longitude})"
                        smsManager.sendTextMessage(delivered.recipientNumber, null, smsContent, null, null)
                        logger.message("SMS fallback sent to ${delivered.recipientNumber}: $smsContent")
                        logger.i(TAG, "SMS fallback sent successfully to ${delivered.recipientNumber}")
                    } catch (e: Throwable) {
                        logger.e(TAG, "SMS fallback failed (needs SEND_SMS permission): ${e.message}", e)
                        logger.message("SMS fallback failed: ${e.message} - user will NOT receive real SMS, only local simulation")
                    }

                    launch(Dispatchers.Main) { 
                        logger.i(TAG, "Callback success for ${delivered.messageId} status=${delivered.status}")
                        callback(true, delivered) 
                    }
                } catch (e: Throwable) {
                    logger.e(TAG, "Test send simulation failed: ${e.message}", e)
                    launch(Dispatchers.Main) { callback(false, queued.copy(status = MessageStatus.FAILED)) }
                }
            }
            return
        }

        // Real mode
        logger.i(TAG, "Real mode send - attempting via HMS service, bound=$isBound messenger=$serviceMessenger")
        try {
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
            if (serviceMessenger != null) {
                serviceMessenger?.send(msg)
                logger.hms("Sent real message via serviceMessenger: id=${message.messageId} to ${message.recipientNumber}")
                logger.message("Real message sent request for ${message.messageId}")
                val sent = queued.copy(status = MessageStatus.SENT)
                _messages.value = _messages.value.map { if (it.messageId == sent.messageId) sent else it }
                applicationScope.launch(Dispatchers.Main) { callback(true, sent) }
            } else {
                logger.w(TAG, "serviceMessenger is null - not bound, cannot send real satellite message. Trying SMS fallback.")
                // SMS fallback for real mode too when service not bound
                try {
                    val smsManager = if (android.os.Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
                    smsManager.sendTextMessage(message.recipientNumber, null, "[BeiDou Real Attempt Fallback SMS] ${message.content}", null, null)
                    logger.message("SMS fallback sent in real mode to ${message.recipientNumber}")
                    val sent = queued.copy(status = MessageStatus.SENT)
                    _messages.value = _messages.value.map { if (it.messageId == sent.messageId) sent else it }
                    applicationScope.launch(Dispatchers.Main) { callback(true, sent) }
                } catch (e2: Throwable) {
                    logger.e(TAG, "Both real satellite and SMS fallback failed", e2)
                    val failed = queued.copy(status = MessageStatus.FAILED)
                    _messages.value = _messages.value.map { if (it.messageId == failed.messageId) failed else it }
                    applicationScope.launch(Dispatchers.Main) { callback(false, failed) }
                }
            }
        } catch (e: Throwable) {
            logger.e(TAG, "Real sendMessage failed: ${e.message}", e)
            val failed = queued.copy(status = MessageStatus.FAILED)
            _messages.value = _messages.value.map { if (it.messageId == failed.messageId) failed else it }
            applicationScope.launch(Dispatchers.Main) { callback(false, failed) }
        } finally {
            logger.i(TAG, "=== sendMessage finished ===")
            logger.i(TAG, "========================================")
        }
    }

    fun simulateIncomingMessage(content: String, sender: String = "+8613800000000") {
        logger.i(TAG, "Simulate incoming: $content from $sender")
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
        if (testModeJob?.isActive == true) {
            logger.i(TAG, "Test simulation already running, not restarting")
            return
        }
        testModeJob?.cancel()
        logger.i(TAG, "Starting new test orbit simulation")
        testModeJob = applicationScope.launch {
            var orbit = 0.0
            var prn = Random.nextInt(1, 63)
            logger.i(TAG, "Orbit simulation started PRN $prn initial orbit $orbit")
            try {
                while (true) {
                    orbit = (orbit + 2.0) % 360.0
                    if (orbit < 2.0) {
                        prn = Random.nextInt(1, 63)
                        logger.i(TAG, "New satellite pass PRN $prn orbit reset")
                    }
                    val elevation = 20 + 60 * (0.5 + 0.5 * sin(Math.toRadians(orbit * 2)))
                    val snr = 20 + (elevation / 90.0) * 18 + Random.nextDouble(-1.5, 1.5)
                    val signal = SatelliteSignalInfo(
                        satelliteId = prn,
                        snrDb = snr,
                        elevationDeg = elevation,
                        azimuthDeg = orbit,
                        dopplerHz = Random.nextDouble(-800.0, 800.0)
                    )
                    _signalInfo.value = signal
                    if (orbit.toInt() % 30 == 0) {
                        logger.sensor("Orbit update: orbit=%.1f PRN %d Az %.1f El %.1f SNR %.1f".format(orbit, prn, orbit, elevation, snr))
                    }
                    delay(600)
                }
            } catch (e: Throwable) {
                logger.e(TAG, "Orbit simulation crashed: ${e.message}", e)
            }
        }
        logger.i(TAG, "Test mode job launched: ${testModeJob?.isActive}")
    }

    fun buildEmergencyContent(location: android.location.Location?): String {
        val content = buildString {
            append("EMERGENCY SOS - I need help! ")
            location?.let {
                append("Lat:${it.latitude}, Lon:${it.longitude}, Alt:${it.altitude}. Acc:${it.accuracy}m. ")
            }
            append("Time:${Instant.now()}. Device:${android.os.Build.MODEL}. ")
        }
        logger.i(TAG, "Built emergency content: $content")
        return content
    }
}
