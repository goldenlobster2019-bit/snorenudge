# SnoreNudge — CLAUDE.md

Standalone Wear OS app for Sorin's **Samsung Galaxy Watch4 Classic**. The watch listens with its own microphone during the night, detects snoring on-device, and vibrates to nudge the wearer to change position. No phone involved (the watch is paired with an iPhone through Herold, which this app does not touch or depend on).

This file is the single source of truth for the project. Keep it updated after every work session: Status, Decisions, Known issues.

---

## 1. Goals and non-goals

**Goals**
- Detect the wearer's snoring on the watch, fully offline.
- Vibrate the watch (not a notification) with a configurable pattern when snoring persists.
- Run reliably all night with the screen off, including in Samsung Bedtime mode / DND.
- Install once via adb, no expiry, no Play Store, no Apple involvement.
- Reasonable battery use: target <= 30% drain over 8 hours on the Watch4 Classic.

**Non-goals (v1)**
- No phone companion app, no iPhone integration, no Herold changes.
- No cloud, no accounts, no analytics. The app has **no INTERNET permission**.
- No audio recordings saved by default (privacy). Optional debug clips only in debug builds.
- No Play Store publishing.

---

## 2. Target device (verified)

| Item | Value |
|---|---|
| Device | Samsung Galaxy Watch4 Classic (SM-R88x family) |
| OS | One UI 8 Watch, Wear OS 6 (Android 16, API 36) — rolled out to Watch4 Classic from Dec 2025 |
| Originally shipped | Wear OS 3 (API 30) |
| SoC / RAM | Exynos W920, 1.5 GB RAM |
| Battery | 247 mAh (42 mm) / 361 mAh (46 mm): battery is the main constraint |
| Display | Round, 396x396 (42 mm) or 450x450 (46 mm) |
| Mic | Yes. Vibration motor: yes |

**SDK levels**
- `minSdk = 30` (covers Wear OS 3 in case the watch is not updated)
- `targetSdk = 36`, `compileSdk = 36`
- Code must handle API 30-36 behavior differences (foreground service types, permissions, vibration APIs). Gate with `Build.VERSION.SDK_INT` where needed.

First step of implementation: confirm the actual OS on the watch with `adb shell getprop ro.build.version.sdk` and record it in Status below.

---

## 3. Tech stack

- **Language:** Kotlin (latest stable 2.x), coroutines + Flow.
- **UI:** Compose for Wear OS (`androidx.wear.compose:compose-material3`), round-screen first.
- **Audio capture:** `android.media.AudioRecord`.
- **ML:** Google **YAMNet** (AudioSet, 521 classes, includes "Snoring") as `.tflite`, run with **LiteRT** (`com.google.ai.edge.litert:litert`, the successor of TensorFlow Lite runtime).
- **Background:** Foreground Service, type `microphone`, plus `PARTIAL_WAKE_LOCK`, plus Wear **Ongoing Activity** (`androidx.wear:wear-ongoing`).
- **Persistence:** Jetpack DataStore (settings), Room (night sessions + events).
- **Build:** Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`). Use the latest stable versions at implementation time; do not use alpha/beta libraries unless there is no stable alternative (note it in Decisions).
- **Dev machine:** macOS, Android Studio (latest stable).

---

## 4. Detection pipeline

```
AudioRecord 16 kHz mono PCM16
   -> ring buffer
   -> energy gate (RMS dBFS)            [cheap, always on]
   -> YAMNet frame 15600 samples (0.975 s), hop 0.5 s   [only when gate open]
   -> "Snoring" score per frame
   -> event builder (merge positive frames into snore events)
   -> episode logic (N events within window)
   -> vibration controller (pattern, cooldown, escalation)
   -> Room log
```

### 4.1 Audio capture
- Sample rate 16000 Hz, `CHANNEL_IN_MONO`, `ENCODING_PCM_16BIT`. If 16 kHz is not supported by `AudioRecord.getMinBufferSize`, capture at 48000 Hz and downsample by 3 with a simple low-pass FIR (document which path the device uses).
- Audio source order of preference:
  1. `MediaRecorder.AudioSource.UNPROCESSED` if `AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"`
  2. `MediaRecorder.AudioSource.VOICE_RECOGNITION` (no AGC, minimal processing)
  3. `MediaRecorder.AudioSource.MIC`
  Reason: noise suppression on the default MIC path can remove low-frequency snoring.
- Read in a dedicated thread/coroutine on `Dispatchers.IO`, 100 ms chunks, into a 2 s ring buffer of `FloatArray` normalized to [-1, 1].

### 4.2 Energy gate (battery saver)
- Compute RMS dBFS per 100 ms chunk.
- Gate opens when level exceeds `noiseFloor + gateMarginDb` (default margin 8 dB) and stays open for 3 s after the last loud chunk.
- `noiseFloor` = rolling 20th percentile of dBFS over the last 5 minutes (adaptive to fan/AC noise).
- When the gate is closed, YAMNet does not run. Log gate open ratio per night for tuning.

### 4.3 YAMNet inference
- Model: `yamnet.tflite` (classification variant, outputs `[1, 521]` scores) from Kaggle Models `google/yamnet/tfLite/classification-tflite`. Place at `app/src/main/assets/yamnet.tflite`. Also ship `yamnet_class_map.csv` (from the `tensorflow/models` repo, `research/audioset/yamnet`) in assets.
- Resolve the snoring index **at runtime by display name `"Snoring"`** from the class map. Do not hardcode the index (expected 38, assert and log if different).
- Input: `FloatArray(15600)` waveform at 16 kHz. Hop 0.5 s (8000 samples).
- Interpreter: 2 threads, XNNPACK default delegate. No GPU/NNAPI (unreliable on this SoC, more power).
- Load the model once per session, release in `onDestroy`.
- Prevent asset compression: `androidResources { noCompress += "tflite" }`.
- Also read scores for "Breathing" and "Speech" for diagnostics (logged, not used for triggering in v1).

### 4.4 Event and episode logic (all values configurable, defaults below)
- **Positive frame:** snoring score >= `scoreThreshold` (default 0.30) AND frame RMS >= `minSnoreDb` above noise floor (default 10 dB). The loudness condition favors the wearer's own snoring over a partner's (closer to the wrist/face).
- **Snore event:** one or more consecutive positive frames. Events closer than 1.5 s are merged. Snoring is periodic with breathing (~3-6 s), so one breath cycle = one event.
- **Episode:** `eventsToTrigger` events (default 3) within `episodeWindowSec` (default 60 s).
- On episode: trigger vibration, then **cooldown** `cooldownSec` (default 90 s) during which no vibration fires (events still logged).
- **Escalation:** if a new episode starts within `escalationWindowSec` (default 5 min) after the previous vibration, use the next stronger pattern level (max level 3). Reset to level 1 after 10 min without episodes.
- **Start delay:** no vibration during the first `startDelayMin` minutes after starting (default 20) so the wearer can fall asleep. Detection and logging still run.
- **Max nudges per night:** `maxVibrationsPerNight` (default 40), then log-only.

### 4.5 Vibration
- Use `Vibrator` / `VibratorManager` directly from the service, NOT a notification, so Bedtime mode / DND does not suppress it.
- On API 33+: `vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))`. On API 30-32: `AudioAttributes.USAGE_ALARM` overload.
- Patterns (`VibrationEffect.createWaveform` with amplitudes when `hasAmplitudeControl()`):
  - Level 1 (gentle): 2 x 250 ms, amplitude ~120
  - Level 2: 3 x 350 ms, amplitude ~180
  - Level 3: 4 x 500 ms, amplitude 255
- User-selectable base intensity (Low / Medium / High) shifts amplitudes.
- **Must be tested with Bedtime mode ON and DND ON on the real watch.** If Samsung still suppresses alarm-usage vibrations, record it in Known issues and evaluate a full-screen-intent alarm as fallback.

---

## 5. Running all night (Wear OS / Samsung constraints)

- `SnoreMonitorService` = foreground service with `android:foregroundServiceType="microphone"`.
- Permissions in manifest:
  - `RECORD_AUDIO` (runtime)
  - `FOREGROUND_SERVICE`
  - `FOREGROUND_SERVICE_MICROPHONE` (API 34+)
  - `POST_NOTIFICATIONS` (runtime, API 33+, needed for the FGS notification)
  - `WAKE_LOCK`
  - `VIBRATE`
  - `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (ask once, explain why)
  - **No** `INTERNET`, no storage permissions.
- Android 14+ "while-in-use" rule: a microphone FGS can only be started while the app is visible. Start the service **only from the Start button in the activity**, never from a broadcast, alarm or boot receiver. Scheduled auto-start is therefore not possible in v1 (record in Decisions).
- Acquire `PARTIAL_WAKE_LOCK` with a tag `SnoreNudge:monitor` and a timeout of `maxSessionHours` (default 10 h); release on stop.
- Show an **Ongoing Activity** (icon on watch face + recents) so the user can return to the app and so the system treats the session as user-visible.
- Auto-stop after `maxSessionHours` or at an optional "stop at" time (default 07:30).
- On low battery (<= 10%), stop the session gracefully, vibrate once briefly, and log it.
- Handle audio focus/mic conflicts: if `AudioRecord` read returns errors (e.g. another app takes the mic, incoming call on the watch), retry with backoff (1 s, 5 s, 30 s) and log.
- Samsung battery management: the setup screen must link the user to battery settings and say to set the app to unrestricted. Verify the service survives 8 h with the screen off.

---

## 6. UI (Compose for Wear OS, round screen)

Keep it minimal and readable in the dark (dark theme only, no bright whites, large touch targets, no emojis anywhere).

1. **Home**
   - Big Start / Stop button.
   - When running: elapsed time, snore events count, nudges count, start-delay countdown.
2. **Live test** (calibration)
   - Live level meter (dBFS), noise floor, current snoring score (0-1), gate state.
   - "Test vibration" buttons for levels 1-3.
   - Used to tune thresholds by playing snoring audio from another device or imitating snoring.
3. **Settings** (rotating bezel scrolls lists on the Watch4 Classic, support `rotary` input via `Modifier.rotaryScrollable` / ScalingLazyColumn defaults)
   - Sensitivity (maps to `scoreThreshold`: Low 0.45 / Medium 0.30 / High 0.20, plus advanced numeric value)
   - Events to trigger (2-5), cooldown (30-300 s), start delay (0-60 min), stop-at time, vibration intensity, max nudges per night
4. **Last nights**
   - List of sessions: date, duration, snore events, nudges, minutes with snoring.
   - Session detail: simple bar timeline per 10 min bucket.
5. **Setup / permissions** (first run)
   - Mic permission, notifications permission, battery optimization exemption, short explanation of each.

Tile (optional, v1.1): Start/Stop tile. Note that the tile can only open the activity, which then starts the service (while-in-use rule).

---

## 7. Data model

**DataStore (settings):** all values from 4.4 and 4.5 plus `maxSessionHours`, `stopAtTime`.

**Room**
- `NightSession(id, startedAt, endedAt, endReason, deviceSdk, settingsSnapshotJson, gateOpenRatio, avgNoiseFloorDb)`
- `SnoreEvent(id, sessionId, timestamp, durationMs, maxScore, peakDb)`
- `Nudge(id, sessionId, timestamp, level)`
- Retention: keep last 60 nights, prune on session start.

No audio is stored. Debug builds only: optional flag to save the 5 s preceding each nudge as WAV in app-private storage, off by default, auto-deleted after 7 days.

---

## 8. Project structure

```
snorenudge/
  CLAUDE.md
  settings.gradle.kts
  build.gradle.kts
  gradle/libs.versions.toml
  app/
    build.gradle.kts
    src/main/
      AndroidManifest.xml
      assets/yamnet.tflite
      assets/yamnet_class_map.csv
      java/ro/goldenlobster/snorenudge/
        MainActivity.kt
        ui/            (HomeScreen, LiveTestScreen, SettingsScreen, NightsScreen, SetupScreen, theme/)
        service/       (SnoreMonitorService, WakeLockHolder, OngoingActivityHelper)
        audio/         (AudioCapture, RingBuffer, EnergyGate, Resampler)
        ml/            (YamnetClassifier, ClassMap)
        detect/        (EventBuilder, EpisodeDetector)   <- pure Kotlin, no Android deps
        haptics/       (VibrationController, Patterns)
        data/          (SettingsRepository, AppDatabase, DAOs, entities)
    src/test/          (unit tests for detect/, audio/EnergyGate, Resampler)
```

- Package / applicationId: `ro.goldenlobster.snorenudge`
- Manifest must declare `<uses-feature android:name="android.hardware.type.watch" />` and `<meta-data android:name="com.google.android.wearable.standalone" android:value="true" />`.

---

## 9. Conventions

- Detection logic (`detect/`) is pure Kotlin with injected clock and inputs, fully unit-tested with synthetic sequences (periodic events, isolated noise, partner-level quiet snoring, cooldown, escalation, start delay).
- No blocking work on the main thread. Audio read loop and inference run off main; UI observes `StateFlow` from the service via a bound connection or a shared repository.
- All thresholds live in one `DetectionConfig` data class, built from settings.
- Logging: `Log` with tag `SnoreNudge`, verbose logs stripped in release (R8 rules).
- No secrets in the repo. Release keystore lives outside the repo, path and passwords in `~/.gradle/gradle.properties`.
- R8/minify enabled for release; keep rules for LiteRT if needed.
- UI copy in English (short, watch-sized). No emojis.

---

## 10. Build and install (macOS, no Play Store, permanent)

1. Install platform tools: `brew install --cask android-platform-tools` (or use Android Studio's SDK).
2. On the watch: Settings > About watch > Software information > tap **Software version** repeatedly until Developer options are enabled.
3. Settings > Developer options: enable **ADB debugging** and **Wireless debugging**. Watch and Mac on the same Wi-Fi.
4. Wireless debugging > **Pair new device** shows IP:port and code:
   - `adb pair <ip>:<pair-port> <code>`
   - `adb connect <ip>:<connect-port>` (the port shown on the Wireless debugging main screen)
   - `adb devices` must list the watch.
5. Create a release keystore once (keep it backed up, never commit):
   `keytool -genkeypair -v -keystore ~/keys/snorenudge.jks -alias snorenudge -keyalg RSA -keysize 4096 -validity 36500`
6. Build and install: `./gradlew :app:installRelease`, or `adb install -r app/build/outputs/apk/release/app-release.apk`.
7. Updates: same keystore, `adb install -r` keeps data. Sideloaded apps do not expire.
8. Turn Wireless debugging off after installing (battery and security).

---

## 11. Test plan (on the real Watch4 Classic)

- [ ] Confirm SDK level and supported audio sources (log at startup).
- [ ] Live test: snoring audio played from a phone at 30 cm triggers scores > threshold; speech/TV/fan do not.
- [ ] Vibration fires with screen off.
- [ ] Vibration fires with Bedtime mode ON.
- [ ] Vibration fires with DND ON.
- [ ] Service survives 8 h with screen off, wrist on, battery drain measured and recorded.
- [ ] Mic interruption (call on watch, voice assistant) recovers.
- [ ] Auto-stop at stop-at time and at max session length.
- [ ] Low battery stop at 10%.
- [ ] First night real use: review events/nudges and tune defaults.

---

## 12. Implementation phases

1. **Skeleton:** project setup, manifest, permissions/setup screen, Home with Start/Stop, FGS with mic + wake lock + ongoing activity. Verify on device the service runs 1 h screen off.
2. **Audio + gate + Live test screen:** level meter and noise floor on device.
3. **YAMNet:** classifier, live snoring score on Live test screen.
4. **Detection + vibration:** EventBuilder, EpisodeDetector (with unit tests), VibrationController, Bedtime/DND tests.
5. **Persistence + Last nights screens.**
6. **Settings screen + rotary support.**
7. **Overnight test, battery measurement, tuning of defaults.**

Workflow: plan with the main model, implement each phase with a cheaper model (subagents), review and test on device between phases.

---

## Status

- [x] Phase 1 code done (2026-11-02): skeleton, setup/permissions screen, Home Start/Stop, mic FGS + wake lock + ongoing activity, live dBFS meter. Builds, unit tests and lint pass. NOT yet tested on the watch (1 h screen-off check pending).
- [x] Phases 2+3 code done (2026-11-02, combined): ring buffer, 48k->16k resampler, adaptive energy gate, YAMNet via LiteRT 2.2.0 (inference on its own thread, every 0.5 s when gate open or Live test visible), Live test screen, test vibrations L1-L3 (alarm usage). 26 unit tests pass. NOT yet tested on the watch.
- [x] Phase 4 code done (2026-11-02): FrameClassifier (score>=0.30, snore >= competitor+0.05, peak >= floor+10 dB), EventBuilder, EpisodeDetector (3 events/60 s, cooldown 90 s, escalation L1-L3, start delay 20 min, max 40), DetectionPipeline, vibration from service, auto-stop 07:30 / 10 h / battery <=10% (skipped while charging), night CSV log in filesDir/nights, Test mode (no delay, 30 s cooldown). 49 unit tests pass.
- [x] Phases 5+6 + tile done (2026-11-02): Room (NightSession/SnoreEvent/Nudge, schema in app/schemas/, retention 60 days, unfinished rows marked 'interrupted' at next start), History (last 30 nights summary + bar chart + list), Night detail (10-min timeline, nudge markers by level, start-delay shading, stats, nudge list, busiest buckets), "Last night" shortcut, Settings via DataStore (sensitivity Low/Med/High = 0.45/0.30/0.20, cooldown, start delay, events to trigger, stop time, vibration strength 0.6x/1.0x/1.4x, max nudges; apply on next start), Start/Stop tile. 67 unit tests pass. Installed on the watch 2026-11-02 16:43, UI not yet reviewed on device.
- Tile: launch actions cannot set an intent action, so the tile passes extra tile_action=ro.goldenlobster.snorenudge.action.START_FROM_TILE / STOP_FROM_TILE; MainActivity handles it in onResume/onNewIntent (start must happen from the visible activity, Android 14 mic rule).
- Test-mode sessions are listed with a TEST marker but excluded from history averages and the 30-day chart.
- Libraries added: Room 2.8.3 + KSP 2.3.2, DataStore Preferences 1.1.7, wear tiles 1.5.0, protolayout 1.3.0, concurrent-futures 1.2.0.
- [x] Log export (2026-11-03): LogExporter mirrors files/nights/*.csv + snorenudge.db to Android/data/ro.goldenlobster.snorenudge/files/export/ on app open and 3 s after session stop, so `adb pull` works without run-as (rea pull_adb_file). Summary rows now include batt=% and charging; start row includes batt.
- First real night 2026-11-03 00:34-08:00 (ran in TEST MODE by mistake: no delay, 30 s cooldown): 619 snore events, 40 nudges (max reached 04:39), 138 suppressed. Positive frames: median snore score 0.96, median 17 dB above floor. Noise floor median -67.6 dBFS with child's white noise machine in the room: no false labels (top labels only Snoring/Breathing/Silence). Heavy snoring 04:00-06:00. 35/40 nudges followed by new snoring within ~1 min (most within 8-9 s): nudges detected fine but rarely stop the snoring. 287 frames rejected as 'quiet' (median 9 dB above floor, snore 0.59). avg inference 42 ms. Session survived to stop time 08:00 (battery % not logged in that build).
- [x] Tuning + UI redesign (2026-11-03, after night 1): cooldown default 30 s (user wants re-nudge 30 s after snoring resumes; options 15-180 s), max nudges default 150 (40/80/150/Unlimited), nudges start at L2 (2->3->3), L3 = 5x400 ms pulses (~2.8 s), test mode auto-off after a session + amber warning on Home, inference hop 1.0 s (mergeGapMs 2200) to save battery. Night 1 battery: 100% -> ~30% in 7.5 h (~9%/h, test mode, 40 nudges mostly L3). New premium dark UI with material-icons-extended 1.7.8 (Icons.Rounded), ring-progress running Home, restyled history/detail/settings/live test/setup/tile, new crescent launcher icon. Debug build is now minified too (icons-extended made unminified APK ~90 MB). 79 unit tests.
- Home (2026-11-03): icon+label buttons centered (CenteredTonalButton, Home only), stronger edge scaling (edgeScale 0.6, edgeAlpha 0.3), Start button is the initially centered item and autoCentering keeps it centered after scrolling back up (title index 0, Start index 1, optional rows after Start).
- Screenshot rendering (2026-11-03): Roborazzi 1.59.0 + Robolectric 4.16 (sdk 35, NATIVE graphics, qualifiers w225dp-h225dp-round-xhdpi). Screens split into stateful wrapper + stateless *Content composables; sample data in src/test/.../screenshots/SampleData.kt. Regenerate: tools/render-screens.sh -> docs/design/current/*.png (excluded from normal testDebugUnitTest). Robolectric android-all jar may need a retry on Maven Central 429.
- Design pass (2026-11-03): docs/design/critique-2026-11-03.md; before renders in docs/design/before/. Fixes: running counters inside the ring, tonal Start button (night glare), Setup labels, timeline legend, busiest periods, nudge level pills, History units, shared ScreenHeader + StatTile + rememberHeaderListState.
- Known: tile_preview.png still shows the old tile design.
- Next: calibration from the first real night (pull CSV + Room stats, tune scoreThreshold / dominanceMargin / minSnoreDbAboveFloor).
- Measured on device: YAMNet ~48-56 ms per inference; real snoring top label Snoring, peak ~0.65; speech up to ~0.70 Speech; baby babbling labeled separately. Test vibration works in Bedtime mode (app in foreground).
- Pull a night log (debug build): adb shell run-as ro.goldenlobster.snorenudge ls files/nights ; adb shell run-as ro.goldenlobster.snorenudge cat files/nights/<file>.csv
- Versions: AGP 8.13.2, Gradle 8.14.3, Kotlin 2.2.21, wear-compose material3 1.6.2, wear-ongoing 1.1.0 (see gradle/libs.versions.toml)
- Device confirmed (2026-11-02): SM-R895F (Watch4 Classic 46 mm LTE, 361 mAh), Android 14 / SDK 34 (Wear OS 5, NOT One UI 8 yet), security patch 2025-08-01, ABI armeabi-v7a only (32-bit userspace). adb over Wi-Fi via mDNS, Mac already paired. Phase 2+3 debug APK installed and launches.
- Audio source used: _to be confirmed_
- Measured battery drain (8 h): _to be measured_

## Decisions

- Standalone watch app, separate from Herold (no risk to Herold, no phone dependency).
- On-device YAMNet instead of a custom model (proven, includes "Snoring" class, ~4 MB).
- Vibration via `Vibrator` with alarm usage, not notifications (Bedtime/DND).
- Session must be started manually from the app (Android 14+ microphone FGS while-in-use rule).
- No INTERNET permission, no stored audio in release builds.
- Stayed on AGP 8.13 / compileSdk 36; newer wear-compose 1.7+, core-ktx 1.19, lifecycle 2.11 require AGP 9.1 + compileSdk 37. Upgrade later as a separate step.
- LiteRT pinned to 2.2.0: 2.3.x does not compile with Kotlin 2.2.21 (newer Kotlin metadata).
- abiFilters armeabi-v7a + arm64-v8a only (Watch4 is ARM; userspace may be 32-bit). Debug APK ~51 MB, release ~22 MB, mostly LiteRT native libs incl. GPU accelerator lib (unused; excluding it is a possible later optimization, test on device first).
- Noise floor = 20th percentile of per-second mean dBFS over 5 min (instead of raw 100 ms chunks).
- APK is built in the cloud container (Linux, Android SDK 36) or locally with ./gradlew; no Android Studio required on the Mac, only adb.
- Public repo on GitHub (MIT for app code; YAMNet model + class map Apache 2.0). README.md is the public description.

## Known issues

- R8 rules to strip verbose logs in release not added yet (section 9), do in a later phase.
- Competitor score = max of Speech, Child speech, Babbling, Baby cry, Baby laughter, Laughter, Children shouting, Hubbub, Cough (added after device test: speech/babbling must not trigger nudges).
- Lint warnings accepted: BatteryLife (intentional exemption request), WearRecents, ObsoleteSdkInt, version-update hints.
