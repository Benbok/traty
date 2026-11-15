package ru.my.traty.ui.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Базовая палитра Traty (Fintech Emerald / Slate / Coral).
 * Разработана специально для финансового трекера:
 * - Изумрудно-мятный акцент (свежесть, деньги, баланс)
 * - Глубокие графитово-грифельные оттенки для темной темы и чистые светлые для светлой
 * - Кораллово-красный для расходов и предупреждений
 * - Пастельные подложки для категорий
 */
object TratyPalette {
    // Brand Emerald (основной цвет бренда)
    val Emerald50 = Color(0xFFE6F7F2)
    val Emerald100 = Color(0xFFC0EFE2)
    val Emerald200 = Color(0xFF96E6D0)
    val Emerald400 = Color(0xFF34C89F)
    val Emerald500 = Color(0xFF10B981) // Core Brand Primary
    val Emerald600 = Color(0xFF059669)
    val Emerald700 = Color(0xFF047857)
    val Emerald900 = Color(0xFF064E3B)

    // Slate / Neutral (фоны, карточки, текст)
    val Slate25 = Color(0xFFFCFDFD)
    val Slate50 = Color(0xFFF8FAFC)
    val Slate100 = Color(0xFFF1F5F9)
    val Slate200 = Color(0xFFE2E8F0)
    val Slate300 = Color(0xFFCBD5E1)
    val Slate400 = Color(0xFF94A3B8)
    val Slate500 = Color(0xFF64748B)
    val Slate600 = Color(0xFF475569)
    val Slate700 = Color(0xFF334155)
    val Slate800 = Color(0xFF1E293B)
    val Slate900 = Color(0xFF0F172A)
    val Slate950 = Color(0xFF090D16)

    // Finance Semantics
    val CoralRose = Color(0xFFF43F5E)      // Расходы / Внимание
    val CoralRoseLight = Color(0xFFFFF1F2)
    val CoralRoseDark = Color(0xFF4C0519)
    val CoralRose700 = Color(0xFFBE123C)   // Расходы текстом на светлом фоне (6.3:1)
    val CoralRose400 = Color(0xFFFB7185)   // Расходы текстом на тёмном фоне (6.6:1)

    val MintGreen = Color(0xFF10B981)      // Доходы / Экономия
    val MintGreenLight = Color(0xFFECFDF5)
    val MintGreenDark = Color(0xFF022C22)

    val AmberWarning = Color(0xFFF59E0B)   // Превышение лимита
    val AmberLight = Color(0xFFFFFBEB)
    val Amber700 = Color(0xFFB45309)       // Предупреждение текстом на светлом фоне (5:1)

    val SkyInfo = Color(0xFF0EA5E9)        // Аналитика / Подсказки
    val SkyLight = Color(0xFFF0F9FF)
    val Sky700 = Color(0xFF0369A1)         // Подсказки текстом на светлом фоне (5.9:1)

    // Пастельные цвета для бэйджей категорий
    val CategoryColors = listOf(
        Color(0xFFFFEDD5), // Оранжевый (Продукты/Кафе)
        Color(0xFFE0E7FF), // Индиго (Связь/Подписки)
        Color(0xFFFCE7F3), // Розовый (Подарки/Развлечения)
        Color(0xFFDCFCE7), // Зеленый (Здоровье)
        Color(0xFFFEF3C7), // Янтарный (Авто/Транспорт)
        Color(0xFFE0F2FE), // Небесный (Путешествия)
        Color(0xFFF3E8FF), // Фиолетовый (Образование)
        Color(0xFFF1F5F9), // Нейтральный
    )
}

/**
 * Семантические цвета дизайн-системы Traty.
 */
@Immutable
data class TratyColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,

    val background: Color,
    val onBackground: Color,

    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,

    /** Тонкие рамки карточек и разделители, чисто декоративные. */
    val border: Color,
    val borderSubtle: Color,
    /** Рамки, которые надо увидеть: поля ввода, контурные кнопки (не ниже 3:1 к фону). */
    val outline: Color,

    val expense: Color,
    val expenseContainer: Color,
    val income: Color,
    val incomeContainer: Color,
    val warning: Color,
    val info: Color,

    val isDark: Boolean,
)

/**
 * Светлая тема: цвета текста и сумм подобраны с контрастом не ниже 4.5:1 к белому
 * и к светлым подложкам (WCAG AA), чтобы суммы читались на солнце.
 */
val LightTratyColors = TratyColors(
    primary = TratyPalette.Emerald700,
    onPrimary = Color.White,
    primaryContainer = TratyPalette.Emerald50,
    onPrimaryContainer = TratyPalette.Emerald900,

    background = TratyPalette.Slate50,
    onBackground = TratyPalette.Slate900,

    surface = Color.White,
    onSurface = TratyPalette.Slate900,
    surfaceVariant = TratyPalette.Slate100,
    onSurfaceVariant = TratyPalette.Slate600,
    surfaceContainer = TratyPalette.Slate100,
    surfaceContainerHigh = TratyPalette.Slate200,

    border = TratyPalette.Slate200,
    borderSubtle = TratyPalette.Slate100,
    outline = TratyPalette.Slate500,

    expense = TratyPalette.CoralRose700,
    expenseContainer = TratyPalette.CoralRoseLight,
    income = TratyPalette.Emerald700,
    incomeContainer = TratyPalette.MintGreenLight,
    warning = TratyPalette.Amber700,
    info = TratyPalette.Sky700,

    isDark = false,
)

val DarkTratyColors = TratyColors(
    primary = TratyPalette.Emerald400,
    onPrimary = TratyPalette.Slate950,
    primaryContainer = TratyPalette.Emerald900,
    onPrimaryContainer = TratyPalette.Emerald100,

    background = TratyPalette.Slate950,
    onBackground = TratyPalette.Slate100,

    surface = TratyPalette.Slate900,
    onSurface = TratyPalette.Slate50,
    surfaceVariant = TratyPalette.Slate800,
    onSurfaceVariant = TratyPalette.Slate400,
    surfaceContainer = TratyPalette.Slate800,
    surfaceContainerHigh = TratyPalette.Slate700,

    border = TratyPalette.Slate800,
    borderSubtle = TratyPalette.Slate900,
    outline = TratyPalette.Slate500,

    expense = TratyPalette.CoralRose400,
    expenseContainer = TratyPalette.CoralRoseDark,
    income = TratyPalette.MintGreen,
    incomeContainer = TratyPalette.MintGreenDark,
    warning = TratyPalette.AmberWarning,
    info = TratyPalette.SkyInfo,

    isDark = true,
)

val LocalTratyColors = staticCompositionLocalOf { LightTratyColors }
