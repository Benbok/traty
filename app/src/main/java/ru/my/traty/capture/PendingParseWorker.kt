package ru.my.traty.capture

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.my.traty.TratyApp
import ru.my.traty.data.PendingStatus
import java.util.concurrent.TimeUnit

/** Разбирает очередь фраз, когда появилась сеть. Если что-то осталось, пробует позже. */
class PendingParseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as TratyApp).container
        container.quickEntry.processPending()
        val left = container.db.pendingPhrases().getByStatus(PendingStatus.WAITING)
        return if (left.isEmpty()) Result.success() else Result.retry()
    }
}

object PendingParseWork {
    private const val NAME = "pending-parse"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<PendingParseWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
    }
}
