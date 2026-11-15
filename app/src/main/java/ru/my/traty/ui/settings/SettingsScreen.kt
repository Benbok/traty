package ru.my.traty.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import android.Manifest
import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import ru.my.traty.data.recurring.canNotify
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.my.traty.data.AppSettings
import ru.my.traty.data.period.MAX_PERIOD_START_DAY
import ru.my.traty.data.period.MIN_PERIOD_START_DAY
import ru.my.traty.ui.MainViewModel
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Экран настроек приложения: тема, категории, резервная копия.
 */
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onOpenCategories: () -> Unit,
    onOpenReconcile: () -> Unit,
    onRedoReconcile: () -> Unit,
    onOpenRules: () -> Unit = {},
    onOpenRecurring: () -> Unit = {},
    onOpenDebts: () -> Unit = {},
    onOpenBudget: () -> Unit = {},
    onOpenAi: () -> Unit = {},
    onOpenOnboarding: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenFeedback: () -> Unit = {},
) {
    val saved by vm.settings.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCsv(uri)
    }

    Column(Modifier.fillMaxSize()) {
        TratyHeaderCard(
            title = "Настройки",
            subtitle = "Параметры приложения и данные",
            icon = Icons.Filled.Settings,
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = TratyTheme.spacing.lg,
                    end = TratyTheme.spacing.lg,
                    bottom = TratyTheme.spacing.lg,
                ),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
        ) {
        OnboardingSettingsCard(onOpenHelp = onOpenHelp, onOpen = onOpenOnboarding)

        ThemeSettingsCard()

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

        // Секция: Голос (модель GigaAM)
        VoiceSettingsCard(vm)

        // Секция: Разбор фраз и ИИ
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(Modifier.padding(TratyTheme.spacing.lg)) {
                Text("Разбор фраз и ИИ", style = TratyTheme.typography.titleMedium)
                Spacer(Modifier.height(TratyTheme.spacing.xs))
                Text(
                    "Текущий режим: ${saved.provider.title}. Подключение нейросетей (DeepSeek, YandexGPT) и ввод API-ключей для умного распознавания текста и голоса.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(TratyTheme.spacing.md))
                TratyOutlinedButton(onClick = onOpenAi) { Text("Модели и API-ключи") }
            }
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

        // Секция: Подписки
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(Modifier.padding(TratyTheme.spacing.lg)) {
                Text("Подписки и регулярные платежи", style = TratyTheme.typography.titleMedium)
                Spacer(Modifier.height(TratyTheme.spacing.xs))
                Text(
                    "Сколько уходит на подписки в месяц и в год, и какие повторяющиеся траты нашло приложение.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(TratyTheme.spacing.md))
                TratyOutlinedButton(onClick = onOpenRecurring) { Text("Открыть подписки") }
            }
        }

        // Секция: Покупки частями и кредиты
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(Modifier.padding(TratyTheme.spacing.lg)) {
                Text("Покупки частями и кредиты", style = TratyTheme.typography.titleMedium)
                Spacer(Modifier.height(TratyTheme.spacing.xs))
                Text(
                    "Рассрочки, кредиты, авансы с доплатами. Графики платежей и дерево выплат.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(TratyTheme.spacing.md))
                TratyOutlinedButton(onClick = onOpenDebts) { Text("Открыть покупки частями") }
            }
        }

        // Секция: План трат (этап 8)
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Column(Modifier.padding(TratyTheme.spacing.lg)) {
                Text("План трат", style = TratyTheme.typography.titleMedium)
                Spacer(Modifier.height(TratyTheme.spacing.xs))
                Text(
                    "Сколько хотите тратить на категорию или подкатегорию в месяц. Остаток и перерасход видны в Статистике.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(TratyTheme.spacing.md))
                TratyOutlinedButton(onClick = onOpenBudget) { Text("Открыть план") }
            }
        }

        // Секция: Итог дня
        val digestOn = saved.dailyDigestEnabled
        val context = LocalContext.current
        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        // Android 13+: без разрешения вечернее уведомление не покажется; спрашиваем, когда итог включён
        LaunchedEffect(digestOn) {
            if (digestOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.canNotify()) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = digestOn, role = Role.Switch, onValueChange = { vm.setDailyDigestEnabled(it) })
                    .padding(TratyTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Итог дня в 20:00", style = TratyTheme.typography.titleMedium)
                    Spacer(Modifier.height(TratyTheme.spacing.xs))
                    Text(
                        "Вечером приложение подскажет, потратили вы сегодня меньше или больше обычного, и сколько поступило.",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
                Switch(checked = digestOn, onCheckedChange = null)
            }
        }

        // Секция: Метки на тратах
        val labelsOn = saved.labelsEnabled
        TratyCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = TratyTheme.colors.surface,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = labelsOn, role = Role.Switch, onValueChange = { vm.setLabelsEnabled(it) })
                    .padding(TratyTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Метки на тратах", style = TratyTheme.typography.titleMedium)
                    Spacer(Modifier.height(TratyTheme.spacing.xs))
                    Text(
                        "🔺 крупная, 🔁 частая, 🆕 новое место, ⚡ импульсивная, 📅 регулярная. " +
                            "Какие категории считать импульсивными, выбирается в настройках категории.",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                }
                Switch(checked = labelsOn, onCheckedChange = null)
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

        UpdateSettingsCard()

        FeedbackSettingsCard(onOpen = onOpenFeedback)
    }
}
}
