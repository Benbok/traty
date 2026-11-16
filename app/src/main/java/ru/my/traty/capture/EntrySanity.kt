package ru.my.traty.capture

import ru.my.traty.ai.ParsedEntry
import java.time.LocalDate

/**
 * Проверка ответа ИИ на здравый смысл перед решением «сохранить сразу или спросить»: сумма и дата
 * сверяются с фразой ([AmountCheck], [DateCheck]). [problems] не пуст — автосохранения не будет,
 * человек увидит окно проверки с этими пояснениями.
 */
object EntrySanity {
    class Result(val entries: List<ParsedEntry>, val problems: List<String>)

    fun check(entries: List<ParsedEntry>, rawText: String, today: LocalDate): Result {
        val dates = DateCheck.apply(entries, rawText, today)
        val amount = AmountCheck.notice(AmountCheck.mismatches(dates.entries, rawText), rawText)
        return Result(dates.entries, listOfNotNull(amount) + dates.problems)
    }
}
