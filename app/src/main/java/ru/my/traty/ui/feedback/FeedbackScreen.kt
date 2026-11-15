package ru.my.traty.ui.feedback

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.theme.TratyTheme

/**
 * «Сообщить о проблеме»: пользователь пишет текст, приложение открывает почту с готовым письмом.
 * Сервера нет: письмо уходит из почтового приложения пользователя после его «Отправить».
 */
@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val info = remember { context.feedbackDeviceInfo() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }

    Scaffold(
        containerColor = TratyTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = "Сообщить о проблеме",
                subtitle = "Письмо разработчику",
                onBack = onBack,
            )
        },
    ) { pad ->
        FeedbackForm(
            text = text,
            onTextChange = { text = it.take(FEEDBACK_MAX_LENGTH) },
            info = info,
            onSend = {
                val letter = buildFeedbackLetter(text, info) ?: return@FeedbackForm
                if (!context.openFeedbackMail(letter)) {
                    scope.launch {
                        snackbar.showSnackbar("На телефоне нет почтового приложения. Установите почту, например Gmail, и попробуйте снова.")
                    }
                }
            },
            modifier = Modifier.padding(pad).consumeWindowInsets(pad),
        )
    }
}

@Composable
private fun FeedbackForm(
    text: String,
    onTextChange: (String) -> Unit,
    info: FeedbackDeviceInfo,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
    ) {
        Text(
            "Опишите, что случилось: что вы делали и что пошло не так. " +
                "После «Отправить» откроется почта с готовым письмом, там останется нажать «Отправить» ещё раз.",
            style = TratyTheme.typography.bodyMedium,
            color = TratyTheme.colors.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text("Что случилось") },
            minLines = 6,
            modifier = Modifier.fillMaxWidth().heightIn(min = TratyTheme.spacing.huge * 3),
            shape = TratyTheme.shapes.small,
            supportingText = { Text("${text.length} / $FEEDBACK_MAX_LENGTH") },
        )
        Text(
            "К письму добавится: версия приложения ${info.appVersion}, телефон ${info.device}, Android ${info.android}. " +
                "Траты и другие данные не отправляются.",
            style = TratyTheme.typography.bodySmall,
            color = TratyTheme.colors.onSurfaceVariant,
        )
        TratyButton(
            onClick = onSend,
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
            Text("Отправить", modifier = Modifier.padding(start = TratyTheme.spacing.sm))
        }
    }
}
