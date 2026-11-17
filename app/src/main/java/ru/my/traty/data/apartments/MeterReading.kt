package ru.my.traty.data.apartments

import kotlinx.serialization.Serializable

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "meter_readings", foreignKeys = [ForeignKey(entity = MeterRegister::class, parentColumns = ["id"], childColumns = ["registerId"], onDelete = ForeignKey.CASCADE)], indices = [Index("registerId"), Index(value = ["registerId", "measuredAt"], unique = true)])
@Serializable
data class MeterReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val registerId: Long, val measuredAt: Long, val valueMilli: Long,
    val note: String? = null, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
)
