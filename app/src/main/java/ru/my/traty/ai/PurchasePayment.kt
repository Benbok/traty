package ru.my.traty.ai

/**
 * «аванс за холодильник», «предоплата за кухню», «доплата Озону», «пополнение за холодильник плюс 5000» —
 * это оплата покупки, то есть трата, хотя слова «аванс» и «пополнение» обычно значат пришедшие деньги.
 * Зарплатный «аванс за ноябрь» или «аванс за дежурства» оплатой покупки не считается.
 */
object PurchasePayment {
    private val alwaysPayment = Regex("""(?<![\p{L}])(предоплат\p{L}*|доплат\p{L}*|доплатил\p{L}*)""", RegexOption.IGNORE_CASE)
    private val incomeLike = Regex("""(?<![\p{L}])(аванс\p{L}*|пополнени\p{L}*|пополнил\p{L}*|плюс)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val forWhat = Regex("""(?<![\p{L}])за\s+(\p{L}+)""", RegexOption.IGNORE_CASE)

    // «аванс за ноябрь / месяц / смену / дежурство / работу / проект» — это доход
    private val incomePeriods = Regex(
        """^(январ|феврал|март|апрел|ма[йя]|июн|июл|август|сентябр|октябр|ноябр|декабр|месяц|недел|смен|дежур|работ|проект|заказ|подработ|прошл|эт[оу]т|текущ|перв|втор)""",
        RegexOption.IGNORE_CASE,
    )

    fun isPurchasePayment(text: String): Boolean {
        if (alwaysPayment.containsMatchIn(text)) return true
        if (!incomeLike.containsMatchIn(text)) return false
        return forWhat.findAll(text).any { !incomePeriods.containsMatchIn(it.groupValues[1]) }
    }
}
