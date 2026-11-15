package ru.my.traty.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import ru.my.traty.data.onboarding.OnboardingExit
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.theme.TratyTheme

private const val PAGE_COUNT = 5

/**
 * Знакомство с приложением: пять карточек, листаются пальцем или кнопкой «Далее».
 * «Пропустить» и «Назад» на первой карточке закрывают его так же, как «Сначала осмотрюсь».
 */
@Composable
fun OnboardingScreen(
    periodStartDay: Int,
    onPeriodStart: (fromSalary: Boolean, salaryDay: Int) -> Unit,
    onFinish: (OnboardingExit) -> Unit,
) {
    val pager = rememberPagerState { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val isLast = pager.currentPage == PAGE_COUNT - 1
    BackHandler {
        if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
        else onFinish(OnboardingExit.BROWSE)
    }
    Scaffold(
        containerColor = TratyTheme.colors.background,
        topBar = { SkipBar(visible = !isLast, onSkip = { onFinish(OnboardingExit.BROWSE) }) },
        bottomBar = {
            OnboardingBottomBar(
                pager = pager,
                onNext = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                onFinish = onFinish,
            )
        },
    ) { pad ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> VoicePage()
                2 -> PlanPage()
                3 -> AssistantPage()
                else -> ReadyPage(periodStartDay, onPeriodStart)
            }
        }
    }
}

@Composable
private fun SkipBar(visible: Boolean, onSkip: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = TratyTheme.spacing.sm),
        horizontalArrangement = Arrangement.End,
    ) {
        // Место под кнопку держим и на последней карточке, чтобы содержимое не прыгало
        TratyTextButton(onClick = onSkip, enabled = visible, contentColor = TratyTheme.colors.onSurfaceVariant) {
            Text(if (visible) "Пропустить" else "")
        }
    }
}

@Composable
private fun OnboardingBottomBar(pager: PagerState, onNext: () -> Unit, onFinish: (OnboardingExit) -> Unit) {
    val isLast = pager.currentPage == PAGE_COUNT - 1
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = TratyTheme.spacing.xxl, vertical = TratyTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.md),
    ) {
        OnboardingDots(current = pager.currentPage, count = PAGE_COUNT)
        if (!isLast) {
            TratyButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Далее") }
        } else {
            TratyButton(onClick = { onFinish(OnboardingExit.RECORD) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(TratyTheme.spacing.xl))
                Spacer(Modifier.size(TratyTheme.spacing.sm))
                Text("Записать первую трату")
            }
            TratyOutlinedButton(onClick = { onFinish(OnboardingExit.CONNECT_AI) }, modifier = Modifier.fillMaxWidth()) {
                Text("Подключить ИИ")
            }
            TratyTextButton(onClick = { onFinish(OnboardingExit.BROWSE) }, contentColor = TratyTheme.colors.onSurfaceVariant) {
                Text("Сначала осмотрюсь")
            }
        }
    }
}
