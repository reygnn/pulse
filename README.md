# Pulse – BLE Heart Rate Monitor

An Android app built with **Jetpack Compose**, **Material 3**, and **MVVM** that
displays heart rate from a **Garmin HRM-200** (or any standard BLE heart rate
sensor) and tracks workouts with HR zones and calorie estimation.

## Architecture (MVVM)

```
┌─────────────────────────────────────────────────┐
│  UI Layer (Compose)                             │
│  ┌───────────────────────────────────────────┐  │
│  │  HeartRateScreen / WorkoutScreen          │  │
│  │  - DisconnectedView   (scan button)       │  │
│  │  - ScanningView       (device list)       │  │
│  │  - ConnectingView     (loading)           │  │
│  │  - ConnectedView      (BPM + chart)       │  │
│  │  - WorkoutPanel       (zone bar, timer)   │  │
│  │  - WorkoutSummaryDialog                   │  │
│  └───────────────────────────────────────────┘  │
│                    ▲                             │
│                    │ StateFlow<UiState>          │
│  ┌───────────────────────────────────────────┐  │
│  │  ViewModel Layer                          │  │
│  │  HeartRateViewModel.kt                    │  │
│  │  - Binds to HeartRateService              │  │
│  │  - Maps BLE state → UiState               │  │
│  │  - Holds live HR history (last 120)       │  │
│  │  - Manages workout session + samples      │  │
│  └───────────────────────────────────────────┘  │
│                    ▲                             │
│                    │ StateFlow<HrState>          │
│  ┌───────────────────────────────────────────┐  │
│  │  Data / BLE Layer                         │  │
│  │  HeartRateService (foreground service)    │  │
│  │  HeartRateManager (BLE / GATT)            │  │
│  │  - BLE scan filtered by HR service        │  │
│  │  - GATT connect + service discovery       │  │
│  │  - HR + battery notifications             │  │
│  │  - Auto-reconnect on dropout              │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

## Features

- **Targeted BLE scan** — only devices advertising the Heart Rate Service
  (UUID `0x180D`) appear in the list.
- **Live BPM display** with a heart icon that pulses in sync with the
  measured rate.
- **Live mini chart** — the last ~120 readings, drawn with a gradient fill.
- **Battery indicator** — reads the standard Battery Service (`0x180F`)
  after the HR notifications are wired up.
- **Foreground service** — keeps the BLE link alive when the screen is off
  and shows the current BPM in a persistent notification.
- **Workout mode**
  - Tracks elapsed time, average / peak HR, calories, and HR zones in
    real time.
  - Six HR zones (Rest / Warm-up / Fat Burn / Cardio / Peak / Max),
    derived from your max HR.
  - **Long-term mode** throttles sampling to 1 sample / 5 s for sessions
    that last hours.
  - **Dim screen overlay** for use during a workout — large BPM, near-black
    background, tap to wake.
- **Workout summary** — duration, min/avg/max HR, calories, zone
  distribution, full HR chart, and a one-tap copy of the result as a
  compact text line.
- **User profile** — age, weight, sex (used for Tanaka max-HR estimate and
  Keytel calorie formula). Persisted in `SharedPreferences`.
- **Material 3** — dark / light theme with a custom heart-red palette.
- **German UI** — the visible strings are in German; not currently
  localized.

## Heart-Rate Math

- **Estimated max HR** uses the Tanaka formula:
  `HRmax = 208 − 0.7 × age` (more accurate than the classic `220 − age`).
- **Calories** use Keytel et al. (2005) per minute:
  - Male: `(−55.0969 + 0.6309·HR + 0.1988·weight + 0.2017·age) / 4.184`
  - Female: `(−20.4022 + 0.4472·HR + 0.1263·weight + 0.0740·age) / 4.184`

## Build & Run

- **JDK 17**, Android Studio with Android 16 (API 36) SDK installed.
- `./gradlew assembleDebug` — build a debug APK.
- `./gradlew installDebug` — install on a connected device.
- BLE does **not** work on the emulator — always test on a real device.

## Permissions

`minSdk = targetSdk = 36`, so only the modern runtime permissions are
requested:

| Permission                | Purpose                                  |
|---------------------------|------------------------------------------|
| `BLUETOOTH_SCAN`          | Discover the heart rate sensor           |
| `BLUETOOTH_CONNECT`       | Connect to the GATT server               |
| `ACCESS_FINE_LOCATION`    | Required by Android for some BLE flows   |
| `POST_NOTIFICATIONS`      | Show the foreground-service notification |

The foreground service uses the `connectedDevice` type (and the matching
`FOREGROUND_SERVICE_CONNECTED_DEVICE` permission), which is what keeps the
BLE link alive when the screen is off.

## Technical Notes

- The Garmin HRM-200 uses the standard BLE **Heart Rate Profile**.
- Service UUID `0x180D`, measurement characteristic `0x2A37`,
  battery service `0x180F`, battery level `0x2A19`.
- The HR measurement byte format is parsed correctly: flag bit 0 selects
  between `UINT8` and `UINT16` little-endian payloads.
- GATT operations are kept strictly serial: `discoverServices` →
  enable HR notifications → write CCCD → only then read battery.
