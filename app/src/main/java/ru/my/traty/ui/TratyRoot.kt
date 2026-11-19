package ru.my.traty.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import ru.my.traty.ui.budget.BudgetScreen
import ru.my.traty.ui.weekly.WeeklyTasksScreen
import ru.my.traty.ui.assistant.AssistantScreen
import ru.my.traty.ui.charts.ChartsScreen
import ru.my.traty.ui.designsystem.components.TratySnackbar
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.capture.EntryConfirmSheet
import ru.my.traty.ui.capture.RuleOfferDialog
import ru.my.traty.ui.capture.PurchaseOfferHost
import ru.my.traty.ui.capture.RecurringOfferHost
import ru.my.traty.ui.capture.RecurringOfferViewModel
import ru.my.traty.ui.feedback.FeedbackScreen
import ru.my.traty.ui.home.EditExpenseOverlay
import ru.my.traty.ui.home.ExpenseItemsViewModel
import ru.my.traty.ui.home.HomeScreen
import ru.my.traty.ui.purchases.PurchaseGoalScreen
import ru.my.traty.ui.navigation.TratyNavTab
import ru.my.traty.ui.help.HelpScreen
import ru.my.traty.ui.whatsnew.WhatsNewSheet
import ru.my.traty.ui.whatsnew.WhatsNewViewModel
import ru.my.traty.ui.onboarding.OnboardingScreen
import ru.my.traty.ui.onboarding.OnboardingViewModel
import ru.my.traty.data.onboarding.OnboardingExit
import ru.my.traty.ui.debt.DebtDetailScreen
import ru.my.traty.ui.debt.DebtsListScreen
import ru.my.traty.ui.reconcile.ReconcileMode
import ru.my.traty.ui.reconcile.ReconcileScreen
import ru.my.traty.ui.recurring.RecurringScreen
import ru.my.traty.ui.settings.AiSettingsScreen
import ru.my.traty.ui.settings.RulesScreen
import ru.my.traty.ui.settings.SettingsScreen
import ru.my.traty.ui.stats.StatsScreen
import ru.my.traty.ui.receipt.ReceiptOverlay

/**
 * Корневой контейнер приложения Traty с нижней навигацией: Траты, Графики, Статистика, ИИ, Настройки.
 */
@Composable
fun TratyRoot(
    vm: MainViewModel = viewModel(),
    revealPurchaseGoals: Boolean = false,
    onPurchaseGoalsRevealed: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(TratyNavTab.HOME) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var showApartments by rememberSaveable { mutableStateOf(false) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var showRecurring by rememberSaveable { mutableStateOf(false) }
    var showDebtsList by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    // Тема справки, которую нужно сразу раскрыть (из «Что нового»)
    var helpTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    val whatsNew: WhatsNewViewModel = viewModel()
    val whatsNewEntries by whatsNew.entries.collectAsStateWithLifecycle()
    var selectedDebtId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showBudget by rememberSaveable { mutableStateOf(false) }
    var showWeekly by rememberSaveable { mutableStateOf(false) }
    var showPurchaseGoals by rememberSaveable { mutableStateOf(false) }
    var showAiSettings by rememberSaveable { mutableStateOf(false) }
    var showFeedback by rememberSaveable { mutableStateOf(false) }
    // null - экран сверки закрыт
    var reconcileMode by rememberSaveable { mutableStateOf<ReconcileMode?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val onboarding: OnboardingViewModel = viewModel()
    val itemsVm: ExpenseItemsViewModel = viewModel()
    val onboardingVisible by onboarding.visible.collectAsStateWithLifecycle()
    val onboardingPeriodStart by onboarding.periodStartDay.collectAsStateWithLifecycle()
    // «Записать первую трату» из знакомства: лента включит микрофон и сбросит флаг
    var startRecording by remember { mutableStateOf(false) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val labels by vm.labels.collectAsStateWithLifecycle()

    LaunchedEffect(revealPurchaseGoals) {
        if (revealPurchaseGoals) {
            tab = TratyNavTab.HOME
            showPurchaseGoals = false
        }
    }

    BackHandler(enabled = tab != TratyNavTab.HOME) {
        tab = TratyNavTab.HOME
    }

    LaunchedEffect(vm.message) {
        val msg = vm.message ?: return@LaunchedEffect
        val hasUndo = vm.deletedForUndo != null
        val hasEdit = vm.lastSaved != null
        val result = try {
            withTimeoutOrNull(4.seconds) {
                snackbar.showSnackbar(
                    message = msg,
                    actionLabel = when {
                        hasUndo -> "Отменить"
                        hasEdit -> "Изменить"
                        else -> null
                    },
                    withDismissAction = !hasUndo && !hasEdit,
                    duration = SnackbarDuration.Short,
                )
            }
        } catch (e: CancellationException) {
            // Сообщение сменилось — старый эффект не должен стирать состояние нового (lastSaved, message)
            throw e
        } catch (_: Exception) {
            null
        }
        val acted = result == SnackbarResult.ActionPerformed
        when {
            acted && hasUndo -> vm.undoDelete()
            acted && hasEdit -> vm.editLastSaved()
            else -> vm.clearDeletedForUndo()
        }
        vm.lastSaved = null
        vm.message = null
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbar,
                    snackbar = { data -> TratySnackbar(data) },
                )
            },
            containerColor = TratyTheme.colors.background,
            bottomBar = {
                if (!showApartments) TratyBottomBar(tab, onSelect = { tab = it })
            },
        ) { pad ->
            Box(
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .fillMaxSize(),
            ) {
                when (tab) {
                    TratyNavTab.HOME -> HomeScreen(
                        vm,
                        onOpenReconcile = { reconcileMode = ReconcileMode.NORMAL },
                        onOpenBudget = { showBudget = true },
                        startRecording = startRecording,
                        onStartRecordingHandled = { startRecording = false },
                        onOpenWeekly = { showWeekly = true },
                        onOpenPurchases = { showPurchaseGoals = true },
                        revealPurchaseGoals = revealPurchaseGoals,
                        onPurchaseGoalsRevealed = onPurchaseGoalsRevealed,
                    )
                    TratyNavTab.CHARTS -> ChartsScreen()
                    TratyNavTab.STATS -> StatsScreen(
                        vm,
                        onOpenBudget = { showBudget = true },
                        onOpenDebts = { showDebtsList = true },
                        onOpenDebt = { selectedDebtId = it },
                    )
                    TratyNavTab.ASSISTANT -> AssistantScreen(
                        onOpenBudget = { showBudget = true },
                        onOpenRecurring = { showRecurring = true },
                        onOpenDebts = { showDebtsList = true },
                        onOpenExpense = { vm.editing = it },
                    )
                    TratyNavTab.SETTINGS -> SettingsScreen(
                        vm,
                        onOpenCategories = { showCategories = true },
                        onOpenApartments = { showApartments = true },
                        onOpenReconcile = { reconcileMode = ReconcileMode.NORMAL },
                        onRedoReconcile = { reconcileMode = ReconcileMode.REDO },
                        onOpenRules = { showRules = true },
                        onOpenRecurring = { showRecurring = true },
                        onOpenDebts = { showDebtsList = true },
                        onOpenBudget = { showBudget = true },
                        onOpenAi = { showAiSettings = true },
                        onOpenOnboarding = onboarding::show,
                        onOpenHelp = { showHelp = true },
                        onOpenFeedback = { showFeedback = true },
                    )
                }
                if (showApartments) {
                    Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                        ru.my.traty.ui.apartments.ApartmentsRoute(onBack = { showApartments = false })
                    }
                }
            }
        }

        // Экран категорий открывается поверх, сохраняя состояние остальных экранов
        if (showCategories) {
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                CategoriesScreen(onBack = { showCategories = false })
            }
        }

        if (showRecurring) {
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                RecurringScreen(onBack = { showRecurring = false })
            }
        }

        if (showBudget) {
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                BudgetScreen(onBack = { showBudget = false }, onOpenWeekly = { showWeekly = true })
            }
        }

        if (showPurchaseGoals) {
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                PurchaseGoalScreen(onBack = { showPurchaseGoals = false })
            }
        }

        if (showWeekly) {
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                WeeklyTasksScreen(onBack = { showWeekly = false })
            }
        }

        if (showRules) {
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                RulesScreen(vm = vm, onBack = { showRules = false })
            }
        }

        if (showAiSettings) {
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                AiSettingsScreen(vm = vm, onBack = { showAiSettings = false })
            }
        }

        if (showFeedback) {
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                FeedbackScreen(onBack = { showFeedback = false })
            }
        }

        // Редактирование траты — отдельный экран поверх вкладок, как «Подписки»
        vm.editing?.let { item ->
            EditExpenseOverlay(
                item = item,
                vm = vm,
                itemsVm = itemsVm,
                categories = categories,
                labels = labels[item.expense.id].orEmpty(),
                onOpenDebt = { debtId -> selectedDebtId = debtId },
            )
        }

        if (showHelp) {
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                HelpScreen(
                    initialTopicId = helpTopicId,
                    onBack = {
                        showHelp = false
                        helpTopicId = null
                    },
                    onOpenOnboarding = {
                        showHelp = false
                        onboarding.show()
                    },
                )
            }
        }

        if (showDebtsList) {
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                DebtsListScreen(
                    onBack = { showDebtsList = false },
                    onSelectDebt = { debtId -> selectedDebtId = debtId },
                )
            }
        }

        selectedDebtId?.let { debtId ->
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                DebtDetailScreen(
                    debtId = debtId,
                    onBack = { selectedDebtId = null },
                    onOpenExpense = { exp -> vm.editing = exp },
                )
            }
        }

        reconcileMode?.let { mode ->
            Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                ReconcileScreen(mode = mode, onBack = { reconcileMode = null })
            }
        }

        // Чек по фото: ожидание разбора и обязательная проверка перед сохранением
        ReceiptOverlay(categories = categories, onSave = { vm.saveEntry(it) })

        // Знакомство поверх всего. Пока проверяем, показывать ли его, — пустой фон, чтобы лента не мелькнула
        when (onboardingVisible) {
            null -> Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {}
            true -> Surface(Modifier.fillMaxSize(), color = TratyTheme.colors.background) {
                OnboardingScreen(
                    periodStartDay = onboardingPeriodStart,
                    onPeriodStart = onboarding::setPeriodStart,
                    onFinish = { exit ->
                        onboarding.finish()
                        when (exit) {
                            OnboardingExit.RECORD -> {
                                tab = TratyNavTab.HOME
                                startRecording = true
                            }
                            OnboardingExit.CONNECT_AI -> showAiSettings = true
                            OnboardingExit.BROWSE -> Unit
                        }
                    },
                )
            }
            false -> Unit
        }
    }

    // После обновления: один раз показать новинки, но не поверх знакомства
    if (onboardingVisible == false && whatsNewEntries.isNotEmpty()) {
        WhatsNewSheet(
            entries = whatsNewEntries,
            onDismiss = whatsNew::dismiss,
            onOpenTopic = { id ->
                helpTopicId = id
                showHelp = true
                whatsNew.dismiss()
            },
        )
    }

    vm.confirm?.let { d ->
        EntryConfirmSheet(
            draft = d,
            categories = categories,
            notice = vm.confirmNotice,
            onDismiss = vm::dismissConfirm,
            onSave = { vm.saveEntry(it) },
        )
    }

    // «Добавить в Подписки?» — после окна проверки и вопроса о правиле, чтобы диалоги не накладывались
    RecurringOfferHost(visible = vm.confirm == null && vm.ruleOffer == null)
    // «Доплата за холодильник?» / «Покупка частями?» — после окна проверки, вопроса о правиле и о подписке
    val recurringOffer by viewModel<RecurringOfferViewModel>().offer.collectAsStateWithLifecycle()
    PurchaseOfferHost(visible = vm.confirm == null && vm.ruleOffer == null && recurringOffer == null)

    vm.ruleOffer?.let { offer ->
        RuleOfferDialog(
            offer = offer,
            onRemember = {
                vm.addRule(offer.place, offer.categoryId, offer.subcategoryId)
                vm.ruleOffer = null
            },
            onDismiss = { vm.ruleOffer = null },
        )
    }
}
