package ru.my.traty.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.my.traty.data.AppSettings
import ru.my.traty.data.LlmProvider
import ru.my.traty.data.ThemeMode
import ru.my.traty.data.period.MAX_PERIOD_START_DAY
import ru.my.traty.data.period.MIN_PERIOD_START_DAY
import ru.my.traty.ui.MainViewModel
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Экран настроек приложения: тема, провайдер ИИ, категории, резервная копия.
 */
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onOpenCategories: () -> Unit,
    onOpenReconcile: () -> Unit,
    onRedoReconcile: () -> Unit,
    onOpenTemplates: () -> Unit = {},
    onOpenRules: () -> Unit = {},
) {
    val saved by vm.settings.collectAsStateWithLifecycle()
    var s by remember(saved) { mutableStateOf<AppSettings>(saved) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCsv(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(TratyTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
    ) {
        // Секция: Тема оформления
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(Modifier.padding(TratyTheme.spacing.lg)) {
                Text("Тема оформления", style = TratyTheme.typography.titleMedium)
                Spacer(Modifier.height(TratyTheme.spacing.xs))
                Text(
                    "Выберите светлую, тёмную или системную тему.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(TratyTheme.spacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val isSelected = s.themeMode == mode
                        val icon = when (mode) {
                            ThemeMode.LIGHT -> Icons.Filled.LightMode
                            ThemeMode.DARK -> Icons.Filled.DarkMode
                            ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                        }
                        TratyCard(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    s = s.copy(themeMode = mode)
                                    vm.setThemeMode(mode)
                                },
                            backgroundColor = if (isSelected) TratyTheme.colors.primaryContainer else TratyTheme.colors.surfaceContainer,
                            border = if (isSelected) BorderStroke(1.5.dp, TratyTheme.colors.primary) else null,
                            shape = TratyTheme.shapes.medium,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = TratyTheme.spacing.md),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = mode.title,
                                    tint = if (isSelected) TratyTheme.colors.primary else TratyTheme.colors.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.height(TratyTheme.spacing.xs))
                                Text(
                                    text = mode.title,
                                    style = TratyTheme.typography.labelMedium,
                                    color = if (isSelected) TratyTheme.colors.primary else TratyTheme.colors.onSurface,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Секция: День начала расчётного периода
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(Modifier.padding(TratyTheme.spacing.lg)) {
                Text("День начала периода", style = TratyTheme.typography.titleMedium)
                Spacer(Modifier.height(TratyTheme.spacing.xs))
                Text(
                    "1 — обычный календарный месяц; укажите день зарплаты, чтобы вести учёт от зарплаты до зарплаты",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(TratyTheme.spacing.md))
                val day = saved.periodStartDay
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TratyIconButton(
                        icon = Icons.Filled.Remove,
                        contentDescription = "Уменьшить день начала периода",
                        onClick = { vm.setPeriodStartDay(day - 1) },
                        enabled = day > MIN_PERIOD_START_DAY,
                        containerColor = TratyTheme.colors.surfaceContainer,
                    )
                    Text(
                        text = day.toString(),
                        style = TratyTheme.typography.headlineLarge,
                        color = TratyTheme.colors.onSurface,
                        modifier = Modifier.width(72.dp),
                        textAlign = TextAlign.Center,
                    )
                    TratyIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "Увеличить день начала периода",
                        onClick = { vm.setPeriodStartDay(day + 1) },
                        enabled = day < MAX_PERIOD_START_DAY,
                        containerColor = TratyTheme.colors.surfaceContainer,
                    )
                }
            }
        }

        // Секция: Провайдер ИИ
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(Modifier.padding(TratyTheme.spacing.lg)) {
                Text("Разбор фраз", style = TratyTheme.typography.titleMedium)
                Spacer(Modifier.height(TratyTheme.spacing.sm))
                LlmProvider.entries.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { s = s.copy(provider = p) }
                            .padding(vertical = TratyTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.provider == p, onClick = { s = s.copy(provider = p) })
                        Spacer(Modifier.width(TratyTheme.spacing.sm))
                        Text(p.title, style = TratyTheme.typography.bodyLarge)
                    }
                }
            }
        }

        // Секция: DeepSeek
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(
                Modifier.padding(TratyTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
            ) {
                Text("DeepSeek", style = TratyTheme.typography.titleMedium)
                SecretField("API-ключ DeepSeek", s.deepseekKey) { s = s.copy(deepseekKey = it) }
                OutlinedTextField(
                    value = s.deepseekModel,
                    onValueChange = { s = s.copy(deepseekModel = it) },
                    label = { Text("Модель") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )
            }
        }

        // Секция: YandexGPT
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(
                Modifier.padding(TratyTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
            ) {
                Text("YandexGPT", style = TratyTheme.typography.titleMedium)
                SecretField("API-ключ Yandex Cloud", s.yandexKey) { s = s.copy(yandexKey = it) }
                OutlinedTextField(
                    value = s.yandexFolderId,
                    onValueChange = { s = s.copy(yandexFolderId = it) },
                    label = { Text("ID каталога (folder id)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )
                OutlinedTextField(
                    value = s.yandexModel,
                    onValueChange = { s = s.copy(yandexModel = it) },
                    label = { Text("Модель (напр. yandexgpt-lite/latest)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = TratyTheme.shapes.small,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md)) {
            TratyButton(onClick = { vm.saveSettings(s) }) { Text("Сохранить") }
            TratyOutlinedButton(onClick = { vm.testConnection(s) }) { Text("Проверить") }
        }

        // Секция: Категории
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(Modifier.padding(TratyTheme.spacing.lg)) {
                Text("Категории", style = TratyTheme.typography.titleMedium)
                Spacer(Modifier.height(TratyTheme.spacing.xs))
                Text(
                    "Переименование, эмодзи, порядок, архив, объединение и подкатегории.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(TratyTheme.spacing.md))
                TratyOutlinedButton(onClick = onOpenCategories) { Text("Категории и подкатегории") }
            }
        }

        // Секция: Шаблоны
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(Modifier.padding(TratyTheme.spacing.lg)) {
                Text("Шаблоны быстрых трат", style = TratyTheme.typography.titleMedium)
                Spacer(Modifier.height(TratyTheme.spacing.xs))
                Text(
                    "Чипсы над строкой ввода: одно нажатие — трата сохранена.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(TratyTheme.spacing.md))
                TratyOutlinedButton(onClick = onOpenTemplates) { Text("Управление шаблонами") }
            }
        }

        // Секция: Правила
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(Modifier.padding(TratyTheme.spacing.lg)) {
                Text("Правила категоризации", style = TratyTheme.typography.titleMedium)
                Spacer(Modifier.height(TratyTheme.spacing.xs))
                Text(
                    "Автоматическая подстановка категории по месту или слову.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(TratyTheme.spacing.md))
                TratyOutlinedButton(onClick = onOpenRules) { Text("Управление правилами") }
            }
        }

        ReconcileSettingsCard(onOpenReconcile, onRedoReconcile)

        // Секция: Резервная копия
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(Modifier.padding(TratyTheme.spacing.lg)) {
                Text("Резервная копия", style = TratyTheme.typography.titleMedium)
                Spacer(Modifier.height(TratyTheme.spacing.xs))
                Text(
                    "Все данные хранятся только на телефоне. Периодически выгружайте CSV (открывается в Excel). " +
                        "Импорт добавляет траты из файла и пропускает те, что уже есть.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(TratyTheme.spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm)) {
                    TratyOutlinedButton(onClick = { exportLauncher.launch("traty-${java.time.LocalDate.now()}.csv") }) {
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
    }
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        shape = TratyTheme.shapes.small,
    )
}
