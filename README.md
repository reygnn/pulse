# Pulse – BLE Heart Rate Monitor App

Eine Android-App mit **Jetpack Compose**, **Material 3** und **MVVM**, die den Herzschlag eines **Garmin HRM-200** (oder jedes anderen BLE Heart Rate Monitors) anzeigt.

## Architektur (MVVM)

```
┌─────────────────────────────────────────────────┐
│  UI Layer (Compose)                             │
│  ┌───────────────────────────────────────────┐  │
│  │  HeartRateScreen.kt                       │  │
│  │  - DisconnectedView  (Scan-Button)        │  │
│  │  - ScanningView      (Geräteliste)        │  │
│  │  - ConnectingView    (Ladeanimation)      │  │
│  │  - ConnectedView     (BPM + Chart)        │  │
│  └───────────────────────────────────────────┘  │
│                    ▲                             │
│                    │ StateFlow<UiState>          │
│  ┌───────────────────────────────────────────┐  │
│  │  ViewModel Layer                          │  │
│  │  HeartRateViewModel.kt                    │  │
│  │  - Transformiert BLE-State → UiState      │  │
│  │  - Hält HR-History (letzte 60 Werte)      │  │
│  └───────────────────────────────────────────┘  │
│                    ▲                             │
│                    │ StateFlow<HrState>          │
│  ┌───────────────────────────────────────────┐  │
│  │  Data/BLE Layer                           │  │
│  │  HeartRateManager.kt                      │  │
│  │  - BLE Scan (gefiltert nach HR-Service)   │  │
│  │  - GATT Connect + Service Discovery       │  │
│  │  - HR Measurement Notifications           │  │
│  └───────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
```

## Features

- **BLE Scan** – Sucht gezielt nach Geräten mit Heart Rate Service (UUID 0x180D)
- **Pulsierende Herz-Animation** – Schlägt synchron zum gemessenen BPM
- **Live BPM-Anzeige** – Grosse, prominente Herzfrequenz
- **Min / Avg / Max** – Statistiken über die Session
- **Mini-Chart** – Verlauf der letzten 60 Messwerte mit Gradient-Fill
- **Material 3** – Dark/Light Theme mit Custom Heart-Red Palette
- **Deutsche UI** – Vollständig auf Deutsch

## Setup

1. Öffne das Projekt in **Android Studio Hedgehog+**
2. Sync Gradle
3. Auf einem **echten Android-Gerät** ausführen (BLE funktioniert nicht im Emulator)
4. Garmin HRM-200 anlegen und aktivieren
5. Scan starten → Gerät auswählen → Herzfrequenz ablesen

## Berechtigungen

Die App fragt zur Laufzeit folgende Berechtigungen ab:

| Android 12+ (API 31+)      | Ältere Versionen        |
|-----------------------------|-------------------------|
| `BLUETOOTH_SCAN`            | `BLUETOOTH`             |
| `BLUETOOTH_CONNECT`         | `BLUETOOTH_ADMIN`       |
| `ACCESS_FINE_LOCATION`      | `ACCESS_FINE_LOCATION`  |

## Technische Details

- Das Garmin HRM-200 verwendet das **Standard BLE Heart Rate Profile**
- Service UUID: `0x180D` (Heart Rate)
- Characteristic UUID: `0x2A37` (Heart Rate Measurement)
- Das HR-Measurement Byte-Format wird korrekt geparst (UINT8 und UINT16)
