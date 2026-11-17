package ru.my.traty.data.apartments

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ApartmentMigrations {
    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `apartments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `address` TEXT, `archived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `meters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `apartmentId` INTEGER NOT NULL, `name` TEXT NOT NULL, `resource` TEXT NOT NULL, `serialNumber` TEXT, `provider` TEXT, `account` TEXT, `archived` INTEGER NOT NULL, `accountingFrom` INTEGER, `accountingTo` INTEGER, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`apartmentId`) REFERENCES `apartments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meters_apartmentId` ON `meters` (`apartmentId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `meter_registers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `meterId` INTEGER NOT NULL, `code` TEXT NOT NULL, FOREIGN KEY(`meterId`) REFERENCES `meters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meter_registers_meterId` ON `meter_registers` (`meterId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_meter_registers_meterId_code` ON `meter_registers` (`meterId`, `code`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `meter_readings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `registerId` INTEGER NOT NULL, `measuredAt` INTEGER NOT NULL, `valueMilli` INTEGER NOT NULL, `note` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`registerId`) REFERENCES `meter_registers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meter_readings_registerId` ON `meter_readings` (`registerId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_meter_readings_registerId_measuredAt` ON `meter_readings` (`registerId`, `measuredAt`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `reading_submissions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `meterId` INTEGER NOT NULL, `submittedAt` INTEGER NOT NULL, `month` INTEGER NOT NULL, FOREIGN KEY(`meterId`) REFERENCES `meters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_submissions_meterId` ON `reading_submissions` (`meterId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `submission_readings` (`submissionId` INTEGER NOT NULL, `readingId` INTEGER NOT NULL, PRIMARY KEY(`submissionId`, `readingId`), FOREIGN KEY(`submissionId`) REFERENCES `reading_submissions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`readingId`) REFERENCES `meter_readings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_submission_readings_readingId` ON `submission_readings` (`readingId`)")
        }
    }
}
