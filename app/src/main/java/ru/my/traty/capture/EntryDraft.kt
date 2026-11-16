package ru.my.traty.capture

import kotlinx.serialization.Serializable
import ru.my.traty.ai.EntryType
import ru.my.traty.ai.ParsedEntry
import ru.my.traty.ai.PurchaseTerms
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.DefaultCategories
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.IncomeOrigin
import ru.my.traty.data.IncomeSources
import ru.my.traty.data.Places
import ru.my.traty.data.normalizeName
import ru.my.traty.data.sameName
import java.time.LocalDate
import kotlin.math.roundToLong

/** Новая категория, предложенная моделью (её ещё нет в базе). */
@Serializable
data class NewCategory(val name: String, val emoji: String)

enum class EntryOrigin {
    VOICE, TEXT, MANUAL;

    fun toExpenseSource(): String = when (this) {
        VOICE -> ExpenseSource.VOICE
        TEXT -> ExpenseSource.TEXT
        MANUAL -> ExpenseSource.MANUAL
    }

    fun toIncomeOrigin(): String = when (this) {
        VOICE -> IncomeOrigin.VOICE
        TEXT -> IncomeOrigin.TEXT
        MANUAL -> IncomeOrigin.MANUAL
    }
}

/** Строка черновика: трата или пополнение. Все поля текстом — так их правят на экране подтверждения. */
@Serializable
data class EntryRow(
    val type: EntryType,
    val amountText: String,
    val category: String,
    val subcategory: String,
    val place: String,
    val note: String,
    /** Источник пополнения (для [EntryType.INCOME]). */
    val source: String,
    val dateText: String,
    /** Создаётся при сохранении, если пользователь оставил её выбранной. */
    val suggestedCategory: NewCategory? = null,
    /** Похожая трата уже записана: в окне проверки спрашиваем «повтор или новая?». */
    val duplicate: DuplicateHint? = null,
    /** Покупка частями из фразы: после сохранения приложение спросит, завести ли её или привязать доплату. */
    val purchase: PurchaseTerms? = null,
    /** Товары внутри покупки: сохраняются позициями траты (только для [EntryType.EXPENSE]). */
    val items: List<DraftItem> = emptyList(),
) {
    /** Сумма в копейках или null, если сумма некорректна или не положительна. */
    fun kopecks(): Long? = parsePositiveKopecks(amountText)

    companion object {
        fun empty(today: LocalDate = LocalDate.now()) = EntryRow(
            type = EntryType.EXPENSE, amountText = "", category = DefaultCategories.OTHER, subcategory = "",
            place = "", note = "", source = IncomeSources.DEFAULT, dateText = today.toString(),
        )

        /** Неизвестный тип считается тратой — но такую фразу политика всё равно отправит на подтверждение. */
        fun fromParsed(entry: ParsedEntry, cats: List<CategoryWithSubs>, spokenDay: LocalDate): EntryRow {
            // Через копейки: сумма из Double (склейка трат, ответ ИИ) может быть 344.70000000000005
            val amountStr = kopecksToText((entry.amount * 100).roundToLong())
            val day = entry.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: spokenDay
            if (entry.entryType == EntryType.INCOME) {
                return EntryRow(
                    type = EntryType.INCOME, amountText = amountStr, category = "", subcategory = "", place = "",
                    note = entry.note.orEmpty(), source = IncomeSources.normalize(entry.source), dateText = day.toString(),
                )
            }
            val name = normalizeName(entry.category)
            val known = cats.firstOrNull { sameName(it.category.name, name) }?.category?.name
            // Категории нет в списке — предлагаем создать новую (создастся при сохранении)
            val suggested = if (known == null && name.isNotBlank()) {
                NewCategory(name, entry.emoji?.trim()?.takeIf { it.isNotEmpty() && it.length <= 8 } ?: DefaultCategories.NEW_EMOJI)
            } else null
            return EntryRow(
                type = EntryType.EXPENSE, amountText = amountStr,
                category = known ?: suggested?.name ?: DefaultCategories.OTHER,
                subcategory = entry.subcategory.orEmpty(), place = Places.normalize(entry.place).orEmpty(), note = entry.note.orEmpty(),
                source = IncomeSources.DEFAULT, dateText = day.toString(), suggestedCategory = suggested,
                purchase = entry.part,
                items = entry.items.orEmpty().map(DraftItem::fromParsed).filter { it.name.isNotBlank() },
            )
        }
    }
}

/** Что уже сохранено из этой фразы: нужно для «Изменить» (замена в одной транзакции). */
@Serializable
data class SavedEntries(val expenseIds: List<Long>, val incomeIds: List<Long>) {
    val count: Int get() = expenseIds.size + incomeIds.size
}

/** Черновик всей фразы. [replaces] != null — при сохранении старые записи заменяются новыми. */
@Serializable
data class EntryDraft(
    val rows: List<EntryRow>,
    val rawText: String,
    val origin: EntryOrigin,
    val replaces: SavedEntries? = null,
)

// Неразрывный пробел (U+00A0) и узкий неразрывный пробел (U+202F) — из кодов, чтобы не прятать их в исходнике
private val NBSP: Char = 0x00A0.toChar()
private val NNBSP: Char = 0x202F.toChar()
private val spaceLike = Regex("[\\s$NBSP$NNBSP]")

internal fun parsePositiveKopecks(text: String): Long? {
    val amount = text.replace(spaceLike, "").replace(',', '.').toDoubleOrNull() ?: return null
    if (!amount.isFinite() || amount <= 0) return null
    return (amount * 100).roundToLong().takeIf { it > 0 }
}

/** 25000 → «250», 25050 → «250.5»: текст для поля суммы. */
internal fun kopecksToText(kopecks: Long): String =
    java.math.BigDecimal(kopecks).movePointLeft(2).stripTrailingZeros().toPlainString()
