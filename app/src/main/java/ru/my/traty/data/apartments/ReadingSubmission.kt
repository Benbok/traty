package ru.my.traty.data.apartments

import kotlinx.serialization.Serializable

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "reading_submissions", foreignKeys = [ForeignKey(entity = Meter::class, parentColumns = ["id"], childColumns = ["meterId"], onDelete = ForeignKey.CASCADE)], indices = [Index("meterId")])
@Serializable
data class ReadingSubmission(@PrimaryKey(autoGenerate = true) val id: Long = 0, val meterId: Long, val submittedAt: Long, val month: Int)

@Entity(tableName = "submission_readings", primaryKeys = ["submissionId", "readingId"], foreignKeys = [
    ForeignKey(entity = ReadingSubmission::class, parentColumns = ["id"], childColumns = ["submissionId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = MeterReading::class, parentColumns = ["id"], childColumns = ["readingId"], onDelete = ForeignKey.CASCADE),
], indices = [Index("readingId")])
@Serializable
data class SubmissionReading(val submissionId: Long, val readingId: Long)
