# Quick Setup Feature

## Overview
The Quick Setup feature allows users to easily set up a new device by transferring the entire
library, downloaded content, and reading progress directly from an existing device.
It provides a seamless "old device → new device" migration experience over a USB cable
(fastest), Wi-Fi, or a manual backup-file transfer (fallback), without requiring an external
server or account.

## Goals
- One-time, device-to-device migration of all user data (library, chapters, progress, covers, settings).
- Zero-configuration discovery: devices find each other automatically on the same network.
- Secure by default: explicit pairing confirmation (QR code or 6-digit PIN) before any data flows.
- Works fully offline (LAN only) — no dependency on the external local-server-sync backend or Firebase.
- Resumable and robust against interruptions (Wi-Fi drops, app backgrounding).

## Non-Goals
- Continuous two-way sync (already covered by `tooling/local_server_sync` and `tooling/firebase_sync`).
- Cross-app or iOS transfer.
- Merging two already-populated libraries (v1 targets a fresh/near-fresh new device; conflicts resolve as "source wins", same-device data preserved where no collision).

---

## What Gets Transferred

| Data | Source | Notes |
|------|--------|-------|
| Books (library entries, metadata) | Room `Book` table (`tooling/local_database`) | Includes `inLibrary`, `completed`, `lastReadChapter`, `lastReadEpochTimeMilli` |
| Chapters + reading progress | Room `Chapter` table | `read`, `lastReadPosition`, `lastReadOffset` |
| Downloaded chapter content | Room `ChapterBody` table | Gzipped in transit (reuse approach from `LocalServerApiService`) |
| Cover images & book images | `filesDir/books/<folder>/` via `AppFileResolver` | Streamed as files; SHA-256 verified |
| Local EPUB files | `NovelFileInfo` + local book folders | Optional toggle (can be large) |
| App settings | `AppPreferences` (SharedPreferences) | Exported as JSON key/value snapshot; theme, reader font, TTS, library filters, etc. |

**Explicitly excluded:** auth tokens (`AuthTokenStorage`), Firebase credentials, debug/scraper settings.

---

## Architecture

### New module: `tooling/quick_setup`
Follows the existing tooling-module conventions (Hilt module, Compose UI, WorkManager where needed).

```
tooling/quick_setup/
├── server/
│   ├── QuickSetupServer.kt          # Embedded HTTP server on source device (Ktor CIO or NanoHTTPD)
│   └── QuickSetupServerService.kt   # Foreground service (FOREGROUND_SERVICE_DATA_SYNC) hosting the server
├── discovery/
│   ├── NsdAdvertiser.kt             # NsdManager service registration (_novelreader-qs._tcp)
│   └── NsdDiscoverer.kt             # NsdManager discovery + resolve on new device
├── pairing/
│   ├── PairingManager.kt            # PIN/QR generation & verification, session key derivation
│   └── QrCodePayload.kt             # ip:port + session token + fingerprint
├── transfer/
│   ├── QuickSetupExporter.kt        # Source side: snapshot DB + enumerate files
│   ├── QuickSetupImporter.kt        # Target side: batched insert (reuse RestoreDataService batching: 100/200/50)
│   ├── TransferManifest.kt          # Counts, sizes, checksums, schema version (Room v9)
│   └── PreferencesSnapshot.kt       # AppPreferences export/import
├── ui/
│   ├── QuickSetupScreen.kt          # Entry: "Send to new device" / "Receive from old device"
│   ├── SendFlowScreen.kt            # QR display, progress, summary
│   ├── ReceiveFlowScreen.kt         # Device list, QR scan / PIN entry, progress, summary
│   └── QuickSetupViewModel.kt
└── di/QuickSetupModule.kt
```

### Roles
- **Source (old device)** — acts as server: starts foreground service, hosts HTTP endpoints, advertises via NSD, displays QR/PIN.
- **Target (new device)** — acts as client: discovers via NSD (or QR scan for direct connect), pairs, pulls data, imports.

### Transfer protocol (HTTP over LAN)
Modeled on the existing `LocalServerApiService` endpoint style, but served locally:

```
GET  /qs/manifest                     # TransferManifest: schema version, counts, total bytes
GET  /qs/database                     # Streamed snapshot: books + chapters + bodies (paged, gzipped JSON)
GET  /qs/books/changes?page=N         # Book batches
GET  /qs/chapters/changes?page=N      # Chapter batches
GET  /qs/chapter-bodies?url=X         # Gzipped body content
GET  /qs/images/manifest              # Path + SHA-256 list
GET  /qs/images/{sha256}              # Image blob
GET  /qs/preferences                  # Settings JSON snapshot
POST /qs/complete                     # Target signals success; source shows confirmation
```

All endpoints require the pairing session token (`Authorization: Bearer <session-token>`).

### Security
- Session token generated per Quick Setup session, exchanged via QR code or 6-digit PIN typed on the target.
- Source shows an accept dialog with the target's device name before serving data.
- Server binds only for the session duration; shuts down on completion/cancel/timeout (5 min idle).
- Optional v2: TLS with ephemeral self-signed cert, fingerprint embedded in QR payload.

### USB cable transfer (AOA — Android Open Accessory)
Direct Android-to-Android USB transfer is what Samsung Smart Switch and Google's device-setup
flow use, and it's available to third-party apps via the AOA protocol. It's the fastest and
most reliable path (~25–40 MB/s over USB 2.0 bulk, no network required).

**How it works:**
- User connects the two devices with a USB-C↔USB-C cable (or A-to-C/OTG). USB role
  negotiation makes one device the **host** and the other the **peripheral**.
- Host side: `UsbManager` enumerates the peer, sends AOA control requests
  (`GET_PROTOCOL`, `SEND_STRING` identity, `START_ACCESSORY`) to switch it into accessory mode.
- Accessory side: app declares an `accessory_filter.xml` (manufacturer/model/version) and
  handles `USB_ACCESSORY_ATTACHED`; opens the accessory `ParcelFileDescriptor` for a raw
  bidirectional byte stream.
- Quick Setup runs the **same logical transfer protocol** as Wi-Fi (manifest → paged
  batches → images → preferences → complete), but framed over the USB stream instead of
  HTTP: length-prefixed messages `[type:1][length:4][payload]`, with the same gzip and
  SHA-256 verification.

**Implementation notes:**
- New components in `tooling/quick_setup/usb/`: `AoaHostConnection.kt` (host role),
  `AoaAccessoryConnection.kt` (accessory role), `UsbFramingCodec.kt`, plus a shared
  `TransferChannel` abstraction so exporter/importer are transport-agnostic (HTTP or USB).
- Either device can end up host or accessory depending on cable/role negotiation, so both
  roles must support both send and receive directions over the framed protocol.
- No pairing PIN/QR needed — physical cable + Android's USB permission dialog is the consent.
- Known friction to handle: per-connection USB permission prompts, OEM role-swap quirks,
  and devices that charge-only by default (prompt user to select "File transfer/USB data").

### File-based fallback (no cable, no shared network)
For cases where neither Wi-Fi nor a cable works, reuse the proven backup pipeline:
1. Source: Quick Setup generates a standard backup ZIP via `BackupDataService` (database + books folder) plus a `preferences.json`.
2. User moves the file via OTG drive/SD card/SAF.
3. Target: Quick Setup "Import from file" invokes `RestoreDataService` plus preferences import.

This fallback is nearly free to implement and battle-tested.

---

## User Flow (revised)

### New device (target)
1. First launch: Library is empty → banner/card "Setting up a new device? Quick Setup" (also always reachable from Settings → Quick Setup).
2. User taps **Receive from another device**.
3. Chooses method: **USB cable (fastest)**, **Wi-Fi**, or **From backup file**.
   - USB: connect cable → Android USB permission dialog on both sides → devices auto-detect roles and proceed straight to the manifest/summary step.
4. Wi-Fi: app runs NSD discovery, lists found devices; user picks one, then scans the QR shown on the old device (or types the PIN).
5. Manifest fetched → summary shown ("142 books, 3,801 chapters, 1.2 GB downloaded content, settings") with toggles: include downloaded chapters / include images / include settings.
6. Transfer runs with progress (per-category counts, bytes, ETA); survives screen-off via foreground notification.
7. Completion screen → "Start reading"; library opens fully populated.

### Old device (source)
1. Settings → Quick Setup → **Send to a new device**.
2. Foreground service starts, QR + PIN displayed.
3. Accept dialog when target connects; live progress; done confirmation.

### Failure handling
- Connection loss: target retains partial import state (manifest + completed page cursor) and offers **Resume** — already-imported batches are skipped via manifest checksums.
- Schema mismatch: source DB newer than target app supports → prompt to update app first; older → migrations applied on import (same as `RestoreDataService` v1→v9 path).

---

## Implementation Phases

### Phase 1 — Foundation
- Create `tooling/quick_setup` module + Hilt wiring, add to `settings.gradle.kts`.
- `PreferencesSnapshot`: export/import allow-listed `AppPreferences` keys as JSON.
- File-based flow: Quick Setup UI wrapping `BackupDataService`/`RestoreDataService` + preferences (delivers a working transfer path end-to-end on day one).
- Settings entry (`features/settings` new section `QuickSetupSection.kt`) + empty-library banner in `libraryExplorer`.

### Phase 2 — Transport-agnostic transfer core + Wi-Fi
- `TransferChannel` abstraction so exporter/importer work over any transport.
- Embedded server (Ktor CIO embedded, aligns with existing OkHttp/kotlinx-serialization stack) inside a foreground service.
- Exporter endpoints with paging + gzip; importer with `RestoreDataService`-style batching.
- Image streaming with SHA-256 verification (mirrors `local_server_sync` image service design).

### Phase 3 — USB (AOA) transport
- AOA host/accessory connections, framed protocol over the USB stream.
- `USB_ACCESSORY_ATTACHED` intent filter + `accessory_filter.xml`, role auto-detection UX.
- Reuses the Phase 2 transfer core unchanged via `TransferChannel`.

### Phase 4 — Discovery & pairing (Wi-Fi)
- NSD advertise/discover (`_novelreader-qs._tcp`), fallback manual `ip:port` entry.
- QR generation (ZXing) on source, CameraX/ML Kit scan on target; PIN alternative.
- Accept dialog + session token auth.

### Phase 5 — Resilience & polish
- Resume support, idle timeouts, cancellation from both sides.
- Progress notifications, transfer summary/receipt.
- Wi-Fi Direct exploration (optional, for no-shared-network case).

---

## Technical Considerations
- **Permissions:** `NEARBY_WIFI_DEVICES` (API 33+, with `neverForLocation`), `ACCESS_WIFI_STATE`, CAMERA (QR scan only, runtime-requested); reuse existing `FOREGROUND_SERVICE_DATA_SYNC` + `POST_NOTIFICATIONS`. USB/AOA needs no manifest permission — consent comes from the per-connection system USB dialog; requires `<uses-feature android:name="android.hardware.usb.accessory"/>` (host side: `usb.host`) marked `required="false"`.
- **Consistency:** export from a Room checkpoint/snapshot (WAL checkpoint or read-transaction paging) so a mid-transfer write on the source can't corrupt the payload.
- **Memory:** never load full tables; stream with the same batch sizes proven in `RestoreDataService` (books 100, chapters 200, bodies 50).
- **Flavors:** feature is flavor-independent (unlike `firebase_sync`).
- **Build:** new deps — Ktor server CIO (or NanoHTTPD), ZXing core, ML Kit barcode (or ZXing embedded) — add to `gradle/libs.versions.toml`.

## Testing Plan
- Unit: manifest generation, preferences allow-list round-trip, pager/batching, PIN/token verification.
- Instrumented: import into empty DB, import with existing rows (source-wins), schema-migration import.
- E2E manual matrix: emulator↔emulator (loopback), two physical devices same AP, AP isolation enabled (expect graceful failure + USB/file fallback suggestion), USB C-to-C between different OEMs (role negotiation), charge-only cable detection, large library (>1 GB), interrupt/resume.

## Open Questions
1. Should Quick Setup also transfer the local-server-sync configuration (server URL, minus tokens) so the new device can re-attach to the user's sync server?
2. Merge semantics when the target is not fresh — block, source-wins, or per-book prompt?
3. Should completed transfer offer to enable the periodic `WiFiSyncWorker` sync between the two devices going forward?
