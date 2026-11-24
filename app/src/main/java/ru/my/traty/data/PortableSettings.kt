package ru.my.traty.data

import kotlinx.serialization.Serializable

/**
 * Настройки, которые переносятся на другой телефон вместе с полной резервной копией.
 *
 * Сюда намеренно не входят ключи ИИ (CLAUDE.md §4: секреты только через SecretCodec),
 * а также служебное состояние этого телефона: отложенная сверка, скрытые карточки, проверка обновлений,
 * показанное знакомство. Все поля необязательные: в копии старой версии чего-то может не быть,
 * и тогда нынешнее значение не трогается.
 */
@Serializable
data class PortableSettings(
    val themeMode: String? = null,
    val dynamicColor: Boolean? = null,
    val holidayDecor: Boolean? = null,
    val provider: String? = null,
    val deepseekModel: String? = null,
    val periodStartDay: Int? = null,
    val calculatorLines: List<String>? = null,
    val voiceEngine: String? = null,
    val labelsEnabled: Boolean? = null,
    val dailyDigestEnabled: Boolean? = null,
    val dailyDigestMinutes: Int? = null,
    val homePlanExpanded: Boolean? = null,
    val budgetAlertsEnabled: Boolean? = null,
    val weeklyTasksNotify: Boolean? = null,
    val updateCheckEnabled: Boolean? = null,
)

fun AppSettings.portable(): PortableSettings = PortableSettings(
    themeMode = themeMode.name,
    dynamicColor = dynamicColor,
    holidayDecor = holidayDecor,
    provider = provider.name,
    deepseekModel = deepseekModel,
    periodStartDay = periodStartDay,
    calculatorLines = calculatorLines,
    voiceEngine = voiceEngine.name,
    labelsEnabled = labelsEnabled,
    dailyDigestEnabled = dailyDigestEnabled,
    dailyDigestMinutes = dailyDigestTime.toSecondOfDay() / 60,
    homePlanExpanded = homePlanExpanded,
    budgetAlertsEnabled = budgetAlertsEnabled,
    weeklyTasksNotify = weeklyTasksNotify,
    updateCheckEnabled = updateCheckEnabled,
)

/** Значение перечисления из копии; незнакомое (из другой версии) — null, нынешнее значение остаётся. */
internal inline fun <reified E : Enum<E>> enumOrNull(raw: String?): E? =
    raw?.let { name -> enumValues<E>().firstOrNull { it.name == name } }
