package ru.my.traty.ui.designsystem.components

import androidx.annotation.DrawableRes
import ru.my.traty.R

// Эмодзи из сохранённых категорий служат ключами для иллюстраций.
@DrawableRes
internal fun categoryIllustration(emoji: String): Int? = when (emoji.replace("\uFE0F", "")) {
    "🛒" -> R.drawable.ic_category_groceries
    "☕" -> R.drawable.ic_category_cafe
    "🛵" -> R.drawable.ic_category_delivery
    "🚕" -> R.drawable.ic_category_transit
    "🚗" -> R.drawable.ic_category_car
    "🏠" -> R.drawable.ic_category_home
    "💡" -> R.drawable.ic_category_utilities
    "📱" -> R.drawable.ic_category_subscriptions
    "💊" -> R.drawable.ic_category_health
    "🏋" -> R.drawable.ic_category_sports
    "💇" -> R.drawable.ic_category_beauty
    "📚" -> R.drawable.ic_category_education
    "👕" -> R.drawable.ic_category_clothing
    "🧴" -> R.drawable.ic_category_household
    "🎬" -> R.drawable.ic_category_entertainment
    "🎨" -> R.drawable.ic_category_hobby
    "🧸" -> R.drawable.ic_category_kids
    "🐾" -> R.drawable.ic_category_pets
    "🎁" -> R.drawable.ic_category_gifts
    "✈" -> R.drawable.ic_category_travel
    "💳" -> R.drawable.ic_category_debt
    "🧾" -> R.drawable.ic_category_tax
    "🏦" -> R.drawable.ic_category_bank_fees
    "🤝" -> R.drawable.ic_category_charity
    "🫥" -> R.drawable.ic_category_unaccounted
    "📦" -> R.drawable.ic_category_other
    else -> null
}
