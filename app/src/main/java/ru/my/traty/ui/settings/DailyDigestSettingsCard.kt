package ru.my.traty.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.recurring.canNotify
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyPickRow
import ru.my.traty.ui.designsystem.components.TratyRowDivider
import ru.my.traty.ui.designsystem.components.TratySwitchRow
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val digestClockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun DailyDigestSettingsCard(
    enabled: Boolean,
    time: LocalTime,
    vm: DailyDigestSettingsViewModel = viewModel(),
) {
    var editingTime by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(enabled) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.canNotify()) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    TratyCard(modifier = Modifier.fillMaxWidth(), backgroundColor = TratyTheme.colors.surface) {
        Column {
            TratySwitchRow(
                title = "Итог дня",
                subtitle = "Сводка расходов и напоминание о взносах на покупки",
                checked = enabled,
                onCheckedChange = { vm.setEnabled(it) },
            )
            TratyRowDivider()
            TratyPickRow(label = "Время отправки", value = time.format(digestClockFormat)) { editingTime = true }
            vm.errorMessage?.let { message ->
                Text(
                    message,
                    color = TratyTheme.colors.onSurfaceVariant,
                    style = TratyTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
                )
            }
        }
    }

    if (editingTime) {
        DigestTimeDialog(
            initialTime = time,
            onDismiss = { editingTime = false },
            onSave = {
                vm.setTime(it)
                editingTime = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DigestTimeDialog(initialTime: LocalTime, onDismiss: () -> Unit, onSave: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialTime.hour, initialMinute = initialTime.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Время отправки") },
        text = { TimeInput(state = state) },
        confirmButton = {
            TratyTextButton(onClick = { onSave(LocalTime.of(state.hour, state.minute)) }) { Text("Сохранить") }
        },
        dismissButton = { TratyTextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
