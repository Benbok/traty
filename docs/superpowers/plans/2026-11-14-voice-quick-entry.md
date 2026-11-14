# Этап 2 — Голос и быстрый ввод: план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** трата или пополнение добавляется голосом за 3–5 секунд — с главной (одна кнопка 🎤), с плитки в шторке или с виджета; смешанные фразы из нескольких операций; уверенные траты сохраняются без подтверждения; без сети фраза ждёт в очереди; распознавание — GigaAM на телефоне.

**Architecture:** вся цепочка «текст → разбор → правила → решение → сохранение» выносится из `MainViewModel` в `QuickEntry` (регистрируется в `AppContainer`). Главная и прозрачная `CaptureActivity` пользуются им одинаково; плитка и виджет только открывают `CaptureActivity`. Распознавание за интерфейсом `SpeechEngine`: сначала `SystemSpeechEngine` (`android.speech.SpeechRecognizer`, без системного окна), в фазе 4 — `GigaAmEngine` (sherpa-onnx).

**Tech Stack:** Kotlin 2.1, Compose (BOM 2024.12.01), Room 2.7 (миграция 6→7), kotlinx.serialization, WorkManager, Glance appwidget, sherpa-onnx (AAR), JUnit4 + Robolectric.

**Spec:** [docs/superpowers/specs/2026-11-14-voice-quick-entry-design.md](../specs/2026-11-14-voice-quick-entry-design.md)

## Статус (2026-11-14): этап 2 закрыт

Задачи 1–15 выполнены; задача 12 (отдельный пробный запуск) пропущена — движок написан сразу.
На телефоне Xiaomi 22041216G (Android 14) проверено пользователем 2026-11-14: главная кнопка, GigaAM, плитка, виджет,
очередь без сети, разбор нескольких операций в одной фразе и несколько записей подряд во всех режимах.
Формальный замер «≥9 из 10 фраз» с подсчётом не делался: критерий принят по результатам ручных проверок.
Сверх плана добавлены мягкие звуки начала и конца записи (`RecordCues`).
Отдельные шаги проверки (панель: повторный тап и отказ в микрофоне; скорость и память GigaAM) остаются неотмеченными ниже.
Мелочи после ревью — список в README.

## Global Constraints

- minSdk 26, compileSdk/targetSdk 35. Только разблокированный телефон, `showWhenLocked` не используем.
- Room: только `Migration(6, 7)`, без пересоздания базы; схема `app/schemas/.../7.json` коммитится.
- В ИИ уходит только распознанный текст + список категорий и правил (как сейчас). Голос не покидает телефон в режиме GigaAM.
- Правило автосохранения: **все** операции фразы — `expense`, у каждой `amount > 0`, у каждой локально сработало правило по `place`/`note` (не по названию категории). Иначе — подтверждение. Нет сети / сетевая ошибка — очередь.
- Простой офлайн-разбор (`OfflineParser`/`OfflineIncomeParser`) используется только когда ИИ не настроен или ответил ошибкой не сетевого характера; после него **всегда** подтверждение.
- Тексты UI: «Сохранено N · Изменить», «N фраз ждут разбора», «Разобрано, проверьте», «Нет сети, разберу позже», плитка «Сказать трату».
- Модель не встраивается в APK. sherpa-onnx — только `arm64-v8a`.
- Стиль: компоненты `TratyTheme`/дизайн-системы проекта, ручной DI через `AppContainer`, `AndroidViewModel`.
- Существующие тесты (`./gradlew :app:testDebugUnitTest`) должны оставаться зелёными после каждой задачи.

## Review Focus

1. **Бессмыслица или фраза без суммы** («эээ, потом»): ничего не сохраняется, открывается подтверждение с одной пустой строкой траты — тест в Task 5.
2. **ИИ вернул неизвестный `type`** («goal_deposit», опечатку): строка становится тратой, но фраза уходит на подтверждение, а не в автосохранение — тест в Task 1 и Task 3.
3. **Фраза из очереди разбирается на следующий день:** «вчера» и дата по умолчанию считаются от времени произнесения (`createdAt`), а не от момента разбора — тест в Task 5 (через `spokenAt`) и Task 11.
4. **Повторный тап по плитке/виджету во время записи:** вторая панель не открывается, текущая запись не прерывается — проверка вручную в Task 8 (`launchMode=singleInstance`, `onNewIntent` игнорирует).
5. **Отказ в разрешении на микрофон:** панель/главная показывают сообщение «Нужен доступ к микрофону» и не падают; повторное нажатие снова спрашивает — проверка вручную в Task 7 и Task 8.

---

## Фаза 1 — Единый разбор, общий ввод, новая главная

Результат фазы: на главной одна кнопка 🎤 (системное распознавание без окна), смешанные фразы, автосохранение уверенных трат, общий экран подтверждения.

### Task 1: Единый разбор `EntryParsing`

**Files:**
- Create: `app/src/main/java/ru/my/traty/ai/EntryParsing.kt`
- Modify: `app/src/main/java/ru/my/traty/ai/LlmClients.kt:45` — бросать `LlmHttpException`
- Test: `app/src/test/java/ru/my/traty/ai/EntryParsingTest.kt`

**Interfaces:**
- Produces:
  - `enum class EntryType { EXPENSE, INCOME }`
  - `@Serializable data class ParsedEntry(val type: String = "expense", val amount: Double = 0.0, val category: String = DefaultCategories.OTHER, val emoji: String? = null, val subcategory: String? = null, val place: String? = null, val note: String? = null, val source: String? = null, val date: String? = null)` с вычисляемыми `val entryType: EntryType?` (`"expense"`→EXPENSE, `"income"`→INCOME, иначе `null`).
  - `object EntryPrompt { fun system(categories: List<CategoryWithSubs>, today: LocalDate, rules: List<Rule>): String }`
  - `object EntryParsing { suspend fun parse(client: LlmClient, text: String, categories: List<CategoryWithSubs>, rules: List<Rule>, today: LocalDate): List<ParsedEntry>; internal fun decode(raw: String): List<ParsedEntry> }`
  - `object OfflineEntryParser { fun parse(text: String, categories: List<CategoryWithSubs>): List<ParsedEntry> }`
  - `class LlmHttpException(val code: Int, message: String) : IOException(message)`

- [x] **Step 1: Написать падающие тесты**

```kotlin
@Test fun decodesMixedTypes() {
    val r = EntryParsing.decode("""{"items":[{"type":"expense","amount":250,"category":"Кафе и рестораны"},{"type":"income","amount":80000,"source":"Зарплата"}]}""")
    assertEquals(listOf(EntryType.EXPENSE, EntryType.INCOME), r.map { it.entryType })
    assertEquals("Зарплата", r[1].source)
}
@Test fun unknownTypeHasNullEntryType() {
    val r = EntryParsing.decode("""[{"type":"goal_deposit","amount":5000}]""")
    assertNull(r.single().entryType)
}
@Test fun incomeSourceIsNormalized() {
    val r = EntryParsing.decode("""{"items":[{"type":"income","amount":100,"source":"зарплата"}]}""")
    assertEquals("Зарплата", r.single().source)   // через IncomeSources.normalize
}
@Test fun dropsNonPositiveAndStripsFence() {
    val r = EntryParsing.decode("```json\n{\"items\":[{\"amount\":0},{\"amount\":400,\"category\":\"Транспорт\"}]}\n```")
    assertEquals(listOf(400.0), r.map { it.amount })
    assertEquals(EntryType.EXPENSE, r.single().entryType)   // type по умолчанию
}
@Test fun offlineDetectsIncomeByKeyword() {
    assertEquals(EntryType.INCOME, OfflineEntryParser.parse("пришла зарплата 80000", emptyList()).single().entryType)
    assertEquals(EntryType.EXPENSE, OfflineEntryParser.parse("кофе 250", emptyList()).single().entryType)
}
@Test fun promptMentionsTypesAndToday() {
    val p = EntryPrompt.system(emptyList(), LocalDate.of(2026, 11, 14), emptyList())
    assertTrue("\"type\"" in p && "income" in p && "2026-11-14" in p)
}
```

- [x] **Step 2: Запустить, убедиться что падают**

Run: `./gradlew :app:testDebugUnitTest --tests "ru.my.traty.ai.EntryParsingTest"`
Expected: FAIL — `Unresolved reference: EntryParsing`

- [x] **Step 3: Реализовать**

- `EntryPrompt.system` — объединение текущих `ParsePrompt.system` и `IncomePrompt.system`: формат `{"items":[{"type":"expense"|"income", ...поля траты..., "source":"для income: одно из IncomeSources.ALL"}]}`; правило «зарплата, аванс, дежурства, перевод мне, вернули долг, кэшбэк → income; остальное — expense»; «несколько операций — несколько элементов, могут быть разного типа». Остальные правила (числа словами, даты, категории, выученные правила) — дословно из `ParsePrompt`.
- `decode` — та же логика чистки ```/массива, что в `ExpenseParsing.decode`; фильтр `amount > 0`; для `income` — `source = IncomeSources.normalize(source ?: IncomeSources.DEFAULT)`.
- `OfflineEntryParser.parse` — если в тексте есть «зарплат|аванс|дежур|пришл|вернул|возврат|подработ|кэшбэк» → результат `OfflineIncomeParser.parse` с `type="income"`, иначе `OfflineParser.parse` с `type="expense"`.
- `LlmClients.kt:45`: `throw LlmHttpException(r.code, "HTTP ${r.code}: ${text.take(300)}")`.
- `ExpenseParsing`/`IncomeParsing` пока не удалять (используются `testConnection`); удаление — в Task 7.

- [x] **Step 4: Запустить тесты** — тот же Run, Expected: PASS (6 tests)

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/ru/my/traty/ai/EntryParsing.kt app/src/main/java/ru/my/traty/ai/LlmClients.kt app/src/test/java/ru/my/traty/ai/EntryParsingTest.kt
git commit -m "feat(ai): unified expense/income parsing with type field"
```

### Task 2: Сопоставление правил только по месту и описанию

**Files:**
- Create: `app/src/main/java/ru/my/traty/capture/RuleMatcher.kt`
- Test: `app/src/test/java/ru/my/traty/capture/RuleMatcherTest.kt`

**Interfaces:**
- Consumes: `ParsedEntry`, `EntryType` (Task 1); `Rule`, `CategoryWithSubs` (существующие).
- Produces:
  - `data class RuledEntry(val entry: ParsedEntry, val matchedRule: Rule?)` + `val ruleMatched get() = matchedRule != null`
  - `object RuleMatcher { fun apply(entries: List<ParsedEntry>, rules: List<Rule>, categories: List<CategoryWithSubs>): List<RuledEntry> }`

- [x] **Step 1: Падающие тесты**

```kotlin
// rules: Rule(id=1, pattern="пятёрочка", categoryId=<Продукты>), Rule(id=2, pattern="кафе", categoryId=<Кафе и рестораны>)
@Test fun matchesPlaceAndOverridesAiCategory() {
    val r = RuleMatcher.apply(listOf(ParsedEntry(amount = 500.0, category = "Прочее", place = "Пятёрочка")), rules, cats).single()
    assertTrue(r.ruleMatched); assertEquals("Продукты", r.entry.category)
}
@Test fun matchesNote() { /* note = "обед в кафе" → ruleMatched == true */ }
@Test fun categoryNameAloneDoesNotMatch() {
    val r = RuleMatcher.apply(listOf(ParsedEntry(amount = 300.0, category = "Кафе и рестораны")), rules, cats).single()
    assertFalse(r.ruleMatched)
}
@Test fun incomeIsNeverMatched() {
    val r = RuleMatcher.apply(listOf(ParsedEntry(type = "income", amount = 1.0, note = "кафе")), rules, cats).single()
    assertFalse(r.ruleMatched)
}
@Test fun subcategoryFromRuleWins() { /* правило с subcategoryId → entry.subcategory == имя подкатегории */ }
```

- [x] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "ru.my.traty.capture.RuleMatcherTest"` — Expected: FAIL (unresolved)
- [x] **Step 3: Реализовать** — поиск: `listOfNotNull(place, note).joinToString(" ").lowercase().contains(rule.pattern)`, первый по порядку `rules` (они уже отсортированы по `hits DESC`); подстановка категории/подкатегории — как в текущем `MainViewModel.applyRules`. `incrementHits` здесь не вызывается (функция чистая) — это делает `QuickEntry` (Task 5).
- [x] **Step 4: Run** — Expected: PASS
- [x] **Step 5: Commit** — `git commit -m "feat(capture): rule matching on place/note only"`

### Task 3: Правило автосохранения `ParsePolicy`

**Files:**
- Create: `app/src/main/java/ru/my/traty/capture/ParsePolicy.kt`
- Test: `app/src/test/java/ru/my/traty/capture/ParsePolicyTest.kt`

**Interfaces:**
- Consumes: `RuledEntry` (Task 2)
- Produces: `enum class EntryDecision { AUTO_SAVE, CONFIRM }`; `object ParsePolicy { fun decide(entries: List<RuledEntry>): EntryDecision }`

- [x] **Step 1: Падающие тесты** — `autoSavesWhenAllExpensesMatched` (2 траты с правилом → AUTO_SAVE); `confirmsWhenAnyIncome`; `confirmsWhenAnyWithoutRule`; `confirmsWhenEmpty`; `confirmsWhenUnknownType` (`type="goal_deposit"` с правилом → CONFIRM); `confirmsWhenZeroAmount`.
- [x] **Step 2: Run** `--tests "ru.my.traty.capture.ParsePolicyTest"` — FAIL
- [x] **Step 3: Реализовать** — `AUTO_SAVE` iff `entries.isNotEmpty() && entries.all { it.entry.entryType == EntryType.EXPENSE && it.entry.amount > 0 && it.ruleMatched }`.
- [x] **Step 4: Run** — PASS
- [x] **Step 5: Commit** — `git commit -m "feat(capture): autosave policy"`

### Task 4: Таблица очереди и миграция 6→7

**Files:**
- Modify: `app/src/main/java/ru/my/traty/data/Database.kt` — сущность, DAO, `version = 7`, `MIGRATION_6_7`, регистрация в `build()`; в `ExpenseDao` добавить `sumRange`
- Create: `app/schemas/ru.my.traty.data.AppDatabase/7.json` (генерируется KSP)
- Test: `app/src/test/java/ru/my/traty/data/DatabaseUpgradeTest.kt` — новый тест

**Interfaces:**
- Produces:
  - `@Entity(tableName = "pending_phrases", indices = [Index("status")]) data class PendingPhrase(@PrimaryKey(autoGenerate = true) val id: Long = 0, val text: String, val createdAt: Long, val status: String = PendingStatus.WAITING, val resultJson: String? = null, val error: String? = null)`
  - `object PendingStatus { const val WAITING = "waiting"; const val PARSED = "parsed" }`
  - `@Dao interface PendingPhraseDao { @Insert suspend fun insert(p: PendingPhrase): Long; @Update suspend fun update(p: PendingPhrase); @Query("DELETE FROM pending_phrases WHERE id = :id") suspend fun delete(id: Long); @Query("SELECT * FROM pending_phrases WHERE status = :status ORDER BY createdAt") suspend fun getByStatus(status: String): List<PendingPhrase>; @Query("SELECT * FROM pending_phrases ORDER BY createdAt") fun observeAll(): Flow<List<PendingPhrase>> }`
  - `AppDatabase.pendingPhrases(): PendingPhraseDao`
  - `ExpenseDao.sumRange(startMillis: Long, endMillisExclusive: Long): Long` — `SELECT COALESCE(SUM(amountKopecks),0) FROM expenses WHERE timestamp >= :startMillis AND timestamp < :endMillisExclusive`

- [x] **Step 1: Падающий тест** `upgradesFrom6To7KeepingData` в `DatabaseUpgradeTest` — создать базу v6 (SQL из `6.json`, по образцу `createV3Database`), вставить трату, открыть через `AppDatabase.build(context, dbName)`; assert: трата на месте, `pendingPhrases().insert(...)` и `getByStatus(WAITING)` работают.
- [x] **Step 2: Run** `--tests "ru.my.traty.data.DatabaseUpgradeTest"` — FAIL (нет миграции / нет DAO)
- [x] **Step 3: Реализовать** — `MIGRATION_6_7`: `CREATE TABLE IF NOT EXISTS pending_phrases (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, text TEXT NOT NULL, createdAt INTEGER NOT NULL, status TEXT NOT NULL, resultJson TEXT, error TEXT)` + `CREATE INDEX IF NOT EXISTS index_pending_phrases_status ON pending_phrases (status)`. SQL сверить с сгенерированным `7.json`.
- [x] **Step 4: Run** весь `DatabaseUpgradeTest` — PASS
- [x] **Step 5: Commit** — включая `app/schemas/.../7.json`: `git commit -m "feat(data): pending_phrases queue, migration 6->7"`

### Task 5: `QuickEntry` — общий движок ввода

**Files:**
- Create: `app/src/main/java/ru/my/traty/capture/EntryDraft.kt`
- Create: `app/src/main/java/ru/my/traty/capture/QuickEntry.kt`
- Modify: `app/src/main/java/ru/my/traty/TratyApp.kt` — `val quickEntry = QuickEntry(db, categories, settings)` в `AppContainer`
- Test: `app/src/test/java/ru/my/traty/capture/EntryDraftTest.kt`, `app/src/test/java/ru/my/traty/capture/QuickEntryTest.kt`

**Interfaces:**
- Consumes: Task 1–4; `CategoryRepository.resolveForSave`, `IncomeDraftItem.toIncome`, `entryTimestamp`.
- Produces:
  - `@Serializable data class EntryRow(val type: EntryType, val amountText: String, val category: String, val subcategory: String, val place: String, val note: String, val source: String, val dateText: String, val suggestedCategory: NewCategory? = null)` (`NewCategory` переносится сюда из `MainViewModel.kt` и становится `@Serializable`)
  - `@Serializable data class EntryDraft(val rows: List<EntryRow>, val rawText: String, val origin: EntryOrigin, val replaces: SavedEntries? = null)`
  - `enum class EntryOrigin { VOICE, TEXT, MANUAL }` → `ExpenseSource`/`IncomeOrigin` через `toExpenseSource()`/`toIncomeOrigin()`
  - `@Serializable data class SavedEntries(val expenseIds: List<Long>, val incomeIds: List<Long>) { val count get() = expenseIds.size + incomeIds.size }`
  - `data class RuleOffer(...)` — переносится сюда из `MainViewModel.kt` без изменений
  - `data class SaveResult(val saved: SavedEntries, val ruleOffer: RuleOffer?, val newCategories: List<String>)`
  - `sealed interface EntryOutcome { data class AutoSaved(val result: SaveResult, val draft: EntryDraft) : EntryOutcome; data class NeedsConfirm(val draft: EntryDraft, val notice: String? = null) : EntryOutcome; data class Queued(val pendingId: Long) : EntryOutcome }`
  - `class QuickEntry(db: AppDatabase, categories: CategoryRepository, settings: SettingsRepository, zone: ZoneId = ZoneId.systemDefault(), clientFor: (AppSettings) -> LlmClient? = ::clientFor)`:
    - `suspend fun process(text: String, origin: EntryOrigin, spokenAt: Long = System.currentTimeMillis()): EntryOutcome`
    - `suspend fun save(draft: EntryDraft): SaveResult` — если `draft.replaces != null`, в одной транзакции удаляет те id и вставляет новые
    - `suspend fun draftFor(saved: SavedEntries, rawText: String, origin: EntryOrigin): EntryDraft` — для «Изменить» (`replaces = saved`)
    - `fun emptyDraft(origin: EntryOrigin, rawText: String = ""): EntryDraft` — одна пустая строка EXPENSE
    - `val changes: SharedFlow<Unit>` — эмитит после каждого сохранения (для виджета, Task 10)

- [x] **Step 1: Падающие тесты `EntryDraftTest`** — `rowFromParsedExpenseUsesKnownCategory`, `rowFromUnknownCategorySuggestsNew` (как текущий `toDraft`), `rowDefaultDateIsSpokenAtDay` (`spokenAt` = 2026-11-13 12:00 → `dateText == "2026-11-13"`), `incomeRowToIncomeKeepsSource`, `invalidAmountRowIsSkipped`.
- [x] **Step 2: Падающие тесты `QuickEntryTest`** (Robolectric, `TestTratyApp`, фейковый `LlmClient`, возвращающий заданный JSON или бросающий исключение):
  - `autoSavesMatchedExpenses` — правило «пятёрочка», ответ 2 траты с place → `AutoSaved`, `count == 2`, в `expenses` 2 строки, `hits` правила вырос.
  - `mixedPhraseNeedsConfirm` — 1 трата + 1 income → `NeedsConfirm`, rows типов `[EXPENSE, INCOME]`, в базе ничего.
  - `networkErrorQueues` — клиент бросает `java.net.UnknownHostException` → `Queued`, в `pending_phrases` одна запись `WAITING` с `createdAt == spokenAt`.
  - `httpErrorFallsBackOfflineAndConfirms` — клиент бросает `LlmHttpException(401, ...)` → `NeedsConfirm` с непустым `notice`.
  - `noClientUsesOfflineAndConfirms` — `clientFor = { null }`, текст «кофе 250» с правилом «кофе» → всё равно `NeedsConfirm`.
  - `garbageGivesEmptyRow` — ответ `{"items":[]}` → `NeedsConfirm`, одна пустая строка EXPENSE, `rawText` сохранён.
  - `replaceSwapsSavedEntries` — `save(draftFor(saved).copy(rows = отредактированные))` → старых id нет, новые есть.
  - `todayInPromptIsSpokenAtDay` — фейковый клиент запоминает `system`; `spokenAt` = 2026-11-13 → промпт содержит `2026-11-13`.
- [x] **Step 3: Run** `--tests "ru.my.traty.capture.*"` — FAIL
- [x] **Step 4: Реализовать** — логику `parseInput`/`parseIncomeInput`/`saveDraft`/`applyRules`/`toDraft` из `MainViewModel.kt` переносом, с изменениями:
  - сетевая ошибка = `IOException`, кроме `LlmHttpException` → `pendingPhrases().insert(PendingPhrase(text = text, createdAt = spokenAt))` → `Queued`;
  - `LlmHttpException` и `SerializationException`/`IllegalArgumentException` → `OfflineEntryParser` + `NeedsConfirm(notice = "${client.name}: … Использован простой разбор.")`;
  - после `RuleMatcher.apply` — `incrementHits` для сработавших правил; решение — `ParsePolicy.decide`; при `AUTO_SAVE` — `save()`.
  - `RuleOffer` — как сейчас: по первой сохранённой трате с непустым place без правила.
- [x] **Step 5: Run** — PASS; затем весь `./gradlew :app:testDebugUnitTest` — PASS
- [x] **Step 6: Commit** — `git commit -m "feat(capture): QuickEntry shared entry pipeline"`

### Task 6: Общий экран подтверждения `EntryConfirmSheet`

**Files:**
- Create: `app/src/main/java/ru/my/traty/ui/capture/EntryConfirmSheet.kt`
- Delete (в Task 7, после переключения главной): `ui/home/ConfirmDialog.kt`, `ui/home/IncomeConfirmDialog.kt`

**Interfaces:**
- Consumes: `EntryDraft`, `EntryRow`, `EntryType` (Task 5); `CategoryWithSubs`; `IncomeSources.ALL`.
- Produces: `@Composable fun EntryConfirmSheet(draft: EntryDraft, categories: List<CategoryWithSubs>, onDismiss: () -> Unit, onSave: (EntryDraft) -> Unit, modifier: Modifier = Modifier)`

- [x] **Step 1: Реализовать** — список строк; у каждой строки переключатель «Трата / Пополнение» (segmented button); для траты — поля и выбор категории/подкатегории как в текущем `ConfirmDialog.kt`, для пополнения — сумма, источник, заметка, дата как в `IncomeConfirmDialog.kt`; «+ строка», удаление строки; кнопка «Сохранить N». Поля переиспользовать из существующих диалогов (вынести общие composable, если они там приватные), `DateField.kt` — как есть.
- [x] **Step 2: Превью** — `@Preview` со смешанным черновиком («кофе 250, такси 400, зарплата 80000»); проверить через render compose preview.
- [x] **Step 3: Сборка** `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- [x] **Step 4: Commit** — `git commit -m "feat(ui): unified confirm sheet for mixed entries"`

### Task 7: `SpeechEngine` + системный движок + новая главная

**Files:**
- Modify: `app/src/main/java/ru/my/traty/speech/Speech.kt` — новый контракт
- Create: `app/src/main/java/ru/my/traty/speech/SystemSpeechEngine.kt`
- Create: `app/src/main/java/ru/my/traty/ui/capture/MicButton.kt`
- Create: `app/src/main/java/ru/my/traty/ui/capture/RecordController.kt`
- Modify: `app/src/main/java/ru/my/traty/ui/MainViewModel.kt` — удалить `input`, `inputFromVoice`, `incomeMode`, `toggleIncomeMode`, `incomeDraft`, `parseInput`, `parseIncomeInput`, `saveIncomeDraft`, `manualDraft`, `saveDraft`, `applyRules`, `toDraft`, `toDraftItem`, `amountAndTime`, `emptyItem`, классы `Draft`/`DraftItem`/`NewCategory`/`RuleOffer`; `testConnection` → `EntryParsing`
- Modify: `app/src/main/java/ru/my/traty/ui/home/HomeScreen.kt:303-325` — вместо `TratyInputBar`: чипсы шаблонов + `MicButton`; карточки очереди
- Modify: `app/src/main/java/ru/my/traty/ui/TratyRoot.kt` — показ `EntryConfirmSheet` вместо двух диалогов; снекбар «Сохранено N · Изменить»
- Delete: `ui/home/ConfirmDialog.kt`, `ui/home/IncomeConfirmDialog.kt`, `ai/ExpenseParsing.kt` и `ai/IncomeParsing.kt` (оставить `OfflineParser`/`OfflineIncomeParser`, перенеся их в `ai/OfflineParsers.kt`; `ExpenseParsingTest` → удалить, `IncomeParsingTest` — оставить только тесты офлайн-парсера)
- Delete: `TratyInputBar.kt`, если больше нигде не используется (проверить `DesignSystemPreview.kt`)
- Test: `app/src/test/java/ru/my/traty/ui/MainViewModelEntryTest.kt`

**Interfaces:**
- Consumes: `QuickEntry`, `EntryOutcome`, `EntryConfirmSheet`.
- Produces:
  - ```kotlin
    interface SpeechListener { fun onPartial(text: String); fun onLevel(level: Float); fun onFinal(text: String); fun onError(e: Throwable) }
    interface SpeechEngine { fun start(listener: SpeechListener); fun stop(); fun cancel(); fun release() }
    ```
    `stop()` — закончить и выдать `onFinal` с тем, что есть; `cancel()` — без `onFinal`. `onLevel` — 0..1.
  - `class SystemSpeechEngine(context: Context) : SpeechEngine` — `SpeechRecognizer.createSpeechRecognizer`, `ru-RU`, `EXTRA_PARTIAL_RESULTS = true`; `SpeechRecognizer.isRecognitionAvailable == false` → `onError(IllegalStateException("На телефоне нет сервиса распознавания речи"))`.
  - `object SpeechEngines { fun create(context: Context, settings: AppSettings): SpeechEngine }` — в этой задаче всегда `SystemSpeechEngine`; в Task 14 — выбор.
  - `class RecordController(engine: SpeechEngine, scope: CoroutineScope, onText: (String) -> Unit)` со `StateFlow<RecordState>`; `sealed interface RecordState { Idle; Listening(partial: String, level: Float); Processing; Error(message: String) }`; `fun toggle()`, `fun cancel()`. Используется главной (Task 7) и панелью (Task 8).
  - `MainViewModel`: `var confirm: EntryDraft?`, `var lastSaved: Pair<SavedEntries, EntryDraft>?` (для «Изменить»), `fun onSpokenText(text: String)`, `fun saveEntry(d: EntryDraft)`, `fun editLastSaved()`, `val pending: StateFlow<List<PendingPhrase>>`, `fun openParsedPending(p: PendingPhrase)`; `useTemplate` остаётся.
  - `@Composable fun MicButton(state: RecordState, onClick: () -> Unit, modifier: Modifier = Modifier)` — крупная круглая кнопка (≈96 dp) с пульсацией по `level`, под ней строка `partial`.

- [x] **Step 1: Падающие тесты `MainViewModelEntryTest`** (по образцу `MainViewModelDeleteTest`, фейковый клиент через `TestTratyApp` → `AppContainer` принимает `quickEntry` параметром):
  - `spokenMixedPhraseOpensConfirm` — `onSpokenText(...)` → `confirm != null`, 2 строки.
  - `spokenMatchedPhraseAutosavesAndOffersEdit` — `message == "Сохранено 2"`, `lastSaved != null`; `editLastSaved()` → `confirm.replaces == lastSaved.first`.
  - `queuedPhraseShowsNotice` — сетевая ошибка → `message == "Нет сети, разберу позже"`, `pending.value.size == 1`.
- [x] **Step 2: Run** `--tests "ru.my.traty.ui.MainViewModelEntryTest"` — FAIL
- [x] **Step 3: Реализовать** ViewModel, `SystemSpeechEngine`, `RecordController`, `MicButton`, правки `HomeScreen`/`TratyRoot`; запрос `RECORD_AUDIO` через `rememberLauncherForActivityResult(RequestPermission())` при первом нажатии; отказ → `message = "Нужен доступ к микрофону"`. Пустой список на главной: текст «Нажмите 🎤 и скажите, например: «кофе 250 и такси 400»». Карточки очереди над лентой: «N фраз ждут разбора» (WAITING, без действия) и «Разобрано, проверьте» (PARSED → `openParsedPending`, после сохранения запись удаляется).
- [x] **Step 4: Run** весь `./gradlew :app:testDebugUnitTest` — PASS; `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL
- [x] **Step 5: Проверка на устройстве** — установить; сказать «кофе 250 и такси 400» → подтверждение с 2 строками; создать правило «пятёрочка», сказать «пятёрочка 500» → снекбар «Сохранено 1 · Изменить»; «Изменить» открывает строку; «зарплата 80 тысяч и кофе 200» → подтверждение со строками разного типа; запретить микрофон → сообщение, не падает.
- [x] **Step 6: Commit** — `git commit -m "feat(home): single mic button, unified voice entry"`

---

## Фаза 2 — Панель записи, плитка, виджет

### Task 8: `CaptureActivity` — панель поверх приложений

**Files:**
- Create: `app/src/main/java/ru/my/traty/ui/capture/CaptureActivity.kt`, `CaptureViewModel.kt`, `RecordPanel.kt`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/themes.xml` (тема `Theme.Traty.Capture`: прозрачный фон, без анимации окна, `windowIsTranslucent`)
- Test: `app/src/test/java/ru/my/traty/ui/capture/CaptureViewModelTest.kt`

**Interfaces:**
- Consumes: `QuickEntry`, `RecordController`, `SpeechEngines`, `EntryConfirmSheet`, `MicButton`.
- Produces: `CaptureActivity` (exported=false; запускается `Intent(context, CaptureActivity::class.java)`), `CaptureViewModel` с `sealed interface CaptureUi { Recording; Confirm(draft); Saved(result, draft); Queued; Error(message) }` и `fun finishRequested: StateFlow<Boolean>`.

- [x] **Step 1: Падающие тесты `CaptureViewModelTest`** — `autosaveShowsSavedThenFinishes` (Saved → через 2 000 мс `finishRequested == true`, с `TestDispatcher.advanceTimeBy`); `editFromSavedOpensConfirmWithReplaces`; `queuedFinishesAfterNotice`; `confirmSaveFinishes`.
- [x] **Step 2: Run** `--tests "ru.my.traty.ui.capture.CaptureViewModelTest"` — FAIL
- [x] **Step 3: Реализовать** — манифест: `android:theme="@style/Theme.Traty.Capture"`, `launchMode="singleInstance"`, `taskAffinity="ru.my.traty.capture"`, `excludeFromRecents="true"`, `noHistory="false"`; `onNewIntent` — игнорировать, если запись идёт. Открытие → (нет разрешения → запрос) → сразу `RecordController.toggle()`. Панель — нижний лист: `MicButton`, текст, «Отмена»; тап вне листа / «назад» — `cancel()` + `finish()`. После сохранения — `TratyWidget.updateAll` (Task 10; до него — no-op).
- [x] **Step 4: Run** — PASS; assembleDebug — SUCCESS
- [x] **Step 5: Commit** — `git commit -m "feat(capture): translucent quick capture activity"`

### Task 9: Плитка в шторке

**Files:**
- Create: `app/src/main/java/ru/my/traty/tile/VoiceTileService.kt`, `app/src/main/res/drawable/ic_tile_mic.xml`
- Modify: `AndroidManifest.xml` — `<service android:name=".tile.VoiceTileService" android:exported="true" android:label="Сказать трату" android:icon="@drawable/ic_tile_mic" android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">` + intent-filter `android.service.quicksettings.action.QS_TILE`

**Interfaces:**
- Consumes: `CaptureActivity`.

- [x] **Step 1: Реализовать** `onClick()`: `if (isLocked) unlockAndRun { launch() } else launch()`; `launch()`: API ≥ 34 — `startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT))`, иначе `@Suppress("DEPRECATION") startActivityAndCollapse(intent.addFlags(FLAG_ACTIVITY_NEW_TASK))`. `onStartListening`: `qsTile.state = Tile.STATE_INACTIVE; updateTile()`.
- [x] **Step 2: Сборка** — SUCCESS
- [ ] **Step 3: Проверка на устройстве** — добавить плитку в шторку; тап → шторка закрывается, панель записи поверх текущего приложения, главное приложение не открывается; тап на заблокированном → просит разблокировать, затем панель. — **не проверено на телефоне**
- [x] **Step 4: Commit** — `git commit -m "feat(tile): quick settings voice tile"`

### Task 10: Виджет на рабочем столе

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` — `androidx.glance:glance-appwidget` (последняя стабильная 1.1.x, проверить version lookup), `glance-material3`
- Create: `app/src/main/java/ru/my/traty/widget/TratyWidget.kt`, `TratyWidgetReceiver.kt`, `WidgetTotals.kt`
- Create: `app/src/main/res/xml/traty_widget_info.xml`
- Modify: `AndroidManifest.xml` — receiver; `TratyApp.kt` — подписка на `quickEntry.changes` → `TratyWidget().updateAll(context)`
- Test: `app/src/test/java/ru/my/traty/widget/WidgetTotalsTest.kt`

**Interfaces:**
- Consumes: `ExpenseDao.sumRange` (Task 4), `BillingPeriod.containing`, `SettingsRepository`, `QuickEntry.changes`.
- Produces: `data class WidgetTotals(val todayKopecks: Long, val periodKopecks: Long)`; `suspend fun loadWidgetTotals(db: AppDatabase, settings: AppSettings, today: LocalDate, zone: ZoneId): WidgetTotals`.

- [x] **Step 1: Падающий тест `WidgetTotalsTest`** — `periodStartDay = 10`, today = 2026-11-14; траты 2026-11-09 (100 ₽, прошлый период), 2026-11-10 (200 ₽), 2026-11-14 (300 ₽), запись «Неучтённые» 2026-11-12 (50 ₽), доход 2026-11-14 → `today == 30000`, `period == 55000` (доход не учитывается).
- [x] **Step 2: Run** `--tests "ru.my.traty.widget.WidgetTotalsTest"` — FAIL
- [x] **Step 3: Реализовать** — Glance-виджет 2×1 (resizable до 4×2): кнопка «🎤 Сказать трату» (`actionStartActivity<CaptureActivity>()`), строки «Сегодня: X», «За период: Y» через `formatRub`; `updatePeriodMillis = 86400000`.
- [x] **Step 4: Run** — PASS; assembleDebug — SUCCESS
- [ ] **Step 5: Проверка на устройстве** — добавить виджет; тап → панель записи; после сохранения суммы обновились. — **не проверено на телефоне**
- [x] **Step 6: Commit** — `git commit -m "feat(widget): home screen voice widget with totals"`

---

## Фаза 3 — Очередь без сети

### Task 11: Фоновый разбор очереди

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` — `androidx.work:work-runtime-ktx` (последняя стабильная 2.10.x), тест: `androidx.work:work-testing`
- Create: `app/src/main/java/ru/my/traty/capture/PendingParseWorker.kt`
- Modify: `QuickEntry.kt` — `suspend fun processPending(): Int`; после `Queued` ставить уникальную работу
- Test: `app/src/test/java/ru/my/traty/capture/PendingParseTest.kt`

**Interfaces:**
- Consumes: `PendingPhraseDao`, `QuickEntry.process` (с `spokenAt = createdAt`).
- Produces: `QuickEntry.processPending(): Int` (сколько фраз обработано); `object PendingParseWork { fun enqueue(context: Context) }` — `enqueueUniqueWork("pending-parse", ExistingWorkPolicy.KEEP, OneTimeWorkRequest с Constraints(NetworkType.CONNECTED) и экспоненциальным backoff)`.

- [x] **Step 1: Падающие тесты `PendingParseTest`** — `matchedPhraseIsSavedAndRemoved`; `mixedPhraseBecomesParsedWithDraftJson` (status PARSED, `resultJson` декодируется в `EntryDraft` с 2 строками); `stillOfflineStaysWaiting` (клиент снова бросает `UnknownHostException` → WAITING, `processPending` возвращает 0 и worker вернёт `Result.retry()`); `relativeDateUsesCreatedAt` (createdAt = 2026-11-13, клиент получает «Сегодня: 2026-11-13»).
- [x] **Step 2: Run** `--tests "ru.my.traty.capture.PendingParseTest"` — FAIL
- [x] **Step 3: Реализовать** — `processPending` проходит `getByStatus(WAITING)`: `AutoSaved` → `delete(id)`; `NeedsConfirm` → `update(status = PARSED, resultJson = Json.encodeToString(draft))`; `Queued` от повторной сетевой ошибки — не создавать новую запись (вызывать внутренний разбор без вставки в очередь) и прекратить проход. Worker: `processPending()`; если остались WAITING → `Result.retry()`. Также ставить работу при старте приложения, если есть WAITING.
- [x] **Step 4: Run** — PASS; весь набор — PASS
- [ ] **Step 5: Проверка на устройстве** — авиарежим → сказать «пятёрочка 500 и зарплата 1000» → «Нет сети, разберу позже», карточка «1 фраза ждёт разбора»; выключить авиарежим → карточка «Разобрано, проверьте» → подтверждение со строками. — **не проверено на телефоне**
- [x] **Step 6: Commit** — `git commit -m "feat(capture): offline phrase queue with background parsing"`

---

## Фаза 4 — GigaAM

### Task 12: Пробный запуск модели (spike, без тестов)

**Files:**
- Modify: `app/build.gradle.kts` — sherpa-onnx: AAR последнего релиза из `github.com/k2-fsa/sherpa-onnx/releases` в `app/libs/` (`implementation(files("libs/sherpa-onnx-<ver>.aar"))`), `ndk { abiFilters += "arm64-v8a" }`
- Create: `docs/superpowers/notes/2026-xx-gigaam-spike.md` — итог

- [x] **Step 1:** Скачать на ПК кандидата №1 — GigaAM v3 e2e CTC int8 из `huggingface.co/protocolvoice/asr-models` (файл модели + tokens), Silero VAD (`silero_vad.onnx` из релизов sherpa-onnx). Скопировать на телефон: `adb push <files> /sdcard/Android/data/ru.my.traty/files/models/gigaam/`.
- [ ] **Step 2:** Временная кнопка в debug-настройках: загрузить `OfflineRecognizer` (тип модели — по карточке модели: NeMo CTC), распознать 5 фраз из WAV/микрофона: «кофе двести пятьдесят и такси четыреста», «пятёрочка полторы тысячи», «пришла зарплата восемьдесят тысяч», «вчера аптека шестьсот сорок», «доставка еды тысяча двести и мойка пятьсот». Замерить время загрузки модели и распознавания, пиковую память. — **пропущено**: движок написан сразу, модель проверена уже в составе приложения
- [ ] **Step 3:** Если v3 не грузится или ≥2 фразы из 5 с ошибкой в сумме — повторить с официальной GigaAM v2 sherpa-onnx (`sherpa-onnx-nemo-ctc-giga-am-v2-russian-2025-04-19` или transducer 2024-10-24). — **не понадобилось**: v3 работает на телефоне
- [x] **Step 4:** Записать в заметку: выбранная модель, URL каждого файла, размеры и SHA-256, тип модели для sherpa-конфига, замеры. Удалить временную кнопку. — сведения о модели (URL, размер, SHA-256, словарь, VAD) записаны в README вместо отдельной заметки; замеров скорости и памяти нет
- [x] **Step 5: Commit** — `git commit -m "chore(speech): gigaam spike notes, sherpa-onnx dependency"`

### Task 13: `ModelManager` — скачивание и проверка модели

**Files:**
- Create: `app/src/main/java/ru/my/traty/speech/ModelManager.kt`, `ModelManifest.kt`
- Create: `app/src/main/assets/silero_vad.onnx` (VAD в APK, ~2 МБ)
- Modify: `TratyApp.kt` — `val models = ModelManager(context)`; `data/Settings.kt` — ключ `voice_engine` (`VoiceEngine { GIGAAM, SYSTEM }`, по умолчанию GIGAAM)
- Modify: `ui/settings/SettingsScreen.kt` — раздел «Голос»: движок, состояние модели, «Скачать (~N МБ)» с прогрессом, «Удалить модель», подсказка про ручное копирование в `Android/data/ru.my.traty/files/models/gigaam/`
- Test: `app/src/test/java/ru/my/traty/speech/ModelManagerTest.kt`

**Interfaces:**
- Consumes: итог Task 12 (URL, размеры, SHA-256, тип модели).
- Produces:
  - `@Serializable data class ModelFile(val name: String, val url: String, val sizeBytes: Long, val sha256: String)`; `data class ModelManifest(val id: String, val kind: String /* "nemo_ctc" | "transducer" */, val files: List<ModelFile>)`; `val GIGAAM_MANIFEST: ModelManifest`
  - `sealed interface ModelState { Missing; Downloading(progress: Float); Ready(dir: File); Failed(message: String) }`
  - `class ModelManager(context: Context, manifest: ModelManifest = GIGAAM_MANIFEST, http: OkHttpClient = OkHttpClient())`: `val state: StateFlow<ModelState>`, `suspend fun download()`, `fun delete()`, `fun refresh()` (проверяет `getExternalFilesDir("models/gigaam")` — ручное копирование — и `filesDir/models/gigaam`)

- [x] **Step 1: Падающие тесты** (MockWebServer или фейковый `OkHttpClient` interceptor) — `missingWhenNoFiles`; `readyWhenAllFilesWithRightSize` (файлы созданы вручную с нужным размером → Ready; SHA проверяется только после скачивания); `downloadWritesTmpThenRenames` (после успешной загрузки нет `*.part`, state Ready); `badHashFailsAndDeletesPart`; `interruptedDownloadLeavesMissing`.
- [x] **Step 2: Run** `--tests "ru.my.traty.speech.ModelManagerTest"` — FAIL
- [x] **Step 3: Реализовать** — файлы качаются по одному в `<name>.part`, прогресс по сумме байтов, проверка SHA-256, `renameTo`. Добавить `testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")`, если выбран MockWebServer.
- [x] **Step 4: Run** — PASS
- [x] **Step 5: Commit** — `git commit -m "feat(speech): model manager with download and verification"`

### Task 14: `GigaAmEngine` и выбор движка

**Files:**
- Create: `app/src/main/java/ru/my/traty/speech/GigaAmEngine.kt`, `app/src/main/java/ru/my/traty/speech/VadSegmenter.kt`
- Modify: `speech/Speech.kt` — `SpeechEngines.create`: `GIGAAM` и `ModelState.Ready` → `GigaAmEngine`, иначе `SystemSpeechEngine`
- Test: `app/src/test/java/ru/my/traty/speech/VadSegmenterTest.kt`

**Interfaces:**
- Consumes: `ModelManager.state`, `ModelManifest.kind`, `SpeechEngine`/`SpeechListener` (Task 7).
- Produces:
  - `class VadSegmenter(silenceToStopMs: Int = 1500, maxDurationMs: Int = 30_000)` — чистая логика поверх флагов «речь/тишина» от VAD: `fun onFrame(isSpeech: Boolean, frameMs: Int): VadAction` где `enum class VadAction { CONTINUE, SEGMENT_END, STOP }`
  - `class GigaAmEngine(context: Context, modelDir: File, kind: String) : SpeechEngine`

- [x] **Step 1: Падающие тесты `VadSegmenterTest`** — `silenceBeforeSpeechNeverStops` (5 с тишины до речи → только CONTINUE; общий лимит 30 с всё равно действует); `pauseAfterSpeechEndsSegment` (речь, затем 400 мс тишины → SEGMENT_END один раз); `longPauseStops` (речь, затем 1500 мс тишины → STOP); `maxDurationStops` (30 с непрерывной речи → STOP).
- [x] **Step 2: Run** `--tests "ru.my.traty.speech.VadSegmenterTest"` — FAIL
- [x] **Step 3: Реализовать** `VadSegmenter`; Run — PASS
- [x] **Step 4: Реализовать `GigaAmEngine`** — `AudioRecord` 16 кГц mono PCM16 на отдельном потоке (`Dispatchers.IO`); Silero VAD (sherpa `Vad`, модель из assets) → `VadSegmenter`; на `SEGMENT_END` — распознать сегмент `OfflineRecognizer`, `onPartial(накопленный текст)`; на `STOP`/`stop()` — распознать остаток, `onFinal(весь текст)`; `onLevel` — RMS кадра. `OfflineRecognizer` создаётся в `start()` и освобождается через 30 с после `onFinal` (повторная запись в этом окне переиспользует его); `release()` освобождает сразу.
- [x] **Step 5: Сборка** — SUCCESS; весь набор тестов — PASS
- [ ] **Step 6: Проверка на устройстве** — с моделью: 10 фраз из Task 12 + 5 смешанных через плитку и виджет; цель — ≥9 из 10 сохранены правильно (сумма, тип, категория после подтверждения/автосохранения). Без модели — кнопка работает через системный движок. Авиарежим — распознавание работает, фраза уходит в очередь. — **частично**: GigaAM на телефоне работает (проверено 2026-11-14); замер «≥9 из 10» не делался, плитка/виджет/авиарежим с GigaAM не проверялись
- [x] **Step 7: Commit** — `git commit -m "feat(speech): on-device GigaAM engine"`

### Task 15: Документация

**Files:**
- Modify: `docs/PLAN.md` — этап 2: отметить сделанное (✅), «Готово, когда» с итогом проверки; таблица §1 строка «Голосовой ввод» → этап 2 ✅; в §3 добавить `pending_phrases` (этап 2)

- [x] **Step 1:** Обновить `PLAN.md`
- [x] **Step 2: Commit** — `git commit -m "docs: stage 2 done"`
