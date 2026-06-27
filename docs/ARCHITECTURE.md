# Architecture Documentation

## Overview

GigRun follows **Clean Architecture** with a clear separation of concerns across layers:

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                         │
│  (Compose Screens + ViewModels + Navigation)                 │
├─────────────────────────────────────────────────────────────┤
│                        Domain Layer                           │
│         (Use Cases / Business Logic - in repositories)       │
├─────────────────────────────────────────────────────────────┤
│                         Data Layer                            │
│    (Room Database + DataStore + Repositories + DAOs)         │
├─────────────────────────────────────────────────────────────┤
│                     Framework Layer                           │
│  (Android Services, Sensors, Location, Notifications, Maps)  │
└─────────────────────────────────────────────────────────────┘
```

## Module Structure

```
com.gigrun/
├── core/
│   └── utils/                 # Pure Kotlin utilities (no Android deps)
│       ├── HaversineCalculator.kt    # GPS distance calculations
│       ├── NotificationParser.kt     # Regex-based fare extraction
│       ├── PolylineEncoder.kt        # Google Maps polyline encoding
│       └── PdfExporter.kt            # PDF report generation
│
├── data/
│   ├── database/              # Room database layer
│   │   ├── AppDatabase.kt
│   │   ├── entities/          # @Entity classes
│   │   └── dao/               # @Dao interfaces
│   ├── preferences/           # DataStore preferences
│   │   └── UserPreferences.kt
│   └── repository/            # Repository implementations
│       ├── ShiftRepository.kt
│       ├── TripRepository.kt
│       ├── EarningsRepository.kt
│       └── MaintenanceRepository.kt
│
├── di/
│   └── AppModule.kt           # Hilt dependency injection
│
├── presentation/
│   ├── dashboard/             # Main earnings dashboard
│   ├── trips/                 # Trip list & detail with maps
│   ├── platforms/             # Platform comparison
│   ├── map/                   # Anchor/map screen
│   ├── maintenance/           # Vehicle maintenance
│   ├── settings/              # Configuration screen
│   └── crash/                 # Crash countdown overlay
│
├── service/
│   ├── LocationTrackingService.kt    # Foreground GPS + FSM
│   ├── CrashDetectionService.kt      # Accelerometer monitoring
│   ├── NotificationScanner.kt        # NotificationListenerService
│   ├── MaintenanceAlertWorker.kt     # WorkManager daily check
│   └── FsmEngine.kt                  # Finite State Machine
│
├── ui/
│   ├── theme/                 # Apple HIG theme system
│   │   ├── AppleColors.kt
│   │   └── Theme.kt
│   └── components/            # Reusable Compose components
│       ├── EarningsCard.kt
│       ├── BreakEvenMeter.kt
│       ├── PlatformBadge.kt
│       └── StatRow.kt
│
├── GigRunApplication.kt       # Hilt Application entry point
└── MainActivity.kt            # Main entry + navigation
```

## Key Architectural Decisions

### 1. Singleton Database Across Services & UI
- `AppDatabase.getInstance()` provides a process-wide singleton
- Hilt provides the same instance via `AppModule`
- Ensures services and ViewModels share the same DB connection

### 2. FSM Engine for Trip Detection
- Pure Kotlin class (`FsmEngine`) with no Android dependencies
- 6 states: `IDLE_AT_HOME` → `UNCLASSIFIED_COMMUTE` → `WAITING_AT_STORE` → `DELIVERING_ORDER` → `ORDER_COMPLETE` → `AT_COLLEGE`
- Confidence threshold: 3 consecutive GPS readings (15 seconds) before state transition
- Testable in isolation with unit tests

### 3. Foreground Service for Background Tracking
- `LocationTrackingService` runs as persistent foreground service
- Adaptive GPS polling: 5s (active) / 60s (idle) / thermal throttled
- WakeLock prevents CPU sleep during active tracking
- Battery temperature monitoring (43°C threshold)

### 4. NotificationListenerService for Earnings
- `NotificationScanner` intercepts delivery app notifications
- Regex-based parsing for Blinkit, Zepto, Rapido, Uber
- Stores raw notification text for debugging/retroactive fixes

### 5. Crash Detection with False Positive Suppression
- Requires 3 simultaneous conditions:
  1. Accelerometer > 4G spike
  2. GPS velocity drop >15→<5 km/h in 4s
  3. 8 seconds of sustained stillness
- 30-second cancelable countdown before SMS dispatch

### 6. Theme System
- Apple HIG-inspired design with dynamic light/dark mode
- SF Pro-inspired typography scale
- Glassmorphism cards with iOS-style rounded corners
- Semantic color roles (label, secondaryLabel, groupedBackground, etc.)

## Data Flow

```
GPS Location → LocationTrackingService → FsmEngine → State Transition
                                                    ↓
                                            Trip/Shift DB Write
                                                    ↓
                                            Repository → ViewModel → UI State
                                                    ↓
                                            Compose Recomposition
```

## Dependency Direction

```
Presentation → Domain/Repository → Data → Framework
     ↑                                               │
     └────────────────── Hilt DI ────────────────────┘
```

- Inner layers don't know about outer layers
- Hilt provides concrete implementations at runtime
- Interfaces (DAOs, Repositories) defined in data layer

## Testing Strategy

- **Unit Tests**: Core utilities (Haversine, Polyline, NotificationParser)
- **Integration Tests**: Database DAOs, Repository implementations
- **UI Tests**: Compose screen testing (planned)
- **Service Tests**: FSM engine logic (planned)