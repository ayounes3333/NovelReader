# AGENTS.md — NovelReader (NovelDokusha fork)

Android novel/web-fiction reader. Kotlin 2.0, Jetpack Compose, Hilt, Room, WorkManager,
multi-module Gradle (AGP 8.12, version catalog in `gradle/libs.versions.toml`,
convention plugins in `build-logic/`).

## Build & test

```bash
./gradlew :app:assembleDebug            # full build
./gradlew :<module>:compileDebugKotlin  # fast per-module compile check
./gradlew :<module>:testDebugUnitTest   # unit tests (few modules have tests)
```

Module path syntax: `:features:reader`, `:tooling:ai_engine`, `:data`, etc.
No ktlint/detekt configured. Source files may have CRLF line endings — harmless warnings on git ops.

## Module map

| Module | Purpose |
|---|---|
| `app` | `App.kt` (Hilt entry, WorkManager `HiltWorkerFactory`), `MainActivity`, DI glue |
| `core` | `AppPreferences` (all user settings, SharedPreferences delegates), base utils, `AppFileResolver` |
| `coreui` | Shared Compose theme/components (design system) |
| `data` | Repositories: `AppRepository` (facade), `BookChaptersRepository`, `ChapterBodyRepository`, `DownloaderRepository`, AI repos (`AiRepository`, `AiPromptsRepository`, `AiBatchRepositories`, `AiUsageStore`, `AiBulkJobStore`, `AiChatRepository` + `AiChatTools`, `WebSearchClient` DuckDuckGo HTML scraping), `interactor/` use-cases |
| `networking` | `NetworkClient` / `ScraperNetworkClient` (OkHttp) |
| `scraper` | Source/catalog scrapers for novel websites (`src/main/.../sources/`) |
| `navigation` | `Navigation.kt` — central Activity-based nav interface (`NavigationRoutes`) |
| `strings` | All user-facing strings (`values/strings.xml` + translations) |

### features/ (one Activity + ViewModel + Compose screen each)
`libraryExplorer` (home/library), `catalogExplorer`, `sourceExplorer`, `globalSourceSearch`,
`databaseExplorer`, `chaptersList` (book detail; AI bulk sheet), `reader` (core reading UI,
TTS, live translation, AI actions; `manager/ReaderSession` orchestrates state,
`features/ReaderChaptersLoader` loads text), `settings`, `webview`, `localExplorer`,
`aiChat` (per-book AI assistant chat; opened from reader top bar and chapters menu).

### tooling/
- `local_database` — Room `AppDatabase` (tables: Book, Chapter, ChapterBody, AiPrompt,
  ChapterAiRevision, AiChatMessage; `DAOs/`, `Migrations.kt` — bump version + add migration on schema change)
- `ai_engine` — AI core: `domain/` (AiTask, PromptTemplate, DefaultPrompts, AiProvider,
  AiChatModels — chat messages/tool specs), `engine/` (OpenAiCompatibleProvider — `complete()`
  and `chat()` with native function calling, AiAgentLoop — model→tool→model loop with
  TextToolProtocol fallback for endpoints lacking tool support, AiChunker, AiHttpClient,
  ProviderRegistry, AiSecretsStore — API keys in EncryptedSharedPreferences, never in `AppPreferences`)
- `application_workers` — WorkManager `@HiltWorker`s (library update, backup, AiBulkTaskWorker);
  `AppWorkersInteractions` implements `data`'s `WorkersInteractions`
- `text_translator` — on-device MLKit translation (`translator` real / `translator_nop` FOSS flavor)
- `text_to_speech`, `epub_parser`, `epub_importer`, `backup_create`, `backup_restore`,
  `firebase_sync`, `local_server_sync`, `quick_setup`, `local_source`, `algorithms`

## Conventions & gotchas

- Package naming is inconsistent: `data` module uses `my.noveldoksuha.data` (typo is intentional,
  do not "fix"); most others use `my.noveldokusha.*`.
- DI: Hilt everywhere. Workers use `@HiltWorker` + `HiltWorkerFactory` (no custom factory).
- Preferences: add new settings as `Preference<T>` objects in `AppPreferences`.
- Strings: always add to `strings/src/main/res/values/strings.xml`; reference via `R.string`.
- Navigation between features goes through the `navigation` module interfaces, never direct
  Activity references across feature modules.
- New modules: register in `settings.gradle.kts` and use the `noveldokusha.android.library`
  / compose convention plugins like sibling modules.
- AI feature flow: UI (reader/chaptersList/settings) → `data` AI repositories →
  `tooling/ai_engine`; bulk jobs run via `AiBulkTaskWorker`; results stored as
  `ChapterAiRevision` rows keyed by chapter URL + task type.
- AI chat flow: `features/aiChat` → `AiChatRepository.send()` → `AiAgentLoop` with
  `AiChatTools` (read_chapter, list_chapters, get_novel_info, search_novel_text, web_search,
  fetch_web_page). History persisted per book in `AiChatMessage`; web tools gated by
  `AI_CHAT_WEB_SEARCH_ENABLED`. Add new tools in `AiChatTools.all()`.
