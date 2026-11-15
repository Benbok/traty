package ru.my.traty.data.onboarding

import ru.my.traty.data.period.MAX_PERIOD_START_DAY
import ru.my.traty.data.period.MIN_PERIOD_START_DAY

/** Как человек вышел из знакомства с приложением. */
enum class OnboardingExit {
    /** «Сначала осмотрюсь», «Пропустить» или «Назад» на первой карточке. */
    BROWSE,

    /** «Записать первую трату»: открыть ленту и сразу включить микрофон. */
    RECORD,

    /** «Подключить ИИ»: открыть экран моделей и ключей. */
    CONNECT_AI,
}

/** Решения знакомства без Android: когда показывать и какой день начала периода сохранить. */
object OnboardingPolicy {
    /**
     * Показывать знакомство при запуске, только если его ещё не видели и в базе нет ни одной записи.
     * У людей, которые уже ведут учёт, оно не всплывает после обновления; его можно открыть из Настроек.
     */
    fun showOnLaunch(seen: Boolean, hasRecords: Boolean): Boolean = !seen && !hasRecords

    /**
     * Нужно ли запомнить знакомство как просмотренное без показа: запись уже есть, а флага нет
     * (обновление со старой версии). Тогда удаление всех записей потом не вернёт знакомство.
     */
    fun markSeenSilently(seen: Boolean, hasRecords: Boolean): Boolean = !seen && hasRecords

    /** День начала периода из шага «Можно начинать»: с 1-го числа или с дня зарплаты. */
    fun periodStartDay(fromSalary: Boolean, salaryDay: Int): Int =
        if (fromSalary) salaryDay.coerceIn(MIN_PERIOD_START_DAY, MAX_PERIOD_START_DAY) else MIN_PERIOD_START_DAY
}
