package ru.my.traty.ui.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.speech.SystemSpeech
import ru.my.traty.ui.MainViewModel
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyEmptyState
import ru.my.traty.ui.designsystem.components.TratyInputBar
import ru.my.traty.ui.designsystem.components.TratyMonthPicker
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDate
import ru.my.traty.ui.formatPeriod
import ru.my.traty.ui.formatRub
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Главный экран списка трат (HomeScreen).
 * Поддерживает просмотр трат за расчётный период, выбор конкретной даты в календаре,
 * фильтрацию по дню и быстрый ввод трат текстом/голосом.
 */
@Composable
fun HomeScreen(vm: MainViewModel) {
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val selectedDate by vm.selectedDate.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }

    var toDelete by remember { mutableStateOf<ExpenseWithCategory?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val displayedExpenses = remember(expenses, selectedDate) {
        if (selectedDate == null) {
            expenses
        } else {
            expenses.filter { e ->
                val expenseDate = Instant.ofEpochMilli(e.expense.timestamp)
                    .atZone(zone)
                    .toLocalDate()
                expenseDate == selectedDate
            }
        }
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) vm.onVoiceResult(text)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TratyMonthPicker(
            periodLabel = formatPeriod(period, settings.periodStartDay),
            totalKopecks = displayedExpenses.sumOf { it.expense.amountKopecks },
            selectedDate = selectedDate,
            onPrev = vm::prevPeriod,
            onNext = vm::nextPeriod,
            onDateClick = { showDatePicker = true },
            onClearDate = { vm.clearSelectedDate() },
        )

        // Чип активного фильтра по конкретной дате
        if (selectedDate != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xxs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InputChip(
                    selected = true,
                    onClick = { showDatePicker = true },
                    label = { Text("Дата: ${formatDate(selectedDate!!)}") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Сбросить фильтр",
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    colors = InputChipDefaults.inputChipColors(
                        selectedContainerColor = TratyTheme.colors.primaryContainer,
                        selectedLabelColor = TratyTheme.colors.primary,
                        selectedLeadingIconColor = TratyTheme.colors.primary,
                        selectedTrailingIconColor = TratyTheme.colors.primary,
                    ),
                    shape = TratyTheme.shapes.small,
                )

                TratyTextButton(
                    onClick = vm::clearSelectedDate,
                ) {
                    Text("Весь период", style = TratyTheme.typography.labelSmall)
                }
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (displayedExpenses.isEmpty()) {
                item {
                    if (selectedDate != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = TratyTheme.spacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            TratyEmptyState(
                                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                title = "За ${formatDate(selectedDate!!)} трат нет",
                                description = "Нажмите «Показать весь период», чтобы увидеть все траты периода.",
                            )
                            Spacer(Modifier.height(TratyTheme.spacing.md))
                            TratyButton(onClick = vm::clearSelectedDate) {
                                Text("Показать весь период")
                            }
                        }
                    } else {
                        TratyEmptyState(
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            title = "Трат пока нет",
                            description = "Скажите или напишите, например: «кофе 250 и такси 400».",
                        )
                    }
                }
            }
            items(displayedExpenses, key = { it.expense.id }) { e ->
                ExpenseRow(e, onClick = { toDelete = e })
                HorizontalDivider(
                    color = TratyTheme.colors.borderSubtle,
                    modifier = Modifier.padding(horizontal = TratyTheme.spacing.lg),
                )
            }
        }

        TratyInputBar(
            value = vm.input,
            onValueChange = { vm.input = it },
            onManualClick = vm::manualDraft,
            onVoiceClick = {
                try {
                    speech.launch(SystemSpeech.intent())
                } catch (e: ActivityNotFoundException) {
                    vm.message = "На телефоне нет сервиса распознавания речи"
                }
            },
            onSendClick = vm::parseInput,
            isBusy = vm.busy,
        )
    }

    if (showDatePicker) {
        DatePickerModal(
            initialDate = selectedDate ?: LocalDate.now(),
            onDateSelected = { pickedDate ->
                vm.selectDate(pickedDate)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            shape = TratyTheme.shapes.large,
            containerColor = TratyTheme.colors.surface,
            title = { Text("Удалить трату?", style = TratyTheme.typography.titleMedium) },
            text = {
                Text(
                    "${e.categoryName}: ${formatRub(e.expense.amountKopecks)}\n${e.expense.note ?: e.expense.rawText ?: ""}",
                    style = TratyTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TratyTextButton(
                    onClick = { vm.delete(e.expense); toDelete = null },
                    contentColor = TratyTheme.colors.expense,
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TratyTextButton(onClick = { toDelete = null }) {
                    Text("Отмена")
                }
            },
        )
    }
}
