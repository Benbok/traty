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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.my.traty.TratyApp
import ru.my.traty.ai.LlmClient
import ru.my.traty.assistant.AssistantContext
import ru.my.traty.assistant.DebtAction
import ru.my.traty.assistant.DebtChange
import ru.my.traty.assistant.DebtWithPaid
import ru.my.traty.assistant.ExpenseAction
import ru.my.traty.assistant.ExpenseChange
import ru.my.traty.assistant.AssistantAnswers
import ru.my.traty.assistant.AssistantData
import ru.my.traty.assistant.Intents
import ru.my.traty.assistant.ProductResolution
import ru.my.traty.assistant.AssistantFallback
import ru.my.traty.assistant.Balance
import ru.my.traty.assistant.AssistantEngine
import ru.my.traty.assistant.AssistantLabelQuestions
import ru.my.traty.assistant.AssistantPrompts
import ru.my.traty.assistant.AssistantQueries
import ru.my.traty.assistant.AssistantQuery
import ru.my.traty.assistant.AssistantTurn
import ru.my.traty.assistant.PlanProposal
import ru.my.traty.assistant.QuestionRules
import ru.my.traty.assistant.SubAction
import ru.my.traty.assistant.SubscriptionChange
import ru.my.traty.data.AppSettings
import ru.my.traty.data.ExpenseEditResult
import ru.my.traty.data.ExpenseMove
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.LlmProvider
import ru.my.traty.data.selectable
import ru.my.traty.data.budget.BudgetRepository
import ru.my.traty.data.budget.PlanKey
import ru.my.traty.data.recurring.RecurringRepository
import ru.my.traty.ui.labels.expenseLabelsFlow
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
    /** Предложенные изменения покупок частями — выполняются по кнопке. */
    val debtChanges: List<DebtChange> = emptyList(),
    /** Сколько изменений покупок уже выполнено; null — ещё нет. */
    val debtsApplied: Int? = null,
    /** Предложенные удаления и переносы трат — выполняются по кнопке и возвращаются кнопкой «Вернуть». */
    val expenseChanges: List<ExpenseChange> = emptyList(),
    val expensesApplied: Int? = null,
    val expensesUndone: Boolean = false,
    val productReport: ru.my.traty.assistant.ProductReportResult? = null,
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
    private val housingConversation = ru.my.traty.assistant.apartments.ApartmentConversation()
    private val history = ArrayList<AssistantTurn>()
    private var nextId = 1L

    /** ИИ выбран в настройках (ключ проверится при первом вопросе). */
    fun hasAi(s: AppSettings): Boolean = s.provider != LlmProvider.OFFLINE

    fun ask(raw: String) {
        val question = raw.trim()
        if (question.isEmpty() || busy) return
        messages += ChatMessage(nextId++, fromUser = true, text = question)
        // Без ИИ вкладка не отказывает: вопрос понимается по ключевым словам, цифры считает приложение
        val client = c.llm(settings.value)
        busy = true
        viewModelScope.launch {
            try {
                val s = c.settings.current()
                val today = LocalDate.now()
                val categories = c.db.categories().getActiveWithSubs().selectable()
                // Шаг 1: вопрос → запрос. Если ИИ не ответил, пробуем понять вопрос правилами
                var aiDown: Exception? = null
                val guessed = QuestionRules.guess(question, categories)
                val previous = history.lastOrNull()?.let { turn ->
                    AssistantQueries.decode(turn.queryJson)?.copy(ids = turn.expenseIds, shownProductKeys = turn.shownProductKeys)
                }
                val knownReport = ru.my.traty.assistant.ProductReportRequest.supports(guessed?.intent) ||
                    ru.my.traty.assistant.ProductReportRequest.isContinuation(question, previous, today, s.periodStartDay)
                val housing = withContext(Dispatchers.IO) { c.apartments.snapshot() }
                val housingCatalog = ru.my.traty.assistant.apartments.ApartmentPrompts.catalog(housing).takeUnless { knownReport }.orEmpty()
                val stepOneUser = AssistantPrompts.queryUser(question, history)
                val fromAi = if (client == null) null else try {
                    AssistantQueries.decode(
                        client.complete(
                            system = AssistantPrompts.querySystem(categories, today, s.periodStartDay) + "\n" + housingCatalog,
                            user = stepOneUser,
                            jsonMode = true,
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    aiDown = e
                    null
                }
                val housingReply = housingConversation.reply(question, fromAi, housing, today, client,
                    AssistantAnswers.sent(stepOneUser, categories.map { it.category.name }, null) + "\n" + housingCatalog)
                if (housingReply != null) {
                    history.clear()
                    messages += ChatMessage(nextId++, false, housingReply.text, sentFacts = housingReply.sentFacts)
                    return@launch
                }
                val offline = aiDown != null
                val offlineReason = aiDown?.message
                // Траты прошлого ответа приложение помнит само: «удали их» относится ровно к ним
                val query = QuestionRules.resolve(question, fromAi, guessed, today, s.periodStartDay, previous, categories)
                if (aiDown != null && query.intent == Intents.UNKNOWN) throw aiDown
                if (client == null && query.intent == Intents.UNKNOWN) {
                    messages += ChatMessage(nextId++, false, AssistantFallback.NO_AI_HINT)
                    return@launch
                }
                history += AssistantTurn(question, AssistantQueries.encode(query))
                while (history.size > AssistantPrompts.HISTORY) history.removeAt(0)

                val expectation = c.reconciliations.expectation()
                val balance = expectation?.let {
                    Balance(it.expectedKopecks, Instant.ofEpochMilli(it.sinceMillis).atZone(zone).toLocalDate())
                }
                val budgets = c.db.budgets().getAll()
                val debts = c.debts.activeWithPayments(zone).map { (d, paid) -> DebtWithPaid(d, paid) }
                val calculated = withContext(Dispatchers.Default) {
                    val expenses = c.db.expenses().getAllWithCategory()
                    val expenseItems = c.expenseItems.getForAll(expenses.map { it.expense.id })
                    val ctx = AssistantContext(
                        expenses = expenses,
                        incomes = c.db.incomes().getAll(),
                        recurring = c.db.recurring().getAll(),
                        today = today,
                        startDay = s.periodStartDay,
                        zone = zone,
                        budgets = budgets,
                        categories = categories,
                        balance = balance,
                        debts = debts,
                        expenseItems = expenseItems,
                    )
                    val scoped = if (query.labels.isEmpty()) query else {
                        AssistantLabelQuestions.attachMatches(query, expenseLabelsFlow(c, zone, includeHidden = true).first())
                    }
                    AssistantEngine.run(scoped, ctx)
                }
                val matching = ProductResolution.prompt(question, calculated)
                val sentMatching = matching?.takeIf { client != null && !offline }
                val decision = if (sentMatching != null && client != null) try {
                    client.complete(ProductResolution.SYSTEM, sentMatching, jsonMode = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                } else null
                val data = withContext(Dispatchers.Default) { ProductResolution.answer(calculated, decision) }
                val remembered = data.expenseIds.ifEmpty { data.expenseChanges.map { it.id } }
                history.lastIndex.takeIf { it >= 0 }?.let { history[it] = history[it].copy(
                    expenseIds = remembered, shownProductKeys = data.productReport?.shownKeys.orEmpty(),
                ) }
                val template = AssistantAnswers.template(query.intent, data)
                val asked = client != null && !offline && data.direct == null && template == null
                val text = data.direct ?: template ?: when {
                    client == null -> AssistantFallback.text(data, guessed = true, noAiMode = true)
                    offline -> AssistantFallback.text(data, guessed = true, reason = offlineReason)
                    else -> AssistantAnswers.verified(answer(client, question, data), data)
                }
                messages += ChatMessage(
                    nextId++, false, text,
                    headline = data.headline,
                    caption = data.caption,
                    expenseIds = data.expenseIds,
                    sentFacts = if (client == null || offline) null else AssistantAnswers.sent(
                        stepOneUser + "\n" + housingCatalog, categories.map { it.category.name }, data.facts.takeIf { asked },
                    ) + sentMatching?.let { "\nСопоставление товаров:\n$it" }.orEmpty(),
                    planProposals = data.planProposals,
                    planMonth = data.planMonth,
                    subscriptionChanges = data.subscriptionChanges,
                    debtChanges = data.debtChanges,
                    expenseChanges = data.expenseChanges,
                    productReport = data.productReport,
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

    /** Шаг 2: ответ словами по посчитанным цифрам. Если ИИ не ответил, цифры всё равно показываются. */
    private suspend fun answer(client: LlmClient, question: String, data: AssistantData): String = try {
        AssistantPrompts.clean(
            client.complete(AssistantPrompts.ANSWER_SYSTEM, AssistantPrompts.answerUser(question, data.facts), jsonMode = false)
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AssistantFallback.text(data)
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

    /** Удаляет, закрывает или отменяет отмеченные покупки частями — так же, как кнопки на экране покупки. */
    fun applyDebts(messageId: Long, selected: List<DebtChange>) {
        if (selected.isEmpty()) return
        viewModelScope.launch {
            try {
                var done = 0
                selected.forEach { ch ->
                    if (c.debts.getById(ch.id) == null) return@forEach
                    when (ch.action) {
                        DebtAction.DELETE -> c.debts.delete(ch.id)
                        DebtAction.CLOSE -> c.debts.close(ch.id)
                        DebtAction.CANCEL -> c.debts.cancel(ch.id)
                    }
                    done++
                }
                val i = messages.indexOfFirst { it.id == messageId }
                if (i >= 0) messages[i] = messages[i].copy(debtsApplied = done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages += ChatMessage(nextId++, false, "Не удалось изменить покупку частями. Попробуйте ещё раз.", error = true)
            }
        }
    }

    /** Что можно вернуть кнопкой «Вернуть»: по номеру сообщения. Живёт, пока открыт экран. */
    private val undoable = HashMap<Long, ExpenseEditResult>()

    /** Удаляет или переносит отмеченные траты одним пакетом. Если что-то пошло не так, не меняется ничего. */
    fun applyExpenses(messageId: Long, selected: List<ExpenseChange>) {
        if (selected.isEmpty()) return
        viewModelScope.launch {
            try {
                val result = when (selected.first().action) {
                    ExpenseAction.DELETE -> c.expenseEdits.delete(selected.map { it.id })
                    ExpenseAction.MOVE -> c.expenseEdits.move(
                        selected.mapNotNull { ch ->
                            ch.toCategoryId?.let { ExpenseMove(ch.id, it, ch.toSubcategoryId, ch.fromCategoryId, ch.fromSubcategoryId) }
                        },
                    )
                }
                undoable[messageId] = result
                val i = messages.indexOfFirst { it.id == messageId }
                if (i >= 0) messages[i] = messages[i].copy(expensesApplied = result.done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages += ChatMessage(nextId++, false, "Не удалось изменить траты, ничего не поменялось. Попробуйте ещё раз.", error = true)
            }
        }
    }

    /** «Вернуть»: удалённые траты возвращаются, перенесённые встают на прежнее место. */
    fun undoExpenses(messageId: Long) {
        val result = undoable.remove(messageId) ?: return
        viewModelScope.launch {
            try {
                c.expenseEdits.undo(result)
                val i = messages.indexOfFirst { it.id == messageId }
                if (i >= 0) messages[i] = messages[i].copy(expensesUndone = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                undoable[messageId] = result
                messages += ChatMessage(nextId++, false, "Не удалось вернуть траты. Попробуйте ещё раз.", error = true)
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
        undoable.clear()
    }
}
