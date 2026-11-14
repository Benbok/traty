package ru.my.traty.data.recurring

import androidx.room.withTransaction
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Expense
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Places
import ru.my.traty.data.Recurring
import ru.my.traty.data.RecurringState
import ru.my.traty.data.entryTimestamp
import java.time.LocalDate
import java.time.ZoneId

/** Регулярные платежи: принять или отклонить предложение, добавить, изменить, убрать. */
class RecurringRepository(private val db: AppDatabase) {
    private val dao get() = db.recurring()

    /** Принять предложение: платёж заводится с датой ближайшего списания. */
    suspend fun accept(candidate: RecurringCandidate, today: LocalDate = LocalDate.now()): Long =
        dao.insert(candidate.toRecurring(today))

    /** «Это не подписка»: запоминаем ключ, чтобы не предлагать то же самое снова. */
    suspend fun dismiss(candidate: RecurringCandidate, today: LocalDate = LocalDate.now()): Long =
        dao.insert(candidate.toRecurring(today, RecurringState.DISMISSED))

    /** Добавить (id == 0) или изменить платёж. У нового ключ автообнаружения берётся из категории и места/названия. */
    suspend fun save(item: Recurring): Long {
        val key = item.detectKey ?: RecurringDetector.keyOf(item.categoryId, item.place, item.title)
        val toSave = item.copy(
            title = item.title.trim(),
            place = Places.normalize(item.place),
            detectKey = key,
        )
        return if (toSave.id == 0L) dao.insert(toSave) else { dao.update(toSave); toSave.id }
    }

    suspend fun setPaused(item: Recurring, paused: Boolean) =
        dao.update(item.copy(state = if (paused) RecurringState.PAUSED else RecurringState.ACTIVE))

    /**
     * «Списалось»: создаёт трату и переносит платёж на следующий период. Трата и сдвиг даты — в одной транзакции.
     * @return false, если платежа нет или он не действует (на паузе, скрыт).
     */
    suspend fun markPaid(id: Long, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Boolean =
        db.withTransaction {
            val item = dao.getById(id)?.takeIf { it.state == RecurringState.ACTIVE } ?: return@withTransaction false
            db.expenses().insert(
                Expense(
                    amountKopecks = item.amountKopecks,
                    currency = item.currency,
                    categoryId = item.categoryId,
                    subcategoryId = item.subcategoryId,
                    place = item.place,
                    note = item.title,
                    timestamp = entryTimestamp(RecurringSchedule.chargeDate(item, today), zone, today),
                    source = ExpenseSource.RECURRING,
                )
            )
            dao.update(item.copy(nextEpochDay = RecurringSchedule.nextAfterPaid(item, today).toEpochDay()))
            true
        }

    /**
     * «Удалить»: строка остаётся со статусом «отклонено», иначе поиск повторов сразу предложил бы
     * этот же платёж снова по старым тратам.
     */
    suspend fun remove(item: Recurring) = dao.update(item.copy(state = RecurringState.DISMISSED))

    companion object {
        /** Категория, где достаточно двух списаний в месяце, чтобы предложить подписку. */
        const val SUBSCRIPTION_CATEGORY = "Связь и подписки"

        /** Предложения по тратам: без уже заведённых и отклонённых платежей. */
        fun suggest(
            expenses: List<ExpenseWithCategory>,
            recurring: List<Recurring>,
            categories: List<CategoryWithSubs>,
            today: LocalDate = LocalDate.now(),
            zone: ZoneId = ZoneId.systemDefault(),
        ): List<RecurringCandidate> = RecurringDetector.detect(
            expenses = expenses,
            today = today,
            zone = zone,
            excludedKeys = recurring.mapNotNull { it.detectKey }.toSet(),
            relaxedCategoryIds = categories
                .filter { it.category.name.equals(SUBSCRIPTION_CATEGORY, ignoreCase = true) }
                .map { it.category.id }
                .toSet(),
        )
    }
}
