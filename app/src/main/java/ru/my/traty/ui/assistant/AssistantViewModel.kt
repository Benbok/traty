package ru.my.traty.ui.assistant

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.my.traty.TratyApp
import ru.my.traty.assistant.AssistantContext
import ru.my.traty.assistant.Balance
import ru.my.traty.assistant.AssistantEngine
import ru.my.traty.assistant.AssistantPrompts
import ru.my.traty.assistant.AssistantQueries
import ru.my.traty.assistant.AssistantQuery
import ru.my.traty.assistant.AssistantTurn
import ru.my.traty.assistant.Intents
import ru.my.traty.assistant.PlanProposal
import ru.my.traty.assistant.SubAction
import ru.my.traty.assistant.SubscriptionChange
import ru.my.traty.data.AppSettings
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.LlmProvider
import ru.my.traty.data.selectable
import ru.my.traty.data.budget.BudgetRepository
import ru.my.traty.data.budget.PlanKey
import ru.my.traty.data.recurring.RecurringRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Одна реплика в разговоре с помощником. */
data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    /** Крупная цифра под ответом. */
    val headline: String? = null,
    val caption: String? = null,
    /** Траты, на которых построен ответ, — для «Показать траты». */
    val expenseIds: List<Long> = emptyList(),
    /** Что именно ушло в ИИ на втором шаге — показывается по кнопке «Что отправлено». */
    val sentFacts: String? = null,
    val error: Boolean = false,
    /** Предложенные строки «Плана трат» — добавляются по кнопке. */
    val planProposals: List<PlanProposal> = emptyList(),
    val planMonth: Int? = null,
    /** Сколько строк уже добавлено в план; null — ещё не добавляли. */
    val planApplied: Int? = null,
    /** Предложенные изменения подписок — выполняются по кнопке. */
    val subscriptionChanges: List<SubscriptionChange> = emptyList(),
    /** Сколько изменений подписок уже выполнено; null — ещё нет. */
    val subscriptionsApplied: Int? = null,
)

/**
 * Помощник «Спросить ИИ». Ответ строится в два шага: ИИ переводит вопрос в запрос ([AssistantQuery]),
 * приложение считает его по базе на телефоне ([AssistantEngine]), затем ИИ формулирует ответ только по этим цифрам.
 */
class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container
    private val zone = ZoneId.systemDefault()

    val settings: StateFlow<AppSettings> = c.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val messages = mutableStateListOf<ChatMessage>()
    var busy by mutableStateOf(false)
        private set

    /** Траты для нижней шторки «Показать траты»; null — шторка закрыта. */
    var sheetRows by mutableStateOf<List<ExpenseWithCategory>?>(null)
        private set

    private val budgets = BudgetRepository(c.db)
    private val subscriptions = RecurringRepository(c.db)
    private val history = ArrayList<AssistantTurn>()
    private var nextId = 1L

    /** ИИ выбран в настройках (ключ проверится при первом вопросе). */
    fun hasAi(s: AppSettings): Boolean = s.provider != LlmProvider.OFFLINE

    fun ask(raw: String) {
        val question = raw.trim()
        if (question.isEmpty() || busy) return
        messages += ChatMessage(nextId++, fromUser = true, text = question)
        val client = c.llm(settings.value)
        if (client == null) {
            messages += ChatMessage(nextId++, false, "Чтобы отвечать на вопросы, подключите ИИ в Настройках → «Разбор фраз и ИИ».", error = true)
            return
        }
        busy = true
        viewModelScope.launch {
            try {
                val s = c.settings.current()
                val today = LocalDate.now()
                val categories = c.db.categories().getActiveWithSubs().selectable()
                val rawQuery = client.complete(
                    system = AssistantPrompts.querySystem(categories, today, s.periodStartDay),
                    user = AssistantPrompts.queryUser(question, history),
                    jsonMode = true,
                )
                val query = AssistantQueries.correct(question, AssistantQueries.decode(rawQuery) ?: AssistantQuery(intent = Intents.UNKNOWN))
                history += AssistantTurn(question, AssistantQueries.encode(query))
                while (history.size > AssistantPrompts.HISTORY) history.removeAt(0)

                val expectation = c.reconciliations.expectation()
                val balance = expectation?.let {
                    Balance(it.expectedKopecks, Instant.ofEpochMilli(it.sinceMillis).atZone(zone).toLocalDate())
                }
                val budgets = c.db.budgets().getAll()
                val data = withContext(Dispatchers.Default) {
                    val ctx = AssistantContext(
                        expenses = c.db.expenses().getAllWithCategory(),
                        incomes = c.db.incomes().getAll(),
                        recurring = c.db.recurring().getAll(),
                        today = today,
                        startDay = s.periodStartDay,
                        zone = zone,
                        budgets = budgets,
                        categories = categories,
                        balance = balance,
                    )
                    AssistantEngine.run(query, ctx)
                }
                val text = data.direct ?: AssistantPrompts.clean(
                    client.complete(AssistantPrompts.ANSWER_SYSTEM, AssistantPrompts.answerUser(question, data.facts), jsonMode = false)
                )
                messages += ChatMessage(
                    nextId++, false, text,
                    headline = data.headline,
                    caption = data.caption,
                    expenseIds = data.expenseIds,
                    sentFacts = data.facts.takeIf { data.direct == null },
                    planProposals = data.planProposals,
                    planMonth = data.planMonth,
                    subscriptionChanges = data.subscriptionChanges,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages += ChatMessage(nextId++, false, "Не получилось ответить: ${e.message ?: "ошибка сети"}", error = true)
            } finally {
                busy = false
            }
        }
    }

    /** Добавляет отмеченные строки предложения в «План трат» (повторяются и в следующих месяцах). */
    fun applyPlan(messageId: Long, selected: List<PlanProposal>) {
        val index = messages.indexOfFirst { it.id == messageId }
        val m = messages.getOrNull(index) ?: return
        val month = m.planMonth ?: return
        if (selected.isEmpty()) return
        viewModelScope.launch {
            try {
                selected.forEach { p ->
                    if (p.remove) {
                        budgets.removeFrom(PlanKey(p.categoryId, p.subcategoryId), month)
                    } else {
                        val subId = p.newSubcategory?.let { name -> subcategoryId(p.categoryId, name) } ?: p.subcategoryId
                        budgets.set(PlanKey(p.categoryId, subId), month, p.amountKopecks, repeats = true)
                    }
                }
                val i = messages.indexOfFirst { it.id == messageId }
                if (i >= 0) messages[i] = messages[i].copy(planApplied = selected.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages += ChatMessage(nextId++, false, "Не удалось добавить в план: ${e.message}", error = true)
            }
        }
    }

    /** Выполняет отмеченные изменения подписок. Платёж читается заново: пока шёл разговор, он мог измениться. */
    fun applySubscriptions(messageId: Long, selected: List<SubscriptionChange>) {
        if (selected.isEmpty()) return
        viewModelScope.launch {
            try {
                var done = 0
                selected.forEach { ch ->
                    val item = c.db.recurring().getById(ch.id) ?: return@forEach
                    when (ch.action) {
                        SubAction.CANCEL -> subscriptions.remove(item)
                        SubAction.PAUSE -> subscriptions.setPaused(item, true)
                        SubAction.RESUME -> subscriptions.setPaused(item, false)
                        SubAction.EDIT -> subscriptions.save(
                            item.copy(
                                amountKopecks = ch.newAmountKopecks ?: item.amountKopecks,
                                nextEpochDay = ch.newNextDay?.toEpochDay() ?: item.nextEpochDay,
                            )
                        )
                    }
                    done++
                }
                val i = messages.indexOfFirst { it.id == messageId }
                if (i >= 0) messages[i] = messages[i].copy(subscriptionsApplied = done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages += ChatMessage(nextId++, false, "Не удалось изменить подписку: ${e.message}", error = true)
            }
        }
    }

    /** Подкатегория из предложения ИИ: создаём, а если её уже успели завести — берём существующую. */
    private suspend fun subcategoryId(categoryId: Long, name: String): Long {
        c.db.categories().getActiveWithSubs().firstOrNull { it.category.id == categoryId }
            ?.subs?.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it.id }
        return c.categories.addSubcategory(categoryId, name)
    }

    fun showExpenses(ids: List<Long>) = viewModelScope.launch {
        if (ids.isEmpty()) return@launch
        sheetRows = c.db.expenses().getByIds(ids).sortedByDescending { it.expense.timestamp }
    }

    fun closeSheet() {
        sheetRows = null
    }

    fun clear() {
        messages.clear()
        history.clear()
    }
}
