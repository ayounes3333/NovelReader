# AI Integration Plan (Expanded)

## 1. Objective

Incorporate AI-powered text tooling into NovelReader to edit, translate, and fix chapters — both individually and in bulk. Users connect their own AI provider (OpenAI, DeepSeek, Z.ai, OpenCode Zen/Go, or any OpenAI-compatible endpoint), choose models per task, manage prompts, and track usage.

## 2. Features

| # | Feature | Description | Scope |
|---|---------|-------------|-------|
| 1 | Create Title | Generate a chapter title from its content | Single + Bulk |
| 2 | Create Summary | Concise overview of a chapter | Single + Bulk |
| 3 | Create Translation | Translate chapters into a target language | Single + Bulk |
| 4 | Fix Narration | Correct narration/POV/coherence issues | Single + Bulk |
| 5 | Fix Grammar | Grammar/spelling/punctuation correction | Single + Bulk |
| 6 | Custom Tasks | User-defined prompt templates for arbitrary edits | Single + Bulk |

All features operate on `ChapterBody.body` (and `Chapter.title` for titles). Results are stored non-destructively so originals can always be restored.

## 3. Architecture Fit

The app is 100% Kotlin, MVVM + Repository + Interactors, Hilt DI, Room, OkHttp/Retrofit, WorkManager, Compose UI, Intent-based navigation, with `full`/`foss` flavors. The AI integration follows these existing patterns.

### 3.1 New module: `:tooling:ai_engine`

Mirrors the pluggable design of `:tooling:text_translator` (domain interface + implementation):

```
tooling/ai_engine/
├── domain/          # interfaces & models (no network deps)
│   ├── AiProvider.kt          # provider abstraction
│   ├── AiTask.kt              # Title | Summary | Translate | FixNarration | FixGrammar | Custom
│   ├── AiModels.kt            # AiRequest, AiResult, AiUsage, AiModelInfo
│   └── PromptTemplate.kt      # template + variable substitution ({{content}}, {{language}}, …)
└── engine/          # implementation
    ├── providers/
    │   ├── OpenAiCompatibleProvider.kt   # covers OpenAI, DeepSeek, Z.ai, custom endpoints
    │   └── ProviderRegistry.kt           # id → provider, user-added custom endpoints
    ├── AiTaskExecutor.kt      # chunking, retries, rate-limit backoff
    ├── network/               # Retrofit service + Moshi models, streaming (SSE) support
    └── di/AiEngineModule.kt   # Hilt bindings
```

Key decisions:
- **OpenAI-compatible API first.** OpenAI, DeepSeek, Z.ai, and OpenCode expose `/v1/chat/completions`-style APIs; a single `OpenAiCompatibleProvider` with configurable base URL + API key covers all of them. Provider-specific quirks (headers, model listing, usage endpoints) handled via per-provider config.
- **Separate OkHttp client** from `ScraperNetworkClient` (no scraper interceptors/cookies), longer timeouts (120 s read) for LLM latency, provided in `AiEngineModule`.
- **Both flavors ship it** — it's user-key based, no proprietary SDK, so no `_nop` variant needed (unlike ML Kit).
- Results wrapped in existing `my.noveldokusha.core.Response<T>`.

### 3.2 Domain interfaces (sketch)

```kotlin
interface AiProvider {
    val id: String
    val name: String
    suspend fun listModels(): Response<List<AiModelInfo>>
    suspend fun complete(request: AiRequest): Response<AiResult>
    suspend fun usageStats(): Response<AiUsage?>   // null when provider has no usage API
}

data class AiRequest(
    val model: String,
    val systemPrompt: String,
    val userContent: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int? = null,
)

sealed interface AiTask {
    data object CreateTitle : AiTask
    data object CreateSummary : AiTask
    data class Translate(val targetLanguage: String) : AiTask
    data object FixNarration : AiTask
    data object FixGrammar : AiTask
    data class Custom(val promptId: Long) : AiTask
}
```

### 3.3 Data layer (`:data`)

New repositories following `ChapterBodyRepository` conventions:

- **`AiRepository`** — orchestrates: load chapter body → build prompt from template → chunk if needed → call provider → persist result.
- **`AiPromptsRepository`** — CRUD for user prompt templates (Room-backed).
- **`AiTaskInteractions`** (interactor, like `LibraryUpdatesInteractions`) — bulk-run logic used by the worker: iterate chapters, per-chapter progress, cancellation, failure collection.

### 3.4 Database changes (Room, `:tooling:local_database`, v9 → v10)

Non-destructive storage — never overwrite scraped originals:

```kotlin
@Entity
data class ChapterAiRevision(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chapterUrl: String,          // FK → Chapter.url (indexed)
    val taskType: String,            // TITLE | SUMMARY | TRANSLATION_<lang> | NARRATION | GRAMMAR | CUSTOM_<id>
    val content: String,             // generated text
    val model: String,
    val providerId: String,
    val active: Boolean,             // shown in reader instead of original
    val createdEpochTimeMilli: Long = System.currentTimeMillis(),
)

@Entity
data class AiPrompt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val systemPrompt: String,        // supports {{content}}, {{title}}, {{language}}
    val taskType: String,            // which built-in task it overrides, or CUSTOM
    val isBuiltIn: Boolean = false,
)
```

Plus `ChapterAiRevisionDao` (get by chapter+task, set active, bulk delete) and `AiPromptDao`. Migration `MIGRATION_9_10` creating both tables. Built-in default prompts seeded on first run.

### 3.5 Preferences (`AppPreferences.kt`)

Following existing `GLOBAL_TRANSLATION_*` pattern:

```kotlin
val AI_PROVIDER_ID          // String, e.g. "openai"
val AI_PROVIDER_BASE_URL    // String, for custom/compatible endpoints
val AI_MODEL_DEFAULT        // String
val AI_MODEL_PER_TASK       // String (JSON map task→model)
val AI_TRANSLATION_TARGET   // String, default from GLOBAL_TRANSLATION_PREFERRED_TARGET
val AI_MAX_CONCURRENT_REQUESTS // Int, default 1
```

**API keys are NOT stored in SharedPreferences.** Use `EncryptedSharedPreferences` (androidx.security-crypto) via a small `AiSecretsStore` in `:core`, keyed per provider id.

### 3.6 Bulk processing (`:tooling:application_workers`)

`AiBulkTaskWorker` modeled on `LibraryUpdatesWorker`:

- `@HiltWorker` + `CoroutineWorker`, unique work per (bookUrl, taskType).
- Constraints: `NetworkType.CONNECTED`; foreground notification with per-chapter progress (`setProgress` + notification like library updates).
- Input: bookUrl, chapter URL list (chunked), taskType, prompt id, model.
- Resumable: skips chapters that already have a revision for that task unless "regenerate" chosen; `Result.retry()` on transient failures with backoff; respects provider rate limits (configurable delay between requests, honor `Retry-After`).
- Failure report persisted so UI can show which chapters failed and offer retry.

## 4. UI Integration

### 4.1 Settings (`:features:settings`)

New section `SettingsAi.kt` (alongside `SettingsTranslationModels.kt`):
- Provider picker (preset list + "Custom OpenAI-compatible" with base URL field).
- API key entry (masked, stored encrypted) + "Test connection" (calls `listModels`).
- Default model picker (fetched from provider) and optional per-task overrides.
- Usage stats card — shown when provider exposes usage/billing API, else token counts accumulated locally from response `usage` fields.
- Prompt manager: list/edit/reset built-in prompts, create custom ones (navigates to a prompt editor screen).

### 4.2 Chapters list (`:features:chaptersList`)

- Selection-mode toolbar gains an "AI actions" menu: run any task on selected chapters (enqueues `AiBulkTaskWorker`).
- Per-chapter indicators (icon/badge) when AI revisions exist; row subtitle can show AI summary if generated.
- Bottom sheet before enqueue: task, model, prompt, target language (for translation), regenerate toggle, estimated chapter count.

### 4.3 Reader (`:features:reader`)

- Toggle between original and active AI revision (mirrors how `ReaderLiveTranslation` swaps text) — reader resolves body via: active `ChapterAiRevision` for TRANSLATION/NARRATION/GRAMMAR → else original `ChapterBody`.
- Chapter menu: "Run AI task on this chapter" for one-off runs with inline progress.
- Summary viewable from reader/chapter info dialog.

### 4.4 Navigation (`:navigation`)

Add to `NavigationRoutes`:
```kotlin
fun aiSettings(context: Context): Intent
fun aiPromptEditor(context: Context, promptId: Long?): Intent
fun aiTaskStatus(context: Context, bookUrl: String): Intent   // bulk job progress/failures
```

## 5. Cross-cutting Concerns

- **Chunking**: LLM context limits — split chapter bodies at paragraph boundaries (~3–4k tokens per chunk via char-count heuristic), process sequentially, rejoin. Title/summary use truncated head+tail sampling of very long chapters.
- **Cost safety**: confirmation dialog with chapter count before bulk runs; optional per-run chapter cap; local token accounting from API `usage` responses.
- **Offline/FOSS**: features degrade gracefully — menus hidden until a provider is configured. Existing ML Kit translation remains untouched as the free/offline option; AI translation is additive.
- **Backup/restore** (`:tooling:backup_restore`): include `ChapterAiRevision` and `AiPrompt` tables; never export API keys.
- **Sync**: out of scope for v1; revisions are local-only.
- **Errors**: all provider calls return `Response.Error` with user-readable message (auth failure, rate limit, quota) surfaced via existing toast/snackbar patterns.

## 6. Phased Delivery

| Phase | Deliverables | Depends on |
|-------|-------------|------------|
| **P1 — Foundation** | `:tooling:ai_engine` module, `OpenAiCompatibleProvider`, `AiSecretsStore`, DB migration 9→10, `AiRepository`, settings screen (provider + key + model + test connection) | — |
| **P2 — Single-chapter tasks** | Title, Summary, Grammar, Narration, Translation on one chapter from reader/chapters list; revision storage + reader toggle | P1 |
| **P3 — Bulk processing** | `AiBulkTaskWorker`, selection-mode AI menu, progress notification, task status screen, retry failed | P2 |
| **P4 — Prompts & customization** | Prompt manager UI, custom tasks, per-task model overrides | P2 |
| **P5 — Polish** | Usage stats, local token accounting, backup integration, streaming previews, apply-title-to-chapter (`ChapterDao.updateTitle`) | P3, P4 |

## 7. Testing

- Unit tests: prompt templating, chunking/rejoining, provider request/response mapping (MockWebServer).
- DAO/migration tests for v9→v10 (`local_database` androidTest).
- Worker test with `WorkManagerTestInitHelper`: resume, skip-existing, failure collection.
- Manual: run each task against a real provider on short/long chapters in both flavors.

## 8. Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Provider API drift / non-standard "compatible" APIs | Single compatible client + per-provider config overrides; connection test in settings |
| Long chapters exceed context window | Paragraph-boundary chunking with overlap for fix tasks |
| Runaway cost on bulk jobs | Confirmation with count, per-run cap, sequential requests by default |
| Losing original text | Revisions table only; originals never mutated (except explicit title apply) |
| API key leakage | EncryptedSharedPreferences; excluded from backups and logs |
