package com.huawei.beidousatellite.data.model

import android.os.Parcel
import android.os.Parcelable
import java.time.Instant
import java.util.*

data class SatelliteSignalInfo(
    val satelliteId: Int,
    val constellation: Constellation = Constellation.BEIDOU,
    val signalType: SignalType = SignalType.B1C,
    val snrDb: Double = 0.0,
    val elevationDeg: Double = 0.0,
    val azimuthDeg: Double = 0.0,
    val dopplerHz: Double = 0.0,
    val carrierPhase: Double = 0.0,
    val lockTimeSec: Long = 0,
    val timestamp: Instant = Instant.now()
) : Parcelable {
    constructor(parcel: Parcel) : this(
        satelliteId = parcel.readInt(),
        constellation = Constellation.valueOf(parcel.readString()!!),
        signalType = SignalType.valueOf(parcel.readString()!!),
        snrDb = parcel.readDouble(),
        elevationDeg = parcel.readDouble(),
        azimuthDeg = parcel.readDouble(),
        dopplerHz = parcel.readDouble(),
        carrierPhase = parcel.readDouble(),
        lockTimeSec = parcel.readLong(),
        timestamp = Instant.parse(parcel.readString()!!)
    )
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(satelliteId)
        parcel.writeString(constellation.name)
        parcel.writeString(signalType.name)
        parcel.writeDouble(snrDb)
        parcel.writeDouble(elevationDeg)
        parcel.writeDouble(azimuthDeg)
        parcel.writeDouble(dopplerHz)
        parcel.writeDouble(carrierPhase)
        parcel.writeLong(lockTimeSec)
        parcel.writeString(timestamp.toString())
    }
    override fun describeContents(): Int = 0
    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<SatelliteSignalInfo> {
            override fun createFromParcel(parcel: Parcel): SatelliteSignalInfo = SatelliteSignalInfo(parcel)
            override fun newArray(size: Int): Array<SatelliteSignalInfo?> = arrayOfNulls(size)
        }
    }
    val signalQuality: SignalQuality get() = when {
        snrDb >= 35.0 -> SignalQuality.EXCELLENT
        snrDb >= 28.0 -> SignalQuality.GOOD
        snrDb >= 20.0 -> SignalQuality.FAIR
        snrDb >= 12.0 -> SignalQuality.POOR
        else -> SignalQuality.NONE
    }
    val isUsable: Boolean get() = signalQuality != SignalQuality.NONE && elevationDeg > 10.0
}

enum class Constellation { BEIDOU, GPS, GLONASS, GALILEO, QZSS, IRNSS }
enum class SignalType { B1C, B2a, B2b, B3, L1, L2, L5, E1, E5a, E5b, E6 }
enum class SignalQuality { EXCELLENT, GOOD, FAIR, POOR, NONE }

data class SmcMessage(
    val messageId: String = UUID.randomUUID().toString(),
    val globalMsgId: String? = null,
    val beidouMsgId: String? = null,
    val senderNumber: String = "",
    val recipientNumber: String = "",
    val content: String = "",
    val priority: MessagePriority = MessagePriority.NORMAL,
    val messageType: MessageType = MessageType.TEXT,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val address: String? = null,
    val utcTime: Instant = Instant.now(),
    val sendTime: Instant? = null,
    val receiveTime: Instant? = null,
    val status: MessageStatus = MessageStatus.PENDING,
    val retryCount: Int = 0,
    val ackReceived: Boolean = false,
    val ackTime: Instant? = null
)

enum class MessagePriority { LOW, NORMAL, HIGH, EMERGENCY }
enum class MessageType { TEXT, LOCATION, EMERGENCY_SOS, SYSTEM, ACK }
enum class MessageStatus { PENDING, QUEUED, SEARCHING_SATELLITE, SENDING, SENT, DELIVERED, FAILED, EXPIRED, CANCELLED }

data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val timestamp: Instant = Instant.now(),
    val address: String? = null
)

data class SmcCapability(
    val searchMode: Int = 0,
    val rcvMsgSupport: Int = 0,
    val sendVoiceSupport: Int = 0,
    val sendImageSupport: Int = 0,
    val ackSupport: Int = 0,
    val sendIntervalSec: Int = 0,
    val batteryWarningPercent: Int = 0,
    val foldTipsValue: Int = 0,
    val maxMessageLength: Int = 140,
    val maxVoiceLengthSec: Int = 30,
    val supportedRegions: List<String> = listOf("CN"),
    val isServiceActive: Boolean = true,
    val lastUpdated: Instant = Instant.now()
) {
    val isDirectSendSupported: Boolean = searchMode == 2
    val isReceiveSupported: Boolean = rcvMsgSupport == 1
    val isVoiceSupported: Boolean = sendVoiceSupport == 1
    val isImageSupported: Boolean = sendImageSupport == 1
}

enum class SatelliteSearchStatus { IDLE, SEARCHING, ACQUIRING, TRACKING, LOST, ERROR }
enum class CalibrationStatus { NOT_CALIBRATED, CALIBRATING, CALIBRATED_HIGH, CALIBRATED_MEDIUM, CALIBRATED_LOW, FAILED }

data class CalibrationData(
    val magneticAccuracy: Int = 0,
    val accelerometerBias: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val magnetometerBias: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val gyroscopeBias: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val lastCalibrated: Instant = Instant.now(),
    val calibrationCount: Int = 0
) {
    val isValid: Boolean = magneticAccuracy >= 2
    val accuracyLevel: CalibrationStatus = when (magneticAccuracy) {
        3 -> CalibrationStatus.CALIBRATED_HIGH
        2 -> CalibrationStatus.CALIBRATED_MEDIUM
        1 -> CalibrationStatus.CALIBRATED_LOW
        else -> CalibrationStatus.NOT_CALIBRATED
    }
}
