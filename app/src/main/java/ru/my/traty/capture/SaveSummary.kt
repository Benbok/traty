package ru.my.traty.capture

/** Что создалось при сохранении: «Новые категории: Питомцы. Новые подкатегории: Питомцы › Зоомагазин». null — ничего. */
fun SaveResult.createdText(): String? = listOfNotNull(
    newCategories.takeIf { it.isNotEmpty() }?.let { "Новые категории: ${it.joinToString()}" },
    newSubcategories.takeIf { it.isNotEmpty() }?.let { "Новые подкатегории: ${it.joinToString()}" },
).joinToString(". ").ifEmpty { null }

/** Сообщение после сохранения на главной. */
fun SaveResult.summary(): String = listOfNotNull(
    "Сохранено: ${saved.count}",
    createdText(),
    recurringAdded.takeIf { it.isNotEmpty() }?.let { "В подписки добавлено: ${it.joinToString()}" },
).joinToString(". ")
