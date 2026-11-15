package ru.my.traty.ui.debt

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.my.traty.data.ExpenseWithCategory
import ru.my.traty.data.debt.Debt
import ru.my.traty.data.debt.DebtKind
import ru.my.traty.data.debt.DebtState
import ru.my.traty.data.debt.DebtTree
import ru.my.traty.data.debt.DebtTreeNode
import ru.my.traty.data.debt.DebtTreeSummary
import ru.my.traty.data.debt.TreePaymentState
import ru.my.traty.ui.AmountInput
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyButton
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.components.TratyEmptyState
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyIconButton
import ru.my.traty.ui.designsystem.components.TratyOutlinedButton
import ru.my.traty.ui.designsystem.components.TratyProgressBar
import ru.my.traty.ui.designsystem.components.TratyTextButton
import ru.my.traty.ui.designsystem.components.rememberAmountFieldValue
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.designsystem.tokens.TratyPalette
import ru.my.traty.ui.formatDateNumeric
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.pluralRu

private enum class TreeFilter {
    ALL,
    OVERDUE,
    UPCOMING,
    PAID,
}

/**
 * Экран дерева и графика платежей по покупке частями (рассрочка, кредит, аванс).
 */
@Composable
fun DebtDetailScreen(
    debtId: Long,
    onBack: () -> Unit,
    onOpenExpense: (ExpenseWithCategory) -> Unit,
    vm: DebtDetailViewModel = viewModel(),
) {
    BackHandler(onBack = onBack)

    LaunchedEffect(debtId) {
        vm.load(debtId)
    }

    val debt by vm.debt.collectAsStateWithLifecycle()
    val tree by vm.tree.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()

    var filter by rememberSaveable { mutableStateOf(TreeFilter.ALL) }
    var payAmountText by rememberSaveable { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = TratyTheme.colors.background,
        topBar = {
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = debt?.title ?: "Покупка частями",
                subtitle = debt?.let { kindSubtitle(it) } ?: "Дерево платежей",
                onBack = onBack,
                actions = {
                    Box {
                        TratyIconButton(
                            icon = Icons.Filled.MoreVert,
                            contentDescription = "Действия",
                            onClick = { showMenu = true },
                        )
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            if (debt?.state == DebtState.ACTIVE) {
                                DropdownMenuItem(
                                    text = { Text("Отметить как закрытую") },
                                    onClick = {
                                        showMenu = false
                                        vm.closeDebt()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Отменить покупку (возврат)") },
                                    onClick = {
                                        showMenu = false
                                        vm.cancelDebt()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Удалить запись", color = TratyTheme.colors.expense) },
                                onClick = {
                                    showMenu = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { pad ->
        val currentDebt = debt
        val currentTree = tree

        if (currentDebt == null || currentTree == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentAlignment = Alignment.Center,
            ) {
                TratyEmptyState(
                    title = "Загрузка...",
                    description = "Получаем график и историю платежей",
                    icon = Icons.Filled.Schedule,
                )
            }
        } else {
            val summary = currentTree.summary
            val catWithSubs = categories.firstOrNull { it.category.id == currentDebt.categoryId }
            val catEmoji = catWithSubs?.category?.emoji ?: "📦"
            val catName = catWithSubs?.category?.name ?: "Покупка"

            val filteredNodes = remember(currentTree.nodes, filter) {
                when (filter) {
                    TreeFilter.ALL -> currentTree.nodes
                    TreeFilter.OVERDUE -> currentTree.nodes.filter { it.state == TreePaymentState.OVERDUE }
                    TreeFilter.UPCOMING -> currentTree.nodes.filter { it.state == TreePaymentState.UPCOMING || it.state == TreePaymentState.PARTIALLY_PAID }
                    TreeFilter.PAID -> currentTree.nodes.filter { it.state == TreePaymentState.PAID }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad),
            ) {
                // Карточка сводки покупки
                item(key = "summary") {
                    DebtSummaryCard(
                        debt = currentDebt,
                        summary = summary,
                        catEmoji = catEmoji,
                        catName = catName,
                        onPayClick = {
                            val next = currentTree.nodes.firstOrNull {
                                it.state == TreePaymentState.OVERDUE || it.state == TreePaymentState.UPCOMING || it.state == TreePaymentState.PARTIALLY_PAID
                            }
                            val prefill = next?.remainingKopecks ?: summary.remainingKopecks ?: 0L
                            payAmountText = AmountInput.fromKopecks(prefill.coerceAtLeast(100L))
                        },
                    )
                }

                // Фильтры дерева платежей
                item(key = "filters") {
                    TreeFilterChips(
                        summary = summary,
                        selected = filter,
                        onSelect = { filter = it },
                    )
                }

                // Узлы дерева платежей
                if (filteredNodes.isEmpty()) {
                    item(key = "empty_filter") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = TratyTheme.spacing.xl),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Нет платежей в этой группе",
                                style = TratyTheme.typography.bodyMedium,
                                color = TratyTheme.colors.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(filteredNodes, key = { it.id }) { node ->
                        val isFirst = node == filteredNodes.first()
                        val isLast = node == filteredNodes.last()
                        DebtTreeNodeRow(
                            node = node,
                            isFirst = isFirst,
                            isLast = isLast,
                            isLoan = currentDebt.debtKind == DebtKind.LOAN,
                            onOpenExpense = { expenseId ->
                                scope.launch {
                                    val exp = vm.getExpense(expenseId)
                                    if (exp != null) onOpenExpense(exp)
                                }
                            },
                            onPayNode = {
                                payAmountText = AmountInput.fromKopecks(node.remainingKopecks.coerceAtLeast(100L))
                            },
                        )
                    }
                }

                item(key = "bottom_space") {
                    Spacer(Modifier.height(TratyTheme.spacing.xxl))
                }
            }
        }
    }

    // Диалог внесения платежа
    payAmountText?.let { rawAmount ->
        AddPaymentDialog(
            initialAmount = rawAmount,
            onDismiss = { payAmountText = null },
            onConfirm = { kopecks ->
                vm.addPayment(kopecks)
                payAmountText = null
            },
        )
    }

    // Диалог подтверждения удаления
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить покупку?", style = TratyTheme.typography.titleMedium) },
            text = {
                Text(
                    "Запись о покупке частями будет удалена. Все уже внесённые траты останутся в вашей ленте трат, будет удалена только связь с этой покупкой.",
                    style = TratyTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TratyButton(
                    onClick = {
                        confirmDelete = false
                        vm.deleteDebt(onDeleted = onBack)
                    },
                    containerColor = TratyTheme.colors.expense,
                    contentColor = Color.White,
                ) { Text("Удалить") }
            },
            dismissButton = {
                TratyTextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun DebtSummaryCard(
    debt: Debt,
    summary: DebtTreeSummary,
    catEmoji: String,
    catName: String,
    onPayClick: () -> Unit,
) {
    TratyCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        backgroundColor = TratyTheme.colors.surface,
        border = BorderStroke(1.dp, TratyTheme.colors.borderSubtle),
    ) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            // Категория, место и бейдж статуса
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TratyCategoryBadge(
                    emoji = catEmoji,
                    categoryName = catName,
                    size = 38.dp,
                )
                Spacer(Modifier.width(TratyTheme.spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = listOfNotNull(catName, debt.place).joinToString(" · "),
                        style = TratyTheme.typography.bodySmall,
                        color = TratyTheme.colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = debt.title,
                        style = TratyTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                StateChip(debt.state)
            }

            Spacer(Modifier.height(TratyTheme.spacing.md))

            // Прогресс-бар
            val barColor = when {
                summary.isPaidOff -> TratyTheme.colors.primary
                summary.overdueCount > 0 -> TratyPalette.CoralRose
                else -> TratyTheme.colors.primary
            }
            TratyProgressBar(
                progress = summary.progressPercent,
                color = barColor,
                height = 8.dp,
            )

            Spacer(Modifier.height(TratyTheme.spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${(summary.progressPercent * 100).toInt()}% оплачено",
                    style = TratyTheme.typography.labelSmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                val countText = if (debt.paymentsCount != null) {
                    "${summary.paidCount} из ${debt.paymentsCount} платежей"
                } else {
                    "${summary.paidCount} ${pluralRu(summary.paidCount.toLong(), "платёж", "платежа", "платежей")}"
                }
                Text(
                    text = countText,
                    style = TratyTheme.typography.labelSmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(TratyTheme.spacing.md))

            // KPI метрики
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.sm),
            ) {
                MetricBox(
                    label = "Оплачено",
                    value = formatRub(summary.paidKopecks),
                    color = TratyTheme.colors.primary,
                    modifier = Modifier.weight(1f),
                )
                MetricBox(
                    label = "Осталось",
                    value = summary.remainingKopecks?.let { formatRub(it) } ?: "—",
                    color = TratyTheme.colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (summary.overdueKopecks > 0) {
                    MetricBox(
                        label = "Просрочено",
                        value = formatRub(summary.overdueKopecks),
                        color = TratyPalette.CoralRose,
                        modifier = Modifier.weight(1f),
                    )
                } else if (debt.debtKind == DebtKind.LOAN && summary.plannedOverpaymentKopecks > 0) {
                    MetricBox(
                        label = "Проценты",
                        value = formatRub(summary.plannedOverpaymentKopecks),
                        color = TratyTheme.colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Кнопка внесения платежа (если активна и не закрыта)
            if (debt.state == DebtState.ACTIVE && !summary.isPaidOff) {
                Spacer(Modifier.height(TratyTheme.spacing.md))
                TratyButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onPayClick,
                ) {
                    Icon(Icons.Filled.Payment, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(TratyTheme.spacing.xs))
                    Text("Внести платёж")
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = TratyTheme.shapes.medium,
        color = TratyTheme.colors.surfaceContainer,
    ) {
        Column(
            Modifier.padding(horizontal = TratyTheme.spacing.sm, vertical = TratyTheme.spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = TratyTheme.typography.labelSmall,
                color = TratyTheme.colors.onSurfaceVariant,
            )
            Text(
                text = value,
                style = TratyTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TreeFilterChips(
    summary: DebtTreeSummary,
    selected: TreeFilter,
    onSelect: (TreeFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
    ) {
        FilterChip(
            selected = selected == TreeFilter.ALL,
            onClick = { onSelect(TreeFilter.ALL) },
            label = { Text("Все (${summary.totalCount})") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = TratyTheme.colors.primaryContainer,
                selectedLabelColor = TratyTheme.colors.primary,
            ),
        )
        if (summary.overdueCount > 0) {
            FilterChip(
                selected = selected == TreeFilter.OVERDUE,
                onClick = { onSelect(TreeFilter.OVERDUE) },
                label = { Text("🔴 Просрочено (${summary.overdueCount})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TratyPalette.CoralRoseLight,
                    selectedLabelColor = TratyPalette.CoralRose700,
                ),
            )
        }
        FilterChip(
            selected = selected == TreeFilter.UPCOMING,
            onClick = { onSelect(TreeFilter.UPCOMING) },
            label = { Text("⏳ Предстоит (${summary.upcomingCount})") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = TratyTheme.colors.primaryContainer,
                selectedLabelColor = TratyTheme.colors.primary,
            ),
        )
        FilterChip(
            selected = selected == TreeFilter.PAID,
            onClick = { onSelect(TreeFilter.PAID) },
            label = { Text("✅ Оплачено (${summary.paidCount})") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = TratyTheme.colors.primaryContainer,
                selectedLabelColor = TratyTheme.colors.primary,
            ),
        )
    }
}

@Composable
private fun DebtTreeNodeRow(
    node: DebtTreeNode,
    isFirst: Boolean,
    isLast: Boolean,
    isLoan: Boolean,
    onOpenExpense: (Long) -> Unit,
    onPayNode: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg)
            .height(IntrinsicSize.Min),
    ) {
        // Таймлайн (вертикальная линия + иконка)
        Column(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Линия сверху
            if (!isFirst) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(12.dp)
                        .background(TratyTheme.colors.borderSubtle),
                )
            } else {
                Spacer(Modifier.height(12.dp))
            }

            // Кружок статуса
            TimelineStatusIcon(node.state)

            // Линия снизу
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(TratyTheme.colors.borderSubtle),
                )
            } else {
                Spacer(Modifier.height(12.dp))
            }
        }

        Spacer(Modifier.width(TratyTheme.spacing.sm))

        // Карточка платежа
        TratyCard(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = TratyTheme.spacing.xs),
            backgroundColor = TratyTheme.colors.surface,
            border = BorderStroke(
                width = 1.dp,
                color = if (node.state == TreePaymentState.OVERDUE) TratyPalette.CoralRose.copy(alpha = 0.5f) else TratyTheme.colors.borderSubtle,
            ),
        ) {
            Column(Modifier.padding(TratyTheme.spacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val title = node.note ?: if (node.number != null && node.totalCount != null) {
                        "Платёж ${node.number} из ${node.totalCount}"
                    } else if (node.number != null) {
                        "Платёж ${node.number}"
                    } else {
                        "Платёж"
                    }
                    Text(
                        text = title,
                        style = TratyTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    NodeStatusBadge(node)
                }

                Spacer(Modifier.height(TratyTheme.spacing.xs))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        TratyAmountText(
                            amountKopecks = node.totalKopecks,
                            size = AmountSize.MEDIUM,
                            fontWeight = FontWeight.Bold,
                        )
                        if (isLoan && node.interestKopecks > 0) {
                            Text(
                                text = "Тело: ${formatRub(node.principalKopecks)} · %: ${formatRub(node.interestKopecks)}",
                                style = TratyTheme.typography.labelSmall,
                                color = TratyTheme.colors.onSurfaceVariant,
                            )
                        }
                    }

                    if (node.state == TreePaymentState.PAID && node.expenseId != null) {
                        TratyTextButton(onClick = { onOpenExpense(node.expenseId) }) {
                            Text("Трата ➔", style = TratyTheme.typography.labelSmall)
                        }
                    } else if (node.state == TreePaymentState.OVERDUE || node.state == TreePaymentState.UPCOMING || node.state == TreePaymentState.PARTIALLY_PAID) {
                        TratyOutlinedButton(onClick = onPayNode) {
                            Text("Оплатить", style = TratyTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineStatusIcon(state: TreePaymentState) {
    val (bg, iconColor, icon) = when (state) {
        TreePaymentState.PAID -> Triple(TratyPalette.Emerald500, Color.White, Icons.Filled.Check)
        TreePaymentState.OVERDUE -> Triple(TratyPalette.CoralRose, Color.White, Icons.Filled.ErrorOutline)
        TreePaymentState.PARTIALLY_PAID -> Triple(TratyPalette.AmberWarning, Color.White, Icons.Filled.Check)
        TreePaymentState.UPCOMING -> Triple(TratyTheme.colors.surfaceContainerHigh, TratyTheme.colors.onSurfaceVariant, Icons.Filled.Schedule)
    }

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun NodeStatusBadge(node: DebtTreeNode) {
    val (bg, text, label) = when (node.state) {
        TreePaymentState.PAID -> {
            val date = node.actualDate?.let { formatDateNumeric(it) }
            val textStr = if (date != null) "Оплачен $date" else "Оплачен"
            Triple(TratyPalette.Emerald50, TratyPalette.Emerald700, textStr)
        }
        TreePaymentState.OVERDUE -> {
            val textStr = if (node.daysOverdue > 0) {
                "Просрочен на ${node.daysOverdue} ${pluralRu(node.daysOverdue, "день", "дня", "дней")}"
            } else {
                "Просрочен"
            }
            Triple(TratyPalette.CoralRoseLight, TratyPalette.CoralRose700, textStr)
        }
        TreePaymentState.PARTIALLY_PAID -> {
            Triple(TratyPalette.AmberLight, TratyPalette.Amber700, "Внесено ${formatRub(node.paidAmountKopecks)}")
        }
        TreePaymentState.UPCOMING -> {
            val date = node.scheduledDate?.let { formatDateNumeric(it) } ?: "По графику"
            Triple(TratyTheme.colors.surfaceContainerHigh, TratyTheme.colors.onSurfaceVariant, "Срок: $date")
        }
    }

    Surface(
        shape = TratyTheme.shapes.small,
        color = bg,
    ) {
        Text(
            text = label,
            style = TratyTheme.typography.labelSmall,
            color = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun StateChip(state: String) {
    val (text, color) = when (state) {
        DebtState.ACTIVE -> "Активна" to TratyTheme.colors.primary
        DebtState.CLOSED -> "Закрыта" to TratyTheme.colors.onSurfaceVariant
        DebtState.CANCELLED -> "Отменена" to TratyPalette.CoralRose
        else -> state to TratyTheme.colors.onSurfaceVariant
    }
    Surface(
        shape = TratyTheme.shapes.pill,
        color = TratyTheme.colors.surfaceContainer,
    ) {
        Text(
            text = text,
            style = TratyTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun AddPaymentDialog(
    initialAmount: String,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var rawAmount by remember { mutableStateOf(initialAmount) }
    val (amountValue, onAmountChange) = rememberAmountFieldValue(rawAmount) { rawAmount = it }
    val kopecks = AmountInput.parsePositiveKopecks(rawAmount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Внести платёж", style = TratyTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    "Будет создана трата в категории этой покупки с текущей датой.",
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(TratyTheme.spacing.md))
                androidx.compose.material3.OutlinedTextField(
                    value = amountValue,
                    onValueChange = onAmountChange,
                    label = { Text("Сумма платежа") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    visualTransformation = ru.my.traty.ui.designsystem.components.AmountVisualTransformation(TratyTheme.colors.onSurfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TratyButton(
                onClick = { kopecks?.let { onConfirm(it) } },
                enabled = kopecks != null,
            ) { Text("Внести") }
        },
        dismissButton = {
            TratyTextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

private fun kindSubtitle(debt: Debt): String = when (debt.debtKind) {
    DebtKind.INSTALLMENT -> {
        val count = debt.paymentsCount?.let { " на $it ${pluralRu(it.toLong(), "платёж", "платежа", "платежей")}" } ?: ""
        "Рассрочка$count"
    }
    DebtKind.LOAN -> {
        val rate = if (debt.rateBasisPoints > 0) " под ${debt.rateBasisPoints / 100}%" else ""
        "Кредит$rate"
    }
    DebtKind.PARTIAL -> "Оплата частями (аванс и доплаты)"
}
