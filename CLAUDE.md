# CLAUDE.md

Projekt-Konventionen für **Pulse** (Android-App, BLE Heart-Rate-Monitor).
Wird von Claude Code automatisch zum Session-Start gelesen. Kurz und
handlungsleitend halten — keine Marketing-Beschreibung (das ist
`README.md`).

---

## Stack

- **Kotlin** + **Jetpack Compose** + **Material 3** (kein Views/XML)
- **MVVM**, ein einziges Gradle-Modul: `:app`
- minSdk = targetSdk = compileSdk = **36** (Android 16)
- **JDK 17** (siehe `app/build.gradle.kts`)
- Compose BOM `2026.02.01`
- `accompanist-permissions` für Runtime-Permissions
- Logging via `android.util.Log` (kein Timber)
- Persistenz via **SharedPreferences** (nur `UserProfile`, ein paar Werte —
  DataStore wäre Overkill)
- Coroutines/Flows: UI-State als `StateFlow`
- Keine Tests (Stand jetzt). Keine DI-Library (kein Hilt/Koin) — `viewModel()`
  reicht.

## Build

```bash
./gradlew assembleDebug    # Debug-APK
./gradlew installDebug     # auf angeschlossenes Gerät installieren
```

BLE funktioniert **nicht im Emulator** — immer auf echtem Gerät testen.

---

## Paketstruktur

Alles unter `com.github.reygnn.pulse`:

```
ble/
  HeartRateManager.kt    BLE-Scan, GATT-Connect, HR-/Battery-Notifications,
                         Auto-Reconnect (2s Delay)
  HeartRateService.kt    Foreground Service (typ: connectedDevice),
                         hält BLE-Verbindung im Hintergrund, persistente
                         Notification mit aktuellem BPM

viewmodel/
  HeartRateViewModel.kt  AndroidViewModel, bindet sich an HeartRateService,
                         mappt HrState -> UiState via flatMapLatest, hält
                         Live-History (max 120 Werte) und Workout-State

workout/
  WorkoutSession.kt      HrZone-Enum (Ruhe/Aufwärmen/.../Maximum),
                         HrSample, WorkoutSession (computed avg/min/peak,
                         Zonenverteilung)
  UserProfile.kt         Tanaka-MaxHR (208 - 0.7*age), Keytel-Kalorien,
                         UserProfileStore (SharedPreferences)

ui/
  theme/Theme.kt         HeartRed-Palette, Dark/Light
  screens/HeartRateScreen.kt   4 State-Views (Disconnected/Scanning/
                               Connecting/Connected), pulsierendes Herz,
                               Mini-Chart
  screens/WorkoutScreen.kt     WorkoutControls, WorkoutPanel mit ZoneBar,
                               WorkoutSummaryDialog mit Verlaufs-Chart
  screens/SettingsScreen.kt    Profil-Dialog (Alter, Gewicht, Geschlecht)

MainActivity.kt          Berechtigungs-Gate (Accompanist), KEEP_SCREEN_ON +
                         Display-Dimm-Logik im "Display dimmen"-Modus
```

Datenfluss: BLE-Notification → `HeartRateManager._state` → `Service`-
Beobachter (Notification-Update) und gleichzeitig `ViewModel` (über
`flatMapLatest` auf `_service.value?.hrManager?.state`) → `UiState` →
Compose.

---

## BLE-spezifisches Wissen

- **Heart Rate Service UUID**: `0x180D`, **Measurement Char**: `0x2A37`,
  **CCCD**: `0x2902`, **Battery Service**: `0x180F`, **Battery Level**:
  `0x2A19`. Konstanten in `HeartRateManager.companion`.
- **HR-Byte-Format**: Flag-Bit 0 entscheidet, ob HR als UINT8 (`data[1]`)
  oder UINT16 little-endian (`data[1] | data[2] << 8`) kommt — siehe
  `parseHeartRate`. Nicht "vereinfachen".
- **Scan-Filter** auf den HR-Service-UUID gesetzt: nur passende Geräte
  tauchen in der Liste auf. Bewusst — keine Geräte-Namen-Filter.
- **`connectGatt(autoConnect=true)`** kümmert sich um System-Reconnects,
  zusätzlich macht der Manager nach `STATE_DISCONNECTED` einen manuellen
  Reconnect mit 2 s Delay (`RECONNECT_DELAY_MS`). Die beiden Mechanismen
  überlappen sich — bekannt, derzeit gewollt.
- **Foreground Service ist Pflicht.** Sobald `connectToDevice` läuft,
  startet das ViewModel den Service als Foreground (Type
  `connectedDevice`). Ohne FG-Status kappt Android die BLE-Verbindung,
  wenn das Display ausgeht.
- **GATT-Operationen sind seriell.** `discoverServices` → `setCharacte-
  risticNotification` → `writeDescriptor` → erst nach
  `onDescriptorWrite` darf die Battery-Char gelesen werden. Reihenfolge
  in `onServicesDiscovered`/`onDescriptorWrite` nicht durcheinander-
  bringen.

## Permissions

API 31+ (siehe Manifest):
`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`,
`POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_CONNECTED_DEVICE`. Alle vier Runtime-Permissions
werden in `MainActivity` über `rememberMultiplePermissionsState` als
Block angefragt — `minSdk = 36` erspart Versions-Branches.

---

## Konventionen

1. **UI-Strings sind aktuell hardcodiert auf Deutsch** (in Kotlin, nicht
   in `strings.xml`). Wenn neue Strings dazukommen, pragmatisch genauso
   handhaben — ein Lokalisierungs-Sweep ist nicht geplant.
2. **Kommentare/Logs**: Sprache wie im Umfeld — bestehende Mischung
   nicht aktiv vereinheitlichen. Tag-Konstante pro Klasse
   (`private const val TAG = "..."`).
3. **`@SuppressLint("MissingPermission")`** ist im BLE-Code in Ordnung
   — die Permissions sind durch das Gate in `MainActivity` garantiert,
   wenn der Code überhaupt läuft.
4. **`HeartRateManager._state`** ist Single Source of Truth für alles
   BLE-bezogene. Niemals UI-State direkt aus dem GATT-Callback bauen.
5. **History/Sample-Buffer haben harte Caps** (`_history` max 120,
   `workoutSamples` unbegrenzt aber im Longterm-Mode auf 1 Sample / 5 s
   gedrosselt). Caps nicht stillschweigend hochziehen.
6. **Keine neuen Dependencies ohne Anlass.** Hilt, Timber, DataStore,
   Room etc. wurden bewusst weggelassen — die App ist klein genug, dass
   sie mit `viewModel()` + SharedPreferences + `Log` auskommt.

---

## Was diese Datei NICHT ist

- Keine Beschreibung der App (`README.md`).
- Keine TODO-Liste.
- Kein Persistenz-Layer für Detail-Notizen, die in Commits gehören.

Aktualisieren, wenn sich eine architektonische Entscheidung ändert oder
eine teuer gelernte Lektion festgehalten gehört. Nicht aufblähen mit
Dingen, die man im Code sieht.
