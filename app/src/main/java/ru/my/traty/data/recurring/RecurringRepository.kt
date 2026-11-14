package ru.my.traty.data.recurring

import ru.my.traty.data.AppDatabase
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Places
import ru.my.traty.data.Recurring
import ru.my.traty.data.RecurringState
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
