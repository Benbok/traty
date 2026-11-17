package ru.my.traty.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import java.time.LocalDate

/**
 * Раздел «Резервная копия» в настройках: полная копия всего приложения (JSON, для переноса на другой телефон)
 * и прежняя таблица CSV для Excel. CSV по-прежнему обслуживает MainViewModel, полную копию — [BackupViewModel].
 */
@Composable
fun BackupSettingsCard(
    onExportCsv: (Uri) -> Unit,
    onImportCsv: (Uri) -> Unit,
    vm: BackupViewModel = viewModel(),
) {
    TratyCard(modifier = Modifier.fillMaxWidth(), backgroundColor = TratyTheme.colors.surface) {
        Column(
            Modifier.padding(TratyTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
        ) {
            Text("Резервная копия", style = TratyTheme.typography.titleMedium)
            FullBackupSection(vm)
            CsvSection(onExportCsv, onImportCsv)
        }
    }
    vm.offer?.let { RestoreConfirmDialog(it, onConfirm = vm::confirmRestore, onDismiss = vm::cancelRestore) }
}

@Composable
private fun FullBackupSection(vm: BackupViewModel) {
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.save(uri)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.open(uri)
    }
    Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
        Text("Полная копия", style = TratyTheme.typography.titleSmall)
        Text(
            "Всё приложение в одном файле: траты, пополнения, категории, планы, подписки, покупки частями, " +
                "правила и настройки. С ней можно перенести «Траты» на другой телефон. " +
                "Ключи ИИ в копию не входят, их нужно будет ввести заново. Модель распознавания речи скачается заново.",
            style = TratyTheme.typography.bodySmall,
            color = TratyTheme.colors.onSurfaceVariant,
        )
        TratyOutlinedButton(
            onClick = { saveLauncher.launch("traty-full-${LocalDate.now()}.json") },
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Сохранить полную копию") }
        TratyOutlinedButton(
            onClick = { openLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Восстановить из копии") }
        BackupStatus(vm)
    }
}

@Composable
private fun BackupStatus(vm: BackupViewModel) {
    val notice = vm.notice
    when {
        vm.busy -> Text(
            "Подождите, идёт работа с копией…",
            style = TratyTheme.typography.bodySmall,
            color = TratyTheme.colors.onSurfaceVariant,
        )
        notice != null -> Text(
            notice.text,
            style = TratyTheme.typography.bodySmall,
            color = if (notice.isError) TratyTheme.colors.expense else TratyTheme.colors.primary,
        )
    }
}

/** Прежний экспорт и импорт CSV, без изменений в поведении. */
@Composable
private fun CsvSection(onExportCsv: (Uri) -> Unit, onImportCsv: (Uri) -> Unit) {
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) onExportCsv(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportCsv(uri)
    }
    Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
        Text("Таблица для Excel", style = TratyTheme.typography.titleSmall)
        Text(
            "Только траты и пополнения в CSV: удобно смотреть в Excel. Для переноса на другой телефон нужна полная копия. " +
                "Импорт добавляет траты из файла и пропускает те, что уже есть.",
            style = TratyTheme.typography.bodySmall,
            color = TratyTheme.colors.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
            TratyOutlinedButton(onClick = { exportLauncher.launch("traty-${LocalDate.now()}.csv") }) {
                Text("Экспорт в CSV")
            }
            TratyOutlinedButton(onClick = {
                importLauncher.launch(
                    arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel")
                )
            }) {
                Text("Импорт из CSV")
            }
        }
    }
}
