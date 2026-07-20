package com.huawei.beidousatellite.data.repository

import com.huawei.beidousatellite.data.local.SatelliteDatabase
import com.huawei.beidousatellite.data.local.SatelliteMessageEntity
import com.huawei.beidousatellite.data.model.SmcMessage
import com.huawei.beidousatellite.util.SatelliteLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SatelliteRepository @Inject constructor(
    private val db: SatelliteDatabase,
    private val logger: SatelliteLogger
) {
    suspend fun getAllMessages(): List<SmcMessage> {
        return db.messageDao().getAll().map { it.toModel() }
    }

    fun getAllMessagesFlow(): Flow<List<SmcMessage>> = flow {
        emit(getAllMessages())
    }

    suspend fun saveMessage(message: SmcMessage) {
        db.messageDao().insert(message.toEntity())
        logger.message("Saved message ${message.messageId} status=${message.status}")
    }

    suspend fun deleteMessage(id: String) {
        db.messageDao().delete(id)
    }

    suspend fun clearAll() {
        db.messageDao().clearAll()
    }

    private fun SatelliteMessageEntity.toModel(): SmcMessage {
        return SmcMessage(
            messageId = messageId,
            globalMsgId = globalMsgId,
            beidouMsgId = beidouMsgId,
            senderNumber = senderNumber,
            recipientNumber = recipientNumber,
            content = content,
            priority = try { com.huawei.beidousatellite.data.model.MessagePriority.valueOf(priority) } catch (_: Exception) { com.huawei.beidousatellite.data.model.MessagePriority.NORMAL },
            messageType = try { com.huawei.beidousatellite.data.model.MessageType.valueOf(messageType) } catch (_: Exception) { com.huawei.beidousatellite.data.model.MessageType.TEXT },
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            address = address,
            utcTime = Instant.ofEpochMilli(utcTime),
            sendTime = sendTime?.let { Instant.ofEpochMilli(it) },
            receiveTime = receiveTime?.let { Instant.ofEpochMilli(it) },
            status = try { com.huawei.beidousatellite.data.model.MessageStatus.valueOf(status) } catch (_: Exception) { com.huawei.beidousatellite.data.model.MessageStatus.PENDING },
            retryCount = retryCount,
            ackReceived = ackReceived,
            ackTime = ackTime?.let { Instant.ofEpochMilli(it) }
        )
    }

    private fun SmcMessage.toEntity(): SatelliteMessageEntity {
        return SatelliteMessageEntity(
            messageId = messageId,
            globalMsgId = globalMsgId,
            beidouMsgId = beidouMsgId,
            senderNumber = senderNumber,
            recipientNumber = recipientNumber,
            content = content,
            priority = priority.name,
            messageType = messageType.name,
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            address = address,
            utcTime = utcTime.toEpochMilli(),
            sendTime = sendTime?.toEpochMilli(),
            receiveTime = receiveTime?.toEpochMilli(),
            status = status.name,
            retryCount = retryCount,
            ackReceived = ackReceived,
            ackTime = ackTime?.toEpochMilli()
        )
    }
}
