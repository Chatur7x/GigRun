<p align="center">
  <img src="https://img.shields.io/badge/Android-14+-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Kotlin-2.3-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" />
  <img src="https://img.shields.io/badge/Version-11.0-00E5FF?style=for-the-badge" />
</p>

# GigRun

> **The ultimate gig-worker companion app for Indian delivery riders.** Track every trip, every rupee, every kilometer — automatically.

GigRun runs silently in the background while you work on **Blinkit, Zepto, Rapido, Uber** and more. It uses GPS tracking, notification interception, and a state machine to automatically classify your activity and calculate your *real* earnings per hour — after fuel, EMI, and wait time.

---

## Features

### Smart Dashboard
- **Live shift tracking** with one-tap Start/End
- Real-time ₹/hour (gross & net), trip count, distance
- **Break-even meter** — animated gauge showing profit vs. daily costs
- **Riding Score** — accelerometer-based monitoring of harsh braking, acceleration & sharp turns
- **Speed Alert** — configurable overspeed warnings with haptic feedback
- Fuel cost input with auto-calculation from km/L settings

### GPS & Route Tracking
- Continuous background GPS via foreground service
- **Polyline route visualization** on Google Maps for every trip
- Start/End markers with full coordinate details
- Anchor-based location awareness (Home, Store, College)

### Automatic Activity Detection (FSM Engine)
- **6-state finite state machine**: Idle → Commute → Waiting → Delivering → Complete → College
- 15-second confidence threshold (3 consecutive readings)
- Geofence-based transitions using configurable anchor points
- Speed + location + notification signals combined for accuracy

### Earnings Tracking
- **Notification interception** for Blinkit, Zepto, Rapido, Uber
- Auto-extracts earning amounts from delivery notifications
- Per-platform breakdown with trip count, distance, wait time
- Weekly platform comparison dashboard

### Crash Detection & Safety
- **Multi-factor crash trigger**: Accelerometer > 4G + velocity drop + 8-second stillness
- 30-second countdown with cancel option before alerting
- Auto-SMS to 3 emergency contacts with GPS coordinates
- Conditional activation based on user preference

### PDF Shift Reports
- One-tap **"Export Shift Report"** with full day summary
- Platform breakdown, fuel costs, net earnings
- Share via WhatsApp, email, or any app

### Vehicle Maintenance Tracker
- Pre-configured reminders: Oil, Air Filter, Chain, Tyres, General Service
- **Dual-threshold alerts**: by kilometers AND days since last service
- Animated progress bars with color-coded urgency
- Snooze & Mark Done actions

### Settings & Configuration
- Location anchors (Home, Store/Hub, College) with lat/lon
- Vehicle info (type, make, model, odometer)
- Fuel efficiency (km/L) & price (₹/L) for auto fuel cost calculation
- Daily fixed costs (EMI, phone plan) for break-even calculation
- Emergency contacts for crash detection

---

## Architecture

```
com.gigrun/
├── core/
│   ├── blockchain/    GigChain — local ledger for earnings integrity
│   ├── fsm/           FsmEngine — 6-state activity classifier
│   └── utils/         HaversineCalculator, PolylineEncoder, NotificationParser,
│                      PdfExporter, PrefsValidation, LedgerManager, GoalsCalculator,
│                      TaxCalculator, OcrProcessor, SmartPlatformDetector,
│                      BatteryOptimizationHelper, RidingScoreService
├── data/
│   ├── database/      Room DB v4 (Shift, Trip, Earning, ServiceReminder, Vehicle,
│   │                  FuelLog, Block, TempTransaction, Expense, EarningsGoal)
│   ├── preferences/   DataStore-backed UserPreferences (corruption-safe)
│   └── repository/    ShiftRepository, TripRepository, EarningsRepository
├── di/                Hilt AppModule — singleton DB, DAOs, UserPreferences
├── presentation/
│   ├── dashboard/     DashboardScreen + DashboardViewModel
│   ├── trips/         TripListScreen, TripDetailScreen, TripsViewModel (with map polylines)
│   ├── platforms/     PlatformCompareScreen
│   ├── map/           MapScreen (anchor markers)
│   ├── maintenance/   MaintenanceScreen (service reminders)
│   ├── fuelbike/      FuelBikeScreen (fuel + GigChain + maintenance health)
│   ├── expenses/      ExpensesScreen (full expense tracking)
│   ├── goals/         GoalsScreen (earnings goals)
│   ├── tax/           TaxHelperScreen (tax calculation)
│   ├── settings/      SettingsScreen + SettingsViewModel
│   └── crash/         CrashCountdownOverlay
├── service/
│   ├── LocationTrackingService    GPS + FSM foreground service
│   ├── CrashDetectionService      Accelerometer-based safety monitor
│   ├── NotificationScanner        NotificationListenerService for earnings
│   ├── SpeedAlertService          Overspeed warning service
│   ├── MaintenanceAlertWorker     Periodic WorkManager check
│   └── BootReceiver               Reschedule maintenance after reboot
└── ui/
    ├── design/        GigRunColors (teal token palette), GigRunTheme, Motion (beast system)
    └── components/    GigCard, EmptyState, BeastPrimitives, ProgressRing,
                       BreakEvenMeter, StatRow, ChipLabel, NavRow, SectionHeader,
                       SurgeBadge, AnimatedRupees
```

### Tech Stack
| Layer | Technology |
|-------|-----------|
| **UI** | Jetpack Compose + Material 3 |
| **DI** | Hilt (singleton DB across all services & ViewModels) |
| **DB** | Room v4 with Flow-based reactive queries + explicit migrations |
| **Preferences** | Jetpack DataStore (corruption-safe with fallback) |
| **Maps** | Google Maps Compose (`maps-compose`) |
| **Background** | Foreground Service + WorkManager |
| **Sensors** | Accelerometer (crash detection), Rotation Vector (vehicle carousel) |
| **Build** | Gradle 9.1 + KSP |

---

## Getting Started

### Prerequisites
- Android Studio Ladybug or newer
- JDK 17+
- Google Maps API key (add to `local.properties` as `MAPS_API_KEY`)

### Build
```bash
# Clone
git clone https://github.com/Chatur7x/GigRun.git
cd GigRun/app-root

# Debug APK
./gradlew assembleDebug

# Run tests
./gradlew test

# Lint
./gradlew lintDebug
```

### Permissions Required
| Permission | Why |
|-----------|-----|
| `ACCESS_FINE_LOCATION` | GPS tracking for trips |
| `ACCESS_BACKGROUND_LOCATION` | Continue tracking when app is backgrounded |
| `FOREGROUND_SERVICE_LOCATION` | Android 14+ foreground service requirement |
| `POST_NOTIFICATIONS` | Shift status & maintenance alerts |
| `SEND_SMS` | Emergency crash alerts |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Auto-detect delivery earnings |

---

## Security

- **NotificationParser**: anchored regex, 2000-char cap, velocity dedup
- **PrefsValidation**: central clamps for all preference setters, premium contact blocklist
- **LedgerManager**: 8KB payload cap, 200-txn cap, mutex serialization
- **CrashDetectionService**: send-time guard, 10-min SMS cooldown
- **TripsScreen**: allowlist, clamps, 30MB+EXIF strip
- **Mock-GPS drop** in LocationTrackingService + CrashDetectionService
- **Room DB v4**: unique index on service_reminders, explicit idempotent migrations

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| **v11** | Sep 2026 | Fixed app crash on launch (ContentObserver in composition path) |
| **v10** | Sep 2026 | Lint fixes (MarkerState remember, uses-feature telephony) |
| **v9** | Sep 2026 | Full UI migration to LocalGigRunColors + GigCard/EmptyState/Motion beast system |
| **v8** | Sep 2026 | Security hardening (NotificationParser, PrefsValidation, LedgerManager caps), Room DB v4 |
| **v7** | Sep 2026 | Beast motion system, full-bleed hero, haptic CTA |
| **v3.2** | June 2026 | Added Riding Score monitor, Speed Alert system, HUD settings |
| **v3.0** | June 2026 | Apple-themed UI redesign, Hilt DI, singleton DB, crash detection, PDF export |
| v2.0 | June 2025 | Core FSM engine, notification parsing, break-even tracker |
| v1.0 | May 2025 | Initial prototype with basic GPS tracking |

---

## Contributing

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## License

This project is for personal/educational use. All rights reserved.

---

<p align="center">
  Built with ❤️ for Indian gig workers<br/>
  <strong>Every rupee counts. Every kilometer matters.</strong>
</p>
