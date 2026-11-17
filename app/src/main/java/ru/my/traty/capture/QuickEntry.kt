package ru.my.traty.capture

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.my.traty.ai.EntryParsing
import ru.my.traty.ai.EntryType
import ru.my.traty.ai.LlmClient
import ru.my.traty.ai.LlmHttpException
import ru.my.traty.ai.OfflineEntryParser
import ru.my.traty.ai.ParsedEntry
import ru.my.traty.ai.PurchaseTerms
import ru.my.traty.ai.PurchaseTermsParser
import ru.my.traty.ai.entryClientFor
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.AppSettings
import ru.my.traty.data.CategoryRepository
import ru.my.traty.data.Expense
import ru.my.traty.data.Income
import ru.my.traty.data.PendingPhrase
import ru.my.traty.data.Places
import ru.my.traty.data.PendingStatus
import ru.my.traty.data.Recurring
import ru.my.traty.data.SettingsRepository
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtPaymentRemoval
import ru.my.traty.data.debt.DebtRepository
import ru.my.traty.data.items.ExpenseItemRepository
import ru.my.traty.data.recurring.RecurringRepository
import ru.my.traty.data.entryTimestamp
import ru.my.traty.data.normalizeName
import ru.my.traty.data.sameName
import ru.my.traty.data.selectable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Сколько трат за дни фразы просматривать в поиске повторов: больше за пару дней не бывает. */
private const val DUPLICATE_SCAN_LIMIT = 1_000

/** Предложение создать правило «место → категория» после сохранения траты. */
data class RuleOffer(val place: String, val categoryId: Long, val subcategoryId: Long?, val categoryName: String)

/**
 * [recurringOffers] — платежи, которые стоит предложить добавить в «Подписки» (ещё не добавлены,
 * их же получает [QuickEntry.recurringOffers]). [newSubcategories] — созданные при сохранении подкатегории, «Питомцы › Зоомагазин».
 */
data class SaveResult(
    val saved: SavedEntries,
    val ruleOffer: RuleOffer?,
    val newCategories: List<String>,
    val recurringOffers: List<Recurring> = emptyList(),
    val newSubcategories: List<String> = emptyList(),
)

sealed interface EntryOutcome {
    /** Всё уверенно — сохранено без подтверждения. [draft] нужен для «Изменить». */
    data class AutoSaved(val result: SaveResult, val draft: EntryDraft) : EntryOutcome

    /** Нужна проверка человеком. [notice] — пояснение (например, «ИИ недоступен, простой разбор»). */
    data class NeedsConfirm(val draft: EntryDraft, val notice: String? = null) : EntryOutcome

    /** Нет сети — фраза в очереди, разберётся позже. */
    data class Queued(val pendingId: Long) : EntryOutcome
}

/**
 * Общая цепочка ввода для главной, панели записи и фонового разбора:
 * текст → разбор (ИИ или простой) → правила → решение → сохранение.
 */
class QuickEntry(
    private val db: AppDatabase,
    private val categories: CategoryRepository,
    private val settings: SettingsRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** Отладочный журнал разбора фразы. null в release: суммы и текст диктовки туда не пишутся. */
    private val debugLog: ((String) -> Unit)? = null,
    private val clientProvider: (AppSettings) -> LlmClient? = ::entryClientFor,
) {
    private val recurring = RecurringRepository(db)

    /** Предложения «добавить в Подписки», ждущие ответа пользователя. */
    val recurringOffers = RecurringOffers(recurring)
    private val debts = DebtRepository(db)
    private val debtRemoval = DebtPaymentRemoval(db)
    private val items = ExpenseItemRepository(db)

    /** Вопросы «Доплата за покупку?» и «Покупка частями?», ждущие ответа пользователя. */
    val purchaseOffers = PurchaseOfferQueue(debts, zone)
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Эмитит после каждого сохранения — виджет обновляет суммы. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    /** Данные поменялись не через ввод (восстановление из копии): виджеты пересчитывают суммы. */
    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }

    fun emptyDraft(origin: EntryOrigin, rawText: String = ""): EntryDraft =
        EntryDraft(listOf(EntryRow.empty()), rawText, origin)

    /** [spokenAt] — время, когда фраза произнесена: от него считаются «сегодня» и «вчера». */
    suspend fun process(text: String, origin: EntryOrigin, spokenAt: Long = System.currentTimeMillis()): EntryOutcome =
        processOrNull(text, origin, spokenAt, allowQueue = true)!!

    /**
     * Разбирает фразы из очереди (после появления сети). Автосохраняет уверенные, остальные помечает PARSED
     * с черновиком для проверки. На первой же сетевой ошибке останавливается: остальные ждут дальше.
     * Возвращает, сколько фраз обработано.
     */
    suspend fun processPending(): Int {
        var done = 0
        for (p in db.pendingPhrases().getByStatus(PendingStatus.WAITING)) {
            when (val outcome = processOrNull(p.text, EntryOrigin.VOICE, p.createdAt, allowQueue = false) ?: break) {
                is EntryOutcome.AutoSaved -> db.pendingPhrases().delete(p.id)
                is EntryOutcome.NeedsConfirm -> db.pendingPhrases().update(
                    p.copy(
                        status = PendingStatus.PARSED,
                        resultJson = Json.encodeToString(outcome.draft),
                        error = outcome.notice,
                    )
                )
                is EntryOutcome.Queued -> break
            }
            done++
        }
        return done
    }

    /** null — сетевая ошибка при выключенной постановке в очередь ([allowQueue] = false). */
    private suspend fun processOrNull(text: String, origin: EntryOrigin, spokenAt: Long, allowQueue: Boolean): EntryOutcome? {
        val spokenDay = Instant.ofEpochMilli(spokenAt).atZone(zone).toLocalDate()
        val cats = db.categories().getActiveWithSubs().selectable()
        val rules = db.rules().getAll()
        val client = clientProvider(settings.current())

        var forceConfirm = false
        var notice: String? = null
        var aiParsed = false
        val parsed: List<ParsedEntry> = if (client == null) {
            forceConfirm = true
            OfflineEntryParser.parse(text, cats, spokenDay)
        } else {
            try {
                val decoded = EntryParsing.parseChecked(client, text, cats, rules, spokenDay)
                aiParsed = true
                if (decoded.dropped > 0) {
                    forceConfirm = true
                    notice = "Часть ответа ИИ не удалось разобрать, проверьте, все ли операции из фразы на месте."
                }
                decoded.items
            } catch (e: CancellationException) {
                throw e
            } catch (e: LlmHttpException) {
                forceConfirm = true
                notice = fallbackNotice(client, e)
                OfflineEntryParser.parse(text, cats, spokenDay)
            } catch (e: IOException) {
                if (!allowQueue) return null
                val id = db.pendingPhrases().insert(PendingPhrase(text = text, createdAt = spokenAt))
                return EntryOutcome.Queued(id)
            } catch (e: Exception) {
                forceConfirm = true
                notice = fallbackNotice(client, e)
                OfflineEntryParser.parse(text, cats, spokenDay)
            }
        }

        debugLog?.invoke("1. фраза: «$text»; разбор ИИ=$aiParsed\n   ответ: ${describe(parsed)}")
        val cleaned0 = withPurchaseTerms(EntryCleanup.apply(parsed, text, cats, spokenDay), text)
        debugLog?.invoke("2. после правил приложения (склейка, список покупок): ${describe(cleaned0)}")
        // Сумму и дату от ИИ сверяем с фразой: не сходится — окно проверки вместо молчаливого сохранения
        val sanity = if (aiParsed) EntrySanity.check(cleaned0, text, spokenDay) else null
        val cleaned = sanity?.entries ?: cleaned0
        if (sanity != null && sanity.problems.isNotEmpty()) {
            forceConfirm = true
            notice = (listOfNotNull(notice) + sanity.problems).joinToString(" ")
        }
        debugLog?.invoke("3. проверка суммы и даты: ${sanity?.problems?.takeIf { it.isNotEmpty() } ?: "замечаний нет"}")
        // Ошибки распознавания речи в названии магазина: берём привычное написание из базы
        val snapped = PlaceSnap.apply(cleaned, db.expenses().distinctPlaces())
        val ruled = RuleMatcher.apply(snapped, rules, cats)
        ruled.mapNotNull { it.matchedRule }.forEach { db.rules().incrementHits(it.id) }

        val rows = markDuplicates(ruled.map { EntryRow.fromParsed(it.entry, cats, spokenDay) }, spokenAt)
        val draft = if (rows.isEmpty()) emptyDraft(origin, text) else EntryDraft(rows, text, origin)
        // Похожая трата уже есть — молча не сохраняем даже по правилу: спросим «повтор или новая?»
        val hasDuplicate = rows.any { it.duplicate != null }
        val needsConfirm = forceConfirm || hasDuplicate || ParsePolicy.decide(ruled) != EntryDecision.AUTO_SAVE
        debugLog?.invoke(
            "4. черновик: ${rows.joinToString(" | ") { r -> "${r.amountText} ₽ ${r.category}›${r.subcategory} ${r.place} товаров=${r.items.size}" }}" +
                "; повтор=$hasDuplicate; окно проверки=$needsConfirm; пояснение=${notice ?: "-"}",
        )
        if (needsConfirm) return EntryOutcome.NeedsConfirm(draft, notice)
        return EntryOutcome.AutoSaved(save(draft), draft)
    }

    /** Короткое описание разобранных трат для отладочного журнала: сумма, место, товары с ценами. */
    private fun describe(entries: List<ParsedEntry>): String = entries.joinToString(" | ") { e ->
        val goods = e.items.orEmpty().joinToString(", ") { "${it.name}=${it.price ?: "?"}" }
        "${e.amount} ₽ ${e.category}›${e.subcategory ?: ""} место=${e.place ?: "-"} заметка=«${e.note ?: ""}»" +
            (if (goods.isNotEmpty()) " товары=[$goods]" else "")
    }

    /** ИИ не заметил «в рассрочку» или «аванс за…» в фразе с одной тратой — условия берутся правилом. */
    private fun withPurchaseTerms(entries: List<ParsedEntry>, text: String): List<ParsedEntry> {
        val single = entries.singleOrNull() ?: return entries
        if (single.entryType != EntryType.EXPENSE || single.part != null) return entries
        return listOf(single.copy(part = PurchaseTermsParser.find(text)))
    }

    /** Помечает строки, похожие на уже записанные траты тех же дней ([DuplicateCheck]). */
    private suspend fun markDuplicates(rows: List<EntryRow>, spokenAt: Long): List<EntryRow> {
        val days = rows.mapNotNull { runCatching { LocalDate.parse(it.dateText.trim()) }.getOrNull() }
        if (days.isEmpty()) return rows
        val from = days.min().atStartOfDay(zone).toInstant().toEpochMilli()
        val to = days.max().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val existing = db.expenses().getRange(from, to, limit = DUPLICATE_SCAN_LIMIT).map {
            ExistingExpense(it.expense.id, it.expense.amountKopecks, it.expense.timestamp, it.expense.place, it.expense.note, it.categoryName)
        } + if (rows.none { it.type == EntryType.INCOME }) emptyList() else db.incomes().getRange(from, to).map {
            ExistingExpense(it.id, it.amountKopecks, it.timestamp, null, it.note, it.source, income = true)
        }
        if (existing.isEmpty()) return rows
        val hints = DuplicateCheck.find(rows, existing, zone, spokenAt)
        return rows.zip(hints) { row, hint -> row.copy(duplicate = hint) }
    }

    private fun fallbackNotice(client: LlmClient, e: Exception) =
        "${client.name}: ${e.message ?: e.javaClass.simpleName}. Использован простой разбор."

    /**
     * Сохраняет строки с корректной суммой. Если [EntryDraft.replaces] задан — старые записи удаляются
     * в той же транзакции. Если корректных строк нет — ничего не меняется.
     */
    suspend fun save(draft: EntryDraft): SaveResult {
        val valid = draft.rows.filter { it.kopecks() != null }
        // «взял ноутбук в кредит за 80 тысяч»: сегодня ничего не платили, трата не создаётся, но покупку предложим завести
        val unpaidPurchases = draft.rows.filter { it.type == EntryType.EXPENSE && it.kopecks() == null && (it.purchase?.total ?: 0.0) > 0 }
        if (valid.isEmpty() && unpaidPurchases.isEmpty()) return SaveResult(SavedEntries(emptyList(), emptyList()), null, emptyList())

        val known = db.categories().getAllWithSubs().map { it.category.name }
        val expenseIds = mutableListOf<Long>()
        val incomeIds = mutableListOf<Long>()
        val offers = mutableListOf<Recurring>()
        val newSubcategories = mutableListOf<String>()
        val purchases = mutableListOf<PurchaseOffer>()
        var ruleOffer: RuleOffer? = null
        db.withTransaction {
            val knownPlaces = db.expenses().distinctPlaces().toMutableList()
            debtRemoval.dropOrphans(zone)
            val openDebts = debts.getActive()
            draft.replaces?.let { old ->
                // Через удаление с учётом покупок: старая трата-аванс не оставит «пустую» покупку
                db.expenses().getByIds(old.expenseIds).forEach { debtRemoval.delete(it.expense, zone) }
                old.incomeIds.forEach { db.incomes().deleteById(it) }
            }
            for (row in valid) {
                val kopecks = row.kopecks()!!
                val day = runCatching { LocalDate.parse(row.dateText.trim()) }.getOrNull() ?: LocalDate.now()
                val timestamp = entryTimestamp(day, zone)
                if (row.type == EntryType.INCOME) {
                    incomeIds += db.incomes().insert(
                        Income(
                            amountKopecks = kopecks, source = row.source,
                            note = row.note.trim().ifBlank { null }, timestamp = timestamp,
                            origin = draft.origin.toIncomeOrigin(),
                        )
                    )
                } else {
                    val ids = categories.resolveForSave(
                        categoryName = row.category,
                        newCategoryEmoji = row.suggestedCategory?.takeIf { it.name == row.category }?.emoji,
                        subcategoryName = row.subcategory.ifBlank { null },
                    )
                    ids.createdSubcategory?.let { newSubcategories += "${normalizeName(row.category)} › $it" }
                    // «пятерочка» → «Пятёрочка», если так магазин уже записан: в статистике это один магазин
                    val place = Places.canonical(row.place, knownPlaces)?.also { if (it !in knownPlaces) knownPlaces += it }
                    val expense = Expense(
                        amountKopecks = kopecks, categoryId = ids.categoryId, subcategoryId = ids.subcategoryId,
                        place = place, note = row.note.trim().ifBlank { null },
                        timestamp = timestamp, rawText = draft.rawText.ifBlank { null },
                        source = draft.origin.toExpenseSource(),
                    )
                    val expenseId = db.expenses().insert(expense)
                    expenseIds += expenseId
                    if (row.items.isNotEmpty()) items.replace(expenseId, row.items.map(DraftItem::toExpenseItem))
                    val purchase = purchaseOffer(row, expense.copy(id = expenseId), day, openDebts)
                    // Платёж по рассрочке или кредиту — не подписка: у покупки свой график
                    if (purchase != null) purchases += purchase
                    else recurring.onExpenseSaved(expense.copy(id = expenseId), row.category, zone, row.subcategory)?.let { offers += it }
                    if (ruleOffer == null && place != null) {
                        if (db.rules().findByPattern(place.lowercase()) == null) {
                            ruleOffer = RuleOffer(place, ids.categoryId, ids.subcategoryId, row.category)
                        }
                    }
                }
            }
            for (row in unpaidPurchases) {
                val ids = categories.resolveForSave(
                    categoryName = row.category,
                    newCategoryEmoji = row.suggestedCategory?.takeIf { it.name == row.category }?.emoji,
                    subcategoryName = row.subcategory.ifBlank { null },
                )
                val day = runCatching { LocalDate.parse(row.dateText.trim()) }.getOrNull() ?: LocalDate.now()
                val place = Places.canonical(row.place, knownPlaces)
                val unpaid = Expense(
                    amountKopecks = 0, categoryId = ids.categoryId, subcategoryId = ids.subcategoryId,
                    place = place, note = row.note.trim().ifBlank { null }, timestamp = entryTimestamp(day, zone),
                )
                purchaseOffer(row, unpaid, day, openDebts)?.let { purchases += it }
            }
        }
        purchaseOffers.add(purchases)
        val created = valid.filter { it.type == EntryType.EXPENSE }
            .map { it.category }
            .filter { name -> known.none { sameName(it, name) } }
            .map { normalizeName(it) }
            .distinct()
        val uniqueOffers = offers.distinctBy { it.detectKey ?: it.title }
        recurringOffers.add(uniqueOffers)
        _changes.tryEmit(Unit)
        return SaveResult(SavedEntries(expenseIds, incomeIds), ruleOffer, created, uniqueOffers, newSubcategories.distinct())
    }

    /** Вопрос про покупку частями для сохранённой траты ([expense] с id == 0 — сегодня ничего не платили). */
    private fun purchaseOffer(row: EntryRow, expense: Expense, day: LocalDate, open: List<Debt>): PurchaseOffer? {
        val paid = expense.id != 0L
        val decision = PurchaseOffers.decide(row.purchase, expense.note, expense.place, open, hasPayment = paid)
        if (decision == PurchaseDecision.None) return null
        return PurchaseOffer(
            expenseId = expense.id.takeIf { paid },
            amountKopecks = if (paid) expense.amountKopecks else 0,
            title = (decision as? PurchaseDecision.Attach)?.debt?.title
                ?: PurchaseOffers.titleOf(expense.note, row.subcategory, expense.place),
            place = expense.place,
            categoryId = expense.categoryId,
            subcategoryId = expense.subcategoryId,
            day = day,
            terms = row.purchase ?: PurchaseTerms(kind = PurchaseTerms.TOPUP),
            match = (decision as? PurchaseDecision.Attach)?.debt,
        )
    }

    /** Черновик из уже сохранённых записей — для «Изменить» после автосохранения. */
    suspend fun draftFor(saved: SavedEntries, rawText: String, origin: EntryOrigin): EntryDraft {
        val savedItems = items.getForAll(saved.expenseIds)
        val expenseRows = db.expenses().getByIds(saved.expenseIds).map { e ->
            EntryRow(
                type = EntryType.EXPENSE, amountText = kopecksToText(e.expense.amountKopecks),
                category = e.categoryName, subcategory = e.subcategoryName.orEmpty(),
                place = e.expense.place.orEmpty(), note = e.expense.note.orEmpty(),
                source = "", dateText = dayOf(e.expense.timestamp),
                items = savedItems[e.expense.id].orEmpty().map(DraftItem::fromSaved),
            )
        }
        val incomeRows = db.incomes().getByIds(saved.incomeIds).map { i ->
            EntryRow(
                type = EntryType.INCOME, amountText = kopecksToText(i.amountKopecks), category = "", subcategory = "",
                place = "", note = i.note.orEmpty(), source = i.source, dateText = dayOf(i.timestamp),
            )
        }
        return EntryDraft(expenseRows + incomeRows, rawText, origin, replaces = saved)
    }

    private fun dayOf(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()
}
