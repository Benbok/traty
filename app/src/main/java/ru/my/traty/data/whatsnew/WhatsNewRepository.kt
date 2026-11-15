package ru.my.traty.data.whatsnew

import kotlinx.coroutines.flow.first
import ru.my.traty.data.SettingsRepository
import ru.my.traty.data.onboarding.OnboardingRepository

/** Окно «Что нового»: что показать после обновления и запоминание, что человек уже видел. */
class WhatsNewRepository(
    private val settings: SettingsRepository,
    private val onboarding: OnboardingRepository,
    private val installedVersion: String,
    private val entries: List<WhatsNewEntry> = WhatsNew.entries,
) {
    /** Записи, которые нужно показать сейчас; пусто — показывать нечего. Если показывать нечего, версия запоминается сразу. */
    suspend fun pending(): List<WhatsNewEntry> {
        val decision = WhatsNewPolicy.decide(
            installed = installedVersion,
            lastSeen = settings.whatsNewSeenVersion.first(),
            hasRecords = onboarding.hasRecords(),
            entries = entries,
        )
        if (decision.toShow.isEmpty()) decision.recordVersion?.let { settings.setWhatsNewSeenVersion(it) }
        return decision.toShow
    }

    /** Человек закрыл окно: эту версию больше не показываем. */
    suspend fun markSeen() {
        if (installedVersion.isNotBlank()) settings.setWhatsNewSeenVersion(installedVersion)
    }
}
