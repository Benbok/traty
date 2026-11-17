package ru.my.traty.data.apartments

import kotlinx.serialization.Serializable

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "apartments")
@Serializable
data class Apartment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val address: String? = null, val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
