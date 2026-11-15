package ru.my.traty.ui.weekly

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.my.traty.TratyApp
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.selectable
import ru.my.traty.data.weekly.TaskProgress
import ru.my.traty.data.weekly.WeekResult
import ru.my.traty.data.weekly.WeeklyProgress
import ru.my.traty.data.weekly.WeeklyTask
import ru.my.traty.data.weekly.WeeklyTaskTexts
import ru.my.traty.data.weekly.WeeklyTaskType
import ru.my.traty.data.weekly.Weeks
import ru.my.traty.data.weekly.toTaskSpends
import java.time.LocalDate
import java.time.ZoneId

/** Что показывает экран заданий: текущая неделя, прошлые недели и серия. */
data class WeeklyUi(
    val weekStart: LocalDate,
    val current: List<TaskProgress>,
    val history: List<WeekResult>,
    val streak: Int,
    val loaded: Boolean,
)

/** Экран «Задания на неделю» и короткая карточка на Тратах. */
class WeeklyTasksViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TratyApp).container
    private val repo = c.weeklyTasks
    private val zone: ZoneId = ZoneId.systemDefault()

    val ui: StateFlow<WeeklyUi> = combine(
        repo.observeAll(),
        c.db.expenses().observeAllWithCategory().map { it.toTaskSpends(zone) },
    ) { tasks, spends ->
        val today = LocalDate.now()
        val week = Weeks.monday(today)
        val current = tasks.filter { it.active && it.weekStart == week.toEpochDay() }.map { WeeklyProgress.of(it, spends, today) }
        val results = WeeklyProgress.results(tasks, spends, today)
        WeeklyUi(week, current, results.filter { it.finished }, WeeklyProgress.streak(results, today), loaded = true)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeeklyUi(Weeks.monday(LocalDate.now()), emptyList(), emptyList(), 0, false))

    val categories: StateFlow<List<CategoryWithSubs>> =
        c.db.categories().observeActiveWithSubs().map { it.selectable() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notifyEnabled: StateFlow<Boolean> = c.settings.flow.map { it.weeklyTasksNotify }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Сообщение для снекбара («Замены не нашлось» и т. п.); экран сбрасывает его после показа. */
    val message = MutableStateFlow<String?>(null)

    init {
        // Задания недели создаются при первом открытии, если фоновая проверка в понедельник не успела
        launchSafely { repo.ensureWeek() }
    }

    fun replace(task: WeeklyTask) = launchSafely {
        if (!repo.replace(task)) message.value = "Другого задания пока нет: мало данных. Можно добавить своё."
    }

    fun remove(task: WeeklyTask) = launchSafely { repo.remove(task) }

    fun setChecked(task: WeeklyTask, checked: Boolean) = launchSafely { repo.setChecked(task, checked) }

    fun add(type: WeeklyTaskType, category: CategoryWithSubs?, target: Long) = launchSafely {
        repo.add(WeeklyTaskTexts.customTask(LocalDate.now(), type, category?.category?.id, category?.category?.name.orEmpty(), target))
    }

    fun setNotify(enabled: Boolean) = launchSafely { c.settings.setWeeklyTasksNotify(enabled) }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message.value = "Не получилось сохранить задание. Попробуйте ещё раз."
            }
        }
    }
}
