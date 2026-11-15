package ru.my.traty.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ru.my.traty.data.period.MAX_PERIOD_START_DAY
import ru.my.traty.data.period.MIN_PERIOD_START_DAY
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.stats.ModeChip

/** Обычный день зарплаты, если человек выбрал «С зарплаты», а день ещё не задан. */
private const val DEFAULT_SALARY_DAY = 10

/** Последняя карточка: один вопрос про начало месяца. Кнопки выхода в нижней панели экрана. */
@Composable
internal fun ReadyPage(periodStartDay: Int, onPeriodStart: (fromSalary: Boolean, salaryDay: Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TratyTheme.spacing.xxl, vertical = TratyTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xxl, Alignment.CenterVertically),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md)) {
            Text("Можно начинать", style = TratyTheme.typography.headlineMedium, color = TratyTheme.colors.onBackground)
            Text(
                "Один вопрос, чтобы итоги месяца считались правильно. Его можно поменять потом в Настройках.",
                style = TratyTheme.typography.bodyLarge,
                color = TratyTheme.colors.onSurfaceVariant,
            )
        }
        PeriodStartCard(periodStartDay, onPeriodStart)
        Text(
            "Это знакомство можно открыть снова: Настройки, «Как пользоваться».",
            style = TratyTheme.typography.bodyMedium,
            color = TratyTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun PeriodStartCard(periodStartDay: Int, onPeriodStart: (Boolean, Int) -> Unit) {
    var fromSalary by rememberSaveable { mutableStateOf(periodStartDay > MIN_PERIOD_START_DAY) }
    var day by rememberSaveable {
        mutableIntStateOf(if (periodStartDay > MIN_PERIOD_START_DAY) periodStartDay else DEFAULT_SALARY_DAY)
    }
    TratyCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(TratyTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
        ) {
            Text("Когда у вас начинается месяц?", style = TratyTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                ModeChip("С 1-го числа", selected = !fromSalary) {
                    fromSalary = false
                    onPeriodStart(false, day)
                }
                ModeChip("С зарплаты", selected = fromSalary) {
                    fromSalary = true
                    onPeriodStart(true, day)
                }
            }
            if (fromSalary) SalaryDayStepper(day) { newDay ->
                day = newDay
                onPeriodStart(true, newDay)
            }
        }
    }
}

@Composable
private fun SalaryDayStepper(day: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "День зарплаты",
            style = TratyTheme.typography.bodyMedium,
            color = TratyTheme.colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TratyIconButton(
            icon = Icons.Filled.Remove,
            contentDescription = "Раньше на день",
            onClick = { onChange((day - 1).coerceAtLeast(MIN_PERIOD_START_DAY)) },
            enabled = day > MIN_PERIOD_START_DAY,
        )
        Text("$day", style = TratyTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = TratyTheme.spacing.sm))
        TratyIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "Позже на день",
            onClick = { onChange((day + 1).coerceAtMost(MAX_PERIOD_START_DAY)) },
            enabled = day < MAX_PERIOD_START_DAY,
        )
    }
}
