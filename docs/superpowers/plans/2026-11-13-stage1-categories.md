# Этап 1, часть A: категории как сущности, схема v2, импорт CSV, редактор категорий — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Перевести траты с названий категорий на `categoryId`/`subcategoryId`, добавить восстановление из CSV и редактор категорий.

> **Решение владельца (13.11.2026):** реальных данных в v1 нет, их можно стирать. Поэтому переход 1→2 — пересоздание базы (`fallbackToDestructiveMigrationFrom(true, 1)`), без Kotlin-миграции. Правило «только миграции» действует для всех версий после 2.

**Architecture:** Room-схема v2: таблицы `categories` (id), `subcategories`, `expenses` с внешними ключами и полем `source`. Установленная база v1 пересоздаётся при обновлении (разрешено только для версии 1). Вся логика «найти или создать категорию/подкатегорию», переименование, архив, объединение — в `CategoryRepository`; ViewModel и разбор фраз продолжают работать с *названиями*, а id подставляются при сохранении. CSV — чистые функции в `backup/CsvBackup.kt` (запись и разбор) плюс `CsvImporter` для записи в базу.

**Tech Stack:** Kotlin 2.1, Compose Material 3, Room 2.7 (KSP), kotlinx.serialization. Тесты: JUnit4 + Robolectric (JVM, без устройства), `room-testing` (`MigrationTestHelper`), `kotlinx-coroutines-test`.

**Spec:** `D:\Загрузки\PLAN.md` — раздел «Этап 1 — Ядро учёта» (пункты «Категории и подкатегории», «Миграция с версии 0») и разд. 3 «Модель данных» (`expenses`, `categories`, `subcategories`); плюс договорённость в чате: импорт своего CSV переносится из этапа 9 в этап 1, ИИ может предлагать новые категории (порядок: категория из списка → новая категория). В Task 1 спецификация копируется в репозиторий как `docs/PLAN.md`.

**Вне этой части (следующие планы этапа 1):** редактирование/удаление траты с «Отменить», доходы и баланс, валюты и курсы ЦБ, шаблоны, правила «место → категория», поиск и фильтры.

## Global Constraints

- `minSdk = 26` → SQLite 3.18: нет `ALTER TABLE … DROP COLUMN` / `RENAME COLUMN`; таблицы меняются через «создать новую → скопировать → удалить старую → переименовать».
- Единственное исключение — `fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)`. Никаких других `fallbackToDestructiveMigration*`. Каждое следующее изменение схемы (v2→v3 и далее) — `Migration(n, n+1)` + JSON схемы в `app/schemas` под git + тест миграции.
- `SQLite COLLATE NOCASE` не работает для кириллицы. Сравнение названий без учёта регистра — только в Kotlin: `a.trim().lowercase() == b.trim().lowercase()`.
- Название категории: `trim()`, не пустое, не длиннее 40 символов, первая буква заглавная, уникально без учёта регистра.
- «Прочее» (`DefaultCategories.OTHER`) существует всегда; его нельзя переименовать, архивировать или объединить в другую категорию. В списках оно всегда последнее.
- Эмодзи категории, созданной автоматически (ИИ без эмодзи, импорт): `🏷️`.
- Ключи ИИ никогда не попадают в CSV.
- Все строки интерфейса — на русском. Новых runtime-зависимостей нет, только тестовые.
- Сборка/тесты: через IDE-инструмент Gradle или `.\gradlew.bat` (создаётся в Task 1). Тесты: `:app:testDebugUnitTest`.

## Review Focus

1. **Установка поверх версии с базой v1** — приложение не должно падать при открытии базы; после пересоздания есть стартовые категории. Тест: `DatabaseUpgradeTest.v1DatabaseIsRecreatedAndSeeded` (Task 2).
2. **Кириллица в разном регистре** («продукты» vs «Продукты», «рынок» vs «Рынок») — должна находиться существующая запись, а не создаваться дубль. Тест: `CategoryRepositoryTest.resolveIsCaseInsensitiveForCyrillic` (Task 2).
3. **CSV, пересохранённый в Excel**: дата `13.11.2026 14:03` вместо ISO, сумма `1 234,50` с пробелом/неразрывным пробелом, без BOM. Тест: `CsvBackupTest.parsesExcelResavedFormats` (Task 4).
4. **Повторный импорт той же копии** — не должен удваивать траты (в т.ч. когда Excel обрезал секунды). Тест: `CsvImporterTest.reimportSkipsDuplicates` (Task 4).
5. **Кавычки, `;` и переводы строк внутри заметки/исходной фразы** — экспорт→импорт без искажений. Тест: `CsvBackupTest.roundTripPreservesSpecialCharacters` (Task 4).

---

## Файлы

| Файл | Ответственность | Task |
|---|---|---|
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | тестовые зависимости, схемы Room в test assets | 1 |
| `app/src/test/java/ru/my/traty/ai/ExpenseParsingTest.kt` | первый тест (разбор JSON ответа модели) | 1 |
| `app/src/main/java/ru/my/traty/data/Database.kt` | сущности v2, DAO, `AppDatabase` v2, `DefaultCategories` | 2 |
| `app/src/main/java/ru/my/traty/data/CategoryRepository.kt` (новый) | найти/создать, переименовать, архив, порядок, объединение | 2, 3 |
| `app/src/main/java/ru/my/traty/TratyApp.kt` | `AppContainer` с репозиторием, сид v2 | 2 |
| `app/src/main/java/ru/my/traty/ai/ExpenseParsing.kt` | промпт и офлайн-разбор на `CategoryWithSubs` | 2 |
| `app/src/main/java/ru/my/traty/ui/MainViewModel.kt` | сохранение через репозиторий, `source`, импорт | 2, 4 |
| `app/src/main/java/ru/my/traty/ui/Screens.kt` | отображение по join-моделям, кнопки импорта и «Категории» | 2, 3, 4 |
| `app/src/main/java/ru/my/traty/ui/CategoriesScreen.kt`, `CategoriesViewModel.kt` (новые) | редактор категорий | 3 |
| `app/src/main/java/ru/my/traty/backup/CsvBackup.kt`, `CsvImporter.kt` (новые) | запись/разбор CSV, импорт в базу | 4 |
| `app/src/test/java/ru/my/traty/data/DatabaseUpgradeTest.kt`, `CategoryRepositoryTest.kt`, `backup/CsvBackupTest.kt`, `backup/CsvImporterTest.kt` | тесты | 2–4 |
| `README.md` | обновить разделы 7 и 8 | 4 |

---

### Task 1: Git, Gradle wrapper и тестовая инфраструктура

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create: `docs/PLAN.md` (копия `D:\Загрузки\PLAN.md`), `app/src/test/java/ru/my/traty/ai/ExpenseParsingTest.kt`

**Interfaces:**
- Produces: рабочая команда `:app:testDebugUnitTest`; Robolectric + `MigrationTestHelper` видят `app/schemas` как assets.

- [ ] **Step 1: Создать репозиторий и первый коммит текущего состояния**

Run: `git init; git add -A; git commit -m "chore: initial import (stage 0)"`
Expected: коммит создан; `git status` чистый; `local.properties`, `.idea/`, `app/build/` не попали в коммит (уже в `.gitignore`).

- [ ] **Step 2: Сгенерировать Gradle wrapper**

Run (IDE Gradle tool): `wrapper --gradle-version 8.13`
Expected: появились `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`. Скопировать `D:\Загрузки\PLAN.md` в `docs/PLAN.md`.

- [ ] **Step 3: Добавить тестовые зависимости**

В `libs.versions.toml`: `junit = "4.13.2"`, `robolectric = "4.14.1"`, `androidxTestCore = "1.6.1"`, `coroutinesTest` = версия `coroutines`; библиотеки `junit`, `robolectric`, `androidx-test-core`, `room-testing` (version.ref `room`), `kotlinx-coroutines-test`. В `app/build.gradle.kts`: все пять как `testImplementation`; в `android {}`:

```kotlin
testOptions { unitTests.isIncludeAndroidResources = true }
sourceSets["test"].assets.srcDir("$projectDir/schemas")
```

- [ ] **Step 4: Написать тест на существующий `ExpenseParsing.decode`**

```kotlin
class ExpenseParsingTest {
    @Test fun decodesObjectInsideCodeFence() {
        val r = ExpenseParsing.decode("```json\n{\"items\":[{\"amount\":250,\"category\":\"Кафе и рестораны\"},{\"amount\":400,\"category\":\"Транспорт\"}]}\n```")
        assertEquals(listOf(250.0, 400.0), r.map { it.amount })
        assertEquals("Транспорт", r[1].category)
    }
    @Test fun decodesBareArrayAndDropsNonPositive() {
        val r = ExpenseParsing.decode("[{\"amount\":0},{\"amount\":99.5,\"emoji\":\"🐶\",\"category\":\"Питомцы\"}]")
        assertEquals(1, r.size); assertEquals("🐶", r[0].emoji)
    }
}
```

- [ ] **Step 5: Запустить тесты**

Run: `:app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 2 tests passed (`decode` уже `internal` — доступен из теста того же модуля).

- [ ] **Step 6: Commit**

`git add -A; git commit -m "test: add unit test infrastructure and gradle wrapper"`

---

### Task 2: Схема v2, пересоздание базы v1, `CategoryRepository.resolveForSave`, перевод приложения на id

Задача одна, потому что смена сущностей ломает компиляцию всех вызовов: модуль должен компилироваться в конце задачи.

**Files:**
- Modify: `data/Database.kt`, `TratyApp.kt`, `ai/ExpenseParsing.kt`, `ui/MainViewModel.kt`, `ui/Screens.kt`
- Create: `data/CategoryRepository.kt`, `app/schemas/ru.my.traty.data.AppDatabase/2.json` (генерирует KSP)
- Test: `app/src/test/java/ru/my/traty/data/DatabaseUpgradeTest.kt`, `app/src/test/java/ru/my/traty/data/CategoryRepositoryTest.kt`

**Interfaces:**
- Consumes: `app/schemas/.../1.json` (текущая схема, не трогать).
- Produces (в `ru.my.traty.data`):
  - `@Entity("categories") data class Category(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val emoji: String, val color: Int? = null, val sortOrder: Int = 0, val isArchived: Boolean = false)`, уникальный индекс по `name`.
  - `@Entity("subcategories", foreignKeys = [categoryId → categories.id ON DELETE CASCADE]) data class Subcategory(@PrimaryKey(autoGenerate = true) val id: Long = 0, val categoryId: Long, val name: String, val sortOrder: Int = 0)`, индексы `categoryId` и уникальный `(categoryId, name)`.
  - `@Entity("expenses", foreignKeys = [categoryId → categories.id (без каскада), subcategoryId → subcategories.id ON DELETE SET NULL]) data class Expense(id: Long = 0, amountKopecks: Long, currency: String = "RUB", categoryId: Long, subcategoryId: Long? = null, place: String? = null, note: String? = null, timestamp: Long, rawText: String? = null, source: String = ExpenseSource.TEXT)`, индексы `categoryId`, `subcategoryId`, `timestamp`.
  - `object ExpenseSource { const val VOICE = "voice"; const val TEXT = "text"; const val MANUAL = "manual"; const val IMPORT = "import" }`
  - `data class CategoryWithSubs(@Embedded val category: Category, @Relation(parentColumn = "id", entityColumn = "categoryId") val subs: List<Subcategory>)` с `fun subNames(): List<String>` (по `sortOrder`).
  - `data class ExpenseWithCategory(@Embedded val expense: Expense, val categoryName: String, val categoryEmoji: String, val subcategoryName: String?)`
  - `data class CategoryTotal(val categoryId: Long, val name: String, val emoji: String, val total: Long)`
  - `ExpenseDao`: `insertAll(List<Expense>)`, `update`, `delete(Expense)`, `observeRange(from, to): Flow<List<ExpenseWithCategory>>`, `observeTotals(from, to): Flow<List<CategoryTotal>>`, `getAllWithCategory(): List<ExpenseWithCategory>` (по `timestamp`), `countByCategory(categoryId): Int`, `countNear(categoryId, amountKopecks, fromTs, toTs): Int`, `reassignCategory(fromId, toId)`, `reassignSubcategory(fromId, toId)`.
  - `CategoryDao`: `observeActiveWithSubs(): Flow<List<CategoryWithSubs>>`, `getActiveWithSubs()`, `observeAllWithSubs()`, `getAllWithSubs()`, `insert(Category): Long`, `update(Category)`, `delete(Category)`, `insertSub(Subcategory): Long`, `updateSub(Subcategory)`, `deleteSub(Subcategory)`, `count(): Int`. Все списки: `ORDER BY (name = 'Прочее'), sortOrder, name`.
  - `AppDatabase` `version = 2`; `build(context, name: String = "traty.db")` добавляет `.fallbackToDestructiveMigrationFrom(true, 1)`.
  - `object DefaultCategories { const val OTHER = "Прочее"; const val NEW_EMOJI = "🏷️"; val list: List<Pair<Category, List<String>>> }` — 25 категорий (ниже).
  - `class CategoryRepository(private val db: AppDatabase)`, `class CategoryException(message: String) : Exception(message)`, `data class ResolvedIds(val categoryId: Long, val subcategoryId: Long?)`, `suspend fun resolveForSave(categoryName: String, newCategoryEmoji: String?, subcategoryName: String?): ResolvedIds`, `suspend fun seedIfEmpty()`.
  - `fun normalizeName(raw: String): String` (top-level, `data/CategoryRepository.kt`): trim, схлопнуть пробелы, `take(40)`, первая буква заглавная.
  - `AppContainer.categories: CategoryRepository`.
  - В `ui`: `DraftItem.suggestedCategory: NewCategory?` вместо `Category?`, где `data class NewCategory(val name: String, val emoji: String)`; `Draft(items, rawText, source: String)`.

- [ ] **Step 1: Обновить сущности и DAO в `Database.kt`, `version = 2`, собрать, чтобы KSP сгенерировал `2.json`**

Ошибки компиляции в `ui/` и `ai/` на этом шаге ожидаемы. Run: `:app:kspDebugKotlin`. Expected: появился `app/schemas/ru.my.traty.data.AppDatabase/2.json`.

Стартовый набор `DefaultCategories.list` (категория, эмодзи, подкатегории):
Продукты 🛒 (Супермаркет, Рынок, Пекарня) · Кафе и рестораны ☕ (Кофе, Обед, Фастфуд) · Доставка еды 🛵 () · Транспорт 🚕 (Такси, Общественный транспорт, Поезд) · Авто 🚗 (Бензин, Мойка, ТО, Парковка, Страховка, Штрафы) · Жильё 🏠 (Аренда, Ипотека, Ремонт) · Коммунальные услуги 💡 (Электричество, Вода, Отопление) · Связь и подписки 📱 (Мобильная связь, Интернет, Подписки, ИИ-сервисы) · Здоровье 💊 (Аптека, Врачи, Анализы, Стоматология) · Спорт 🏋️ (Абонемент, Экипировка) · Красота и уход 💇 (Парикмахерская, Косметика) · Образование 📚 (Курсы, Книги, Конференции, НМО) · Одежда и обувь 👕 (Одежда, Обувь, Аксессуары) · Дом и быт 🧴 (Бытовая химия, Техника, Мебель) · Развлечения 🎬 (Кино, Игры, Концерты) · Хобби 🎨 () · Дети 🧸 (Игрушки, Кружки, Одежда) · Питомцы 🐾 (Корм, Ветеринар) · Подарки 🎁 () · Путешествия ✈️ (Билеты, Жильё в поездке, Экскурсии) · Долги и кредиты 💳 (Кредит, Рассрочка) · Налоги и сборы 🧾 (Налоги, Госпошлины) · Комиссии банков 🏦 () · Благотворительность 🤝 () · Прочее 📦 (). `sortOrder` = индекс в списке.

- [ ] **Step 2: Довести модуль до компиляции**

Создать `CategoryRepository` с сигнатурами из Interfaces (тела `resolveForSave` и `seedIfEmpty` — `TODO()`, заменяются в Step 7) и выполнить Step 8 (перевод разбора фраз и UI). Run: `:app:compileDebugUnitTestKotlin` → успешно.

- [ ] **Step 3: Тест пересоздания базы v1 (Robolectric, `MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)`)**

```kotlin
@Test fun v1DatabaseIsRecreatedAndSeeded()
// helper.createDatabase("upgrade.db", 1) → вставить v1-строку в categories ('Продукты','🛒','',0) и в expenses (25000,'RUB','Продукты',NULL,NULL,NULL,1000,NULL) → close;
// AppDatabase.build(context, "upgrade.db") + CategoryRepository(db).seedIfEmpty()
// → expenses().getAllWithCategory() пустой; categories().getActiveWithSubs().size == 25
```

Run: `:app:testDebugUnitTest --tests "ru.my.traty.data.DatabaseUpgradeTest"`. Expected: FAIL (`NotImplementedError` из `seedIfEmpty`; если падает с `IllegalStateException: A migration from 1 to 2 was required` — значит не подключён fallback). Проходит после Step 7.

- [ ] **Step 6: Тесты `CategoryRepository.resolveForSave` (Robolectric, `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries()`, `runTest`, сид `seedIfEmpty()`)**

```kotlin
@Test fun resolvesExistingCategoryAndSubcategory()     // ("Продукты", null, "Рынок") → id «Продукты», id «Рынок»; число категорий не изменилось
@Test fun resolveIsCaseInsensitiveForCyrillic()        // ("  продукты ", null, "РЫНОК") → те же id, что выше; дублей нет
@Test fun createsNewCategoryWithSuggestedEmoji()       // ("питомцы домашние", "🐶", "Корм") → создана «Питомцы домашние» с 🐶 и подкатегорией «Корм»
@Test fun newCategoryWithoutEmojiGetsTag()             // ("Яхты", null, null) → emoji '🏷️', subcategoryId == null
@Test fun blankCategoryFallsBackToOther()              // ("", null, null) → id «Прочее»
@Test fun createsUnknownSubcategoryInExistingCategory() // ("Продукты", null, "Сыры") → у «Продукты» появилась «Сыры»
@Test fun seedCreates25CategoriesWithOtherLast()       // getActiveWithSubs(): 25 штук, last().category.name == "Прочее"
```

- [ ] **Step 7: Реализовать `CategoryRepository.seedIfEmpty()` и `resolveForSave(...)`**

`resolveForSave` — в `db.withTransaction`; поиск среди `getAllWithSubs()` (включая архивные: архивная категория переиспользуется, не разархивируется); имена через `normalizeName`; новая категория получает `sortOrder = max + 1`. `AppContainer`: `val categories = CategoryRepository(db)`, `seedIfEmpty()` вызывает репозиторий.

- [ ] **Step 8: Перевести разбор фраз и UI на новые модели** (выполняется в рамках Step 2; здесь — что именно меняется)

- `ParsePrompt.system(categories: List<CategoryWithSubs>, today)` и `OfflineParser.parse(text, categories: List<CategoryWithSubs>)` — вместо `c.subList()` использовать `subNames()`; в промпт попадают только активные (вызывающий передаёт `getActiveWithSubs()`).
- `MainViewModel`: `categories: StateFlow<List<CategoryWithSubs>>` из `observeActiveWithSubs()`; `expenses: StateFlow<List<ExpenseWithCategory>>`; `toDraft` сравнивает с `category.name`; удалить `createSuggestedCategories`, `saveDraft` для каждого валидного `DraftItem` вызывает `c.categories.resolveForSave(item.category, item.suggestedCategory?.takeIf { it.name == item.category }?.emoji, item.subcategory.ifBlank { null })` и вставляет `Expense(..., source = d.source)`. Сообщение о новых категориях оставить: имена тех `suggestedCategory`, которых не было в `categories.value` до сохранения.
- Источник: `fun onVoiceResult(text: String)` ставит `input` и флаг `inputFromVoice = true`; `parseInput` создаёт `Draft(..., source = if (inputFromVoice) VOICE else TEXT)`; `manualDraft` → `MANUAL`; `saveDraft` сбрасывает флаг. В `HomeScreen` колбэк распознавания вызывает `vm.onVoiceResult(text)` вместо `vm.input = text`.
- `Screens.kt`: `ExpenseRow(e: ExpenseWithCategory, …)` берёт `categoryEmoji`, `subcategoryName ?: categoryName`; диалог удаления передаёт `e.expense`; статистика: `prevMap` по `categoryId`, `key = { it.categoryId }`, подпись `${t.emoji} ${t.name}`; `CategoryPicker(selected, categories: List<CategoryWithSubs>, suggested: NewCategory?, onPick)`.
- `requestInsight` и `exportCsv` — на `name` / `getAllWithCategory()` (export переедет в Task 4, здесь — минимально, чтобы компилировалось).

- [ ] **Step 9: Полная проверка**

Run: `:app:testDebugUnitTest` → все тесты PASS. Run: `:app:assembleDebug` → успешно.
Ручная проверка на телефоне: `installDebug` поверх установленной версии → приложение открывается, трат нет, в выборе 25 категорий; фраза «кофе 250 и такси 400» сохраняется двумя тратами и видна в статистике.

- [ ] **Step 10: Commit**

`git add -A; git commit -m "feat(db): schema v2 with category ids, subcategories table; recreate v1 db"`

---

### Task 3: Редактор категорий

**Files:**
- Modify: `data/CategoryRepository.kt`, `ui/Screens.kt` (кнопка «Категории» в настройках, показ экрана)
- Create: `ui/CategoriesViewModel.kt`, `ui/CategoriesScreen.kt`
- Test: `app/src/test/java/ru/my/traty/data/CategoryRepositoryTest.kt` (дополнить)

**Interfaces:**
- Consumes: `CategoryRepository`, `CategoryDao`, `ExpenseDao.countByCategory/reassignCategory/reassignSubcategory` из Task 2.
- Produces (методы `CategoryRepository`, все `suspend`, ошибки — `CategoryException` с текстом для пользователя):
  - `addCategory(name: String, emoji: String): Long`
  - `updateCategory(id: Long, name: String, emoji: String, color: Int?)`
  - `setArchived(id: Long, archived: Boolean)`
  - `move(id: Long, up: Boolean)` — меняет `sortOrder` с соседом среди *неархивных*, «Прочее» не двигается и не обменивается
  - `merge(fromId: Long, intoId: Long): Int` — возвращает число перенесённых трат
  - `addSubcategory(categoryId: Long, name: String): Long`, `renameSubcategory(id: Long, name: String)`, `deleteSubcategory(id: Long)`
- UI: `class CategoriesViewModel(app: Application) : AndroidViewModel(app)` с `categories: StateFlow<List<CategoryWithSubs>>` (`observeAllWithSubs`), `message: String?` и обёртками над методами репозитория, ловящими `CategoryException`. `@Composable fun CategoriesScreen(onBack: () -> Unit, vm: CategoriesViewModel = viewModel())`.

- [ ] **Step 1: Тесты операций**

```kotlin
@Test fun renameRejectsDuplicateIgnoringCase()     // updateCategory(id «Хобби», "продукты", …) → CategoryException; имя не изменилось
@Test fun renameOtherIsForbidden()                 // updateCategory(id «Прочее», "Разное", …) → CategoryException
@Test fun archivedHiddenFromActiveButExpensesKept() // трата в «Хобби»; setArchived(true) → нет в getActiveWithSubs(), трата и её categoryId на месте; getAllWithSubs() содержит «Хобби»
@Test fun archiveOtherIsForbidden()                // setArchived(id «Прочее», true) → CategoryException
@Test fun moveSwapsWithNeighbourAndKeepsOtherLast() // move(id «Кафе и рестораны», up = true) → «Кафе» первая, «Продукты» вторая; move(предпоследней, up = false) → без изменений, «Прочее» последнее
@Test fun mergeMovesExpensesAndSubcategories()     // «Доставка еды» (подкатегории: «Роллы»; 2 траты, одна с «Роллы») merge в «Кафе и рестораны» → вернуло 2; траты в «Кафе»; «Роллы» теперь подкатегория «Кафе»; «Доставка еды» удалена
@Test fun mergeUnifiesSameNamedSubcategory()       // у обеих есть «Обед» (разный регистр: «обед»/«Обед») → после merge одна «Обед» у цели, трата источника ссылается на неё
@Test fun mergeIntoItselfOrFromOtherIsForbidden()  // merge(a, a) и merge(id «Прочее», b) → CategoryException
@Test fun deleteSubcategoryNullsExpenseLink()      // трата с «Рынок»; deleteSubcategory → у траты subcategoryId == null, сама трата есть
```

- [ ] **Step 2: Запустить — падают** (`--tests "ru.my.traty.data.CategoryRepositoryTest"`, FAIL: методы не определены).

- [ ] **Step 3: Реализовать методы**

Каждый — в `db.withTransaction`. Проверки имён — общая приватная `validateName(name, exceptId: Long?)` поверх `normalizeName`: пустое → «Введите название»; занято → «Категория «X» уже есть». `merge`: для каждой подкатегории источника — если у цели есть с тем же `lowercase` именем, `reassignSubcategory(src, dst)` и удалить источник, иначе `updateSub(copy(categoryId = intoId, sortOrder = max + 1))`; затем `reassignCategory(fromId, intoId)`; удалить категорию-источник.

- [ ] **Step 4: Тесты проходят** (`--tests "ru.my.traty.data.CategoryRepositoryTest"` → PASS, все 16).

- [ ] **Step 5: UI редактора**

`CategoriesScreen` — полноэкранный `Scaffold` с `TopAppBar` («Категории», стрелка назад) и FAB «Добавить»; `BackHandler(onBack = onBack)`. Список: активные, затем заголовок «В архиве» и архивные (приглушённо). Строка: эмодзи, название, «N подкатегорий», кнопки ↑/↓ (у «Прочее» и архивных их нет). Нажатие → диалог правки: поле «Название», поле «Эмодзи» (одна строка), ряд из 8 цветов + «без цвета», список подкатегорий (переименовать полем, удалить ✕, добавить полем + «Добавить»), кнопки «Сохранить», «В архив»/«Вернуть из архива», «Объединить с…». «Объединить с…» — выбор категории из активных (кроме текущей) → подтверждение «Все траты (N) и подкатегории перейдут в «X». Категория «Y» будет удалена.» → `merge`, снекбар «Перенесено трат: N». Для «Прочее» скрыть переименование, архив и объединение.
Открытие: в `SettingsScreen` над «Резервная копия» — раздел «Категории» с `OutlinedButton("Категории и подкатегории")`; в `TratyRoot` состояние `var showCategories by rememberSaveable { mutableStateOf(false) }`, при `true` вместо `Scaffold` с вкладками показывается `CategoriesScreen(onBack = { showCategories = false })`.

- [ ] **Step 6: Проверка**

Run: `:app:assembleDebug` → успешно. На телефоне: переименовать категорию → новое имя в ленте трат и статистике; архивировать → её нет в выборе на экране подтверждения, старые траты отображаются; объединить две категории → суммы в статистике сложились.

- [ ] **Step 7: Commit** — `git commit -am "feat(categories): category editor with rename, archive, reorder and merge"` (+ `git add` новых файлов).

---

### Task 4: Экспорт и импорт CSV

**Files:**
- Create: `backup/CsvBackup.kt`, `backup/CsvImporter.kt`
- Modify: `ui/MainViewModel.kt` (`exportCsv` через `CsvBackup.write`, новый `importCsv`), `ui/Screens.kt` (кнопка импорта), `README.md`
- Test: `app/src/test/java/ru/my/traty/backup/CsvBackupTest.kt`, `app/src/test/java/ru/my/traty/backup/CsvImporterTest.kt`

**Interfaces:**
- Consumes: `ExpenseWithCategory`, `ExpenseDao.getAllWithCategory/countNear/insertAll`, `CategoryRepository.resolveForSave`, `ExpenseSource.IMPORT`.
- Produces (`ru.my.traty.backup`):
  - `data class CsvRow(val timestamp: Long, val amountKopecks: Long, val currency: String, val category: String, val subcategory: String?, val place: String?, val note: String?, val rawText: String?)`
  - `data class CsvParseResult(val rows: List<CsvRow>, val badLines: List<Int>)` — номера строк файла (с 1) с ошибкой
  - `object CsvBackup { const val HEADER = "дата;сумма;валюта;категория;подкатегория;место;заметка;исходная фраза"; fun write(rows: List<ExpenseWithCategory>, zone: ZoneId, out: Writer); fun parse(text: String, zone: ZoneId): CsvParseResult }`
  - `data class ImportResult(val imported: Int, val duplicates: Int, val badLines: List<Int>)`
  - `class CsvImporter(private val db: AppDatabase, private val categories: CategoryRepository) { suspend fun import(parsed: CsvParseResult): ImportResult }`
  - `MainViewModel.importCsv(uri: Uri)`

Формат записи — как сейчас: BOM, заголовок `HEADER`, разделитель `;`, все поля в `"…"` с удвоением `"`, сумма с запятой, дата `LocalDateTime.toString()` в `zone`, перевод строки `\n`.

- [ ] **Step 1: Тесты `CsvBackup`**

```kotlin
@Test fun roundTripPreservesSpecialCharacters()   // заметка = "сказал \"да\"; потом\nушёл", rawText = "кофе; 250" → write → parse → поля равны исходным, badLines пустой
@Test fun roundTripPreservesAmountsAndTime()      // 12345 коп. и 100 коп.; timestamp с секундами → после parse те же amountKopecks и timestamp (точность до мс)
@Test fun parsesExcelResavedFormats()             // без BOM; строки: "13.11.2026 14:03;1 234,50;RUB;Продукты;;;;" и "13.11.2026;99.9;RUB;Кафе и рестораны;Кофе;;;"; пробел в сумме — также U+00A0 → amountKopecks 123450 и 9990; первая — 14:03 в zone; вторая (без времени) — 12:00
@Test fun skipsHeaderAndBlankLines()              // заголовок с BOM и пустые строки в конце не считаются ни данными, ни ошибками
@Test fun reportsBadLinesAndKeepsGoodOnes()       // строка 3 с суммой "abc", строка 4 без категории → badLines == [3, 4], остальные строки импортированы
@Test fun emptyCurrencyDefaultsToRub()            // пустая валюта → "RUB"
```

- [ ] **Step 2: Запустить — падают** (`--tests "ru.my.traty.backup.CsvBackupTest"`, FAIL: `CsvBackup` не определён).

- [ ] **Step 3: Реализовать `CsvBackup`**

`parse` — посимвольный разбор с состоянием «внутри кавычек» (перевод строки внутри кавычек — часть поля; номер строки файла считать по физическим строкам начала записи). Дата: по очереди `LocalDateTime.parse` (ISO), `dd.MM.yyyy HH:mm[:ss]`, `dd.MM.yyyy` (→ 12:00), `yyyy-MM-dd` (→ 12:00). Сумма: удалить пробелы и `\u00A0`, `,` → `.`, `toBigDecimal()`, `* 100`, `setScale(0, HALF_UP)`; ≤ 0 — ошибка строки. Пустые поля → `null`. Категория обязательна.

- [ ] **Step 4: Тесты `CsvBackup` проходят.**

- [ ] **Step 5: Тесты `CsvImporter` (Robolectric, in-memory БД с сидом)**

```kotlin
@Test fun importsRowsCreatingMissingCategories()  // 2 строки, одна с категорией «Яхты» и подкатегорией «Парус» → imported=2; «Яхты» с 🏷️ и «Парус» созданы; source у трат == "import"
@Test fun reimportSkipsDuplicates()               // тот же parse-результат дважды → второй раз imported=0, duplicates=2; трат в БД 2
@Test fun duplicateDetectionToleratesLostSeconds() // в БД трата 14:03:42, в CSV та же сумма и категория на 14:03 → duplicates=1
@Test fun sameAmountDifferentDayIsNotDuplicate()  // та же сумма/категория, другой день → imported=1
```

Дубль = `countNear(categoryId, amountKopecks, timestamp - 60_000, timestamp + 60_000) > 0`, проверка — до вставки пакета, по состоянию БД (две одинаковые строки внутри одного файла обе импортируются). Всё — в `db.withTransaction`.

- [ ] **Step 6: Реализовать `CsvImporter.import`; тесты проходят** (`--tests "ru.my.traty.backup.*"` → PASS).

- [ ] **Step 7: Подключить в UI**

`exportCsv` использует `CsvBackup.write(c.db.expenses().getAllWithCategory(), zone, writer)`. `importCsv(uri)`: прочитать текст UTF-8 на `Dispatchers.IO`, `CsvBackup.parse`, `CsvImporter(c.db, c.categories).import(...)`; сообщение: «Импортировано: N, пропущено дублей: M» + при ошибках «, строки с ошибками: 3, 4» (не больше 10 номеров, затем «…»). В разделе «Резервная копия» `SettingsScreen` рядом с экспортом — `OutlinedButton("Импорт из CSV")` с `ActivityResultContracts.OpenDocument()` и типами `arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel")`.

- [ ] **Step 8: README**

Раздел 7: добавить `data/CategoryRepository.kt`, `backup/`, `ui/CategoriesScreen.kt`; убрать фразу «Список применяется только к пустой базе: … удалите приложение» — заменить на «Категории правятся в Настройки → Категории». Раздел 8: отметить `[x]` «Редактирование категорий в приложении» и «Импорт CSV».

- [ ] **Step 9: Проверка**

Run: `:app:testDebugUnitTest` → все PASS; `:app:assembleDebug` → успешно. На телефоне: экспорт → импорт того же файла → «Импортировано: 0, пропущено дублей: N»; открыть файл в Excel, сохранить как CSV, импортировать → снова только дубли, ошибок нет.

- [ ] **Step 10: Commit** — `git add -A; git commit -m "feat(backup): CSV import with duplicate detection; shared CSV writer"`
