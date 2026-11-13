# Пополнения, расчётный период и сверка баланса — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ввод пополнений строкой с переключателем «+», расчётный период с настраиваемым днём начала, сверка фактического остатка с расчётным и учёт расхождения как «Неучтённые».

**Architecture:** Чистая логика (`BillingPeriod`, правило показа карточки, разбор пополнений) живёт отдельно от Android и покрыта JVM-тестами. Данные: схема Room v4 (`incomes.origin`, таблица `reconciliations`, системная категория «Неучтённые»), миграция 3→4. Сверка — `ReconciliationRepository` поверх существующих `expenses`/`incomes`: якорь сверки — момент ввода, ожидаемый остаток = фактический остаток прошлой сверки + пополнения − траты, записанные позже якоря. UI — новые экраны сверки отдельными файлами в `ui/reconcile/`.

**Tech Stack:** Kotlin 2.1, Compose Material 3, Room 2.7 (KSP), DataStore, JUnit4 + Robolectric + `kotlinx-coroutines-test`.

**Spec:** `docs/superpowers/specs/2026-11-13-balance-reconciliation-design.md` (плюс `docs/PLAN.md`, этап 1). Исполнитель читает оба.

**Состояние репозитория на старте:** ветка `main`, коммит `bb0ca28` содержит работу владельца: таблица `incomes` (схема v3, `Migration(2,3)`, тест), экран «Графики» (`ui/charts/`), `AddIncomeDialog`, `Screens.kt` разбит на `ui/home|stats|settings|navigation`, переключатель темы. Полный прогон тестов на старте красный: см. Task 1.

## Global Constraints

- Никаких `fallbackToDestructiveMigration*` кроме существующего `fallbackToDestructiveMigrationFrom(true, 1)`. Схема меняется только через `Migration(n, n+1)` + `app/schemas/.../N.json` в git + тест миграции (v3-база собирается `SQLiteDatabase` с SQL из `3.json`, как в `DatabaseUpgradeTest.createV2Database`).
- Сравнение названий без учёта регистра — только в Kotlin (`sameName`), не в SQL (кириллица).
- **Размер файлов:** при ~500 строках делить на компоненты по функционалу в отдельные файлы (правило владельца). Новые экраны — сразу отдельными файлами; в Task 9 проверить, что ни один файл не перешёл ~500 строк.
- UI только на компонентах `ui/designsystem` и `TratyTheme`; светлая и тёмная темы. Все тексты интерфейса на русском.
- Суммы — `Long` в копейках. Всё в рублях.
- Системные категории «Прочее» и «Неучтённые»: нельзя переименовать, архивировать или объединить с другой; «Прочее» всегда последняя в списках.
- Эмодзи «Неучтённые» — 🫥. День начала периода — `1..28`, по умолчанию `1`.
- Тон текстов о «Неучтённых» — спокойный, без упрёков.
- Тесты: `.\gradlew.bat :app:testDebugUnitTest`. Лог — в файл вне репозитория, читать хвост.

## Review Focus

1. **Границы периода:** день начала 28 и 1; период, пересекающий Новый год; дата ровно в день начала; смена дня начала посреди использования. Тест: `BillingPeriodTest.crossesYearBoundary`, `BillingPeriodTest.dayOfMonthStartIsInclusive` (Task 2).
2. **Первая сверка при пустых/неполных данных:** нет записей; записи только из одного вида; введён 0 ₽. Тест: `ReconciliationRepositoryTest.firstReconciliationOnlyStoresCapital`, `...zeroActualIsValidCapital` (Task 4).
3. **Повтор:** двойное нажатие «Сохранить» не создаёт две сверки; «Переделать» заменяет последнюю и её запись расхождения, не плодя дубли. Тест: `...secondCallNotAfterAnchorIsRejected`, `...redoReplacesLastAndItsEntry` (Task 4).
4. **Запись «Неучтённые» не считается дважды:** после сверки расхождение (датированное задним числом) не попадает в «ожидаемое» следующей сверки. Тест: `...discrepancyEntryDoesNotAffectNextExpectation` (Task 4).
5. **Знак при вводе:** переключатель «+» сбрасывается после сохранения; отмена диалога не оставляет режим «+» включённым навсегда; голос следует положению переключателя. Тест: `IncomeDraftTest.*`, ручная проверка (Task 6).

---

## Файлы

| Файл | Ответственность | Task |
|---|---|---|
| `ui/MainViewModel.kt`, `ui/charts/ChartsViewModel.kt`, `ui/home/HomeScreen.kt`, `ui/stats/StatsScreen.kt`, `ui/designsystem/components/TratyMonthPicker.kt`, `ui/Format.kt`, `ui/settings/SettingsScreen.kt` | период вместо месяца, настройка дня | 2 |
| `data/period/BillingPeriod.kt` (новый) | чистая арифметика периодов | 2 |
| `data/Settings.kt` | `periodStartDay`, `reconcileSnoozeUntilEpochDay`, `calculatorLines` | 2, 7 |
| `data/Database.kt` | v4, `Reconciliation`, `ReconciliationDao`, `Income.origin`, миграция 3→4 | 3 |
| `data/CategoryRepository.kt` | системные категории | 3 |
| `data/ReconciliationRepository.kt` (новый) | логика сверки | 4 |
| `ai/IncomeParsing.kt` (новый), `data/IncomeSources.kt` (новый) | разбор пополнений | 5 |
| `ui/designsystem/components/TratyInputBar.kt`, `ui/home/IncomeDraft.kt`, `ui/home/IncomeConfirmDialog.kt`, `ui/home/HomeScreen.kt`, `ui/MainViewModel.kt` | переключатель «+» и подтверждение | 6 |
| `ui/reconcile/*` (новые) | калькулятор, итог, карточка, правило показа | 7 |
| `backup/CsvBackup.kt`, `backup/CsvImporter.kt` | колонка «тип» | 8 |
| `README.md`, `docs/PLAN.md` | документация | 9 |

---

### Task 1: Ветка и зелёный базовый прогон

**Files:**
- Modify: `app/src/test/java/ru/my/traty/ui/ChartsViewModelTest.kt`, `app/src/test/java/ru/my/traty/ui/MainViewModelDateTest.kt`, при необходимости `TratyApp.kt` (тестовый путь контейнера)

**Interfaces:**
- Produces: полный `:app:testDebugUnitTest` зелёный и без 60-секундных ожиданий; ветка `balance-reconciliation`.

- [ ] **Step 1:** `git switch -c balance-reconciliation`.
- [ ] **Step 2: Воспроизвести.** Run: `:app:testDebugUnitTest --tests "ru.my.traty.ui.ChartsViewModelTest"`. Expected: FAIL `UncompletedCoroutinesError: After waiting for 1m` в `testAddIncomeAndCalculateBalance`; в полном прогоне соседние тесты падают с `UncaughtExceptionsBeforeTest`.
- [ ] **Step 3: Найти причину (systematic-debugging) и исправить тест.** Известно: оба VM-теста используют `@Config(application = TratyApp::class)` — настоящую базу `traty.db` и фоновый seed на `Dispatchers.IO`; ждут `Flow.first()` под виртуальным временем `runTest`. Допустимые решения: контейнер приложения с in-memory БД для тестов, `Dispatchers.setMain`, прокрутка главного цикла Robolectric. Нельзя: менять поведение продукта, чтобы тест прошёл. Записать выбранный подход в ledger как Ruling.
- [ ] **Step 4: Полный прогон.** Run: `:app:testDebugUnitTest`. Expected: 38/38 pass, прогон ≲ 1 минуты.
- [ ] **Step 5: Commit** `test: stabilize view-model tests (no real db, no real-time waits)`.

---

### Task 2: Расчётный период вместо календарного месяца

**Files:**
- Create: `data/period/BillingPeriod.kt`, `app/src/test/java/ru/my/traty/data/period/BillingPeriodTest.kt`
- Modify: `data/Settings.kt`, `ui/MainViewModel.kt`, `ui/charts/ChartsViewModel.kt`, `ui/home/HomeScreen.kt`, `ui/stats/StatsScreen.kt`, `ui/designsystem/components/TratyMonthPicker.kt`, `ui/designsystem/DesignSystemPreview.kt`, `ui/Format.kt`, `ui/settings/SettingsScreen.kt`, `app/src/test/java/ru/my/traty/ui/MainViewModelDateTest.kt`

**Interfaces:**
- Produces (`ru.my.traty.data.period`):
  - `const val MIN_PERIOD_START_DAY = 1`, `const val MAX_PERIOD_START_DAY = 28`
  - `data class BillingPeriod(val startDate: LocalDate, val endDate: LocalDate)` — `endDate` включительно; `fun startMillis(zone: ZoneId): Long`, `fun endMillisExclusive(zone: ZoneId): Long`, `operator fun contains(date: LocalDate): Boolean`, `fun previous(startDay: Int): BillingPeriod`, `fun next(startDay: Int): BillingPeriod`; `companion object { fun containing(date: LocalDate, startDay: Int): BillingPeriod }`
- Produces: `AppSettings.periodStartDay: Int = 1` (значение вне `1..28` при чтении приводится к границе), `SettingsRepository.setPeriodStartDay(day: Int)`.
- Produces (`ui/Format.kt`): `fun formatPeriod(p: BillingPeriod, startDay: Int): String` — для `startDay == 1`: «Ноябрь 2026»; иначе «10 ноя — 9 дек» (с годом, если период пересекает границу года: «10 дек — 9 янв 2027»).
- Produces (`MainViewModel`): `val period: StateFlow<BillingPeriod>` вместо `month`; `prevBillingPeriod()`, `nextBillingPeriod()` вместо `prevMonth()`/`nextMonth()`; `prevPeriod()`/`nextPeriod()` и `selectDate`/`clearSelectedDate` сохраняют смысл (день, если выбран, иначе период). `TratyMonthPicker(periodLabel: String, ...)` вместо `month: YearMonth`.

- [ ] **Step 1: Тесты `BillingPeriodTest` (чистый JUnit).** Для `startDay = 10`: `containing(2026-11-15)` = 2026-11-10..2026-12-09; `containing(2026-11-10)` начинается 2026-11-10 (`dayOfMonthStartIsInclusive`); `containing(2026-11-09)` = 2026-10-10..2026-11-09; для `startDay = 1`: `containing(2026-02-17)` = 2026-02-01..2026-02-28; `crossesYearBoundary`: `startDay = 28`, `containing(2026-12-30)` = 2026-12-28..2027-01-27; `previous(10)` от 2026-11-10..12-09 = 2026-10-10..11-09, `next` обратно; `startMillis`/`endMillisExclusive` соседних периодов стыкуются без зазора (`endMillisExclusive(p) == startMillis(p.next())`).
- [ ] **Step 2:** Run → FAIL (нет класса). **Step 3:** реализовать `BillingPeriod`. **Step 4:** Run → PASS.
- [ ] **Step 5: Настройки.** `periodStartDay` в `AppSettings`/`SettingsRepository` (ключ `period_start_day`); в `SettingsScreen` — карточка «День начала периода» (выбор `1..28`, подпись «1 — обычный календарный месяц; укажите день зарплаты, чтобы вести учёт от зарплаты до зарплаты»).
- [ ] **Step 6: Перевести экраны.** `MainViewModel` держит `period`, пересчитывает его при смене `periodStartDay` (подписка на `settings`), все запросы (`observeRange`, `observeTotals`, прошлый период, ИИ-сводка) идут по `period`/`period.previous`. `ChartsViewModel`: вкладка «Месяц» использует расчётный период тем же способом; «Квартал/Полгода/Год/Всё» остаются календарными по месяцам (ledger Ruling). `formatMonth` остаётся для графиков. Обновить `MainViewModelDateTest` под новые имена (смысл теста сохранить).
- [ ] **Step 7:** Run `:app:testDebugUnitTest` и `:app:assembleDebug` → зелёные. Ручная проверка: `startDay = 10` → шапка «10 ноя — 9 дек», траты/статистика/графики за период сходятся.
- [ ] **Step 8: Commit** `feat(period): configurable billing period start day`.

---

### Task 3: Схема v4 — сверки, происхождение пополнений, «Неучтённые»

**Files:**
- Modify: `data/Database.kt`, `data/CategoryRepository.kt`, `app/src/test/java/ru/my/traty/data/DatabaseUpgradeTest.kt`, `app/src/test/java/ru/my/traty/data/CategoryRepositoryTest.kt`
- Create: `app/schemas/ru.my.traty.data.AppDatabase/4.json` (генерирует KSP)

**Interfaces:**
- Produces: `object IncomeOrigin { MANUAL = "manual"; TEXT = "text"; VOICE = "voice"; RECONCILE = "reconcile" }`; `Income.origin: String = IncomeOrigin.MANUAL` (`@ColumnInfo(defaultValue = "'manual'")`); `ExpenseSource.RECONCILE = "reconcile"`.
- Produces: `@Entity("reconciliations") data class Reconciliation(id: Long = 0, timestamp: Long, actualKopecks: Long, expectedKopecks: Long?, differenceKopecks: Long, expenseId: Long?, incomeId: Long?, isOpening: Boolean)`, индекс по `timestamp`. `ReconciliationDao`: `insert(Reconciliation): Long`, `delete(Reconciliation)`, `getLast(): Reconciliation?`, `getAll(): List<Reconciliation>`, `observeAll(): Flow<List<Reconciliation>>`. `IncomeDao`: добавить `suspend fun sumAfter(afterMillis: Long): Long` (SUM по `timestamp > :afterMillis`, 0 если пусто), `suspend fun deleteById(id: Long)`, `suspend fun getFirstTimestamp(): Long?`. `ExpenseDao`: `suspend fun sumAfter(afterMillis: Long): Long`, `suspend fun deleteById(id: Long)`, `suspend fun getFirstTimestamp(): Long?`. `AppDatabase.reconciliations()`, `MIGRATION_3_4`, `version = 4`.
- Produces: `val Expense.isUserEditable: Boolean` (`source != ExpenseSource.RECONCILE`) и `val Income.isUserEditable: Boolean` (`origin != IncomeOrigin.RECONCILE`) — записи расхождения правятся и удаляются только через «Переделать».
- Produces: `DefaultCategories.UNACCOUNTED = "Неучтённые"`, `UNACCOUNTED_EMOJI = "🫥"`; `DefaultCategories.list` содержит её (26 категорий, «Прочее» последняя). `CategoryRepository.ensureUnaccounted(): Long` (id; создаёт при отсутствии), системность `Прочее`/`Неучтённые` в `updateCategory`/`setArchived`/`merge`(источник и цель — «Неучтённые» нельзя объединять в другую и в неё), `move` её не двигает.

- [ ] **Step 1: Тесты.** `DatabaseUpgradeTest.v3DatabaseMigratesToV4WithoutDataLoss`: база v3 (SQL из `3.json`) с 1 категорией, 1 тратой, 1 пополнением → после открытия: трата и пополнение на месте, `origin == "manual"`, в БД есть категория «Неучтённые» (🫥), `reconciliations` пуста и принимает вставку. Обновить `v1DatabaseIsRecreatedAndSeeded` и `seedCreates25CategoriesWithOtherLast` → 26. `CategoryRepositoryTest`: `unaccountedCannotBeRenamedArchivedOrMerged`, `ensureUnaccountedIsIdempotent`. Чистый тест `EditableTest.reconcileEntriesAreNotUserEditable` (трата с `source = reconcile` и пополнение с `origin = reconcile` → `false`, остальные → `true`).
- [ ] **Step 2:** Run → FAIL. **Step 3:** реализовать сущности, DAO, `MIGRATION_3_4` (`ALTER TABLE incomes ADD COLUMN origin TEXT NOT NULL DEFAULT 'manual'`; `CREATE TABLE reconciliations …` и индекс — дословно из сгенерированного `4.json`; вставка «Неучтённые» через `INSERT … WHERE NOT EXISTS`, `sortOrder = max(sortOrder среди не-«Прочее») + 1`), системность в `CategoryRepository`. **Step 4:** Run `:app:testDebugUnitTest` → PASS.
- [ ] **Step 5: Commit** `feat(db): schema v4 — reconciliations, income origin, system category «Неучтённые»`.

---

### Task 4: `ReconciliationRepository`

**Files:**
- Create: `data/ReconciliationRepository.kt`, `app/src/test/java/ru/my/traty/data/ReconciliationRepositoryTest.kt`
- Modify: `TratyApp.kt` (`AppContainer.reconciliations`)

**Interfaces:**
- Consumes: DAO из Task 3, `CategoryRepository.ensureUnaccounted()`.
- Produces: `data class Expectation(val capitalKopecks: Long, val sinceMillis: Long, val incomesKopecks: Long, val expensesKopecks: Long) { val expectedKopecks: Long }`; `sealed interface ReconcileOutcome { Opening(capitalKopecks), Matched, Unaccounted(amountKopecks), Surplus(amountKopecks) }` (суммы положительные); `class ReconcileException(message: String) : Exception(message)`; `class ReconciliationRepository(db: AppDatabase, categories: CategoryRepository)` с методами:
  - `suspend fun last(): Reconciliation?`
  - `suspend fun expectation(): Expectation?` — `null`, если сверок ещё не было; иначе капитал = `last.actualKopecks`, `sinceMillis = last.timestamp`, суммы пополнений/трат с `timestamp > sinceMillis`
  - `suspend fun reconcile(actualKopecks: Long, nowMillis: Long, entryDate: LocalDate, zone: ZoneId): ReconcileOutcome`
  - `suspend fun redoLast(actualKopecks: Long, nowMillis: Long, zone: ZoneId): ReconcileOutcome`
  - `fun observeAll(): Flow<List<Reconciliation>>`
- Правила: `actualKopecks ≥ 0`, иначе `ReconcileException`. Нет прошлой сверки → `Opening` (запись `Reconciliation(isOpening = true, expectedKopecks = null, differenceKopecks = 0)`, трат/пополнений не создаётся). Есть → `diff = actual − expected`: `0` → запись сверки без трат (`Matched`); `< 0` → `Expense(category = «Неучтённые», amountKopecks = −diff, timestamp = entryDate 12:00, source = RECONCILE)` (`Unaccounted`); `> 0` → `Income(source = «Прочее», amountKopecks = diff, timestamp = entryDate 12:00, origin = RECONCILE)` (`Surplus`). `nowMillis <= last.timestamp` → `ReconcileException("Сверка уже сделана")`. Всё внутри `db.withTransaction`. `redoLast`: удаляет записи расхождения последней сверки (по `expenseId`/`incomeId`) и её саму, затем `reconcile` с датой записи прежней сверки (`entryDate` берётся из удаляемой записи; для `Opening` — сегодняшняя) и новым `nowMillis`.

- [ ] **Step 1: Тесты (Robolectric, in-memory БД, сид).** Фиксированные `nowMillis`:
  - `firstReconciliationOnlyStoresCapital`: есть трата 500 ₽; `reconcile(20_000_00, …)` → `Opening(20_000_00)`, траты/пополнения не добавились, `expectation()` теперь не null.
  - `zeroActualIsValidCapital`: `reconcile(0, …)` → `Opening(0)`.
  - `negativeActualIsRejected`.
  - `unaccountedExpenseWhenLess`: капитал 20 000; позже пополнение 80 000 и трата 71 500 → `expected = 28 500`; `reconcile(25_500_00, …)` → `Unaccounted(3_000_00)`; в БД трата «Неучтённые» 3 000, `source = reconcile`, дата = `entryDate`.
  - `surplusIncomeWhenMore`: `reconcile(30_000_00, …)` при ожидаемых 28 500 → `Surplus(1_500_00)`; пополнение «Прочее», `origin = reconcile`.
  - `matchedWritesNoEntries`.
  - `discrepancyEntryDoesNotAffectNextExpectation`: после `Unaccounted` `expectation()` даёт `incomesKopecks = 0`, `expensesKopecks = 0`, капитал = фактический остаток (запись расхождения датирована задним числом, но не позже якоря).
  - `expenseDatedBeforeAnchorButRecordedLaterIsIgnored`: трата, добавленная после сверки, но датированная раньше якоря, в следующее «ожидаемое» не входит.
  - `secondCallNotAfterAnchorIsRejected`: второй `reconcile` с тем же `nowMillis` → `ReconcileException`; в БД одна сверка.
  - `redoReplacesLastAndItsEntry`: после `Unaccounted(3 000)` вызвать `redoLast(26_000_00, later)` → сверка одна, трата «Неучтённые» одна на 2 500 (а не две записи).
  - `skippedPeriodsAreSummedIntoOneEntry`: между сверкой и следующей пропущены несколько месяцев с записями → одна запись расхождения.
- [ ] **Step 2:** Run → FAIL. **Step 3:** реализовать. **Step 4:** Run → PASS. **Step 5: Commit** `feat(reconcile): reconciliation repository`.

---

### Task 5: Разбор пополнений

**Files:**
- Create: `ai/IncomeParsing.kt`, `data/IncomeSources.kt`, `app/src/test/java/ru/my/traty/ai/IncomeParsingTest.kt`
- Modify: `ui/charts/components/AddIncomeDialog.kt` (список источников берётся из `IncomeSources`)

**Interfaces:**
- Produces: `object IncomeSources { val ALL: List<Pair<String, String>> }` (название → эмодзи): Зарплата 💼, Аванс 💰, Дежурства 🩺, Перевод 💳, Подработка 🛠️, Возврат ↩️, Прочее 📦 (бывший список диалога + «Дежурства»); `const val DEFAULT = "Прочее"`.
- Produces (`ru.my.traty.ai`): `@Serializable data class ParsedIncome(amount: Double = 0.0, source: String = IncomeSources.DEFAULT, note: String? = null, date: String? = null)`; `object IncomePrompt { fun system(today: LocalDate): String }` (перечисляет `IncomeSources.ALL`, правила чисел словами и дат — как в `ParsePrompt`, формат `{"items":[{"amount","source","note","date"}]}`); `object IncomeParsing { suspend fun parse(client: LlmClient, text: String): List<ParsedIncome>; internal fun decode(raw: String): List<ParsedIncome> }`; `object OfflineIncomeParser { fun parse(text: String): List<ParsedIncome> }`.
- Правила офлайн-разбора: сумма — первое число с возможными разделителями тысяч пробелом/неразрывным пробелом («80 000» = 80000); источник по подстроке без регистра: `зарплат`→Зарплата, `аванс`→Аванс, `дежур`→Дежурства, `возврат|вернул`→Возврат, `подработ`→Подработка, иначе «Прочее»; заметка — остаток текста.

- [ ] **Step 1: Тесты `IncomeParsingTest` (JUnit).** `decodeObjectInsideCodeFence`, `decodeDropsNonPositive`, `unknownSourceFallsBackToDefault` (источник нормализуется под список без учёта регистра; неизвестный → «Прочее»), `offlineSalary` («зарплата 80000» → 80000, Зарплата), `offlineThousandsWithSpace` («аванс 30 000» → 30000, Аванс), `offlineKeywordVariants` («вернули 1500» → Возврат, «дежурство 4500» → Дежурства), `offlineNoNumberReturnsEmpty`.
- [ ] **Step 2:** Run → FAIL. **Step 3:** реализовать (структура — по образцу `ExpenseParsing.kt`/`OfflineParser`). **Step 4:** Run → PASS.
- [ ] **Step 5: Commit** `feat(ai): income phrase parsing`.

---

### Task 6: Переключатель «+» в строке ввода

**Files:**
- Create: `ui/home/IncomeDraft.kt`, `ui/home/IncomeConfirmDialog.kt`, `app/src/test/java/ru/my/traty/ui/home/IncomeDraftTest.kt`
- Modify: `ui/designsystem/components/TratyInputBar.kt`, `ui/home/HomeScreen.kt`, `ui/MainViewModel.kt`, `ui/TratyRoot.kt`, `ui/designsystem/DesignSystemPreview.kt`

**Interfaces:**
- Consumes: `ParsedIncome`, `IncomeParsing`, `OfflineIncomeParser`, `IncomeSources`, `Income`, `IncomeOrigin`.
- Produces: `data class IncomeDraftItem(amountText: String, source: String, note: String, dateText: String)`; `data class IncomeDraft(items: List<IncomeDraftItem>, rawText: String, origin: String)`; `fun IncomeDraftItem.toIncome(origin: String, zone: ZoneId, today: LocalDate = LocalDate.now()): Income?` (null при некорректной/неположительной сумме; время суток — текущее для сегодня, 12:00 для прошлых дат, как у трат); `TratyInputBar(..., isIncomeMode: Boolean = false, onToggleIncomeMode: (() -> Unit)? = null)`; `MainViewModel`: `var incomeMode by mutableStateOf(false)`, `fun toggleIncomeMode()`, `var incomeDraft by mutableStateOf<IncomeDraft?>(null)`, `fun saveIncomeDraft(d: IncomeDraft)`.
- Поведение: в строке ввода слева от поля — переключатель «−/+» (по умолчанию «−»; кнопка «+» слева «Добавить вручную» остаётся для трат); в режиме «+» плейсхолдер «зарплата 80000, аванс 30 тысяч…», поле и кнопка отправки окрашены доходным цветом (токен из `TratyTheme.colors`; если подходящего нет — добавить в `tokens/Color.kt`, оба варианта темы). `parseInput()` в режиме «+» вызывает `IncomeParsing`/`OfflineIncomeParser` и открывает `IncomeConfirmDialog` («Проверьте пополнение»: сумма, выбор вида из `IncomeSources`, заметка, дата `ГГГГ-ММ-ДД`); голос следует положению переключателя; `saveIncomeDraft` пишет в `incomes` (origin `text`/`voice`) и сбрасывает `incomeMode = false`; закрытие диалога без сохранения режим не меняет.

- [ ] **Step 1: Тесты `IncomeDraftTest` (JUnit).** `toIncomeParsesCommaAndSpaces` («80 000,50» → 8_000_050 коп.), `toIncomeRejectsNonPositiveOrGarbage` («0», «abc», «») → null, `toIncomeUsesNoonForPastDate`, `toIncomeKeepsOriginAndSource`.
- [ ] **Step 2:** Run → FAIL. **Step 3:** реализовать `IncomeDraft.kt`, UI и VM. **Step 4:** Run `:app:testDebugUnitTest` + `:app:assembleDebug` → зелёные.
- [ ] **Step 5: Ручная проверка на телефоне:** «+» → «зарплата 80000» → диалог → сохранить → пополнение в «Графиках», переключатель вернулся в «−»; «−» → «кофе 250» → обычная трата; голос в режиме «+».
- [ ] **Step 6: Commit** `feat(input): income entry toggle in the input bar`.

---

### Task 7: Сверка — калькулятор, итог, карточка

**Files:**
- Create: `ui/reconcile/ReconcilePolicy.kt`, `ui/reconcile/ReconcileViewModel.kt`, `ui/reconcile/ReconcileScreen.kt`, `ui/reconcile/BalanceCalculator.kt`, `ui/reconcile/ReconcileResult.kt`, `ui/reconcile/ReconcileCard.kt`, `app/src/test/java/ru/my/traty/ui/reconcile/ReconcilePolicyTest.kt`
- Modify: `data/Settings.kt`, `ui/home/HomeScreen.kt`, `ui/TratyRoot.kt`, `ui/settings/SettingsScreen.kt`, `ui/MainViewModel.kt` (промпт ИИ-комментария, защита записей сверки), `ui/charts/ChartsViewModel.kt`, `ui/charts/ChartsScreen.kt`

**Interfaces:**
- Consumes: `ReconciliationRepository`, `BillingPeriod`, `AppSettings.periodStartDay`.
- Produces: `object ReconcilePolicy { fun shouldPrompt(today: LocalDate, startDay: Int, referenceDate: LocalDate?, snoozeUntil: LocalDate?): Boolean; fun entryDate(today: LocalDate, startDay: Int, lastReconciledDate: LocalDate?, firstRecordDate: LocalDate?): LocalDate }`. `referenceDate` — дата последней сверки, а если сверок не было — дата самой ранней записи (`null` — записей нет → `false`). Показывать, если период, содержащий `today`, начинается позже периода, содержащего `referenceDate`, и `snoozeUntil` пуст или раньше `today`. `entryDate`: если период `today` позже периода reference — последний день предыдущего периода (закончившегося), иначе `today`.
- Settings: `reconcileSnoozeUntilEpochDay: Long` (0 = нет), `calculatorLines: List<String>` (названия строк калькулятора, хранятся одной строкой через `\n`).
- UI: `ReconcileCard` на Главной над списком: «Период закончился — сколько у вас сейчас денег на всех картах вместе?», кнопки «Сверить остаток» и «Напомнить завтра»; не блокирует работу. `ReconcileScreen` (открывается поверх, как `CategoriesScreen`, из карточки и из Настроек → «Сверка баланса»): шаг 1 `BalanceCalculator` — строки «название — сумма», «+ добавить строку», итог, названия подставляются из `calculatorLines`, суммы нет; шаг 2 `ReconcileResult` — «Должно остаться / Фактически», «Неучтённые: −3 000 ₽» (или «Лишние: +1 200 ₽ записаны как пополнение «Прочее»», или «Всё сходится 👍», или для первой сверки «Стартовый капитал: 25 500 ₽»), динамика «Неучтённых» за последние периоды (из `observeAll()`, расхождения < 0), кнопки «Готово» и (для последней сверки) «Переделать». Двойное нажатие «Готово» игнорируется (флаг `saving` ставится синхронно до `launch`). Записи расхождения защищены: `MainViewModel.delete` и `ChartsViewModel.deleteIncome` игнорируют записи с `isUserEditable == false`, диалог удаления в ленте и список пополнений показывают для них «Запись сверки» вместо кнопки «Удалить».
- Промпт ИИ-комментария (`requestInsight`): добавить правило «Если есть категория „Неучтённые“ — говори о ней спокойно и поддерживающе, без упрёков; это деньги, потраченные вне записей».

- [ ] **Step 1: Тесты `ReconcilePolicyTest` (JUnit).** При `startDay = 1`: `shouldPrompt` false, если записи только в текущем месяце; true на 1-е число следующего месяца; false при `snoozeUntil = завтра`, true при `snoozeUntil = вчера`; false при `referenceDate = null`; после сверки 12 дек (период 10 дек–9 янв, `startDay = 10`) false до 10 янв, true 10 янв; `entryDate` для сверки 12 дек при reference в предыдущем периоде = 9 дек; для сверки внутри периода reference = `today`.
- [ ] **Step 2:** Run → FAIL. **Step 3:** реализовать `ReconcilePolicy`, настройки, ViewModel (читает `ReconciliationRepository`, считает фактический итог калькулятора, вызывает `reconcile`/`redoLast` с `System.currentTimeMillis()`), экраны по перечню. **Step 4:** Run `:app:testDebugUnitTest` + `:app:assembleDebug` → зелёные.
- [ ] **Step 5: Ручная проверка на телефоне.** Первая сверка (введённая сумма = капитал); вторая: пополнение + траты → ожидаемый остаток верен; ввод меньше → «Неучтённые» появились в ленте и статистике; ввод больше → пополнение «Прочее»; «Переделать»; «Напомнить завтра»; светлая и тёмная темы.
- [ ] **Step 6: Commit** `feat(reconcile): calculator, result screen and period-end card`.

---

### Task 8: Пополнения в CSV

**Files:**
- Modify: `backup/CsvBackup.kt`, `backup/CsvImporter.kt`, `ui/MainViewModel.kt` (экспорт/импорт), `app/src/test/java/ru/my/traty/backup/CsvBackupTest.kt`, `app/src/test/java/ru/my/traty/backup/CsvImporterTest.kt`

**Interfaces:**
- Produces: `CsvRow.type: String = "трата"` (значения `трата`/`пополнение`), колонка «тип» в конце строки: `HEADER = "дата;сумма;валюта;категория;подкатегория;место;заметка;исходная фраза;тип"`; `CsvBackup.write(rows: List<ExpenseWithCategory>, incomes: List<Income>, zone, out)`; для пополнения `категория` = вид (`source`), `подкатегория`/`место` пусты; старые файлы без колонки читаются как траты; заголовок старого формата по-прежнему распознаётся (`hasHeader`). Импорт пополнений: `Income(origin = IncomeOrigin.MANUAL)`, дубли — `IncomeDao.countNear(amountKopecks, fromTs, toTs): Int` (окно ±60 с, как у трат). Записи расхождения («Неучтённые», origin reconcile) экспортируются как обычные траты/пополнения; сверки в CSV не входят (будут в полной резервной копии, этап 10).

- [ ] **Step 1: Тесты.** `CsvBackupTest.roundTripWithIncomes` (трата + пополнение → `parse` вернул обе, типы верны), `oldFormatWithoutTypeColumnParsesAsExpenses`, `incomeRowWithoutCategoryIsAccepted` (в строке пополнения категория — вид; пустой вид → «Прочее»). `CsvImporterTest.importsIncomesAndSkipsTheirDuplicates`, `incomeAndExpenseWithSameAmountAreNotDuplicates`.
- [ ] **Step 2:** Run → FAIL. **Step 3:** реализовать. **Step 4:** Run → PASS. **Step 5: Commit** `feat(backup): incomes in CSV export/import`.

---

### Task 9: Документация и проверка размеров файлов

**Files:**
- Modify: `README.md` (раздел «Что где лежит»), `docs/PLAN.md` (пометить сделанное в этапе 1: доходы и баланс, расчётный период; добавить сверку)

- [ ] **Step 1:** Найти файлы ≥ 500 строк: `Get-ChildItem -Recurse app/src -Filter *.kt | Where-Object { (Get-Content $_.FullName | Measure-Object -Line).Lines -ge 500 }`. Expected: пусто; иначе разделить по функционалу (правило владельца) и записать в ledger.
- [ ] **Step 2:** Обновить README и PLAN.md.
- [ ] **Step 3:** Полный прогон `:app:testDebugUnitTest` + `:app:assembleDebug` → зелёные. **Step 4: Commit** `docs: reconciliation, billing period, file size check`.
