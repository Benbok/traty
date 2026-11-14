package ru.my.traty.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.my.traty.data.AppSettings
import ru.my.traty.data.LlmProvider
import ru.my.traty.ui.MainViewModel
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * Экран настроек разбора фраз и подключения ИИ-моделей (DeepSeek, YandexGPT, офлайн).
 */
@Composable
fun AiSettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
) {
    val saved by vm.settings.collectAsStateWithLifecycle()
    var s by remember(saved) { mutableStateOf<AppSettings>(saved) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        val msg = vm.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        vm.message = null
    }

    Scaffold(
        containerColor = TratyTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = "Разбор фраз и ИИ",
                subtitle = "Подключение моделей и API-ключи",
                onBack = onBack,
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .consumeWindowInsets(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
        ) {
            // Карточка выбора провайдера
            TratyCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = TratyTheme.colors.surface,
            ) {
                Column(Modifier.padding(TratyTheme.spacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = TratyTheme.colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(TratyTheme.spacing.sm))
                        Text("Провайдер разбора", style = TratyTheme.typography.titleMedium)
                    }

                    Spacer(Modifier.height(TratyTheme.spacing.xs))
                    Text(
                        "Выберите, какой сервис будет определять категорию, сумму и место из введенного текста или голоса.",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(TratyTheme.spacing.md))

                    LlmProvider.entries.forEach { p ->
                        val isSelected = s.provider == p
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { s = s.copy(provider = p) }
                                .padding(vertical = TratyTheme.spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { s = s.copy(provider = p) },
                            )
                            Spacer(Modifier.width(TratyTheme.spacing.sm))
                            Column {
                                Text(
                                    text = p.title,
                                    style = TratyTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) TratyTheme.colors.primary else TratyTheme.colors.onSurface,
                                )
                                val subtitle = when (p) {
                                    LlmProvider.DEEPSEEK -> "Точный и экономичный разбор через DeepSeek API"
                                    LlmProvider.YANDEX -> "Российская языковая модель YandexGPT"
                                    LlmProvider.OFFLINE -> "Работает без интернета по регулярным выражениям"
                                }
                                Text(
                                    text = subtitle,
                                    style = TratyTheme.typography.bodySmall,
                                    color = TratyTheme.colors.onSurfaceVariant,
                                )
                            }
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
                    Text("Параметры DeepSeek", style = TratyTheme.typography.titleMedium)
                    Text(
                        "Ключ можно получить на platform.deepseek.com",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(TratyTheme.spacing.xxs))
                    AiSecretField("API-ключ DeepSeek", s.deepseekKey) { s = s.copy(deepseekKey = it) }
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
                    Text("Параметры YandexGPT", style = TratyTheme.typography.titleMedium)
                    Text(
                        "Настройка сервисного аккаунта и каталога в Yandex Cloud",
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(TratyTheme.spacing.xxs))
                    AiSecretField("API-ключ Yandex Cloud", s.yandexKey) { s = s.copy(yandexKey = it) }
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

            // Кнопки управления
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
            ) {
                TratyButton(
                    onClick = {
                        vm.saveSettings(s)
                        vm.message = "Настройки сохранены"
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Сохранить")
                }
                TratyOutlinedButton(
                    onClick = { vm.testConnection(s) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Проверить")
                }
            }

            Spacer(Modifier.height(TratyTheme.spacing.xl))
        }
    }
}

@Composable
private fun AiSecretField(label: String, value: String, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            val icon = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
            val desc = if (visible) "Скрыть ключ" else "Показать ключ"
            IconButton(onClick = { visible = !visible }) {
                Icon(imageVector = icon, contentDescription = desc, modifier = Modifier.size(20.dp))
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = TratyTheme.shapes.small,
    )
}
