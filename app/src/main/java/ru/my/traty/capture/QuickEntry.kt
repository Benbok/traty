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
import ru.my.traty.ai.clientFor
import ru.my.traty.data.AppDatabase
import ru.my.traty.data.AppSettings
import ru.my.traty.data.CategoryRepository
import ru.my.traty.data.Expense
import ru.my.traty.data.Income
import ru.my.traty.data.PendingPhrase
import ru.my.traty.data.SettingsRepository
import ru.my.traty.data.entryTimestamp
import ru.my.traty.data.normalizeName
import ru.my.traty.data.sameName
import ru.my.traty.data.selectable
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Предложение создать правило «место → категория» после сохранения траты. */
data class RuleOffer(val place: String, val categoryId: Long, val subcategoryId: Long?, val categoryName: String)

data class SaveResult(val saved: SavedEntries, val ruleOffer: RuleOffer?, val newCategories: List<String>)

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
    private val clientProvider: (AppSettings) -> LlmClient? = ::clientFor,
) {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Эмитит после каждого сохранения — виджет обновляет суммы. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    fun emptyDraft(origin: EntryOrigin, rawText: String = ""): EntryDraft =
        EntryDraft(listOf(EntryRow.empty()), rawText, origin)

    /** [spokenAt] — время, когда фраза произнесена: от него считаются «сегодня» и «вчера». */
    suspend fun process(text: String, origin: EntryOrigin, spokenAt: Long = System.currentTimeMillis()): EntryOutcome {
        val spokenDay = Instant.ofEpochMilli(spokenAt).atZone(zone).toLocalDate()
        val cats = db.categories().getActiveWithSubs().selectable()
        val rules = db.rules().getAll()
        val client = clientProvider(settings.current())

        var forceConfirm = false
        var notice: String? = null
        val parsed: List<ParsedEntry> = if (client == null) {
            forceConfirm = true
            OfflineEntryParser.parse(text, cats)
        } else {
            try {
                EntryParsing.parse(client, text, cats, rules, spokenDay)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LlmHttpException) {
                forceConfirm = true
                notice = fallbackNotice(client, e)
                OfflineEntryParser.parse(text, cats)
            } catch (e: IOException) {
                val id = db.pendingPhrases().insert(PendingPhrase(text = text, createdAt = spokenAt))
                return EntryOutcome.Queued(id)
            } catch (e: Exception) {
                forceConfirm = true
                notice = fallbackNotice(client, e)
                OfflineEntryParser.parse(text, cats)
            }
        }

        val ruled = RuleMatcher.apply(parsed, rules, cats)
        ruled.mapNotNull { it.matchedRule }.forEach { db.rules().incrementHits(it.id) }

        val rows = ruled.map { EntryRow.fromParsed(it.entry, cats, spokenDay) }
        val draft = if (rows.isEmpty()) emptyDraft(origin, text) else EntryDraft(rows, text, origin)
        if (forceConfirm || ParsePolicy.decide(ruled) != EntryDecision.AUTO_SAVE) {
            return EntryOutcome.NeedsConfirm(draft, notice)
        }
        return EntryOutcome.AutoSaved(save(draft), draft)
    }

    private fun fallbackNotice(client: LlmClient, e: Exception) =
        "${client.name}: ${e.message ?: e.javaClass.simpleName}. Использован простой разбор."

    /**
     * Сохраняет строки с корректной суммой. Если [EntryDraft.replaces] задан — старые записи удаляются
     * в той же транзакции. Если корректных строк нет — ничего не меняется.
     */
    suspend fun save(draft: EntryDraft): SaveResult {
        val valid = draft.rows.filter { it.kopecks() != null }
        if (valid.isEmpty()) return SaveResult(SavedEntries(emptyList(), emptyList()), null, emptyList())

        val known = db.categories().getAllWithSubs().map { it.category.name }
        val expenseIds = mutableListOf<Long>()
        val incomeIds = mutableListOf<Long>()
        var ruleOffer: RuleOffer? = null
        db.withTransaction {
            draft.replaces?.let { old ->
                old.expenseIds.forEach { db.expenses().deleteById(it) }
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
                    val expense = Expense(
                        amountKopecks = kopecks, categoryId = ids.categoryId, subcategoryId = ids.subcategoryId,
                        place = row.place.trim().ifBlank { null }, note = row.note.trim().ifBlank { null },
                        timestamp = timestamp, rawText = draft.rawText.ifBlank { null },
                        source = draft.origin.toExpenseSource(),
                    )
                    expenseIds += db.expenses().insert(expense)
                    if (ruleOffer == null) {
                        val place = row.place.trim()
                        if (place.isNotBlank() && db.rules().findByPattern(place.lowercase()) == null) {
                            ruleOffer = RuleOffer(place, ids.categoryId, ids.subcategoryId, row.category)
                        }
                    }
                }
            }
        }
        val created = valid.filter { it.type == EntryType.EXPENSE }
            .map { it.category }
            .filter { name -> known.none { sameName(it, name) } }
            .map { normalizeName(it) }
            .distinct()
        _changes.tryEmit(Unit)
        return SaveResult(SavedEntries(expenseIds, incomeIds), ruleOffer, created)
    }

    /** Черновик из уже сохранённых записей — для «Изменить» после автосохранения. */
    suspend fun draftFor(saved: SavedEntries, rawText: String, origin: EntryOrigin): EntryDraft {
        val expenseRows = db.expenses().getByIds(saved.expenseIds).map { e ->
            EntryRow(
                type = EntryType.EXPENSE, amountText = kopecksToText(e.expense.amountKopecks),
                category = e.categoryName, subcategory = e.subcategoryName.orEmpty(),
                place = e.expense.place.orEmpty(), note = e.expense.note.orEmpty(),
                source = "", dateText = dayOf(e.expense.timestamp),
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
