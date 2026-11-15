package ru.my.traty.data.update

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import ru.my.traty.ai.http
import ru.my.traty.data.AppSettings
import ru.my.traty.data.SettingsRepository
import java.io.IOException

/** Адрес последнего релиза. Единственный запрос в сеть, который делает проверка: ничего о человеке в нём нет. */
private const val LATEST_RELEASE_URL = "https://api.github.com/repos/Benbok/traty/releases/latest"

/** Итог проверки для экрана настроек. */
sealed interface UpdateCheckResult {
    /** Вышла новая версия [version]. */
    data class Available(val version: String) : UpdateCheckResult

    /** У вас последняя версия. */
    data object UpToDate : UpdateCheckResult

    /** Проверить не удалось: [message] можно показать человеку. */
    data class Failed(val message: String) : UpdateCheckResult
}

/** Новая версия, о которой нужно сказать на главном экране. */
data class AvailableUpdate(val version: String, val url: String) {
    companion object {
        /** Плашка нужна, если проверка включена, найденная версия новее установленной и её ещё не скрыли. */
        fun from(settings: AppSettings, installedVersion: String): AvailableUpdate? {
            if (!settings.updateCheckEnabled) return null
            val version = settings.updateVersion
            if (version.isBlank() || version == settings.updateDismissedVersion) return null
            if (!AppVersion.isNewer(version, installedVersion)) return null
            val url = settings.updateUrl.takeIf { ReleaseParser.isTrusted(it) } ?: RELEASES_PAGE
            return AvailableUpdate(version, url)
        }
    }
}

/**
 * Проверка обновлений: сравнивает установленную версию с последним релизом на GitHub.
 * Само приложение не скачивается и не ставится, человек открывает ссылку и делает это сам.
 */
class UpdateRepository(
    private val settings: SettingsRepository,
    /** Версия установленного приложения («0.1»). */
    val installedVersion: String,
    private val fetch: suspend () -> String = ::fetchLatestRelease,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Тихая проверка при запуске: не чаще раза в [CHECK_INTERVAL_MS], без сети и при ошибках молчит. */
    suspend fun checkIfDue() {
        val s = settings.current()
        if (!s.updateCheckEnabled) return
        val elapsed = now() - s.updateCheckedAtMillis
        if (s.updateCheckedAtMillis > 0 && elapsed in 0 until CHECK_INTERVAL_MS) return
        checkNow()
    }

    /** Проверка по кнопке: всегда идёт в сеть и возвращает понятный итог. */
    suspend fun checkNow(): UpdateCheckResult {
        val body = try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoReleasesException) {
            return UpdateCheckResult.Failed("Релизов пока нет")
        } catch (e: Exception) {
            return UpdateCheckResult.Failed("Не получилось проверить: нет связи с GitHub")
        }
        val release = ReleaseParser.parse(body)
            ?: return UpdateCheckResult.Failed("Не получилось разобрать ответ GitHub")

        return if (AppVersion.isNewer(release.version, installedVersion)) {
            settings.saveUpdateCheck(now(), release.version, release.downloadUrl)
            UpdateCheckResult.Available(release.version)
        } else {
            settings.saveUpdateCheck(now(), "", "")
            UpdateCheckResult.UpToDate
        }
    }

    companion object {
        /** Не чаще, чем раз в 12 часов. */
        const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
    }
}

/** Номер версии установленного приложения (versionName из сборки) или пустая строка, если прочитать не удалось. */
fun installedVersionOf(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

/** В репозитории ещё нет ни одного релиза. */
internal class NoReleasesException : IOException("Релизов пока нет")

/** Запрос последнего релиза у GitHub. Только HTTPS; таймауты берутся из общего клиента. */
internal suspend fun fetchLatestRelease(): String = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(LATEST_RELEASE_URL)
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "Traty")
        .build()
    http.newCall(request).execute().use { r ->
        if (r.code == 404) throw NoReleasesException()
        if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
        r.body?.string().orEmpty()
    }
}
