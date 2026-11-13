package ru.my.traty.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Вкладки нижней навигационной панели приложения.
 */
enum class TratyNavTab(val title: String, val icon: ImageVector) {
    HOME("Траты", Icons.Filled.Home),
    CHARTS("Графики", Icons.Filled.ShowChart),
    STATS("Статистика", Icons.Filled.PieChart),
    SETTINGS("Настройки", Icons.Filled.Settings),
}
