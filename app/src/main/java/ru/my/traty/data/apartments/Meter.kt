package ru.my.traty.data.apartments

import kotlinx.serialization.Serializable

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "meters", foreignKeys = [ForeignKey(entity = Apartment::class, parentColumns = ["id"], childColumns = ["apartmentId"], onDelete = ForeignKey.CASCADE)], indices = [Index("apartmentId")])
@Serializable
data class Meter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val apartmentId: Long, val name: String, val resource: String,
    val serialNumber: String? = null, val provider: String? = null, val account: String? = null,
    val archived: Boolean = false, val accountingFrom: Long? = null, val accountingTo: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "meter_registers", foreignKeys = [ForeignKey(entity = Meter::class, parentColumns = ["id"], childColumns = ["meterId"], onDelete = ForeignKey.CASCADE)], indices = [Index("meterId"), Index(value = ["meterId", "code"], unique = true)])
@Serializable
data class MeterRegister(@PrimaryKey(autoGenerate = true) val id: Long = 0, val meterId: Long, val code: String = "TOTAL")
