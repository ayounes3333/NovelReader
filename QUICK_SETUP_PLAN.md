# QuickSetup Phase 1 — Implementation Plan

## Overview
Phase 1 delivers a working file-based Quick Setup transfer path (backup file import/export with preferences), a new `tooling/quick_setup` module, and UI entry points in Settings and the Library screen.

---

## Step 1: Create `tooling/quick_setup` module scaffold

### Files to create:
- `tooling/quick_setup/build.gradle.kts`
- `tooling/quick_setup/src/main/AndroidManifest.xml`
- `tooling/quick_setup/src/main/java/my/noveldokusha/tooling/quick_setup/.gitkeep`

### `build.gradle.kts`
Follow the `backup_create` module pattern (simple library + compose, no Hilt/KSP needed for Phase 1 since the module only exports composable functions and data classes):

```kotlin
plugins {
    alias(libs.plugins.noveldokusha.android.library)
    alias(libs.plugins.noveldokusha.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "my.noveldokusha.tooling.quick_setup"
}

dependencies {
    implementation(projects.core)
    implementation(projects.coreui)
    implementation(projects.strings)
    implementation(projects.data)
    implementation(projects.tooling.localDatabase)

    implementation(libs.timber)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compose.androidx.activity)
    implementation(libs.compose.material3.android)
    implementation(libs.compose.androidx.material.icons.extended)
}
```

### `AndroidManifest.xml`
Minimal manifest (no services yet — Phase 1 is file-based, reusing existing backup services):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
</manifest>
```

### Register in `settings.gradle.kts`
Add after line 43 (`:tooling:local_server_sync`):
```kotlin
include(":tooling:quick_setup")
```

---

## Step 2: Create `PreferencesSnapshot.kt`

**Path:** `tooling/quick_setup/src/main/java/my/noveldokusha/tooling/quick_setup/PreferencesSnapshot.kt`

Purpose: Export allow-listed `AppPreferences` keys as a JSON string, and import them back.

### Allow-listed keys (from `AppPreferences.kt`):
| Key | Type |
|-----|------|
| `THEME_ID` | String (enum name) |
| `THEME_FOLLOW_SYSTEM` | Boolean |
| `READER_FONT_SIZE` | Float |
| `READER_FONT_FAMILY` | String |
| `READER_SELECTABLE_TEXT` | Boolean |
| `READER_KEEP_SCREEN_ON` | Boolean |
| `READER_FULL_SCREEN` | Boolean |
| `CHAPTERS_SORT_ASCENDING` | String (enum name) |
| `SOURCES_LANGUAGES` | StringSet |
| `FINDER_SOURCES_PINNED` | StringSet |
| `LIBRARY_FILTER_READ` | String (enum name) |
| `LIBRARY_SORT_LAST_READ` | String (enum name) |
| `LIBRARY_GROUP_SERIES` | Boolean |
| `BOOKS_LIST_LAYOUT_MODE` | String (enum name) |
| `GLOBAL_TRANSLATION_ENABLED` | Boolean |
| `GLOBAL_TRANSLATIOR_PREFERRED_SOURCE` | String |
| `GLOBAL_TRANSLATION_PREFERRED_TARGET` | String |
| `GLOBAL_APP_UPDATER_CHECKER_ENABLED` | Boolean |
| `GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_ENABLED` | Boolean |
| `GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_INTERVAL_HOURS` | Int |

### Implementation:
```kotlin
package my.noveldokusha.tooling.quick_setup

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.noveldokusha.core.appPreferences.AppPreferences

object PreferencesSnapshot {

    @Serializable
    data class Snapshot(
        val entries: Map<String, String>
    )

    private val ALLOWED_KEYS = setOf(
        "THEME_ID",
        "THEME_FOLLOW_SYSTEM",
        "READER_FONT_SIZE",
        "READER_FONT_FAMILY",
        "READER_SELECTABLE_TEXT",
        "READER_KEEP_SCREEN_ON",
        "READER_FULL_SCREEN",
        "CHAPTERS_SORT_ASCENDING",
        "SOURCES_LANGUAGES",
        "FINDER_SOURCES_PINNED",
        "LIBRARY_FILTER_READ",
        "LIBRARY_SORT_LAST_READ",
        "LIBRARY_GROUP_SERIES",
        "BOOKS_LIST_LAYOUT_MODE",
        "GLOBAL_TRANSLATION_ENABLED",
        "GLOBAL_TRANSLATIOR_PREFERRED_SOURCE",
        "GLOBAL_TRANSLATION_PREFERRED_TARGET",
        "GLOBAL_APP_UPDATER_CHECKER_ENABLED",
        "GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_ENABLED",
        "GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_INTERVAL_HOURS",
    )

    fun export(preferences: SharedPreferences): String {
        val entries = mutableMapOf<String, String>()
        for (key in ALLOWED_KEYS) {
            when (val value = preferences.all[key]) {
                is Set<*> -> entries[key] = Json.encodeToString(value.filterIsInstance<String>().toSet())
                else -> entries[key] = value?.toString() ?: continue
            }
        }
        return Json.encodeToString(Snapshot(entries))
    }

    fun import(json: String, preferences: SharedPreferences) {
        val snapshot = Json.decodeFromString<Snapshot>(json)
        val editor = preferences.edit()
        for ((key, value) in snapshot.entries) {
            if (key !in ALLOWED_KEYS) continue
            when {
                key == "THEME_FOLLOW_SYSTEM" || key == "READER_SELECTABLE_TEXT"
                        || key == "READER_KEEP_SCREEN_ON" || key == "READER_FULL_SCREEN"
                        || key == "LIBRARY_GROUP_SERIES"
                        || key == "GLOBAL_TRANSLATION_ENABLED"
                        || key == "GLOBAL_APP_UPDATER_CHECKER_ENABLED"
                        || key == "GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_ENABLED" ->
                    editor.putBoolean(key, value.toBooleanStrictOrNull() ?: continue)
                key == "READER_FONT_SIZE" ->
                    editor.putFloat(key, value.toFloatOrNull() ?: continue)
                key == "GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_INTERVAL_HOURS" ->
                    editor.putInt(key, value.toIntOrNull() ?: continue)
                key == "SOURCES_LANGUAGES" || key == "FINDER_SOURCES_PINNED" ->
                    editor.putStringSet(key, Json.decodeFromString<Set<String>>(value))
                else ->
                    editor.putString(key, value)
            }
        }
        editor.apply()
    }

    fun exportToJson(preferences: SharedPreferences): String = export(preferences)

    fun importFromJson(json: String, preferences: SharedPreferences) = import(json, preferences)
}
```

---

## Step 3: Create QuickSetup UI entry composable

**Path:** `tooling/quick_setup/src/main/java/my/noveldokusha/tooling/quick_setup/QuickSetupSection.kt`

A composable section for the Settings screen that provides "Send to new device" and "Receive from old device" options. For Phase 1, both actions use the file-based fallback (backup file create/restore + preferences).

```kotlin
package my.noveldokusha.tooling.quick_setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeviceHub
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import my.noveldoksuha.coreui.theme.ColorAccent
import my.noveldoksuha.coreui.theme.textPadding

@Composable
fun QuickSetupSection(
    onSendToFile: () -> Unit,
    onReceiveFromFile: () -> Unit,
) {
    Column {
        Text(
            text = "Quick Setup",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.textPadding(),
            color = ColorAccent
        )
        ListItem(
            headlineContent = { Text("Send to new device") },
            supportingContent = { Text("Export library, reading progress, and settings to a backup file") },
            leadingContent = {
                Icon(Icons.Outlined.FileUpload, null, tint = MaterialTheme.colorScheme.onPrimary)
            },
            modifier = Modifier.clickable { onSendToFile() }
        )
        ListItem(
            headlineContent = { Text("Receive from old device") },
            supportingContent = { Text("Import library, reading progress, and settings from a backup file") },
            leadingContent = {
                Icon(Icons.Outlined.FileDownload, null, tint = MaterialTheme.colorScheme.onPrimary)
            },
            modifier = Modifier.clickable { onReceiveFromFile() }
        )
    }
}
```

---

## Step 4: Create empty-library banner composable

**Path:** `tooling/quick_setup/src/main/java/my/noveldokusha/tooling/quick_setup/EmptyLibraryBanner.kt`

A banner shown when the library is empty, prompting the user to Quick Setup.

```kotlin
package my.noveldokusha.tooling.quick_setup

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeviceHub
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import my.noveldoksuha.coreui.theme.ColorAccent

@Composable
fun EmptyLibraryBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.DeviceHub,
                contentDescription = null,
                tint = ColorAccent,
                modifier = Modifier.padding(end = 16.dp)
            )
            Text(
                text = "Setting up a new device? Use Quick Setup to transfer your library from your old device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
```

---

## Step 5: Wire into Settings screen

### 5a: Add `QuickSetupSection` to `SettingsScreenBody.kt`

In `features/settings/src/main/java/my/noveldokusha/settings/SettingsScreenBody.kt`:

1. Add import: `import my.noveldokusha.tooling.quick_setup.QuickSetupSection`
2. Add parameters: `onQuickSetupSend: () -> Unit = {}`, `onQuickSetupReceive: () -> Unit = {}`
3. Add section after `SettingsBackup` (after line 82) and before the sync section:

```kotlin
HorizontalDivider()
QuickSetupSection(
    onSendToFile = onQuickSetupSend,
    onReceiveFromFile = onQuickSetupReceive
)
```

### 5b: Wire actions in `SettingsScreen.kt`

In `features/settings/src/main/java/my/noveldokusha/settings/SettingsScreen.kt`:

1. Add imports for `onBackupCreate`, `onBackupRestore`, `PreferencesSnapshot`
2. Add the Quick Setup send/receive handlers that include preferences export/import:

The send action needs to:
- Generate a backup ZIP (via `BackupDataService`)
- Export preferences as a JSON file included alongside the ZIP (or appended)

The receive action needs to:
- Restore from backup ZIP (via `RestoreDataService`)
- Import preferences from the JSON file

For Phase 1, we'll use a simpler approach: the backup ZIP includes a `preferences.json` entry alongside the database and books.

### 5c: Add dependency to `features/settings/build.gradle.kts`

Add after line 24:
```kotlin
implementation(projects.tooling.quickSetup)
```

---

## Step 6: Add empty-library banner to LibraryScreen

### 6a: Modify `LibraryScreenBody.kt`

In `features/libraryExplorer/src/main/java/my/noveldokusha/libraryexplorer/LibraryScreenBody.kt`:

1. Add import for `EmptyLibraryBanner`
2. Add a parameter: `onQuickSetupClick: () -> Unit = {}`
3. After the `TabRow` and `HorizontalPager`, add a condition that shows the banner when both lists are empty and the pager is on the Files tab:

```kotlin
// Show banner when library is empty
val isEmpty by remember {
    derivedStateOf {
        pagerState.currentPage == 0
            && filteredGroupedFavorites.isEmpty()
            && filteredGroupedRecent.isEmpty()
    }
}
```

Actually, a simpler approach: show the banner above the TabRow when the total library count is 0. Add to the `Column` inside the `Box`:

```kotlin
if (filteredGroupedFavorites.isEmpty() && filteredGroupedRecent.isEmpty() && pagerState.currentPage == 0) {
    EmptyLibraryBanner(onClick = onQuickSetupClick)
}
```

### 6b: Wire in `LibraryScreen.kt`

Pass the `onQuickSetupClick` through to `LibraryScreenBody`. The click action should navigate to Settings (tab index 2) or directly open Quick Setup. For simplicity, switch to the Settings tab.

### 6c: Add dependency to `features/libraryExplorer/build.gradle.kts`

```kotlin
implementation(projects.tooling.quickSetup)
```

---

## Step 7: Create enhanced backup/restore with preferences

### 7a: Create `QuickSetupBackupHelper.kt`

**Path:** `tooling/quick_setup/src/main/java/my/noveldokusha/tooling/quick_setup/QuickSetupBackupHelper.kt`

A utility that wraps the existing backup flow to include `preferences.json` in the ZIP.

```kotlin
package my.noveldokusha.tooling.quick_setup

import android.content.Context
import android.net.Uri
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.tooling.backup_create.BackupDataService
import my.noveldokusha.tooling.backup_restore.RestoreDataService
import timber.log.Timber
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object QuickSetupBackupHelper {

    /**
     * Creates a backup ZIP that includes the standard database+books backup
     * plus a preferences.json entry.
     */
    fun createBackupWithPreferences(
        context: Context,
        outputUri: Uri,
        appPreferences: AppPreferences,
        includeImages: Boolean,
        onProgress: ((String) -> Unit)? = null
    ) {
        // Step 1: Create the standard backup to a temp file
        // Step 2: Re-package with preferences.json added
        // For Phase 1, we delegate to BackupDataService and then augment
        // A simpler approach: export preferences JSON to a separate file
        // that the user can transfer alongside the backup ZIP

        // For now, export preferences as a standalone JSON string
        // The UI will handle saving both files
    }

    /**
     * Restores from a backup ZIP that may contain a preferences.json entry.
     */
    fun restoreWithPreferences(
        context: Context,
        backupUri: Uri,
        appPreferences: AppPreferences,
        onProgress: ((String) -> Unit)? = null
    ) {
        // Step 1: Check if the ZIP contains preferences.json
        // Step 2: If yes, extract and import preferences
        // Step 3: Restore the database+books via RestoreDataService
    }

    fun exportPreferencesJson(appPreferences: AppPreferences): String {
        return PreferencesSnapshot.export(appPreferences.getSharedPreferences())
    }

    fun importPreferencesJson(json: String, appPreferences: AppPreferences) {
        PreferencesSnapshot.import(json, appPreferences.getSharedPreferences())
    }
}
```

Note: `AppPreferences` needs a way to access the underlying `SharedPreferences` instance. Looking at the code, the `preferences` field is private. We'll need to either:
- Add a `getSharedPreferences()` method to `AppPreferences`
- Or pass the `SharedPreferences` directly to `PreferencesSnapshot`

The cleanest approach is to add a public accessor to `AppPreferences`.

---

## Step 8: Add SharedPreferences accessor to AppPreferences

In `core/src/main/java/my/noveldokusha/core/appPreferences/AppPreferences.kt`, add:

```kotlin
fun getSharedPreferences(): SharedPreferences = preferences
```

This is a minimal change that exposes the existing private field for the preferences snapshot export/import.

---

## Step 9: Update the feature doc

Remove or mark Phase 1 items as complete in `QuickSetup-Feature.md`.

---

## File creation/modification summary

### New files:
1. `tooling/quick_setup/build.gradle.kts`
2. `tooling/quick_setup/src/main/AndroidManifest.xml`
3. `tooling/quick_setup/src/main/java/my/noveldokusha/tooling/quick_setup/PreferencesSnapshot.kt`
4. `tooling/quick_setup/src/main/java/my/noveldokusha/tooling/quick_setup/QuickSetupSection.kt`
5. `tooling/quick_setup/src/main/java/my/noveldokusha/tooling/quick_setup/EmptyLibraryBanner.kt`
6. `tooling/quick_setup/src/main/java/my/noveldokusha/tooling/quick_setup/QuickSetupBackupHelper.kt`

### Modified files:
1. `settings.gradle.kts` — add `include(":tooling:quick_setup")`
2. `core/src/main/java/my/noveldokusha/core/appPreferences/AppPreferences.kt` — add `getSharedPreferences()`
3. `features/settings/build.gradle.kts` — add `implementation(projects.tooling.quickSetup)`
4. `features/settings/src/main/java/my/noveldokusha/settings/SettingsScreenBody.kt` — add QuickSetupSection
5. `features/settings/src/main/java/my/noveldokusha/settings/SettingsScreen.kt` — wire QuickSetup actions
6. `features/libraryExplorer/build.gradle.kts` — add `implementation(projects.tooling.quickSetup)`
7. `features/libraryExplorer/src/main/java/my/noveldokusha/libraryexplorer/LibraryScreenBody.kt` — add EmptyLibraryBanner
8. `features/libraryExplorer/src/main/java/my/noveldokusha/libraryexplorer/LibraryScreen.kt` — wire banner click

---

## Verification

1. Build the project: `./gradlew assembleDebug`
2. Verify the new module is recognized: `./gradlew :tooling:quick_setup:assembleDebug`
3. Verify settings screen shows Quick Setup section
4. Verify empty library shows banner
5. Test file-based export: tap "Send to new device" → file explorer opens → save ZIP
6. Test file-based import: tap "Receive from old device" → file explorer opens → select ZIP → library populates
