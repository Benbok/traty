package ru.my.traty.ui.recurring

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.CategoryWithSubs
import ru.my.traty.data.Recurring
import ru.my.traty.data.RecurringState
import ru.my.traty.data.RecurringWithNames
import ru.my.traty.data.recurring.PeriodUnit
import ru.my.traty.data.recurring.RecurringCandidate
import ru.my.traty.data.recurring.RecurringCharge
import ru.my.traty.data.recurring.RecurringSchedule
import ru.my.traty.data.recurring.canNotify
import ru.my.traty.data.recurring.nextDate
import ru.my.traty.data.recurring.period
import ru.my.traty.data.recurring.yearlyKopecks
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.formatDateWithWeekday
import java.time.temporal.ChronoUnit
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.home.CategoryPicker
import ru.my.traty.ui.home.DateField
import java.time.LocalDate

/** Подписки и регулярные платежи: итоги, предложения из истории трат, список платежей. */
@Composable
fun RecurringScreen(onBack: () -> Unit, vm: RecurringViewModel = viewModel()) {
    val payments by vm.items.collectAsStateWithLifecycle()
    val totals by vm.totals.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val upcoming by vm.upcoming.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // Android 13+: без разрешения напоминания не покажутся, спрашиваем один раз при добавлении платежа
    val askNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.canNotify()) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // null — диалог закрыт; id == 0 — новый платёж
    var editing by remember { mutableStateOf<Recurring?>(null) }
    // платёж с меняющейся суммой: перед «Списалось» спрашиваем, сколько списалось на самом деле
    var paying by remember { mutableStateOf<Recurring?>(null) }

    Column(Modifier.fillMaxSize()) {
        TratyHeaderCard(
            title = "Подписки",
            subtitle = "Регулярные платежи",
            onBack = onBack,
            actions = {
                TratyIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "Добавить платёж",
                    onClick = { editing = newRecurring(categories) },
                    containerColor = TratyTheme.colors.primaryContainer,
                    tint = TratyTheme.colors.primary,
                    modifier = Modifier.size(40.dp),
                )
            },
        )

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = TratyTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
        ) {
            item {
                Spacer(Modifier.height(TratyTheme.spacing.xs))
                TotalsCard(totals.count, totals.monthlyKopecks, totals.yearlyKopecks, totals.approximate)
            }

            if (upcoming.isNotEmpty()) {
                item { UpcomingCard(upcoming, payments.associate { it.recurring.id to it.categoryEmoji }) }
            }

            if (suggestions.isNotEmpty()) {
                item {
                    Text("Похоже на подписку", style = TratyTheme.typography.titleMedium)
                }
                items(suggestions, key = { it.key }) { s ->
                    SuggestionCard(
                        candidate = s,
                        emoji = categories.firstOrNull { it.category.id == s.categoryId }?.category?.emoji.orEmpty(),
                        onAdd = {
                            askNotifications()
                            vm.accept(s)
                        },
                        onDismiss = { vm.dismiss(s) },
                    )
                }
            }

            if (payments.isEmpty() && suggestions.isEmpty()) {
                item {
                    Text(
                        "Платежей пока нет. Добавьте подписку кнопкой «+» или вводите траты как обычно: " +
                            "если трата повторяется каждый месяц, приложение предложит добавить её сюда.",
                        style = TratyTheme.typography.bodyMedium,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
            }

            items(payments, key = { it.recurring.id }) { r ->
                RecurringRow(
                    r,
                    onEdit = { editing = r.recurring },
                    onMarkPaid = { if (r.recurring.amountVaries) paying = r.recurring else vm.markPaid(r.recurring) },
                    onTogglePause = { vm.setPaused(r.recurring, r.recurring.state == RecurringState.ACTIVE) },
                    onDelete = { vm.remove(r.recurring) },
                )
            }
            item { Spacer(Modifier.height(TratyTheme.spacing.xxl)) }
        }
    }

    paying?.let { item ->
        PaidAmountDialog(
            item = item,
            onDismiss = { paying = null },
            onConfirm = { kopecks ->
                vm.markPaid(item, kopecks)
                paying = null
            },
        )
    }

    editing?.let { initial ->
        RecurringEditDialog(
            initial = initial,
            categories = categories,
            onDismiss = { editing = null },
            onSave = {
                if (it.remindDaysBefore > 0) askNotifications()
                vm.save(it)
                editing = null
            },
        )
    }
}

private fun newRecurring(categories: List<CategoryWithSubs>): Recurring = Recurring(
    title = "",
    amountKopecks = 0,
    categoryId = categories.firstOrNull { it.category.name == "Связь и подписки" }?.category?.id
        ?: categories.firstOrNull()?.category?.id
        ?: 0,
    periodUnit = PeriodUnit.MONTH.key,
    nextEpochDay = LocalDate.now().plusMonths(1).toEpochDay(),
)

@Composable
private fun TotalsCard(count: Int, monthly: Long, yearly: Long, approximate: Boolean) {
    val sign = if (approximate) "≈ " else ""
    TratyCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(TratyTheme.spacing.lg)) {
            Text("Подписки и платежи в месяц", style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
            Text(sign + formatRub(monthly), style = TratyTheme.typography.titleMedium)
            Spacer(Modifier.height(TratyTheme.spacing.xs))
            Text(
                "≈ ${formatRub(yearly)} в год · действующих платежей: $count" +
                    if (approximate) " (часть сумм меняется)" else "",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
    }
}

/** Календарь списаний на 30 дней вперёд: даты по порядку, просроченные — первыми. */
@Composable
private fun UpcomingCard(charges: List<RecurringCharge>, emojiById: Map<Long, String>) {
    val today = LocalDate.now()
    TratyCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(TratyTheme.spacing.lg)) {
            Text("Ближайшие списания", style = TratyTheme.typography.titleSmall)
            Text(
                "На ${RecurringSchedule.CALENDAR_DAYS} дней вперёд: ${formatRub(charges.sumOf { it.item.amountKopecks })}",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(TratyTheme.spacing.sm))
            charges.forEach { c ->
                val days = ChronoUnit.DAYS.between(today, c.date)
                Row(Modifier.fillMaxWidth().padding(vertical = TratyTheme.spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${emojiById[c.item.id].orEmpty()} ${c.item.title}",
                        style = TratyTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatRub(c.item.amountKopecks), style = TratyTheme.typography.bodyMedium)
                        Text(
                            when {
                                c.overdue -> "просрочено на ${-days} дн."
                                days == 0L -> "сегодня"
                                days == 1L -> "завтра"
                                else -> formatDateWithWeekday(c.date)
                            },
                            style = TratyTheme.typography.bodySmall,
                            color = if (c.overdue) TratyTheme.colors.expense else TratyTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(candidate: RecurringCandidate, emoji: String, onAdd: () -> Unit, onDismiss: () -> Unit) {
    TratyCard(Modifier.fillMaxWidth(), backgroundColor = TratyTheme.colors.surfaceContainer) {
        Column(Modifier.padding(TratyTheme.spacing.lg)) {
            Text(
                "$emoji ${candidate.title}: ${formatRub(candidate.amountKopecks)} ${candidate.period.label()}",
                style = TratyTheme.typography.titleSmall,
            )
            Text(
                "Списаний в истории: ${candidate.charges}, последнее ${formatDateWithWeekday(candidate.lastDate)}",
                style = TratyTheme.typography.bodySmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(TratyTheme.spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                TratyButton(onClick = onAdd) { Text("Добавить") }
                TratyOutlinedButton(onClick = onDismiss) { Text("Не подписка") }
            }
        }
    }
}

@Composable
private fun RecurringRow(
    r: RecurringWithNames,
    onEdit: () -> Unit,
    onMarkPaid: () -> Unit,
    onTogglePause: () -> Unit,
    onDelete: () -> Unit,
) {
    val item = r.recurring
    val active = item.state == RecurringState.ACTIVE
    TratyCard(Modifier.fillMaxWidth(), onClick = onEdit) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${r.categoryEmoji} ${item.title}", style = TratyTheme.typography.titleSmall)
                    Text(
                        "${if (item.amountVaries) "≈ " else ""}${formatRub(item.amountKopecks)} ${item.period().label()} · " +
                            if (active) "следующее ${formatDateWithWeekday(item.nextDate())}" else "на паузе",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                    Text(
                        "Отмена сэкономит ${formatRub(item.yearlyKopecks)} в год",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
                TratyIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Удалить",
                    onClick = onDelete,
                    tint = TratyTheme.colors.expense,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                if (active) TratyTextButton(onClick = onMarkPaid) { Text("Списалось") }
                TratyTextButton(onClick = onTogglePause) { Text(if (active) "Пауза" else "Возобновить") }
            }
        }
    }
}

private val UNIT_CHIPS = listOf(
    PeriodUnit.DAY to "Дни",
    PeriodUnit.WEEK to "Недели",
    PeriodUnit.MONTH to "Месяцы",
    PeriodUnit.YEAR to "Годы",
)
private val REMIND_CHIPS = listOf(0 to "Нет", 1 to "За 1 день", 3 to "За 3 дня", 7 to "За 7 дней")

@Composable
private fun RecurringEditDialog(
    initial: Recurring,
    categories: List<CategoryWithSubs>,
    onDismiss: () -> Unit,
    onSave: (Recurring) -> Unit,
) {
    val isNew = initial.id == 0L
    var title by remember { mutableStateOf(initial.title) }
    var amount by remember { mutableStateOf(if (initial.amountKopecks > 0) (initial.amountKopecks / 100.0).toString().removeSuffix(".0") else "") }
    var categoryName by remember {
        mutableStateOf(categories.firstOrNull { it.category.id == initial.categoryId }?.category?.name ?: categories.firstOrNull()?.category?.name.orEmpty())
    }
    var unit by remember { mutableStateOf(PeriodUnit.of(initial.periodUnit)) }
    var every by remember { mutableStateOf(initial.periodEvery.toString()) }
    var dateText by remember { mutableStateOf(LocalDate.ofEpochDay(initial.nextEpochDay).toString()) }
    var remind by remember { mutableStateOf(initial.remindDaysBefore) }
    var varies by remember { mutableStateOf(initial.amountVaries) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = { Text(if (isNew) "Новый платёж" else "Платёж", style = TratyTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название (напр. Яндекс Плюс)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Сумма, ₽") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )
                CategoryPicker(categoryName, categories, null) { categoryName = it }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
                ) {
                    UNIT_CHIPS.forEach { (u, label) ->
                        FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(label) })
                    }
                }
                OutlinedTextField(
                    value = every,
                    onValueChange = { every = it.filter(Char::isDigit).take(3) },
                    label = { Text("Раз в … (1 — каждый период)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )
                FilterChip(
                    selected = varies,
                    onClick = { varies = !varies },
                    label = { Text("Сумма меняется (коммуналка)") },
                )
                DateField(dateText = dateText, onDateTextChange = { dateText = it })
                Text("Напомнить", style = TratyTheme.typography.bodySmall, color = TratyTheme.colors.onSurfaceVariant)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
                ) {
                    REMIND_CHIPS.forEach { (days, label) ->
                        FilterChip(selected = remind == days, onClick = { remind = days }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            TratyButton(
                onClick = {
                    val rub = amount.replace(",", ".").replace(" ", "").toDoubleOrNull()
                    val cat = categories.firstOrNull { it.category.name == categoryName }
                    val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
                    val n = every.toIntOrNull()?.takeIf { it >= 1 }
                    if (title.isNotBlank() && rub != null && rub > 0 && cat != null && date != null && n != null) {
                        onSave(
                            initial.copy(
                                title = title,
                                amountKopecks = Math.round(rub * 100),
                                categoryId = cat.category.id,
                                subcategoryId = if (cat.category.id == initial.categoryId) initial.subcategoryId else null,
                                periodUnit = unit.key,
                                periodEvery = n,
                                nextEpochDay = date.toEpochDay(),
                                remindDaysBefore = remind,
                                amountVaries = varies,
                            )
                        )
                    }
                },
            ) { Text(if (isNew) "Добавить" else "Сохранить") }
        },
        dismissButton = { TratyTextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun PaidAmountDialog(item: Recurring, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var amount by remember { mutableStateOf((item.amountKopecks / 100.0).toString().removeSuffix(".0")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = TratyTheme.shapes.large,
        containerColor = TratyTheme.colors.surface,
        title = { Text("Сколько списалось?", style = TratyTheme.typography.titleMedium) },
        text = {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("${item.title}, ₽") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = TratyTheme.shapes.small,
            )
        },
        confirmButton = {
            TratyButton(
                onClick = {
                    val rub = amount.replace(",", ".").replace(" ", "").toDoubleOrNull()
                    if (rub != null && rub > 0) onConfirm(Math.round(rub * 100))
                },
            ) { Text("Списалось") }
        },
        dismissButton = { TratyTextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
