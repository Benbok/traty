package ru.my.traty.assistant.apartments

import kotlinx.coroutines.CancellationException
import ru.my.traty.ai.LlmClient
import ru.my.traty.assistant.AssistantAnswers
import ru.my.traty.assistant.AssistantQuery
import ru.my.traty.data.apartments.ApartmentSnapshot
import java.time.LocalDate

/** Контекст одного открытого чата, без глобального состояния. */
class ApartmentConversation {
    private var previous: ApartmentQuestion? = null
    suspend fun reply(raw: String, ai: AssistantQuery?, s: ApartmentSnapshot, today: LocalDate, client: LlmClient?, sentQuery: String): ApartmentReply? {
        var q = ApartmentQuestionRules.resolve(raw, ai, previous, today) ?: return null
        // Для локального разбора сопоставляем только короткие названия, не адрес и реквизиты.
        val folded = raw.lowercase().replace('ё', 'е')
        val namedApartments = s.apartments.filter { folded.contains(it.name.lowercase().replace('ё', 'е')) }
        if (q.apartment == null && namedApartments.size == 1) q = q.copy(apartment = namedApartments.single().name)
        val namedMeters = s.meters.filter { folded.contains(it.name.lowercase().replace('ё', 'е')) }
        if (q.meter == null && namedMeters.map { it.name }.distinct().size == 1) q = q.copy(meter = namedMeters.first().name)
        previous = q
        val facts = ApartmentAssistant.answer(q, s)
        if (client == null) return ApartmentReply(facts, null)
        // Факты остаются в ответе дословно; модель может добавить только краткое пояснение.
        val explain = !facts.contains("Уточните") && !facts.contains("не найдена") && !facts.contains("Недостаточно")
        val explanation = if (!explain) null else try {
            val text = client.complete(
                "Ты объясняешь потребление коммунальных ресурсов. Не вычисляй числа, не делай выводов о деньгах и не утверждай причины роста. Не повторяй и не добавляй числа. Дай не более двух предложений по готовым фактам; возможные причины обозначай предположениями. Если фактов мало, скажи об этом.",
                "Вопрос: $raw\nРассчитанные приложением факты:\n$facts", jsonMode = false,
            )
            text.takeIf { it.none(Char::isDigit) }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        return ApartmentReply(facts + explanation?.let { "\n\n$it" }.orEmpty(), sentQuery + if (explain) "\n\nРассчитанные факты для пояснения:\n$facts" else "")
    }
}
data class ApartmentReply(val text: String, val sentFacts: String?)
