package ru.my.traty.data.whatsnew

/** Один пункт «Что нового». [topicId] — тема справки, где это описано подробнее. */
data class WhatsNewItem(val text: String, val topicId: String? = null)

/** Что появилось в версии [version] (без «v», как в теге релиза: «0.2»). */
data class WhatsNewEntry(val version: String, val items: List<WhatsNewItem>)

/**
 * Новинки по версиям, свежие сверху. Тексты пишутся при выпуске релиза, по смыслу, для обычного человека.
 * Версия записи должна совпадать с тегом релиза: окно «Что нового» показывается тем, кто обновился до неё.
 */
object WhatsNew {
    val entries: List<WhatsNewEntry> = listOf(
        WhatsNewEntry(
            version = "0.2",
            items = listOf(
                WhatsNewItem("Справка по всем функциям: Настройки → «Как пользоваться»."),
                WhatsNewItem("Знакомство с приложением для новых пользователей. Его можно открыть снова из справки."),
                WhatsNewItem("Список покупок голосом записывается одной тратой, а товары попадают в заметку.", "shopping"),
                WhatsNewItem("Покупки частями: дерево платежей, что оплачено и что осталось.", "installments"),
                WhatsNewItem("План трат: общий лимит на месяц и уведомления, когда потрачено 80 % и 100 %.", "plan"),
                WhatsNewItem("Задания на неделю: небольшие цели, чтобы тратить меньше.", "weekly"),
            ),
        ),
    )
}
