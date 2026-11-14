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
import java.time.Instant
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
    suspend fun markPaid(
        id: Long,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        amountKopecks: Long? = null,
    ): Boolean =
        db.withTransaction {
            val item = dao.getById(id)?.takeIf { it.state == RecurringState.ACTIVE } ?: return@withTransaction false
            val paid = amountKopecks?.takeIf { it > 0 } ?: item.amountKopecks
            db.expenses().insert(
                Expense(
                    amountKopecks = paid,
                    currency = item.currency,
                    categoryId = item.categoryId,
                    subcategoryId = item.subcategoryId,
                    place = item.place,
                    note = item.title,
                    timestamp = entryTimestamp(RecurringSchedule.chargeDate(item, today), zone, today),
                    source = ExpenseSource.RECURRING,
                )
            )
            // Новая сумма запоминается: для меняющихся платежей это лучшая оценка следующей
            dao.update(item.copy(amountKopecks = paid, nextEpochDay = RecurringSchedule.nextAfterPaid(item, today).toEpochDay()))
            true
        }

    /**
     * Вызывается при сохранении траты (голос, подтверждение): если трата из «регулярной» категории
     * ([AUTO_ADD_CATEGORIES]) или в названии/месте сказано «подписка» и такого платежа ещё нет, заводит его — раз в месяц, следующее списание через месяц.
     * Если платёж уже есть и эта трата — очередной платёж по нему, дата просто сдвигается (второй траты не создаётся).
     * Платёж, который пользователь удалил или отклонил, заново не заводится.
     * Работает на DAO без своей транзакции, поэтому безопасно вызывать внутри чужой.
     * @return название нового платежа или null, если ничего не создано.
     */
    suspend fun onExpenseSaved(expense: Expense, categoryName: String, zone: ZoneId = ZoneId.systemDefault()): String? {
        val saysSubscription = listOfNotNull(expense.note, expense.place).any { it.contains("подписк", ignoreCase = true) }
        if (!saysSubscription && AUTO_ADD_CATEGORIES.none { it.equals(categoryName, ignoreCase = true) }) return null
        if (expense.source == ExpenseSource.RECURRING || expense.source == ExpenseSource.RECONCILE) return null
        val key = RecurringDetector.keyOf(expense.categoryId, expense.place, expense.note) ?: return null
        val day = Instant.ofEpochMilli(expense.timestamp).atZone(zone).toLocalDate()

        val existing = dao.getAll().firstOrNull { it.detectKey == key }
        if (existing != null) {
            // Очередной платёж по расписанию (за неделю до срока и позже): сдвигаем дату, чтобы напоминание не просило отметить его ещё раз
            if (existing.state == RecurringState.ACTIVE && !day.isBefore(existing.nextDate().minusDays(7))) {
                dao.update(
                    existing.copy(
                        amountKopecks = expense.amountKopecks,
                        nextEpochDay = RecurringSchedule.nextAfterPaid(existing, day).toEpochDay(),
                    )
                )
            }
            return null
        }

        val title = Places.normalize(expense.place)
            ?: expense.note?.trim()?.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercaseChar() }
            ?: return null
        dao.insert(
            Recurring(
                title = title,
                amountKopecks = expense.amountKopecks,
                currency = expense.currency,
                categoryId = expense.categoryId,
                subcategoryId = expense.subcategoryId,
                place = Places.normalize(expense.place),
                periodUnit = PeriodUnit.MONTH.key,
                nextEpochDay = RecurrencePeriod.MONTHLY.advance(day).toEpochDay(),
                autoDetected = true,
                detectKey = key,
                amountVaries = VARIABLE_AMOUNT_CATEGORIES.any { it.equals(categoryName, ignoreCase = true) },
            )
        )
        return title
    }

    /**
     * «Удалить»: строка остаётся со статусом «отклонено», иначе поиск повторов сразу предложил бы
     * этот же платёж снова по старым тратам.
     */
    suspend fun remove(item: Recurring) = dao.update(item.copy(state = RecurringState.DISMISSED))

    companion object {
        /** Категория, где достаточно двух списаний в месяце, чтобы предложить подписку. */
        const val SUBSCRIPTION_CATEGORY = "Связь и подписки"

        /**
         * Категории, где каждая новая трата с названием — скорее всего регулярный платёж (связь, подписки,
         * аренда, коммуналка, кредиты). Такая трата сразу попадает в «Подписки».
         */
        val AUTO_ADD_CATEGORIES = listOf(SUBSCRIPTION_CATEGORY, "Жильё", "Коммунальные услуги", "Долги и кредиты")

        /** Категории, где сумма каждый месяц другая. */
        val VARIABLE_AMOUNT_CATEGORIES = listOf("Коммунальные услуги")

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
