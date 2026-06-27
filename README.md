<p align="center">
  <img src="https://img.shields.io/badge/Android-14+-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" />
  <img src="https://img.shields.io/badge/Version-3.2-00E5FF?style=for-the-badge" />
</p>

# 🏍️ GigRun V3.1

> **The ultimate gig-worker companion app for Indian delivery riders.** Track every trip, every rupee, every kilometer, every ride quality metric — automatically.

GigRun runs silently in the background while you work on **Blinkit, Zepto, Rapido, Uber** and more. It uses GPS tracking, notification interception, and a state machine to automatically classify your activity and calculate your *real* earnings per hour — after fuel, EMI, and wait time.

---

## ✨ Features

### 📊 Smart Dashboard
- **Live shift tracking** with one-tap Start/End
- Real-time ₹/hour (gross & net), trip count, distance
- **Break-even meter** — animated gauge showing profit vs. daily costs
- **Riding Score** — accelerometer-based monitoring of harsh braking, acceleration & sharp turns
- **Speed Alert** — configurable overspeed warnings with haptic feedback
- Fuel cost input with auto-calculation from km/L settings

### 🗺️ GPS & Route Tracking
- Continuous background GPS via foreground service
- **Polyline route visualization** on Google Maps for every trip
- Start/End markers with full coordinate details
- Anchor-based location awareness (Home, Store, College)

### 🤖 Automatic Activity Detection (FSM Engine)
- **6-state finite state machine**: Idle → Commute → Waiting → Delivering → Complete → College
- 15-second confidence threshold (3 consecutive readings)
- Geofence-based transitions using configurable anchor points
- Speed + location + notification signals combined for accuracy

### 💰 Earnings Tracking
- **Notification interception** for Blinkit, Zepto, Rapido, Uber
- Auto-extracts earning amounts from delivery notifications
- Per-platform breakdown with trip count, distance, wait time
- Weekly platform comparison dashboard

### 🚨 Crash Detection & Safety
- **Multi-factor crash trigger**: Accelerometer > 4G + velocity drop + 8-second stillness
- 30-second countdown with cancel option before alerting
- Auto-SMS to 3 emergency contacts with GPS coordinates
- Conditional activation based on user preference

### 📄 PDF Shift Reports
- One-tap **"Export Shift Report"** with full day summary
- Platform breakdown, fuel costs, net earnings
- Share via WhatsApp, email, or any app

### 🔧 Vehicle Maintenance Tracker
- Pre-configured reminders: Oil, Air Filter, Chain, Tyres, General Service
- **Dual-threshold alerts**: by kilometers AND days since last service
- Animated progress bars with color-coded urgency
- Snooze & Mark Done actions

### ⚙️ Settings & Configuration
- Location anchors (Home, Store/Hub, College) with lat/lon
- Vehicle info (type, make, model, odometer)
- Fuel efficiency (km/L) & price (₹/L) for auto fuel cost calculation
- Daily fixed costs (EMI, phone plan) for break-even calculation
- Emergency contacts for crash detection

---

## 🏗️ Architecture

```
com.gigrun/
├── core/
│   ├── fsm/          FsmEngine — 6-state activity classifier
│   └── utils/        HaversineCalculator, PolylineEncoder, NotificationParser, PdfExporter
├── data/
│   ├── database/     Room DB (Shift, Trip, Earning, ServiceReminder)
│   ├── preferences/  DataStore-backed UserPreferences
│   └── repository/   ShiftRepository, TripRepository, EarningsRepository, MaintenanceRepository
├── di/               Hilt AppModule — singleton DB, DAOs, UserPreferences
├── presentation/
│   ├── dashboard/    DashboardScreen + DashboardViewModel
│   ├── trips/        TripListScreen + TripDetailScreen (with map polylines)
│   ├── platforms/    PlatformCompareScreen
│   ├── map/          MapScreen (anchor markers)
│   ├── maintenance/  MaintenanceScreen (service reminders)
│   ├── settings/     SettingsScreen (full configuration)
│   └── crash/        CrashCountdownOverlay
├── service/
│   ├── LocationTrackingService    GPS + FSM foreground service
│   ├── CrashDetectionService      Accelerometer-based safety monitor
│   ├── NotificationScanner        NotificationListenerService for earnings
│   └── MaintenanceAlertWorker     Periodic WorkManager check
└── ui/
    ├── theme/        Apple HIG theme (AppleColors, dynamic light/dark mode, SF Pro typography)
    └── components/   EarningsCard, BreakEvenMeter, PlatformBadge, StatRow
```

### Tech Stack
| Layer | Technology |
|-------|-----------|
| **UI** | Jetpack Compose + Material 3 |
| **DI** | Hilt (singleton DB across all services & ViewModels) |
| **DB** | Room with Flow-based reactive queries |
| **Preferences** | Jetpack DataStore |
| **Maps** | Google Maps Compose (`maps-compose`) |
| **Background** | Foreground Service + WorkManager |
| **Sensors** | Accelerometer (crash detection) |
| **Build** | Gradle 9.1 + KSP |

---

## 🚀 Getting Started

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

## 📱 Screenshots

*The app features a premium Apple-themed design with dynamic light/dark mode, SF Pro-inspired typography, minimalist glassmorphism card layouts, and iOS-style rounded containers.*

---

## 📋 Version History

| Version | Date | Changes |
|---------|------|---------|
| **v3.2** | June 2026 | Added Riding Score monitor, Speed Alert system, HUD settings. Refactored project structure with consolidated docs. |
| **v3.0** | June 2026 | Pure Apple-themed UI redesign (dynamic light/dark, SF Pro typography, glassmorphism), Hilt DI, singleton DB, crash detection lifecycle, PDF export, polyline maps, background location, maintenance dedup, FSM reset |
| v2.0 | June 2025 | Core FSM engine, notification parsing, break-even tracker, maintenance system |
| v1.0 | May 2025 | Initial prototype with basic GPS tracking |

---

## 🤝 Contributing

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is for personal/educational use. All rights reserved.

---

<p align="center">
  Built with ❤️ for Indian gig workers<br/>
  <strong>Every rupee counts. Every kilometer matters.</strong>
</p>
