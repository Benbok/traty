package ru.my.traty.data.apartments

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ApartmentDao {
    @Query("SELECT * FROM apartments ORDER BY archived, name, id") fun observeApartments(): Flow<List<Apartment>>
    @Query("SELECT * FROM apartments ORDER BY id") suspend fun apartments(): List<Apartment>
    @Query("SELECT * FROM meters ORDER BY id") suspend fun meters(): List<Meter>
    @Query("SELECT * FROM meter_registers ORDER BY id") suspend fun registers(): List<MeterRegister>
    @Query("SELECT * FROM meter_readings ORDER BY measuredAt, id") suspend fun readings(): List<MeterReading>
    @Query("SELECT * FROM reading_submissions ORDER BY submittedAt, id") suspend fun submissions(): List<ReadingSubmission>
    @Query("SELECT * FROM submission_readings") suspend fun submissionReadings(): List<SubmissionReading>
    @Query("SELECT * FROM meters") fun observeMeters(): Flow<List<Meter>>
    @Query("SELECT * FROM meter_readings") fun observeReadings(): Flow<List<MeterReading>>
    @Query("SELECT * FROM reading_submissions") fun observeSubmissions(): Flow<List<ReadingSubmission>>
    @Query("SELECT COUNT(*) FROM meter_readings") fun observeReadingCount(): Flow<Int>
    @Insert suspend fun insertApartment(value: Apartment): Long
    @Update suspend fun updateApartment(value: Apartment)
    @Insert suspend fun insertMeter(value: Meter): Long
    @Update suspend fun updateMeter(value: Meter)
    @Insert suspend fun insertRegister(value: MeterRegister): Long
    @Insert suspend fun insertReading(value: MeterReading): Long
    @Update suspend fun updateReading(value: MeterReading)
    @Insert suspend fun insertSubmission(value: ReadingSubmission): Long
    @Insert suspend fun insertSubmissionReading(value: SubmissionReading)
    @Query("DELETE FROM meter_readings WHERE id = :id") suspend fun deleteReading(id: Long)
    @Query("DELETE FROM reading_submissions WHERE id IN (SELECT submissionId FROM submission_readings WHERE readingId = :id)") suspend fun clearSubmissionForReading(id: Long)
}
