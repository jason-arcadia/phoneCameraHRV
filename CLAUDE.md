# phoneCameraHRV — Project Reference

Kotlin Multiplatform (KMP) + Compose Multiplatform app that measures Heart-Rate
Variability (HRV) using the phone's rear camera and torch (photoplethysmography, PPG).

---

## Architecture

```
composeApp/
├── commonMain/          Shared Kotlin: UI, signal processing, ViewModel, scoring
├── androidMain/         Android: CameraX, Google Sign-In, Supabase upload, local storage
└── iosMain/             iOS: AVFoundation camera (complete), Google Sign-In (TODO)
```

### Key layers

| Layer | File(s) | Responsibility |
|-------|---------|---------------|
| Signal processing | `PPGprocessor.kt` | Bandpass filter, valley detection, RMSSD |
| State management | `HRVViewModel.kt` | Flows, countdown timer, saveEvent |
| Camera abstraction | `CameraController.kt` (expect) | Cross-platform camera API |
| Android camera | `CameraController.android.kt`, `CameraManager.kt` | CameraX YUV frame extraction |
| iOS camera | `CameraController.ios.kt` | AVFoundation biplanar YCbCr frame extraction |
| Scoring | `HRVScores.kt` | Stress / Energy / Recovery vs personal baseline |
| Local storage | `MeasurementStore.kt` (Android only) | JSON in SharedPreferences, up to 50 records |
| Cloud upload | `MeasurementApiClient.kt` (Android only) | Supabase REST + Storage |
| Auth | `GoogleSignInHelper.kt` (Android only) | Google Sign-In v2 |
| UI screens | `App.kt`, `HRVScreen.kt`, `ResultsScreen.kt`, `SignInScreen.kt`, `SettingsScreen.kt` | Compose UI |

### PPG signal pipeline

```
Camera frame (YUV_420_888 / biplanar YCbCr)
  → centre 50×50 pixel region
  → red = clip(Y + 1.402·(Cr−128), 0, 255)   [PPG signal]
  → yAvg = Y luma average                      [finger detection]
  → PPGSignalFilter: rolling-mean normalisation + 2nd-order Butterworth BPF 0.7–3.5 Hz
  → detectPeak(): valley detection with adaptive threshold (60% of rolling min over 3 s)
    refractory 500 ms, local-minimum confirmation ±8 samples
  → RR intervals (300–1500 ms gate)
  → HRVMetrics: RMSSD, HR, lastRR, meanRR, isStable, isFingerDetected
```

### Measurement flow

1. User taps **開始測量** → camera permission requested → `CameraController.start()`.
2. `HRVViewModel.onFrame()` feeds every frame to `PPGProcessor`.
3. When signal is stable (`variance < 2e-3` over 1 s), a 70-second countdown begins.
4. At 70 s, `saveEvent` emits RR intervals from the 10–70 s stable window.
5. Platform-specific code (MainActivity / MainViewController) handles:
   - Save RR file to device storage (Android) / TODO (iOS)
   - Compute HRV scores vs personal baseline
   - Upload to Supabase (Android) / TODO (iOS)
   - Show `ResultsScreen`

---

## Android — Fully Implemented

- [x] Camera (CameraX, rear camera + torch)
- [x] Google Sign-In (`play-services-auth:21.2.0`, `requestEmail()` only)
- [x] Supabase REST upload (`/rest/v1/measurements`)
- [x] Supabase Storage upload (bucket `rr-files`, public URL stored in `rr_file_url`)
- [x] Local measurement history (`MeasurementStore`, SharedPreferences JSON)
- [x] Personal baseline scoring after 5+ measurements
- [x] Pink adaptive launcher icon
- [x] Keep-screen-on during measurement
- [x] Success toast "測量結果成功上傳雲端" after upload

### Android debug SHA-1 (required in Google Cloud Console)
```
32:70:00:52:36:BB:3A:D9:32:9A:19:CA:A0:82:55:41:CB:AA:F2:32
```
Location in Google Cloud Console:
**APIs & Services → Credentials → OAuth 2.0 Client IDs → Android client**
(project: phoneCameraHRV, client ID: 755883204511-isna0en1ppfk3ju0d7j1sfo3gtp4ahk2.apps.googleusercontent.com)

---

## iOS — What Needs Debugging on Mac / Xcode

### Already written (Kotlin/Native, in `iosMain/`)
- `CameraController.ios.kt` — `AVCaptureSession`, `AVCaptureVideoDataOutput`,
  frame delegate, centre-50×50 YCbCr extraction, torch toggle.
- `CameraPreviewView` (in same file) — `UIKitView` + `AVCaptureVideoPreviewLayer`.
- `MainViewController.kt` — wires `HRVViewModel` + `CameraController` + `App()`.

### Checklist before first iOS build

1. **`Info.plist` — camera permission string**
   File: `iosApp/iosApp/Info.plist`
   ```xml
   <key>NSCameraUsageDescription</key>
   <string>Camera is used to measure your heart-rate variability via PPG.</string>
   ```

2. **Kotlin/Native cinterop imports** — verify the following import paths resolve
   in the Xcode-generated KMP framework:
   - `platform.AVFoundation.*`
   - `platform.CoreMedia.*`
   - `platform.CoreVideo.*`
   - `platform.darwin.DISPATCH_QUEUE_SERIAL` (may need to be `null` cast to
     `dispatch_queue_attr_t?` depending on K/N version)
   - `platform.QuartzCore.CATransaction`

3. **`kCVPixelFormatType_420YpCbCr8BiPlanarFullRange` type**
   Declared as `UInt` in K/N CoreVideo bindings. Verify `NSNumber(unsignedInt = ...)` compiles;
   if not, try `NSNumber(int = 875704422)` (the numeric value).

4. **`AVCaptureDeviceInput.deviceInputWithDevice(_:error:)`**
   In K/N this may be a throwing init. If the `runCatching { ... as? ... }` cast fails,
   try `AVCaptureDeviceInput(device = backCamera)` (the designated initialiser mapping).

5. **`startRunning()` threading**
   `session.startRunning()` should NOT be on the main thread. In
   `MainViewController.kt` / `CameraController.ios.kt`, it is dispatched to
   `dispatch_get_main_queue()` as a workaround for Compose re-entry.
   On real hardware, change to a background queue:
   ```kotlin
   val startQueue = dispatch_queue_create("ppg.start", DISPATCH_QUEUE_SERIAL)
   dispatch_async(startQueue) { session.startRunning() }
   ```

6. **Google Sign-In on iOS** — not implemented. Options:
   - Credential Manager / `GoogleSignIn-iOS` SDK via SPM in Xcode
   - Or: use Sign-in-with-Apple as alternative
   Currently `isSignedIn = true` is hard-coded in `MainViewController.kt` so the
   measurement screen is reachable without auth.

7. **Supabase upload on iOS** — `MeasurementApiClient` is in `androidMain`.
   To enable upload on iOS, move to `commonMain` (it only uses `kotlinx.coroutines`
   and `java.net.HttpURLConnection` — replace the latter with `NSURLSession` or
   move to `ktor-client`).

8. **`MeasurementStore` on iOS** — currently no-op; baseline is always `null`
   (first measurement uses self as reference). Implement with `NSUserDefaults` or
   SQLite if persistent baseline is desired.

9. **`AVCaptureTorchModeOn` / `AVCaptureTorchModeOff` constants**
   Verify they are `Long` (not `Int`) in the K/N bindings and match the enum type
   expected by `device?.torchMode`.

---

## Supabase Project

**URL:** `https://yczqytmdhxkxsyxlgiql.supabase.co`

**Anon key:** stored in `MeasurementApiClient.kt` (`SUPABASE_ANON_KEY` constant).

### Table: `measurements`

| Column | Type | Notes |
|--------|------|-------|
| `id` | uuid / serial | auto |
| `email` | text | Google account email |
| `timestamp` | text | `yyyy-MM-dd HH:mm:ss` |
| `rmssd` | float8 | ms |
| `heart_rate` | float8 | bpm |
| `peak_to_peak` | float8 | ms (last RR interval) |
| `mean_rr` | float8 | ms (session mean) |
| `stress_score` | int4 | 0–100 |
| `energy_score` | int4 | 0–100 |
| `recovery_score` | int4 | 0–100 |
| `rr_file_url` | text | public Supabase Storage URL |

### Storage bucket: `rr-files`

- Public bucket
- Files: `{safeEmail}_{safeTimestamp}.txt` (one RR interval per line, ms)
- Required RLS policy (run in SQL Editor if uploads get 403):
  ```sql
  DROP POLICY IF EXISTS "rr-files anon insert" ON storage.objects;
  CREATE POLICY "rr-files anon insert"
    ON storage.objects FOR INSERT TO anon
    WITH CHECK (true);

  DROP POLICY IF EXISTS "rr-files anon select" ON storage.objects;
  CREATE POLICY "rr-files anon select"
    ON storage.objects FOR SELECT TO anon
    USING (bucket_id = 'rr-files');
  ```

---

## Google OAuth Client

**Client ID:** `755883204511-isna0en1ppfk3ju0d7j1sfo3gtp4ahk2.apps.googleusercontent.com`

**Location in source:** `GoogleSignInHelper.kt`
```kotlin
private const val CLIENT_ID =
    "755883204511-isna0en1ppfk3ju0d7j1sfo3gtp4ahk2.apps.googleusercontent.com"
```

**Google Cloud Console path:**
APIs & Services → Credentials → OAuth 2.0 Client IDs

**Scopes used:** email only (`requestEmail()`).
No `requestIdToken()` — avoids the SHA-1 mismatch error code 10 for debug builds.

---

## Build Commands

```bash
# Android debug APK
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" \
  ./gradlew :composeApp:assembleDebug

# Install on connected device
/Users/<you>/Library/Android/sdk/platform-tools/adb install -r \
  composeApp/build/outputs/apk/debug/composeApp-debug.apk

# iOS framework (requires Mac + Xcode)
./gradlew :composeApp:linkDebugFrameworkIosArm64
# Then open iosApp/iosApp.xcodeproj in Xcode and run on device/simulator
```
