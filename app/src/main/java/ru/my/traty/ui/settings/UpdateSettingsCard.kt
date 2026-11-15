package ru.my.traty.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.update.UpdateCheckResult
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.update.UpdateViewModel

/** Карточка «Обновления»: версия приложения, переключатель автопроверки и кнопка «Проверить сейчас». */
@Composable
fun UpdateSettingsCard(vm: UpdateViewModel = viewModel()) {
    val enabled by vm.enabled.collectAsStateWithLifecycle()
    val checking by vm.checking.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val available by vm.available.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    TratyCard(modifier = Modifier.fillMaxWidth(), backgroundColor = TratyTheme.colors.surface) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = enabled, role = Role.Switch, onValueChange = vm::setEnabled)
                    .padding(TratyTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Обновления", style = TratyTheme.typography.titleMedium)
                    Spacer(Modifier.height(TratyTheme.spacing.xs))
                    val version = vm.installedVersion.takeIf { it.isNotBlank() }?.let { "Версия приложения $it. " }.orEmpty()
                    Text(
                        version + "Раз в полдня приложение узнаёт у GitHub, вышла ли новая версия, и показывает плашку на главном экране. " +
                            "Больше ничего никуда не отправляется.",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = null)
            }
            Row(
                Modifier.fillMaxWidth().padding(start = TratyTheme.spacing.lg, end = TratyTheme.spacing.lg, bottom = TratyTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
            ) {
                TratyOutlinedButton(onClick = vm::checkNow, enabled = !checking) {
                    Text(if (checking) "Проверяю…" else "Проверить сейчас")
                }
                val message = when (val r = result) {
                    is UpdateCheckResult.Available -> "Вышла версия ${r.version}"
                    UpdateCheckResult.UpToDate -> "У вас последняя версия"
                    is UpdateCheckResult.Failed -> r.message
                    null -> null
                }
                if (message != null) {
                    Text(
                        message,
                        style = TratyTheme.typography.bodySmall,
                        color = if (result is UpdateCheckResult.Failed) TratyTheme.colors.warning else TratyTheme.colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            available?.let { update ->
                Row(Modifier.padding(start = TratyTheme.spacing.lg, end = TratyTheme.spacing.lg, bottom = TratyTheme.spacing.lg)) {
                    TratyOutlinedButton(onClick = { uriHandler.openUri(update.url) }) { Text("Скачать ${update.version}") }
                }
            }
        }
    }
}
