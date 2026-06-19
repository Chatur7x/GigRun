# 🏍️ GigRun V3.1 — Release & Feature Documentation

Welcome to **GigRun V3.1**, the ultimate high-performance, premium-designed companion app tailored for Indian delivery riders working on platforms like **Blinkit, Zepto, Rapido, Uber, and Swiggy**.

This release introduces a **complete Apple HIG Design Redesign**, a **robust Hilt dependency injection architecture**, automated **notification interception**, and high-precision **GPS tracking** for calculating real earnings per hour.

---

## 📱 Features inside `v3.1_GigRun.apk`

Here is a detailed breakdown of all the premium features packed into the **v3.1** build:

### 1. 🍎 Premium Apple HIG Theme & Design System
* **Dynamic Day/Night Modes**: The entire application automatically adapts to system light and dark themes using exact color specifications from the **Apple Human Interface Guidelines (HIG)** (e.g., system gray systems, backgrounds, fills, and label colors).
* **SF Pro-inspired Typography**: Uses a premium, clean typographic scale inspired by Apple's signature SF Pro fonts, optimizing text legibility on the road.
* **Apple Shapes & Insets**: High-end minimalist layout containers featuring smooth iOS-style curves (`RoundedCornerShape(14.dp)` for cards, `20.dp` for menus).
* **Glassmorphism Fills**: Sleek, theme-aware translucent fill overlays for status chips, platform tags, and action buttons.

### 2. 🤖 Automatic Activity Detection (FSM Engine)
* **6-State Finite State Machine**: Implements a core activity classifier that automatically transitions between:
  `Idle` ➔ `Commute` ➔ `Waiting` ➔ `Delivering` ➔ `Complete` ➔ `College`
* **Geofence-Based Anchors**: Detects proximity to designated locations (such as Stores/Hubs, Home, or Colleges) using custom coordinates and radius thresholding.
* **Confidence Filtering**: Employs a 15-second confidence filter (requiring 3 consecutive matching readings) to prevent false classification jumps while active.

### 3. 💰 Automated Earnings & Notification Interception
* **Background Listener Service**: Runs silently in the background, intercepting incoming push notifications from delivery platforms.
* **Auto-Regex Extraction**: Automatically parses earning figures from notifications (e.g., extracting `"₹85"` from Blinkit delivery messages) and logs them directly to the local database in real-time.
* **Platform Comparison Screen**: View charts comparing weekly earnings, total shifts, average pay per hour, and distance covered across Swiggy, Zepto, Blinkit, and Uber.

### 4. 🗺️ Route Tracking & Google Maps Integration
* **Foreground Location Service**: Continual location updates via foreground service to ensure stable GPS logging.
* **Polyline Map Visualizations**: The trip detail screen decodes compressed path coordinates and renders a Google Map showing the exact route, complete with Start and End markers.
* **Android 10+ Background Support**: Requests foreground and background location permissions separately to guarantee tracking stability when screen is locked.

### 5. 🚨 Accelerometer-Based Crash Detection
* **Multi-Factor Trigger**: Activates when the system registers a sudden impact (`> 4G` shock) followed by velocity drop and 8 seconds of absolute stillness.
* **30-Second Grace Countdown**: Displays a full-screen, high-priority countdown with progress bar. The driver can tap to dismiss in case of false alarms.
* **Emergency Broadcast**: Automatically sends an SMS to up to 3 emergency contacts with the driver's exact GPS coordinates if the countdown expires.

### 6. 📄 PDF Shift Reports
* **Export Summary**: Generate a comprehensive PDF summarizing today's shift details, gross earnings, net earnings (after auto-calculating fuel cost based on km/L and vehicle parameters), and individual platform stats.
* **One-Tap Share**: Direct integration with standard Android share sheets to send reports via WhatsApp, Email, or Slack instantly.

### 7. 🔧 Vehicle Maintenance Tracker
* **Dual-Threshold Alerts**: Alerts the driver based on both distance (odometer mileage) and time elapsed (days since last service).
* **Smart Duplication Prevention**: Repositories check active vehicle profiles, preventing duplicate cards on settings updates.
* **Actionable Progress Indicators**: High-performance progress bars that turn orange/red as the vehicle approaches service thresholds.

---

## 🏗️ Technical Architecture

```
com.gigrun/
├── core/
│   ├── fsm/          FsmEngine — 6-state activity classifier
│   └── utils/        HaversineCalculator, PolylineEncoder, NotificationParser, PdfExporter
├── data/
│   ├── database/     Room DB (AppDatabase Singleton, DAOs)
│   ├── preferences/  DataStore-backed UserPreferences (Theme, Targets, Contacts)
│   └── repository/   Shift, Trip, Earnings, and Maintenance repositories
├── di/               Hilt Dependency Injection modules (AppModule)
├── presentation/
│   ├── dashboard/    DashboardScreen (Shift management, PDF export, Break-Even gauge)
│   ├── trips/        TripListScreen & TripDetailScreen (Polyline Maps)
│   ├── platforms/    PlatformCompareScreen (Interactive platform comparison metrics)
│   ├── map/          MapScreen (Visualizing custom anchors)
│   ├── maintenance/  MaintenanceScreen (Dual-threshold maintenance trackers)
│   ├── settings/     SettingsScreen (Preferences configuration)
│   └── crash/        CrashCountdownOverlay (Grace-period overlay UI)
├── service/
│   ├── LocationTrackingService    GPS + FSM Foreground Location Engine
│   ├── CrashDetectionService      SensorEventListener accelerometer listener
│   ├── NotificationScanner        NotificationListenerService listener
│   └── MaintenanceAlertWorker     Periodic background WorkManager alarm
└── ui/
    ├── theme/        Apple HIG Theme System (AppleColors, AppleTypography, Theme.kt)
    └── components/   EarningsCard, BreakEvenMeter (Canvas arc gauge), PlatformBadge, StatRow
```

---

## 🛠️ Verification Metrics

The `v3.1_GigRun.apk` build was verified against the following checks before packaging:

* **Compilation Status**: Clean compilation on Gradle 9.1 (`.\gradlew assembleDebug`) with **zero errors**.
* **Unit Tests Status**: Passed all local JVM unit tests (`.\gradlew test`) assessing `HaversineCalculator`, `NotificationParser`, and `PolylineEncoder`.
* **Database Stability**: Room DB migrations configured safely with singleton instances, avoiding instance leaks across foreground threads.
