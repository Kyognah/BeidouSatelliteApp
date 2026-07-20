package com.huawei.beidousatellite.data.model

import android.os.Parcel
import android.os.Parcelable
import kotlinx.datetime.Instant
import java.util.*

/**
 * Satellite signal information from BeiDou-3 satellites
 */
@kotlinx.serialization.Serializable
data class SatelliteSignalInfo(
    val satelliteId: Int,
    val constellation: Constellation = Constellation.BEIDOU,
    val signalType: SignalType = SignalType.B1C,
    val snrDb: Double,
    val elevationDeg: Double,
    val azimuthDeg: Double,
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
        @JvmField
        val CREATOR: Parcelable.Creator<SatelliteSignalInfo> = object : Parcelable.Creator<SatelliteSignalInfo> {
            override fun createFromParcel(parcel: Parcel): SatelliteSignalInfo = SatelliteSignalInfo(parcel)
            override fun newArray(size: Int): Array<SatelliteSignalInfo?> = arrayOfNulls(size)
        }
    }

    val signalQuality: SignalQuality
        get() = when {
            snrDb >= 35.0 -> SignalQuality.EXCELLENT
            snrDb >= 28.0 -> SignalQuality.GOOD
            snrDb >= 20.0 -> SignalQuality.FAIR
            snrDb >= 12.0 -> SignalQuality.POOR
            else -> SignalQuality.NONE
        }

    val isUsable: Boolean
        get() = signalQuality != SignalQuality.NONE && elevationDeg > 10.0
}

enum class Constellation {
    BEIDOU, GPS, GLONASS, GALILEO, QZSS, IRNSS
}

enum class SignalType {
    B1C, B2a, B2b, B3, L1, L2, L5, E1, E5a, E5b, E6
}

enum class SignalQuality {
    EXCELLENT, GOOD, FAIR, POOR, NONE
}

@kotlinx.serialization.Serializable
data class SmcMessage(
    val messageId: String = UUID.randomUUID().toString(),
    val globalMsgId: String? = null,
    val beidouMsgId: String? = null,
    val senderNumber: String,
    val recipientNumber: String,
    val content: String,
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
) : Parcelable {
    constructor(parcel: Parcel) : this(
        messageId = parcel.readString()!!,
        globalMsgId = parcel.readString(),
        beidouMsgId = parcel.readString(),
        senderNumber = parcel.readString()!!,
        recipientNumber = parcel.readString()!!,
        content = parcel.readString()!!,
        priority = MessagePriority.valueOf(parcel.readString()!!),
        messageType = MessageType.valueOf(parcel.readString()!!),
        latitude = parcel.readDouble().takeIf { it != -1.0 },
        longitude = parcel.readDouble().takeIf { it != -1.0 },
        altitude = parcel.readDouble().takeIf { it != -1.0 },
        address = parcel.readString(),
        utcTime = Instant.parse(parcel.readString()!!),
        sendTime = parcel.readString()?.let { Instant.parse(it) },
        receiveTime = parcel.readString()?.let { Instant.parse(it) },
        status = MessageStatus.valueOf(parcel.readString()!!),
        retryCount = parcel.readInt(),
        ackReceived = parcel.readByte() != 0,
        ackTime = parcel.readString()?.let { Instant.parse(it) }
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(messageId)
        parcel.writeString(globalMsgId)
        parcel.writeString(beidouMsgId)
        parcel.writeString(senderNumber)
        parcel.writeString(recipientNumber)
        parcel.writeString(content)
        parcel.writeString(priority.name)
        parcel.writeString(messageType.name)
        parcel.writeDouble(latitude ?: -1.0)
        parcel.writeDouble(longitude ?: -1.0)
        parcel.writeDouble(altitude ?: -1.0)
        parcel.writeString(address)
        parcel.writeString(utcTime.toString())
        parcel.writeString(sendTime?.toString())
        parcel.writeString(receiveTime?.toString())
        parcel.writeString(status.name)
        parcel.writeInt(retryCount)
        parcel.writeByte(if (ackReceived) 1 else 0)
        parcel.writeString(ackTime?.toString())
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<SmcMessage> = object : Parcelable.Creator<SmcMessage> {
            override fun createFromParcel(parcel: Parcel): SmcMessage = SmcMessage(parcel)
            override fun newArray(size: Int): Array<SmcMessage?> = arrayOfNulls(size)
        }
    }
}

enum class MessagePriority { LOW, NORMAL, HIGH, EMERGENCY }
enum class MessageType { TEXT, LOCATION, EMERGENCY_SOS, SYSTEM, ACK }
enum class MessageStatus { PENDING, QUEUED, SEARCHING_SATELLITE, SENDING, SENT, DELIVERED, FAILED, EXPIRED, CANCELLED }

@kotlinx.serialization.Serializable
data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val timestamp: Instant = Instant.now(),
    val address: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        latitude = parcel.readDouble(),
        longitude = parcel.readDouble(),
        altitude = parcel.readDouble().takeIf { it != -1.0 },
        accuracy = parcel.readFloat().takeIf { it >= 0 },
        timestamp = Instant.parse(parcel.readString()!!),
        address = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeDouble(latitude)
        parcel.writeDouble(longitude)
        parcel.writeDouble(altitude ?: -1.0)
        parcel.writeFloat(accuracy ?: -1f)
        parcel.writeString(timestamp.toString())
        parcel.writeString(address)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<GeoLocation> = object : Parcelable.Creator<GeoLocation> {
            override fun createFromParcel(parcel: Parcel): GeoLocation = GeoLocation(parcel)
            override fun newArray(size: Int): Array<GeoLocation?> = arrayOfNulls(size)
        }
    }
}

@kotlinx.serialization.Serializable
data class SmcCapability(
    val searchMode: Int,
    val rcvMsgSupport: Int,
    val sendVoiceSupport: Int,
    val sendImageSupport: Int,
    val ackSupport: Int,
    val sendIntervalSec: Int,
    val batteryWarningPercent: Int,
    val foldTipsValue: Int,
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

@kotlinx.serialization.Serializable
data class CalibrationData(
    val magneticAccuracy: Int,
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