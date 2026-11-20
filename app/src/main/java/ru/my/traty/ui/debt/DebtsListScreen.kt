package ru.my.traty.ui.debt

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.data.debt.DebtKind
import ru.my.traty.data.debt.DebtState
import ru.my.traty.ui.designsystem.components.AmountSize
import ru.my.traty.ui.designsystem.components.TratyAmountText
import ru.my.traty.ui.designsystem.components.TratyCard
import ru.my.traty.ui.designsystem.components.TratyCategoryBadge
import ru.my.traty.ui.designsystem.components.TratyEmptyState
import ru.my.traty.ui.designsystem.components.TratyFilterChip
import ru.my.traty.ui.designsystem.components.TratyHeaderCard
import ru.my.traty.ui.designsystem.components.TratyProgressBar
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.designsystem.tokens.TratyPalette
import ru.my.traty.ui.formatRub
import ru.my.traty.ui.pluralRu

/**
 * Экран со списком всех покупок частями, рассрочек и кредитов.
 */
@Composable
fun DebtsListScreen(
    onBack: () -> Unit,
    onSelectDebt: (Long) -> Unit,
    vm: DebtsListViewModel = viewModel(),
) {
    BackHandler(onBack = onBack)

    val items by vm.items.collectAsStateWithLifecycle()
    var showClosed by rememberSaveable { mutableStateOf(false) }

    val activeItems = items.filter { it.debt.state == DebtState.ACTIVE && !it.summary.isPaidOff }
    val closedItems = items.filter { it.debt.state != DebtState.ACTIVE || it.summary.isPaidOff }
    val currentItems = if (showClosed) closedItems else activeItems

    Scaffold(
        containerColor = TratyTheme.colors.background,
        topBar = {
            TratyHeaderCard(
                modifier = Modifier.statusBarsPadding(),
                title = "Покупки частями",
                subtitle = "Рассрочки, кредиты и авансы",
                onBack = onBack,
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad),
        ) {
            // Переключатель Активные / Завершённые
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TratyTheme.spacing.lg, vertical = TratyTheme.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
            ) {
                TratyFilterChip(
                    selected = !showClosed,
                    onClick = { showClosed = false },
                    label = { Text("Активные (${activeItems.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TratyTheme.colors.primaryContainer,
                        selectedLabelColor = TratyTheme.colors.primary,
                    ),
                )
                TratyFilterChip(
                    selected = showClosed,
                    onClick = { showClosed = true },
                    label = { Text("Завершённые (${closedItems.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TratyTheme.colors.primaryContainer,
                        selectedLabelColor = TratyTheme.colors.primary,
                    ),
                )
            }

            if (currentItems.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = TratyTheme.spacing.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    TratyEmptyState(
                        icon = Icons.Filled.AccountBalanceWallet,
                        title = if (showClosed) "Нет завершённых покупок" else "Нет активных рассрочек",
                        description = if (showClosed) {
                            "Здесь будут закрытые и полностью выплаченные покупки"
                        } else {
                            "При записи голосом фразы вроде «в рассрочку на 6 месяцев» или «аванс 20 000» приложение само предложит создать график"
                        },
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(TratyTheme.spacing.xs),
                ) {
                    items(currentItems, key = { it.debt.id }) { item ->
                        DebtListRow(
                            item = item,
                            onClick = { onSelectDebt(item.debt.id) },
                        )
                    }
                    item {
                        Spacer(Modifier.height(TratyTheme.spacing.xxl))
                    }
                }
            }
        }
    }
}

@Composable
private fun DebtListRow(
    item: DebtListItem,
    onClick: () -> Unit,
) {
    val debt = item.debt
    val summary = item.summary
    val cat = item.category
    val catEmoji = cat?.category?.emoji ?: "📦"
    val catName = cat?.category?.name ?: "Покупка"

    TratyCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TratyTheme.spacing.lg),
        onClick = onClick,
        backgroundColor = TratyTheme.colors.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (summary.overdueCount > 0) TratyPalette.CoralRose.copy(alpha = 0.5f) else TratyTheme.colors.borderSubtle,
        ),
    ) {
        Column(Modifier.padding(TratyTheme.spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TratyCategoryBadge(
                    emoji = catEmoji,
                    categoryName = catName,
                    size = 40.dp,
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
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = TratyTheme.colors.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(TratyTheme.spacing.sm))

            // Прогресс-бар
            val barColor = when {
                summary.isPaidOff -> TratyTheme.colors.primary
                summary.overdueCount > 0 -> TratyPalette.CoralRose
                else -> TratyTheme.colors.primary
            }
            TratyProgressBar(
                progress = summary.progressPercent,
                color = barColor,
                height = 6.dp,
            )

            Spacer(Modifier.height(TratyTheme.spacing.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Подпись вида и прогресса
                val kindText = when (debt.debtKind) {
                    DebtKind.INSTALLMENT -> "Рассрочка"
                    DebtKind.LOAN -> "Кредит"
                    DebtKind.PARTIAL -> "Частями"
                }
                val countText = if (debt.paymentsCount != null) {
                    "$kindText · ${summary.paidCount}/${debt.paymentsCount}"
                } else {
                    "$kindText · ${summary.paidCount} ${pluralRu(summary.paidCount.toLong(), "платёж", "платежа", "платежей")}"
                }
                Text(
                    text = countText,
                    style = TratyTheme.typography.bodySmall,
                    color = TratyTheme.colors.onSurfaceVariant,
                )

                // Суммы
                val totalKopecks = summary.totalKopecks
                if (totalKopecks != null) {
                    Text(
                        text = "${formatRub(summary.paidKopecks)} из ${formatRub(totalKopecks)}",
                        style = TratyTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    TratyAmountText(
                        amountKopecks = summary.paidKopecks,
                        size = AmountSize.SMALL,
                    )
                }
            }

            if (summary.overdueCount > 0) {
                Spacer(Modifier.height(TratyTheme.spacing.xs))
                Surface(
                    shape = TratyTheme.shapes.small,
                    color = TratyPalette.CoralRoseLight,
                ) {
                    Text(
                        text = "🔴 Просрочено: ${formatRub(summary.overdueKopecks)} (${summary.overdueCount} ${pluralRu(summary.overdueCount.toLong(), "платёж", "платежа", "платежей")})",
                        style = TratyTheme.typography.labelSmall,
                        color = TratyPalette.CoralRose700,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}
