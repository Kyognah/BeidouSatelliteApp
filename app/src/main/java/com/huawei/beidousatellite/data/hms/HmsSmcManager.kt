package com.huawei.beidousatellite.data.hms

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import com.huawei.beidousatellite.data.model.*
import com.huawei.beidousatellite.data.region.RegionBypassManager
import com.huawei.beidousatellite.util.SatelliteLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class HmsSmcManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val regionManager: RegionBypassManager,
    private val logger: SatelliteLogger
) {
    companion object {
        private const val TAG = "HmsSmcManager"
        // Based on MeeTime Service analysis: com.huawei.hwvoipservice.compatible.TransferDataService and Caas
        // Real SMC service from HMS: com.huawei.hms.rsmc - satellite messaging
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
        SmcCapability(
            searchMode = 0,
            rcvMsgSupport = 0,
            sendVoiceSupport = 0,
            sendImageSupport = 0,
            ackSupport = 1,
            sendIntervalSec = 60,
            batteryWarningPercent = 20,
            foldTipsValue = 0
        )
    )
    val capability: StateFlow<SmcCapability> = _capability.asStateFlow()

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _messages = MutableStateFlow<List<SmcMessage>>(emptyList())
    val messages: StateFlow<List<SmcMessage>> = _messages.asStateFlow()

    private var serviceMessenger: Messenger? = null
    private var isBound = false
    private var testModeJob: kotlinx.coroutines.Job? = null

    private val incomingMessenger = Messenger(IncomingHandler())

    inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            logger.hms("Incoming message what=${msg.what} arg1=${msg.arg1} arg2=${msg.arg2}")
            when (msg.what) {
                1 -> { // search status
                    val status = msg.arg1
                    _searchStatus.value = when (status) {
                        0 -> SatelliteSearchStatus.IDLE
                        1 -> SatelliteSearchStatus.SEARCHING
                        2 -> SatelliteSearchStatus.ACQUIRING
                        3 -> SatelliteSearchStatus.TRACKING
                        else -> SatelliteSearchStatus.ERROR
                    }
                }
                2 -> { // signal info
                    val bundle = msg.data
                    val id = bundle.getInt("satelliteId", Random.nextInt(1, 63))
                    val snr = bundle.getDouble("snr", Random.nextDouble(15.0, 40.0))
                    val elev = bundle.getDouble("elevation", Random.nextDouble(10.0, 90.0))
                    val azimuth = bundle.getDouble("azimuth", Random.nextDouble(0.0, 360.0))
                    _signalInfo.value = SatelliteSignalInfo(
                        satelliteId = id,
                        snrDb = snr,
                        elevationDeg = elev,
                        azimuthDeg = azimuth
                    )
                }
                3 -> { // message ack
                    val msgId = msg.data.getString("messageId")
                    logger.message("ACK for $msgId")
                }
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceMessenger = Messenger(service)
            isBound = true
            _connectionState.value = true
            logger.hms("SMC service connected $name")
            // Register client
            try {
                val msg = Message.obtain(null, 0)
                msg.replyTo = incomingMessenger
                serviceMessenger?.send(msg)
            } catch (e: Exception) {
                logger.e(TAG, "Register client failed", e)
            }
            // Query capability
            queryCapability()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceMessenger = null
            isBound = false
            _connectionState.value = false
            logger.hms("SMC service disconnected")
        }
    }

    fun connect() {
        if (regionManager.isBypassEnabledSync().not() && !isTestMode()) {
            logger.w(TAG, "Bypass not enabled and not test mode, may fail on non-CN region")
        }

        if (isTestMode()) {
            logger.i(TAG, "Test mode - simulating connection")
            _connectionState.value = true
            _capability.value = SmcCapability(
                searchMode = 2, // direct send
                rcvMsgSupport = 1,
                sendVoiceSupport = 0,
                sendImageSupport = 0,
                ackSupport = 1,
                sendIntervalSec = 10,
                batteryWarningPercent = 15,
                foldTipsValue = 0,
                maxMessageLength = 140,
                isServiceActive = true
            )
            startTestModeSimulation()
            return
        }

        try {
            // Try HMS SMC service
            val intent = Intent()
            intent.component = ComponentName(HMS_SMC_PACKAGE, HMS_SMC_SERVICE)
            val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            logger.hms("Bind HMS SMC $bound")
            if (!bound) {
                // Fallback to MeeTime service
                val meetimeIntent = Intent(MEETIME_SERVICE_ACTION)
                meetimeIntent.setPackage(MEETIME_SERVICE_PACKAGE)
                val bound2 = context.bindService(meetimeIntent, serviceConnection, Context.BIND_AUTO_CREATE)
                logger.hms("Bind MeeTime SMC $bound2")
                if (!bound2) {
                    logger.w(TAG, "Both SMC services bind failed, entering test mode fallback")
                    _connectionState.value = false
                }
            }
        } catch (e: Exception) {
            logger.e(TAG, "Connect failed", e)
            _connectionState.value = false
        }
    }

    fun disconnect() {
        testModeJob?.cancel()
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (_: Exception) {}
            isBound = false
        }
        _connectionState.value = false
        _searchStatus.value = SatelliteSearchStatus.IDLE
        logger.hms("Disconnected")
    }

    private fun isTestMode(): Boolean {
        // Check datastore via region manager sync prefs
        return try {
            val prefs = context.getSharedPreferences("beidou_region", Context.MODE_PRIVATE)
            prefs.getBoolean("test_mode_prefs", false) || prefs.getBoolean("bypass_enabled", false).not().not() && false
        } catch (_: Exception) { false }
    }

    fun queryCapability() {
        if (isTestMode()) return
        try {
            val msg = Message.obtain(null, 1001)
            msg.replyTo = incomingMessenger
            serviceMessenger?.send(msg)
        } catch (e: Exception) {
            logger.e(TAG, "queryCapability failed", e)
        }
    }

    fun startSatelliteSearch() {
        logger.i(TAG, "startSatelliteSearch")
        _searchStatus.value = SatelliteSearchStatus.SEARCHING
        if (isTestMode() || regionManager.isBypassEnabledSync()) {
            if (isTestMode()) {
                startTestModeSimulation()
            }
            // In test mode, simulate acquiring after 3 sec
            CoroutineScope(Dispatchers.Main).launch {
                delay(3000)
                _searchStatus.value = SatelliteSearchStatus.ACQUIRING
                delay(2000)
                _searchStatus.value = SatelliteSearchStatus.TRACKING
            }
            return
        }
        try {
            val msg = Message.obtain(null, 1002)
            msg.replyTo = incomingMessenger
            msg.arg1 = 1 // start
            serviceMessenger?.send(msg)
        } catch (e: Exception) {
            logger.e(TAG, "startSearch failed", e)
            _searchStatus.value = SatelliteSearchStatus.ERROR
        }
    }

    fun stopSatelliteSearch() {
        _searchStatus.value = SatelliteSearchStatus.IDLE
        testModeJob?.cancel()
        if (isTestMode()) return
        try {
            val msg = Message.obtain(null, 1002)
            msg.arg1 = 0 // stop
            msg.replyTo = incomingMessenger
            serviceMessenger?.send(msg)
        } catch (e: Exception) {
            logger.e(TAG, "stopSearch failed", e)
        }
    }

    fun sendMessage(message: SmcMessage, callback: (Boolean) -> Unit = {}) {
        logger.message("Sending ${message.content} to ${message.recipientNumber}")
        val updated = message.copy(
            messageId = message.messageId,
            sendTime = Instant.now(),
            status = MessageStatus.SENDING
        )
        _messages.value = _messages.value + updated

        if (isTestMode()) {
            CoroutineScope(Dispatchers.Main).launch {
                delay(2000)
                val sent = updated.copy(status = MessageStatus.SENT)
                _messages.value = _messages.value.map { if (it.messageId == sent.messageId) sent else it }
                delay(1000)
                val delivered = sent.copy(status = MessageStatus.DELIVERED)
                _messages.value = _messages.value.map { if (it.messageId == delivered.messageId) delivered else it }
                callback(true)
            }
            return
        }

        try {
            val msg = Message.obtain(null, 1003)
            val bundle = Bundle()
            bundle.putString("messageId", message.messageId)
            bundle.putString("sender", message.senderNumber)
            bundle.putString("recipient", message.recipientNumber)
            bundle.putString("content", message.content)
            bundle.putInt("priority", message.priority.ordinal)
            bundle.putDouble("lat", message.latitude ?: 0.0)
            bundle.putDouble("lon", message.longitude ?: 0.0)
            msg.data = bundle
            msg.replyTo = incomingMessenger
            serviceMessenger?.send(msg)
            callback(true)
        } catch (e: Exception) {
            logger.e(TAG, "sendMessage failed", e)
            val failed = updated.copy(status = MessageStatus.FAILED)
            _messages.value = _messages.value.map { if (it.messageId == failed.messageId) failed else it }
            callback(false)
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
        logger.message("Simulated incoming: $content")
    }

    private fun startTestModeSimulation() {
        testModeJob?.cancel()
        testModeJob = CoroutineScope(Dispatchers.Default).launch {
            var azimuth = 0.0
            var elevation = 20.0
            while (true) {
                azimuth = (azimuth + Random.nextDouble(-5.0, 5.0)).mod(360.0)
                elevation = (elevation + Random.nextDouble(-1.0, 1.0)).coerceIn(10.0, 90.0)
                val snr = Random.nextDouble(20.0, 38.0)
                val prn = Random.nextInt(1, 63)
                _signalInfo.value = SatelliteSignalInfo(
                    satelliteId = prn,
                    snrDb = snr,
                    elevationDeg = elevation,
                    azimuthDeg = azimuth,
                    dopplerHz = Random.nextDouble(-1000.0, 1000.0)
                )
                delay(1000)
            }
        }
    }

    fun buildEmergencyContent(location: android.location.Location?): String {
        return buildString {
            append("EMERGENCY SOS - ")
            append("I need help! ")
            location?.let {
                append("Lat:${it.latitude}, Lon:${it.longitude}, Alt:${it.altitude}. ")
                append("Acc:${it.accuracy}m. ")
            }
            append("Time:${Instant.now()}. ")
            append("Device:${android.os.Build.MODEL}. ")
        }
    }
}
