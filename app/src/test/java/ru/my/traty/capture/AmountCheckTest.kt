package ru.my.traty.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.my.traty.ai.ParsedEntry
import ru.my.traty.ai.ParsedItem

class AmountCheckTest {
    private fun e(amount: Double) = ParsedEntry(type = "expense", amount = amount)
    private fun bad(phrase: String, vararg amounts: Double) = AmountCheck.mismatches(amounts.map(::e), phrase).map { it.amount }

    @Test
    fun amountNamedInPhrasePasses() {
        assertEquals(emptyList<Double>(), bad("кофе 250", 250.0))
        assertEquals(emptyList<Double>(), bad("кофе двести пятьдесят", 250.0))
        assertEquals(emptyList<Double>(), bad("такси полторы тысячи", 1500.0))
        assertEquals(emptyList<Double>(), bad("пятёрочка пятьсот и триста", 500.0, 300.0))
        assertEquals(emptyList<Double>(), bad("зоомагазин вчера 731 лекарство для кошки Чуни от блох", 731.0))
    }

    @Test
    fun formsTheSimpleParserMissesAreStillRecognized() {
        assertEquals(emptyList<Double>(), bad("две с половиной тысячи коммуналка", 2500.0))
        assertEquals(emptyList<Double>(), bad("машина миллион двести", 1_000_000.0))
        assertEquals(emptyList<Double>(), bad("полтора миллиона на квартиру", 1_500_000.0))
        assertEquals(emptyList<Double>(), bad("две сотни на кофе", 200.0))
        assertEquals(listOf(3000.0), bad("две с половиной тысячи коммуналка", 3000.0))
    }

    @Test
    fun amountThatPhraseDoesNotContainIsFlagged() {
        assertEquals(listOf(5000.0), bad("купил 2 билета за 3000", 5000.0))
        assertEquals(listOf(2500.0), bad("кофе 250", 2500.0))
        assertEquals(listOf(731.0), bad("зоомагазин 713", 731.0))
    }

    @Test
    fun phraseWithoutAnyNumberCannotConfirmAmount() {
        assertEquals(listOf(500.0), bad("потратил на еду как обычно", 500.0))
    }

    @Test
    fun sumOfNamedPricesPasses() {
        // Список с ценами без итога: приложение складывает цены само
        assertEquals(emptyList<Double>(), bad("рыба 500 хлеб 80 сок 120", 700.0))
        assertEquals(emptyList<Double>(), bad("рыба 500 хлеб 80 сок 120", 580.0))
        assertEquals(listOf(999.0), bad("рыба 500 хлеб 80 сок 120", 999.0))
    }

    @Test
    fun entryWithoutPaymentTodayIsNotChecked() {
        // «взял в кредит»: сегодня ничего не платили, цена лежит в part.total
        assertEquals(emptyList<Double>(), bad("взял ноутбук в кредит за восемьдесят тысяч", 0.0))
    }

    @Test
    fun kopecksAfterDashAddUpToAmount() {
        // Сумма от ИИ верна: 83,90 + 99 + 69,90 + 91,90. Предупреждения «не сходится» быть не должно
        val phrase = "Аа Гречка фасованная — 83—90, горошек зелёный 99, Фруктовое яблоко — это сок 69—90, оливки зелёные — 91—90"
        assertEquals(emptyList<Double>(), bad(phrase, 83.9 + 99.0 + 69.9 + 91.9))
    }

    @Test
    fun priceMisheardAsGramsCountsWhenAiNamedItAsItemPrice() {
        // Текст GigaAM с телефона: цену горошка распознавание записало как «99г».
        // ИИ понял, что это цена, и сумма 344,70 верна — предупреждения быть не должно
        val phrase = "Покупка в Магните. Гречка фасованная 900 г, 83—90. Горошек зелёный 99г, " +
            "фруктовое яблоко — моя цена, яблочный сок 69—90, оливки зелёные — 91—90"
        val goods = listOf(83.9, 99.0, 69.9, 91.9).map { ParsedItem(name = "товар", price = it) }
        val entry = ParsedEntry(type = "expense", amount = 344.7, items = goods)
        assertEquals(emptyList<ParsedEntry>(), AmountCheck.mismatches(listOf(entry), phrase))
        // Без товаров от ИИ «99г» остаётся весом, и сумма по-прежнему под вопросом
        assertEquals(listOf(344.7), bad(phrase, 344.7))
    }

    @Test
    fun kopecksSplitIntoTwoNumbersInPhraseAreConfirmedByAiItems() {
        // Текст GigaAM: «Ц1 231, 29» (категория яиц слиплась с ценой) и «129 99» (копейки через пробел).
        // ИИ верно собрал 7 + 231,29 + 59,99 + 129,99 — предупреждения быть не должно
        val phrase = "Покупка в Магните. Пакет Ммайка — 7 ₽, яйца Ц1 231, 29. Пастеризованная четырёхпроцентная 59,99. " +
            "Аа-а, сельдь ТИАС 129 99"
        val goods = listOf(7.0, 231.29, 59.99, 129.99).map { ParsedItem(name = "товар", price = it) }
        val entry = ParsedEntry(type = "expense", amount = 428.27, items = goods)
        assertEquals(emptyList<ParsedEntry>(), AmountCheck.mismatches(listOf(entry), phrase))
    }

    @Test
    fun manyMisheardPricesAreConfirmedBySumOfAiItems() {
        // Реальная фраза из журнала телефона: каждая цена записана по-разному («173,—59», «169 80», «332—60»),
        // во фразе набирается больше 10 чисел, и перебор сумм частей не запускается. ИИ собрал верно.
        val phrase = "Покупка в Магнит. Бананы 1 кг 173,—59. Млоко «Село зелёное», 169 80. Хлеб бородинский, нарезка — 46,50. " +
            "Филе куриное охлаждённое 332—60; Сыр российский 200 г, 189—99"
        val goods = listOf(173.59, 169.8, 46.5, 332.6, 189.99).map { ParsedItem(name = "товар", price = it) }
        val entry = ParsedEntry(type = "expense", amount = 912.48, items = goods)
        assertEquals(emptyList<ParsedEntry>(), AmountCheck.mismatches(listOf(entry), phrase))
    }

    @Test
    fun priceGluedWithoutSeparatorIsConfirmedByAiItem() {
        // Диктовка с телефона: «14998» — цена 149,98 без разделителя, «14:90. Точнее, 114—90» — поправка цены яиц.
        // ИИ верно понял и то, и другое; сумма 588,47 складывается из цен товаров
        val phrase = "ТМагнит, яблоки Гала, 1 кг, 184,59. Творог Простоквашино, 180 г, 14998. " +
            "яйцо куриное Ц1, 10 шт, 14:90. Точнее, 114—90 Масло подсолнечное «Слобода», 139"
        val goods = listOf(184.59, 149.98, 114.9, 139.0).map { ParsedItem(name = "товар", price = it) }
        val entry = ParsedEntry(type = "expense", amount = 588.47, items = goods)
        assertEquals(emptyList<ParsedEntry>(), AmountCheck.mismatches(listOf(entry), phrase))
    }

    @Test
    fun glueAndUnitPricesAreReportedAsGuessed() {
        // Диктовка с телефона: «11490» и «20997» слитно, «139л» с прилипшей буквой. ИИ верно угадал, но сохранять
        // без взгляда человека такие цены нельзя
        val phrase = "«Магнит, яблоки Гала, вес 1 кг, 184,59. Творог Простоквашино 149,98.а яйцо куриное Ц1, 10 шт., 11490; " +
            "Масло подсолнечное «Слобода» 139л, шоколад Alpen Gold 20997"
        val goods = listOf(184.59, 149.98, 114.9, 139.0, 209.97).map { ParsedItem(name = "товар", price = it) }
        val entry = ParsedEntry(type = "expense", amount = 798.44, items = goods)
        assertEquals(emptyList<ParsedEntry>(), AmountCheck.mismatches(listOf(entry), phrase))
        assertEquals(listOf(114.9, 139.0, 209.97), AmountCheck.guessedPrices(listOf(entry), phrase))
        assertTrue(EntrySanity.check(listOf(entry), phrase, java.time.LocalDate.of(2026, 11, 16)).problems.any { "114,90" in it && "209,97" in it })
    }

    @Test
    fun cleanPricesAreNotGuessed() {
        // Копейки «83—90», «231, 29», «129 99» читаются однозначно — без лишних вопросов
        val phrase = "гречка 83—90, яйца 231, 29, сельдь 129 99, пакет 7"
        val goods = listOf(83.9, 231.29, 129.99, 7.0).map { ParsedItem(name = "товар", price = it) }
        val entry = ParsedEntry(type = "expense", amount = 452.18, items = goods)
        assertEquals(emptyList<Double>(), AmountCheck.guessedPrices(listOf(entry), phrase))
        assertEquals(emptyList<String>(), EntrySanity.check(listOf(entry), phrase, java.time.LocalDate.of(2026, 11, 16)).problems)
    }

    @Test
    fun gluedPriceMustMatchDigitsExactly() {
        // ИИ округлил до 149,99, а во фразе «14998» — такую цену подтвердить нельзя
        val phrase = "творог 14998, масло 139"
        val goods = listOf(149.99, 139.0).map { ParsedItem(name = "товар", price = it) }
        val entry = ParsedEntry(type = "expense", amount = 288.99, items = goods)
        assertEquals(listOf(288.99), AmountCheck.mismatches(listOf(entry), phrase).map { it.amount })
    }

    @Test
    fun shortGluedNumberIsNotTakenForKopecks() {
        // «1250» — это обычные тысяча двести пятьдесят, а не 12,50: консервативно подтверждаем только длинные числа (от 100 ₽)
        val phrase = "хлеб 1250, молоко 90"
        val goods = listOf(12.5, 90.0).map { ParsedItem(name = "товар", price = it) }
        val entry = ParsedEntry(type = "expense", amount = 102.5, items = goods)
        assertEquals(listOf(102.5), AmountCheck.mismatches(listOf(entry), phrase).map { it.amount })
    }

    @Test
    fun oneUnconfirmedItemPriceKeepsTheWarning() {
        // ИИ смещал одну цену (173,60 вместо 173,59): она во фразе не находится, поэтому сумму подтвердить нельзя
        val phrase = "Бананы 173,—59. Молоко 169 80. Хлеб 46,50. Филе 332—60; Сыр 189—99. Ещё масло 77, сок 88, чай 66"
        val goods = listOf(173.60, 169.8, 46.5, 332.6, 189.99).map { ParsedItem(name = "товар", price = it) }
        val entry = ParsedEntry(type = "expense", amount = 912.49, items = goods)
        assertEquals(listOf(912.49), AmountCheck.mismatches(listOf(entry), phrase).map { it.amount })
    }

    @Test
    fun splitKopecksNeedToBeInPhraseExactly() {
        // ИИ придумал 231,30, а во фразе «231, 29»: такая цена не подтверждается
        val phrase = "яйца 231, 29 и пакет 7"
        val goods = listOf(231.30, 7.0).map { ParsedItem(name = "товар", price = it) }
        val entry = ParsedEntry(type = "expense", amount = 238.3, items = goods)
        assertEquals(listOf(238.3), AmountCheck.mismatches(listOf(entry), phrase).map { it.amount })
    }

    @Test
    fun weightIsStillNotMoneyWhenNoItemCostsThat() {
        // «900 г» — вес: ни один товар ИИ не стоит 900, значит 983,90 подтвердить нечем
        val goods = listOf(ParsedItem(name = "гречка", price = 83.9), ParsedItem(name = "хлеб", price = 45.0))
        val entry = ParsedEntry(type = "expense", amount = 983.9, items = goods)
        assertEquals(listOf(983.9), AmountCheck.mismatches(listOf(entry), "гречка 900 г 83,90, хлеб 45").map { it.amount })
    }

    @Test
    fun noticeNamesBothAmounts() {
        val list = AmountCheck.mismatches(listOf(e(2500.0)), "кофе 250")
        val text = AmountCheck.notice(list, "кофе 250")!!
        assertTrue(text, Regex("2[\\u00A0 ]500 ₽").containsMatchIn(text) && "250 ₽" in text)
        assertNull(AmountCheck.notice(emptyList(), "кофе 250"))
    }
}
