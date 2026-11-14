package ru.my.traty.assistant

import ru.my.traty.data.CategoryTotal
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.ExpenseSource
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.Income
import ru.my.traty.data.IncomeOrigin
import ru.my.traty.data.PeriodComparison
import ru.my.traty.data.Recurring
import ru.my.traty.data.RecurringState
import ru.my.traty.data.budget.Budget
import ru.my.traty.data.budget.BudgetPlans
import ru.my.traty.data.budget.PlanKey
import ru.my.traty.data.budget.PlanMonth
import ru.my.traty.data.budget.PlanProgress
import ru.my.traty.data.budget.PlanProgresses
import ru.my.traty.data.budget.PlanSpend
import ru.my.traty.data.budget.PlanStatus
import ru.my.traty.data.period.BillingPeriod
import ru.my.traty.data.period.SpendingPaces
import ru.my.traty.data.recurring.RecurringSummary
import ru.my.traty.data.recurring.nextDate
import ru.my.traty.data.recurring.period
import ru.my.traty.data.recurring.yearlyKopecks
import ru.my.traty.ui.formatRub
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Сколько денег на счёте по последней сверке и записям после неё. */
data class Balance(val kopecks: Long, val reconciledOn: LocalDate)

/** Всё, из чего приложение считает ответ. Хранится только на телефоне. */
class AssistantContext(
    val expenses: List<ExpenseWithCategory>,
    val incomes: List<Income>,
    val recurring: List<Recurring>,
    val today: LocalDate,
    val startDay: Int,
    val zone: ZoneId = ZoneId.systemDefault(),
    /** Строки плана трат (все месяцы); действующие на период выбираются здесь. */
    val budgets: List<Budget> = emptyList(),
    /** Категории — для названий в плане трат. */
    val categories: List<CategoryWithSubs> = emptyList(),
    /** null — сверок не было, остаток на счёте неизвестен. */
    val balance: Balance? = null,
)

/** План одной категории (подкатегории) с прогрессом. */
private data class NamedPlan(val title: String, val categoryName: String, val subName: String?, val progress: PlanProgress)

/**
 * Результат расчёта.
 * [facts] уходят в ИИ, чтобы он сформулировал ответ: только посчитанные приложением цифры.
 * [headline] и [caption] — крупная цифра и подпись под ответом; [expenseIds] — для «Показать траты».
 * [direct] — готовый ответ без второго обращения к ИИ (когда вопрос не про деньги).
 */
data class AssistantData(
    val facts: String,
    val headline: String? = null,
    val caption: String? = null,
    val expenseIds: List<Long> = emptyList(),
    val direct: String? = null,
    /** Предложения в «План трат»: применяются только по кнопке пользователя. */
    val planProposals: List<PlanProposal> = emptyList(),
    /** Месяц плана (ГГГГММ), в который добавятся предложения. */
    val planMonth: Int? = null,
)

/** Предложенная строка плана трат. Сумму считает приложение, ИИ её не меняет. */
data class PlanProposal(
    val categoryId: Long,
    val subcategoryId: Long?,
    val title: String,
    val emoji: String,
    val amountKopecks: Long,
    /** Действующий сейчас план по этой строке; null — плана нет. */
    val currentKopecks: Long?,
)

/** Считает ответ на [AssistantQuery] по данным на телефоне. Чистая логика без Android. */
object AssistantEngine {
    const val DEFAULT_TOP = 5
    const val MAX_TOP = 20
    const val MAX_SHOWN_IDS = 300

    private val RU: Locale = Locale.forLanguageTag("ru")
    private val dayFmt = DateTimeFormatter.ofPattern("d MMMM", RU)
    private val dayYearFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", RU)

    const val NOT_UNDERSTOOD = "Я отвечаю на вопросы о ваших тратах, доходах и подписках. " +
        "Например: «Сколько я потратил на кафе в этом месяце?» или «Могу ли я потратить 10 000 ₽ до конца месяца?»"

    fun run(q: AssistantQuery, ctx: AssistantContext): AssistantData {
        if (q.intent == Intents.UNKNOWN) return AssistantData(facts = "", direct = NOT_UNDERSTOOD)
        val spending = ctx.expenses.filter { it.expense.source != ExpenseSource.RECONCILE }
        val period = BillingPeriod.containing(ctx.today, ctx.startDay)
        val (from, to) = range(q.from, q.to, period.startDate, ctx.today)

        val filter = Filter.of(q, spending, ctx.categories)
        if (filter.unknownNames.isNotEmpty() && filter.names.isEmpty()) {
            val known = spending.map { it.categoryName }.distinct().sorted().joinToString(", ")
            return AssistantData(
                facts = header(ctx) + "Категорий «${filter.unknownNames.joinToString("», «")}» в записях нет. Есть категории: $known.",
            )
        }

        fun rowsIn(a: LocalDate, b: LocalDate) = spending.filter { r ->
            val d = day(r.expense.timestamp, ctx.zone)
            !d.isBefore(a) && !d.isAfter(b) && filter.matches(r)
        }

        val rows = rowsIn(from, to)
        val span = span(from, to)
        val subject = filter.describe()
        val base = header(ctx) + (if (filter.unknownNames.isNotEmpty()) "Не найдены в записях: ${filter.unknownNames.joinToString(", ")}.\n" else "")

        return when (q.intent) {
            Intents.TOTAL -> {
                val sum = rows.sumOf { it.expense.amountKopecks }
                val facts = buildString {
                    append(base)
                    appendLine("Потрачено за $span${subject?.let { " ($it)" }.orEmpty()}: ${formatRub(sum)}, покупок: ${rows.size}.")
                    if (filter.isEmpty && rows.isNotEmpty()) {
                        appendLine("Больше всего:")
                        byCategory(rows).take(5).forEach { (name, s) -> appendLine("- $name: ${formatRub(s)} (${share(s, sum)})") }
                    }
                }
                AssistantData(facts, formatRub(sum), listOfNotNull(subject, span).joinToString(" · "), ids(rows))
            }

            Intents.TOP -> {
                val n = (q.limit ?: DEFAULT_TOP).coerceIn(1, MAX_TOP)
                val top = rows.sortedByDescending { it.expense.amountKopecks }.take(n)
                val facts = buildString {
                    append(base)
                    appendLine("Самые крупные траты за $span${subject?.let { " ($it)" }.orEmpty()}:")
                    if (top.isEmpty()) appendLine("трат нет")
                    top.forEach { appendLine("- ${describe(it, ctx.zone)}") }
                }
                AssistantData(facts, top.firstOrNull()?.let { formatRub(it.expense.amountKopecks) }, "Самая крупная · $span", top.map { it.expense.id })
            }

            Intents.BREAKDOWN -> {
                val sum = rows.sumOf { it.expense.amountKopecks }
                // По одной категории разбиваем на подкатегории, иначе по категориям
                val bySub = filter.names.size == 1
                val groups = if (bySub) rows.groupBy { it.subcategoryName ?: "без подкатегории" }
                    .map { (k, v) -> k to v.sumOf { it.expense.amountKopecks } }.sortedByDescending { it.second }
                else byCategory(rows)
                val facts = buildString {
                    append(base)
                    appendLine("Траты за $span${subject?.let { " ($it)" }.orEmpty()} по ${if (bySub) "подкатегориям" else "категориям"}, всего ${formatRub(sum)}:")
                    groups.take(10).forEach { (name, s) -> appendLine("- $name: ${formatRub(s)} (${share(s, sum)})") }
                }
                AssistantData(facts, formatRub(sum), listOfNotNull(subject, span).joinToString(" · "), ids(rows))
            }

            Intents.PLACES -> {
                val withPlace = rows.filter { !it.expense.place.isNullOrBlank() }
                val groups = withPlace.groupBy { it.expense.place!!.trim().lowercase(RU) }
                    .map { (_, v) -> v.first().expense.place!!.trim() to v }
                    .sortedByDescending { (_, v) -> v.sumOf { it.expense.amountKopecks } }
                val facts = buildString {
                    append(base)
                    appendLine("Места, где больше всего потрачено за $span${subject?.let { " ($it)" }.orEmpty()}:")
                    if (groups.isEmpty()) appendLine("у трат за этот срок не записаны места")
                    groups.take(8).forEach { (name, v) ->
                        appendLine("- $name: ${formatRub(v.sumOf { it.expense.amountKopecks })}, покупок: ${v.size}")
                    }
                    val noPlace = rows.size - withPlace.size
                    if (noPlace > 0) appendLine("Трат без места: $noPlace.")
                }
                val top = groups.firstOrNull()
                AssistantData(
                    facts,
                    top?.let { (_, v) -> formatRub(v.sumOf { it.expense.amountKopecks }) },
                    top?.let { "${it.first} · $span" },
                    top?.second?.let { ids(it) }.orEmpty(),
                )
            }

            Intents.COMPARE -> {
                val (cFrom, cTo) = compareRange(q, from, to, period, ctx)
                val other = rowsIn(cFrom, cTo)
                val cur = rows.sumOf { it.expense.amountKopecks }
                val prev = other.sumOf { it.expense.amountKopecks }
                val diff = cur - prev
                val changes = PeriodComparison.changes(totals(rows), totals(other)).take(5)
                val facts = buildString {
                    append(base)
                    appendLine("Сравнение${subject?.let { " ($it)" }.orEmpty()}:")
                    appendLine("- $span: ${formatRub(cur)}")
                    appendLine("- ${span(cFrom, cTo)}: ${formatRub(prev)}")
                    appendLine("Разница: ${signed(diff)}${percent(diff, prev)?.let { " ($it)" }.orEmpty()}.")
                    if (filter.isEmpty && changes.isNotEmpty()) {
                        appendLine("Что изменилось сильнее всего:")
                        changes.forEach { c -> appendLine("- ${c.name}: ${formatRub(c.current)} против ${formatRub(c.previous)} (${signed(c.diff)})") }
                    }
                }
                AssistantData(facts, signed(diff), "$span против ${span(cFrom, cTo)}", ids(rows))
            }

            Intents.AVERAGE -> {
                val days = (ChronoUnit.DAYS.between(from, to) + 1).toInt()
                val sum = rows.sumOf { it.expense.amountKopecks }
                val perDay = sum / days
                val facts = buildString {
                    append(base)
                    appendLine("За $span${subject?.let { " ($it)" }.orEmpty()} ($days ${daysWord(days)}): ${formatRub(sum)}.")
                    appendLine("В среднем в день: ${formatRub(perDay)}, в неделю: ${formatRub(perDay * 7)}.")
                    if (rows.isNotEmpty()) appendLine("Средняя покупка: ${formatRub(sum / rows.size)}.")
                }
                AssistantData(facts, formatRub(perDay), "в среднем в день · $span", ids(rows))
            }

            Intents.SUBSCRIPTIONS -> {
                val active = ctx.recurring.filter { it.state == RecurringState.ACTIVE }.sortedBy { it.nextEpochDay }
                val totals = RecurringSummary.totals(ctx.recurring)
                val facts = buildString {
                    append(header(ctx))
                    if (active.isEmpty()) appendLine("Активных подписок и регулярных платежей нет.")
                    else {
                        appendLine("Подписки и регулярные платежи (${active.size}):")
                        active.forEach { r ->
                            val amount = (if (r.amountVaries) "около " else "") + formatRub(r.amountKopecks)
                            appendLine("- ${r.title}: $amount, ${r.period().label()}, следующее списание ${date(r.nextDate())}")
                        }
                        appendLine("В месяц: ${formatRub(totals.monthlyKopecks)}, в год: ${formatRub(totals.yearlyKopecks)}${if (totals.approximate) " (примерно)" else ""}.")
                    }
                }
                AssistantData(facts, formatRub(totals.monthlyKopecks), "подписки в месяц")
            }

            Intents.INCOME -> {
                val inc = incomesIn(ctx, from, to)
                val got = inc.sumOf { it.amountKopecks }
                val spent = rows.sumOf { it.expense.amountKopecks }
                val facts = buildString {
                    append(base)
                    appendLine("Поступления за $span: ${formatRub(got)}.")
                    inc.groupBy { it.source }.map { (k, v) -> k to v.sumOf { it.amountKopecks } }
                        .sortedByDescending { it.second }.forEach { (k, s) -> appendLine("- $k: ${formatRub(s)}") }
                    appendLine("Траты за тот же срок: ${formatRub(spent)}.")
                    appendLine("Разница поступлений и трат: ${signed(got - spent)}.")
                }
                AssistantData(facts, formatRub(got), "поступления · $span")
            }

            Intents.AFFORD -> afford(q, ctx, spending, period, filter.names)

            Intents.PLAN -> plan(ctx, spending, period, filter.names)

            Intents.SUGGEST_PLAN -> suggestPlan(ctx, spending, period, filter.names)

            Intents.SET_PLAN -> setPlan(q, ctx, period, filter.names)

            Intents.BALANCE -> {
                val b = ctx.balance
                val facts = buildString {
                    append(header(ctx))
                    if (b == null) {
                        appendLine("Сверок баланса ещё не было, поэтому остаток на счёте приложению неизвестен. Сверка делается на Главной или в Настройках.")
                    } else {
                        appendLine("На счёте сейчас примерно ${formatRub(b.kopecks)}: по последней сверке ${date(b.reconciledOn)} и записям трат и поступлений после неё.")
                        val left = period.endDate.toEpochDay() - ctx.today.toEpochDay() + 1
                        if (left > 0 && b.kopecks > 0) appendLine("До конца периода дней: $left, это примерно ${formatRub(b.kopecks / left)} в день.")
                    }
                }
                AssistantData(facts, b?.let { formatRub(it.kopecks) }, b?.let { "на счёте, по сверке ${date(it.reconciledOn)}" })
            }

            Intents.ADVICE -> {
                val previous = period.previous(ctx.startDay)
                val (wFrom, wTo) = PeriodComparison.comparableWindow(period, previous, ctx.today)
                val cur = spending.filter { day(it.expense.timestamp, ctx.zone).let { d -> d in period && !d.isAfter(ctx.today) } }
                val prev = spending.filter { day(it.expense.timestamp, ctx.zone).let { d -> !d.isBefore(wFrom) && !d.isAfter(wTo) } }
                val sum = cur.sumOf { it.expense.amountKopecks }
                val subs = RecurringSummary.totals(ctx.recurring)
                val facts = buildString {
                    append(header(ctx))
                    appendLine("Текущий период: ${span(period.startDate, ctx.today)}, потрачено ${formatRub(sum)}.")
                    appendLine("За тот же срок прошлого периода (${span(wFrom, wTo)}): ${formatRub(prev.sumOf { it.expense.amountKopecks })}.")
                    appendLine("Категории сейчас:")
                    byCategory(cur).take(8).forEach { (name, s) -> appendLine("- $name: ${formatRub(s)} (${share(s, sum)})") }
                    val changes = PeriodComparison.changes(totals(cur), totals(prev)).filter { it.diff > 0 }.take(3)
                    if (changes.isNotEmpty()) {
                        appendLine("Выросло сильнее всего:")
                        changes.forEach { c -> appendLine("- ${c.name}: ${signed(c.diff)}") }
                    }
                    if (subs.count > 0) {
                        appendLine("Подписки: ${subs.count} шт., ${formatRub(subs.monthlyKopecks)} в месяц, ${formatRub(subs.yearlyKopecks)} в год.")
                        ctx.recurring.filter { it.state == RecurringState.ACTIVE }
                            .sortedByDescending { it.period().monthlyKopecks(it.amountKopecks) }.take(3)
                            .forEach { r -> appendLine("- ${r.title}: ${formatRub(r.period().monthlyKopecks(r.amountKopecks))} в месяц, отмена сэкономит ${formatRub(r.yearlyKopecks)} в год") }
                    }
                    val troubled = plans(ctx, spending, period).filter { it.progress.status == PlanStatus.OVER || it.progress.status == PlanStatus.AT_RISK }
                    if (troubled.isNotEmpty()) {
                        appendLine("План трат под угрозой:")
                        troubled.forEach { appendLine("- ${planLine(it)}") }
                    }
                }
                AssistantData(facts, caption = "текущий период · ${span(period.startDate, ctx.today)}")
            }

            else -> AssistantData(facts = "", direct = NOT_UNDERSTOOD)
        }
    }

    /**
     * «Могу ли я позволить себе X». Опираемся на то, что известно, в таком порядке:
     * остаток на счёте по сверке → поступления периода → прошлый период. Плюс план трат, если он задан.
     * Остаток к концу периода = деньги сейчас − ещё ожидаемые траты по темпу − предстоящие платежи.
     */
    private fun afford(
        q: AssistantQuery,
        ctx: AssistantContext,
        spending: List<ExpenseWithCategory>,
        period: BillingPeriod,
        categoryNames: Set<String>,
    ): AssistantData {
        val wanted = ((q.amount ?: 0.0) * 100).roundToLong().coerceAtLeast(0)
        val spent = spending.filter { day(it.expense.timestamp, ctx.zone).let { d -> d in period && !d.isAfter(ctx.today) } }
            .sumOf { it.expense.amountKopecks }
        val pace = SpendingPaces.of(period, ctx.today, spent)
        val forecast = pace?.forecast ?: spent
        val stillExpected = (forecast - spent).coerceAtLeast(0)
        val upcoming = upcomingPayments(ctx, period)
        val upcomingSum = upcoming.sumOf { it.amountKopecks }
        val income = incomesIn(ctx, period.startDate, period.endDate).sumOf { it.amountKopecks }
        val previous = period.previous(ctx.startDay)
        val prevTotal = spending.filter { day(it.expense.timestamp, ctx.zone) in previous }.sumOf { it.expense.amountKopecks }
        val plans = plans(ctx, spending, period)

        var headline: String? = null
        var caption: String? = null
        val facts = buildString {
            append(header(ctx))
            appendLine("Пользователь хочет потратить: ${formatRub(wanted)}${categoryNames.takeIf { it.isNotEmpty() }?.let { " (категория: ${it.joinToString()})" }.orEmpty()}.")
            appendLine("Текущий период: ${span(period.startDate, period.endDate)}, осталось дней: ${pace?.daysLeft ?: 0}.")
            appendLine("Уже потрачено: ${formatRub(spent)}. При нынешнем темпе до конца периода уйдёт ещё примерно ${formatRub(stillExpected)}.")
            if (upcoming.isNotEmpty()) {
                appendLine("Ещё спишутся регулярные платежи на ${formatRub(upcomingSum)}: ${upcoming.joinToString(", ") { "${it.title} ${date(it.nextDate())}" }}.")
            }
            val b = ctx.balance
            when {
                b != null -> {
                    val free = b.kopecks - stillExpected - upcomingSum
                    appendLine("На счёте сейчас примерно ${formatRub(b.kopecks)} (сверка ${date(b.reconciledOn)} и записи после неё).")
                    appendLine("Оценка остатка к концу периода без покупки: ${signed(free)}; с покупкой: ${signed(free - wanted)}.")
                    headline = signed(free - wanted)
                    caption = "останется к концу периода с покупкой"
                }
                income > 0 -> {
                    val free = income - forecast - upcomingSum
                    appendLine("Поступления за период: ${formatRub(income)}; сверок баланса не было, поэтому считаем от поступлений.")
                    appendLine("Оценка остатка к концу периода без покупки: ${signed(free)}; с покупкой: ${signed(free - wanted)}.")
                    headline = signed(free - wanted)
                    caption = "останется от поступлений периода с покупкой"
                }
                else -> appendLine("Ни сверок баланса, ни поступлений за период нет, поэтому сколько денег есть, приложению неизвестно.")
            }
            if (prevTotal > 0) {
                appendLine("В прошлом периоде потрачено всего ${formatRub(prevTotal)}; с покупкой текущий выйдет примерно на ${formatRub(forecast + upcomingSum + wanted)}.")
            }
            // План трат: нужная категория, иначе общий итог плана
            val relevant = if (categoryNames.isNotEmpty()) plans.filter { p ->
                p.categoryName.lowercase(RU) in categoryNames || p.subName?.lowercase(RU) in categoryNames
            } else emptyList()
            if (relevant.isNotEmpty()) {
                relevant.forEach { p ->
                    appendLine("План «${p.title}»: ${formatRub(p.progress.planned)}, потрачено ${formatRub(p.progress.spent)}, после покупки останется ${signed(p.progress.remaining - wanted)}.")
                }
                if (headline == null) {
                    val p = relevant.first()
                    headline = signed(p.progress.remaining - wanted)
                    caption = "останется по плану «${p.title}» с покупкой"
                }
            } else if (plans.isNotEmpty()) {
                val planned = plans.filter { it.subName == null }.sumOf { it.progress.planned }
                val inPlan = plans.filter { it.subName == null }.sumOf { it.progress.spent }
                if (planned > 0) appendLine("Общий план трат по категориям: ${formatRub(planned)}, по ним потрачено ${formatRub(inPlan)}.")
            }
            appendLine("Это оценка по линейному темпу, не точный прогноз.")
        }
        return AssistantData(facts, headline, caption)
    }

    /** «Как я иду по плану», «сколько осталось на кафе». */
    private fun plan(ctx: AssistantContext, spending: List<ExpenseWithCategory>, period: BillingPeriod, names: Set<String>): AssistantData {
        val all = plans(ctx, spending, period)
        val shown = if (names.isEmpty()) all else all.filter { it.categoryName.lowercase(RU) in names || it.subName?.lowercase(RU) in names }
        val facts = buildString {
            append(header(ctx))
            appendLine("План трат на период ${span(period.startDate, period.endDate)}:")
            when {
                all.isEmpty() -> appendLine("план трат на этот период не задан (задаётся в Настройках → План трат)")
                shown.isEmpty() -> appendLine("для этой категории плана нет; планы есть для: ${all.joinToString { it.title }}")
                else -> shown.forEach { appendLine("- ${planLine(it)}") }
            }
        }
        val main = shown.firstOrNull()
        val headline = when {
            main == null -> null
            shown.size == 1 -> signed(main.progress.remaining)
            else -> shown.filter { it.subName == null }.sumOf { it.progress.remaining }.let(::signed)
        }
        return AssistantData(facts, headline, main?.let { if (shown.size == 1) "осталось по плану «${it.title}»" else "осталось по планам" })
    }

    /** Сколько строк плана предлагать за раз. */
    const val MAX_PROPOSALS = 10

    /**
     * «Предложи план трат»: по каждой категории — средняя трата за прошлые периоды минус 10 %
     * ([BudgetPlans.hint]), с подписью, из чего она получилась. Добавляется в план только по кнопке.
     */
    private fun suggestPlan(ctx: AssistantContext, spending: List<ExpenseWithCategory>, period: BillingPeriod, names: Set<String>): AssistantData {
        val items = spending.map { r ->
            PlanSpend(r.expense.categoryId, r.expense.subcategoryId, r.expense.amountKopecks, day(r.expense.timestamp, ctx.zone))
        }
        val month = PlanMonth.of(period)
        val current = BudgetPlans.effective(ctx.budgets, month).associateBy { PlanKey(it.categoryId, it.subcategoryId) }
        val keys = keysFor(ctx, names)
        val proposals = keys.mapNotNull { (key, title, emoji) ->
            val hint = BudgetPlans.hint(items, key, period, ctx.startDay) ?: return@mapNotNull null
            PlanProposal(key.categoryId, key.subcategoryId, title, emoji, hint, current[key]?.amountKopecks)
        }.sortedByDescending { it.amountKopecks }.take(MAX_PROPOSALS)

        val facts = buildString {
            append(header(ctx))
            appendLine("Предложение плана трат на период ${span(period.startDate, period.endDate)}.")
            appendLine("Как посчитано: средняя трата категории за последние ${BudgetPlans.HINT_PERIODS} расчётных периода (если история короче — за сколько есть) минус 10 %, округлено до 100 ₽.")
            if (proposals.isEmpty()) {
                appendLine("Предложить нечего: в прошлых периодах трат${if (names.isNotEmpty()) " в этих категориях" else ""} не было.")
            } else {
                proposals.forEach { p ->
                    val now = p.currentKopecks?.let { "сейчас план ${formatRub(it)}" } ?: "плана сейчас нет"
                    appendLine("- ${p.title}: ${formatRub(p.amountKopecks)} ($now)")
                }
                appendLine("Всего: ${formatRub(proposals.sumOf { it.amountKopecks })}. Пользователь может отметить нужные строки и добавить их в план кнопкой под ответом.")
            }
        }
        return AssistantData(
            facts,
            proposals.takeIf { it.isNotEmpty() }?.let { formatRub(it.sumOf { p -> p.amountKopecks }) },
            proposals.takeIf { it.isNotEmpty() }?.let { "предложенный план на период" },
            planProposals = proposals,
            planMonth = month,
        )
    }

    /** «Поставь план на кафе 10 000»: одна строка с суммой пользователя, добавляется по кнопке. */
    private fun setPlan(q: AssistantQuery, ctx: AssistantContext, period: BillingPeriod, names: Set<String>): AssistantData {
        val amount = ((q.amount ?: 0.0) * 100).roundToLong()
        val month = PlanMonth.of(period)
        val target = keysFor(ctx, names).firstOrNull()
        val facts = buildString {
            append(header(ctx))
            when {
                target == null -> appendLine("Не понял, для какой категории поставить план. Нужно назвать категорию из списка.")
                amount <= 0 -> appendLine("Не понял сумму плана для «${target.second}».")
                else -> {
                    val now = BudgetPlans.effective(ctx.budgets, month).firstOrNull { PlanKey(it.categoryId, it.subcategoryId) == target.first }
                    appendLine("Готово к добавлению: план «${target.second}» ${formatRub(amount)} на период ${span(period.startDate, period.endDate)} и дальше каждый месяц.")
                    appendLine(now?.let { "Сейчас план ${formatRub(it.amountKopecks)}, он заменится." } ?: "Сейчас плана нет.")
                    appendLine("План ещё не изменён: пользователь подтверждает кнопкой под ответом.")
                }
            }
        }
        if (target == null || amount <= 0) return AssistantData(facts)
        val now = BudgetPlans.effective(ctx.budgets, month).firstOrNull { PlanKey(it.categoryId, it.subcategoryId) == target.first }
        val proposal = PlanProposal(target.first.categoryId, target.first.subcategoryId, target.second, target.third, amount, now?.amountKopecks)
        return AssistantData(facts, formatRub(amount), "новый план «${target.second}»", planProposals = listOf(proposal), planMonth = month)
    }

    /** Ключи плана для названных категорий (подкатегорий); без названий — все категории. */
    private fun keysFor(ctx: AssistantContext, names: Set<String>): List<Triple<PlanKey, String, String>> {
        val out = ArrayList<Triple<PlanKey, String, String>>()
        for (c in ctx.categories) {
            val catName = c.category.name
            if (names.isEmpty() || catName.lowercase(RU) in names) {
                out += Triple(PlanKey(c.category.id), catName, c.category.emoji)
            }
            if (names.isNotEmpty()) {
                c.subs.filter { it.name.lowercase(RU) in names }.forEach { sub ->
                    out += Triple(PlanKey(c.category.id, sub.id), "$catName · ${sub.name}", c.category.emoji)
                }
            }
        }
        return out
    }

    private fun plans(ctx: AssistantContext, spending: List<ExpenseWithCategory>, period: BillingPeriod): List<NamedPlan> {
        val effective = BudgetPlans.effective(ctx.budgets, PlanMonth.of(period))
        if (effective.isEmpty()) return emptyList()
        val items = spending.mapNotNull { r ->
            val d = day(r.expense.timestamp, ctx.zone)
            if (d in period && !d.isAfter(ctx.today)) PlanSpend(r.expense.categoryId, r.expense.subcategoryId, r.expense.amountKopecks, d) else null
        }
        val spent: Map<PlanKey, Long> = BudgetPlans.spent(items)
        val byId = ctx.categories.associateBy { it.category.id }
        return PlanProgresses.all(effective, spent, period, ctx.today).mapNotNull { p ->
            val cat = byId[p.plan.categoryId] ?: return@mapNotNull null
            val sub = p.plan.subcategoryId?.let { id -> cat.subs.firstOrNull { it.id == id }?.name }
            NamedPlan(if (sub != null) "${cat.category.name} · $sub" else cat.category.name, cat.category.name, sub, p)
        }
    }

    private fun planLine(p: NamedPlan): String {
        val pr = p.progress
        val status = when (pr.status) {
            PlanStatus.OVER -> "превышен на ${formatRub(-pr.remaining)}"
            PlanStatus.AT_RISK -> "по темпу к концу периода выйдет ${pr.forecast?.let(::formatRub) ?: "больше плана"}, перерасход вероятен"
            PlanStatus.NEAR -> "потрачено больше 80%"
            PlanStatus.OK -> "в порядке"
        }
        val perDay = pr.perDay?.let { ", можно ${formatRub(it)} в день" }.orEmpty()
        return "${p.title}: план ${formatRub(pr.planned)}, потрачено ${formatRub(pr.spent)}, осталось ${signed(pr.remaining)} — $status$perDay"
    }

    private fun upcomingPayments(ctx: AssistantContext, period: BillingPeriod) = ctx.recurring.filter {
        it.state == RecurringState.ACTIVE && it.nextDate().isAfter(ctx.today) && !it.nextDate().isAfter(period.endDate)
    }

    // --- Отбор трат ---

    private class Filter(val names: Set<String>, val unknownNames: List<String>, val place: String?) {
        val isEmpty get() = names.isEmpty() && place == null

        fun matches(r: ExpenseWithCategory): Boolean {
            if (names.isNotEmpty()) {
                val cat = r.categoryName.lowercase(RU)
                val sub = r.subcategoryName?.lowercase(RU)
                if (cat !in names && sub !in names) return false
            }
            if (place != null) {
                val p = r.expense.place?.lowercase(RU).orEmpty()
                val n = r.expense.note?.lowercase(RU).orEmpty()
                if (place !in p && place !in n) return false
            }
            return true
        }

        fun describe(): String? = listOfNotNull(
            names.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } },
            place?.let { "«$it»" },
        ).joinToString(", ").ifEmpty { null }

        companion object {
            fun of(q: AssistantQuery, rows: List<ExpenseWithCategory>, categories: List<CategoryWithSubs>): Filter {
                val known = HashSet<String>()
                rows.forEach { r -> known += r.categoryName.lowercase(RU); r.subcategoryName?.let { known += it.lowercase(RU) } }
                categories.forEach { c -> known += c.category.name.lowercase(RU); c.subs.forEach { known += it.name.lowercase(RU) } }
                val asked = q.categories.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase(RU) }
                val place = q.place?.trim()?.lowercase(RU)?.takeIf { it.isNotEmpty() }
                val (found, missing) = asked.partition { it.lowercase(RU) in known }
                return Filter(found.map { it.lowercase(RU) }.toSet(), missing, place)
            }
        }
    }

    // --- Даты ---

    /** Отрезок из запроса; без дат — текущий период до сегодня. Будущее обрезается сегодняшним днём. */
    internal fun range(fromText: String?, toText: String?, defaultFrom: LocalDate, today: LocalDate): Pair<LocalDate, LocalDate> {
        var from = parse(fromText) ?: defaultFrom
        var to = parse(toText) ?: today
        if (to.isAfter(today)) to = today
        if (from.isAfter(to)) from = to.also { to = from }
        if (to.isAfter(today)) to = today
        return from to to
    }

    private fun compareRange(q: AssistantQuery, from: LocalDate, to: LocalDate, period: BillingPeriod, ctx: AssistantContext): Pair<LocalDate, LocalDate> {
        parse(q.compareFrom)?.let { a ->
            val b = parse(q.compareTo) ?: a.plusDays(ChronoUnit.DAYS.between(from, to))
            return if (a.isAfter(b)) b to a else a to b
        }
        // Текущий период до сегодня сравниваем с тем же сроком прошлого, иначе — с отрезком той же длины перед ним
        if (from == period.startDate && to == ctx.today) {
            return PeriodComparison.comparableWindow(period, period.previous(ctx.startDay), ctx.today)
        }
        val len = ChronoUnit.DAYS.between(from, to) + 1
        return from.minusDays(len) to from.minusDays(1)
    }

    private fun parse(s: String?): LocalDate? = s?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun day(ts: Long, zone: ZoneId) = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()

    internal fun span(from: LocalDate, to: LocalDate): String = when {
        from == to -> date(from)
        from.year == to.year && from.month == to.month -> "${from.dayOfMonth}–${to.format(dayFmt)}"
        from.year == to.year -> "${from.format(dayFmt)} — ${to.format(dayFmt)}"
        else -> "${from.format(dayYearFmt)} — ${to.format(dayYearFmt)}"
    }

    private fun date(d: LocalDate) = d.format(dayFmt)

    private fun header(ctx: AssistantContext) = "Сегодня ${ctx.today.format(dayYearFmt)}. Суммы в рублях.\n"

    // --- Форматирование ---

    private fun byCategory(rows: List<ExpenseWithCategory>): List<Pair<String, Long>> =
        rows.groupBy { it.categoryName }.map { (k, v) -> k to v.sumOf { it.expense.amountKopecks } }.sortedByDescending { it.second }

    private fun totals(rows: List<ExpenseWithCategory>): List<CategoryTotal> =
        rows.groupBy { it.expense.categoryId }.map { (id, v) -> CategoryTotal(id, v.first().categoryName, v.first().categoryEmoji, v.sumOf { it.expense.amountKopecks }) }

    private fun incomesIn(ctx: AssistantContext, from: LocalDate, to: LocalDate) = ctx.incomes.filter {
        it.origin != IncomeOrigin.RECONCILE && day(it.timestamp, ctx.zone).let { d -> !d.isBefore(from) && !d.isAfter(to) }
    }

    private fun describe(r: ExpenseWithCategory, zone: ZoneId): String {
        val what = listOfNotNull(r.categoryName, r.subcategoryName, r.expense.place, r.expense.note).distinct().joinToString(", ")
        return "${date(day(r.expense.timestamp, zone))}: ${formatRub(r.expense.amountKopecks)}, $what"
    }

    private fun ids(rows: List<ExpenseWithCategory>) =
        rows.sortedByDescending { it.expense.timestamp }.take(MAX_SHOWN_IDS).map { it.expense.id }

    private fun share(part: Long, total: Long) = if (total <= 0) "0%" else "${(part * 100.0 / total).roundToInt()}%"

    private fun signed(k: Long) = when {
        k > 0 -> "+" + formatRub(k)
        k < 0 -> "−" + formatRub(abs(k))
        else -> formatRub(0)
    }

    private fun percent(diff: Long, base: Long) = if (base == 0L) null else "${if (diff > 0) "+" else if (diff < 0) "−" else ""}${abs(diff * 100.0 / base).roundToInt()}%"

    private fun daysWord(n: Int) = if (n % 10 == 1 && n % 100 != 11) "день" else if (n % 10 in 2..4 && n % 100 !in 12..14) "дня" else "дней"
}
