<p align="center">
  <img src="https://img.shields.io/badge/Android-14+-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Kotlin-2.3-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" />
  <img src="https://img.shields.io/badge/Version-11.0-00E5FF?style=for-the-badge" />
</p>

<h1 align="center">GigRun</h1>

<p align="center">
  <strong>The smart companion app for Indian delivery riders.</strong><br/>
  Track every trip, every rupee, every kilometer — automatically.
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#screenshots">Screenshots</a> •
  <a href="#architecture">Architecture</a> •
  <a href="#getting-started">Get Started</a> •
  <a href="#security">Security</a> •
  <a href="#license">License</a>
</p>

---

## About

GigRun runs quietly in the background while you work on **Blinkit, Zepto, Rapido, Uber** and more. It uses your phone's GPS, notifications, and motion sensors to automatically understand what you are doing — and calculate your **real earnings per hour** after fuel, EMI, and waiting time.

No manual entry. No spreadsheets. Just open the app, start your shift, and see your money grow in real time.

---

## Features

### Smart Dashboard
- **Live shift tracking** — one tap to start, one tap to end
- Real-time ₹/hour (gross and net), trip count, distance covered
- **Break-even meter** — a circular gauge that shows when you start making profit
- **Riding Score** — monitors harsh braking, fast acceleration, and sharp turns
- **Speed Alert** — warns you when you go over your set speed limit
- **Fuel cost calculator** — auto-calculates from your bike's km/L and fuel price

### GPS and Route Tracking
- Continuous background GPS using a foreground service
- **Route polyline** drawn on Google Maps for every trip
- Start and end markers with full address details
- **Anchor points** — set Home, Store, and College locations for smart detection

### Automatic Activity Detection
- **6-state engine**: Idle, Commute, Waiting, Delivering, Complete, College
- 15-second confidence check (3 readings in a row)
- Uses speed, location, and notifications together for accuracy
- Geofence-based transitions using your anchor points

### Earnings Tracking
- **Notification interception** for Blinkit, Zepto, Rapido, Uber
- Auto-reads earning amounts from delivery notifications
- Per-platform breakdown: trip count, distance, wait time
- Weekly platform comparison chart

### Crash Detection and Safety
- **Multi-factor crash trigger**: high G-force + sudden stop + 8 seconds of stillness
- 30-second countdown with a cancel button before sending alert
- Auto-SMS to 3 emergency contacts with your GPS location
- You choose when crash detection is active

### PDF Shift Reports
- One-tap **"Export Shift Report"** with full day summary
- Platform breakdown, fuel costs, net earnings
- Share via WhatsApp, email, or any app

### Vehicle Maintenance
- Pre-set reminders: Oil, Air Filter, Chain, Tyres, General Service
- **Dual alerts**: by kilometers AND by days since last service
- Color-coded progress bars (green, orange, red)
- Snooze and Mark Done actions

### Settings
- Location anchors (Home, Store, College) with latitude and longitude
- Vehicle info: type, make, model, odometer
- Fuel efficiency (km/L) and fuel price (₹/L)
- Daily fixed costs: EMI, phone plan
- Emergency contacts for crash alerts

---

## Screenshots

<p align="center">
  <em>Clean teal design with smooth animations, dark mode support, and clear typography.</em>
</p>

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
│   ├── trips/         TripListScreen, TripDetailScreen, TripsViewModel
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
    ├── design/        GigRunColors (teal palette), GigRunTheme, Motion (animations)
    └── components/    GigCard, EmptyState, ProgressRing, BreakEvenMeter,
                       StatRow, ChipLabel, NavRow, SectionHeader, SurgeBadge,
                       AnimatedRupees
```

### Tech Stack

| Layer | Technology |
|-------|-----------|
| **UI** | Jetpack Compose + Material 3 |
| **DI** | Hilt (singleton DB across all services and ViewModels) |
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
# Clone the repo
git clone https://github.com/Chatur7x/GigRun.git
cd GigRun/app-root

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run lint check
./gradlew lintDebug
```

### Permissions

| Permission | Why |
|-----------|-----|
| `ACCESS_FINE_LOCATION` | GPS tracking for trips |
| `ACCESS_BACKGROUND_LOCATION` | Keep tracking when app is in background |
| `FOREGROUND_SERVICE_LOCATION` | Required on Android 14+ |
| `POST_NOTIFICATIONS` | Shift status and maintenance alerts |
| `SEND_SMS` | Emergency crash alerts |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Auto-detect delivery earnings |

---

## Security

- **NotificationParser**: anchored regex, 2000-char cap, velocity dedup
- **PrefsValidation**: central clamps for all preference setters, premium contact blocklist
- **LedgerManager**: 8KB payload cap, 200-transaction cap, mutex serialization
- **CrashDetectionService**: send-time guard, 10-minute SMS cooldown
- **TripsScreen**: allowlist, clamps, 30MB+ EXIF strip
- **Mock-GPS drop** in LocationTrackingService and CrashDetectionService
- **Room DB v4**: unique index on service_reminders, explicit idempotent migrations

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| **v11** | Sep 2026 | Fixed app crash on launch (ContentObserver in composition path) |
| **v10** | Sep 2026 | Lint fixes (MarkerState remember, uses-feature telephony) |
| **v9** | Sep 2026 | Full UI migration to GigRunColors + GigCard/EmptyState/Motion system |
| **v8** | Sep 2026 | Security hardening (NotificationParser, PrefsValidation, LedgerManager caps), Room DB v4 |
| **v7** | Sep 2026 | Smooth motion system, full-bleed hero, haptic feedback |
| **v3.2** | June 2026 | Riding Score monitor, Speed Alert system, HUD settings |
| **v3.0** | June 2026 | UI redesign, Hilt DI, singleton DB, crash detection, PDF export |
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

This project is for personal and educational use. All rights reserved.

---

<p align="center">
  Built with ❤️ for Indian gig workers<br/>
  <strong>Every rupee counts. Every kilometer matters.</strong>
</p>
