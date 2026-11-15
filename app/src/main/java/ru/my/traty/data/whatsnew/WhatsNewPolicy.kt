package ru.my.traty.data.whatsnew

import ru.my.traty.data.update.AppVersion

/**
 * Что показать в окне «Что нового» и какую версию после этого запомнить.
 * [recordVersion] == null — запоминать нечего.
 */
data class WhatsNewDecision(val toShow: List<WhatsNewEntry>, val recordVersion: String?) {
    companion object {
        val NOTHING = WhatsNewDecision(emptyList(), null)
    }
}

/** Правила показа «Что нового» без Android. */
object WhatsNewPolicy {
    /**
     * @param installed версия установленного приложения.
     * @param lastSeen версия, на которой окно уже показывали или решили не показывать; null — ещё не записано.
     * @param hasRecords есть ли у человека траты или пополнения (по ним отличаем новичка от обновившегося).
     */
    fun decide(
        installed: String,
        lastSeen: String?,
        hasRecords: Boolean,
        entries: List<WhatsNewEntry>,
    ): WhatsNewDecision {
        // Пробная или неразборная версия («0.2-test», пусто): ничего не показываем и не запоминаем
        if (AppVersion.parse(installed) == null) return WhatsNewDecision.NOTHING
        val seen = lastSeen?.takeIf { AppVersion.parse(it) != null }
        val upTo = entries.filter { !AppVersion.isNewer(it.version, installed) }
        return when {
            // Новичку знакомство уже всё показало: просто запоминаем версию
            seen == null && !hasRecords -> WhatsNewDecision(emptyList(), installed)
            // Обновился с версии, где «Что нового» ещё не было: показываем самую свежую запись
            seen == null -> WhatsNewDecision(upTo.take(1), installed)
            AppVersion.isNewer(installed, seen) ->
                WhatsNewDecision(upTo.filter { AppVersion.isNewer(it.version, seen) }, installed)
            // Та же версия или откат на старую: показывать нечего, запись не трогаем
            else -> WhatsNewDecision.NOTHING
        }
    }
}
