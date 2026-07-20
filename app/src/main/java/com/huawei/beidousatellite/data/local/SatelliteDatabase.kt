package com.huawei.beidousatellite.data.local

import androidx.room.*
import com.huawei.beidousatellite.data.model.MessagePriority
import com.huawei.beidousatellite.data.model.MessageStatus
import com.huawei.beidousatellite.data.model.MessageType
import java.time.Instant

@Entity(tableName = "satellite_messages")
data class SatelliteMessageEntity(
    @PrimaryKey val messageId: String,
    val globalMsgId: String? = null,
    val beidouMsgId: String? = null,
    val senderNumber: String,
    val recipientNumber: String,
    val content: String,
    val priority: String, // enum name
    val messageType: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val address: String? = null,
    val utcTime: Long, // epoch millis
    val sendTime: Long? = null,
    val receiveTime: Long? = null,
    val status: String,
    val retryCount: Int = 0,
    val ackReceived: Boolean = false,
    val ackTime: Long? = null
)

@Dao
interface SatelliteMessageDao {
    @Query("SELECT * FROM satellite_messages ORDER BY utcTime DESC")
    suspend fun getAll(): List<SatelliteMessageEntity>

    @Query("SELECT * FROM satellite_messages WHERE messageId = :id LIMIT 1")
    suspend fun getById(id: String): SatelliteMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SatelliteMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SatelliteMessageEntity>)

    @Query("DELETE FROM satellite_messages WHERE messageId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM satellite_messages")
    suspend fun clearAll()

    @Query("SELECT * FROM satellite_messages WHERE status = :status")
    suspend fun getByStatus(status: String): List<SatelliteMessageEntity>
}

@Database(entities = [SatelliteMessageEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class SatelliteDatabase : RoomDatabase() {
    abstract fun messageDao(): SatelliteMessageDao
}

class Converters {
    @TypeConverter
    fun fromInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }
    @TypeConverter
    fun toInstant(instant: Instant?): Long? = instant?.toEpochMilli()
}
