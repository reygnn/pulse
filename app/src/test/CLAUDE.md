# CLAUDE.md (Tests)

Orientierung für Sessions, die sich mit den Unit-Tests von **Pulse**
beschäftigen. Keine technische Referenz — die liegt in
`java/com/github/reygnn/pulse/TESTING_CONVENTIONS.kt`. Diese Datei
beantwortet Meta-Fragen: was existiert, warum, was wurde bewusst
weggelassen, wo fängt man an?

Pulse ist eine kleine Single-Module-App. Die Test-Infrastruktur ist
deshalb deutlich schlanker als beim Schwesterprojekt **Kolibri-
Launcher** — kein Hilt, keine Repository-Interfaces, kein DataStore,
kein Robolectric, kein Contract-Test-Geflecht. Das ist Absicht; siehe
unten unter „Was bewusst nicht da ist".

---

## Stack

- **JUnit 4** (4.13.2) — pure JVM, keine Android-Runtime nötig
- Aktuell **kein** MockK / Turbine / Robolectric / coroutines-test —
  wird hinzugezogen, sobald ein Test-Bedarf das verlangt (siehe
  TESTING_CONVENTIONS.kt → „Wenn ViewModel-Tests dazukommen")

Tests laufen via `./gradlew test`. Es gibt keinen `androidTest/`-
Sourceset; BLE-Verhalten lässt sich ohnehin nur am echten Gerät
verifizieren (siehe oberste `CLAUDE.md` unter „Build").

---

## Was getestet ist

| Datei | Was es abdeckt |
|---|---|
| `workout/HrZoneTest.kt` | `HrZone.fromHeartRate` — Grenzen jeder Zone, `hr=0`/`maxHr<=0`-Fallbacks, mid-zone-Sweep über alle sechs Zonen |
| `workout/UserProfileTest.kt` | Tanaka `estimatedMaxHr`; Keytel-Kalorien (Mann/Frau, leere/single Samples, negative-HR-Schutz, Skalierung mit Dauer, glitch-Timestamps) |
| `workout/WorkoutSessionTest.kt` | `avgHr`/`minHr`/`peakHr`/`durationMs` für leer/single/many; `zoneDistribution` (Summe = 1, Single-Zone, 50/50-Split); Kalorien-Delegation |
| `ble/HeartRateManagerParseTest.kt` | `parseHeartRate` — UINT8/UINT16-LE, Flag-Bits, `0xFF`-Masking, Malformed-Payload-Härtung, Extra-Flags ignoriert |

**Pure-Logic-Schwerpunkt** ist gewollt: das sind die Stellen, an denen
ein Bug stumm produziert werden kann (Formel falsch, Off-by-One in der
Zonen-Lookup, Byte-Order vertauscht). Die haben einen Test verdient.

---

## Was bewusst NICHT da ist

- **`HeartRateViewModel`-Tests.** `recordSample` benutzt
  `System.currentTimeMillis()` direkt für den Long-Term-5-Sekunden-
  Throttle. Ohne Clock-Abstraktion lässt sich das nicht in Virtual
  Time prüfen, und einen Refactor dafür einzubauen sprengt den
  Nutzen-pro-LOC-Rahmen, den die App heute hat. Wenn der Throttle
  einmal kompliziert genug wird, dass ein Test ihn rechtfertigt,
  zuerst eine `Clock`-Schnittstelle einziehen, dann Test schreiben.
- **`UserProfileStore`-Test.** Würde Robolectric einziehen, nur um
  den SharedPreferences-Round-Trip zu prüfen. Der Code ist trivial
  genug, dass die JVM-`UserProfile`-Tests reichen.
- **BLE / GATT-Callback-Tests.** Pure Android-System-API-Verkettung;
  ohne Robolectric oder Mock-Theater kein ehrlicher Test möglich.
  Die Verhaltens-Garantie kommt aus der CLAUDE.md-Doku zur seriellen
  GATT-Reihenfolge plus echtem Geräte-Test.
- **Compose-UI-Tests.** Aufwand zu hoch für eine Solo-Hobby-App,
  und der visuelle Smoke-Test am Gerät fängt Regressionen
  zuverlässig genug.
- **Contract-Tests / Fake-Repositories.** Pulse hat keine
  Repository-Interfaces (`HeartRateManager`, `UserProfileStore` etc.
  sind konkrete Klassen). Damit gibt es nichts zu „driften" und
  damit auch nichts zu schützen.

---

## Wenn du als Claude-Session reinkommst

1. **Zuerst** `TESTING_CONVENTIONS.kt` lesen.
2. **Dann** eine bestehende Test-Datei als Vorlage — `HrZoneTest.kt`
   ist der einfachste Einstieg, `UserProfileTest.kt` der ausführlichste.
3. **Vor jedem neuen Test**: ist die zu testende Funktion pure?
   → JUnit reicht. Touched sie SharedPreferences / Context /
   `System.currentTimeMillis` direkt? → erst Refactor (Clock /
   Helper), dann Test, **nicht** Robolectric reflexartig dazuziehen.
4. **Bei „Test schlägt rot fehl"**: Produktions-Code ist Wahrheit.
   Test angleichen, nicht Code lockern. Ausnahme: Test deckt einen
   echten Bug auf — dann Code fixen und in der Commit-Message den
   Bug benennen.

---

## Was diese Datei NICHT ist

- Keine technische Referenz — die liegt in
  `java/com/github/reygnn/pulse/TESTING_CONVENTIONS.kt`.
- Keine TODO-Liste.
- Kein Spiegel der Kolibri-Launcher-`test/CLAUDE.md`. Pulse ist
  klein genug, dass die meisten dortigen Sektionen hier reine
  Karteileichen wären.
