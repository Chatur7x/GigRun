# GigRun V2 — Project Blueprint
### Revised & Realistic Build Plan for a Solo Developer

---

## What Changed From V1 and Why

Three features from the original spec were removed entirely:

- **Accessibility Service scraper** — Play Store flags and removes apps using this for non-accessibility purposes. Blinkit/Zepto UI updates would break your selectors constantly. Also violates platform ToS and risks account suspension.
- **"Battery bus temperature" governor** — Not accessible on unrooted Android. The original spec described hardware access that simply does not exist on standard devices.
- **Over-engineered crash detection** — The original design had no false positive suppression. Hyderabad speed bumps and potholes would trigger emergency SMS every shift. Redesigned below with a proper multi-condition check.

Everything else was kept, fixed, or expanded with features that are actually missing.

---

## 1. What This App Does (Plain Language)

GigRun runs silently in the background while you ride. It automatically detects when you start a shift, which platform you are working on (Rapido, Zepto, Blinkit), tracks each trip, measures time spent waiting at stores, and calculates your real earnings per hour after fuel. At the end of any day or week, it shows you a clear picture of where your time and money went — and which platform is actually paying you better.

Zero manual input needed during a shift. Open the app, check your numbers.

---

## 2. Core Features — What To Build

### 2.1 Automatic Trip Detection and Classification

The app uses your phone's GPS combined with pre-saved location anchors (Home, Store/Hub, College) to automatically detect what you are doing at any given moment.

**How it works:**

The system uses a Finite State Machine — a set of defined states and rules for moving between them. At any moment, you are in exactly one state:

```
IDLE_AT_HOME
    |
    | (leave home radius)
    v
UNCLASSIFIED_COMMUTE
    |
    |--- (arrive at Store/Hub) ---> WAITING_AT_STORE
    |                                       |
    |                              (leave store with order)
    |                                       v
    |                               DELIVERING_ORDER
    |                                       |
    |                              (arrive at customer)
    |                                       v
    |                               ORDER_COMPLETE
    |                                       |
    |                              (return to store)
    |                                       v
    |                               WAITING_AT_STORE (loop)
    |
    |--- (arrive at College) ---> AT_COLLEGE
```

**State transitions use the Haversine formula** to check if your GPS coordinates are inside any saved anchor radius:

```
d = 2r * arcsin( sqrt( sin²(Δlat/2) + cos(lat1) * cos(lat2) * sin²(Δlon/2) ) )
```

Where r = 6371 km. If d < anchor_radius (default 150 meters), you are inside that anchor zone.

**What gets recorded per trip:**
- Start time and end time
- GPS path (compressed polyline)
- Distance in kilometers
- Platform tag (Rapido / Zepto / Blinkit / Untagged)
- Wait time at store before departing
- Earning attached to that trip (from notification)

---

### 2.2 Notification Listener — Auto-Platform Tagging and Earnings Capture

The app registers a `NotificationListenerService` to intercept notifications from delivery platform apps. This is a legitimate Android API with clear Play Store approval precedent when the use case is justified.

**What it does:**
- Reads the notification sender package name to tag the trip to the correct platform automatically
- Parses the notification text to extract the order fare (e.g. "Order accepted — ₹52")
- Stores the earning against the active trip in Room DB

**Target packages (verify before coding — these must be confirmed from a real device):**
- Blinkit delivery partner: confirm via `adb shell pm list packages | grep grofers`
- Zepto delivery: confirm via `adb shell pm list packages | grep zepto`
- Rapido captain: confirm via `adb shell pm list packages | grep rapido`

**Important:** Do not hardcode package names based on assumptions. Install each app on a device and run the adb command above to get the real package string before writing the listener.

**Parsing strategy:**
- Use simple regex on notification body text to extract rupee amounts
- Example pattern: `₹\d+(\.\d{1,2})?` — matches ₹52, ₹52.50, etc.
- Store the raw notification text alongside the parsed amount so you can fix parsing errors retroactively
- If parsing fails, the trip is logged with earnings = null and flagged for manual entry

---

### 2.3 Earnings Per Hour Dashboard — The Most Important Screen

This is the core value of the entire app. Every other feature feeds into this.

**Metrics to show on the main dashboard:**

```
TODAY
─────────────────────────────────────────
Total earned:          ₹ 847
Fuel cost (entered):   ₹ 120
Net earned:            ₹ 727

Active shift time:     4h 32m
Unpaid wait time:      38 min
Actual riding time:    3h 54m

₹/hour (gross):        ₹ 186/hr
₹/hour (net):          ₹ 160/hr    ← most important number

Trips completed:       12
Avg earning/trip:      ₹ 70.5
Total distance:        47.3 km
─────────────────────────────────────────
```

**Shift time vs riding time distinction:**
- Shift time = from first trip start to last trip end
- Riding time = shift time minus all wait time at stores and idle gaps
- Both matter. Shift time tells you how long you were out. Riding time tells you how productive those hours were.

**Fuel cost entry:**
- Simple daily input field: "How much did you spend on fuel today?"
- Alternatively, auto-calculate based on distance and a saved fuel efficiency setting (km/litre + price per litre)
- Both options should be available. Manual entry is more accurate.

---

### 2.4 Unpaid Wait Time Tracker

This feature is missing from almost every gig tracking app but is critical for understanding real earnings.

When the FSM enters `WAITING_AT_STORE`, a timer starts. When it leaves (entering `DELIVERING_ORDER`), the timer stops. That duration is recorded as unpaid wait time for that trip.

**Why this matters:** If you wait 15 minutes at a Zepto hub before every order, and you complete 10 orders in a shift, you have lost 2.5 hours to unpaid waiting. That completely changes your real ₹/hour.

**Dashboard display:**
- Daily unpaid wait time total
- Average wait time per order, per platform
- Weekly trend — is your wait time going up? (could indicate hub congestion at certain times)

---

### 2.5 Platform Comparison Dashboard

This answers the question every multi-app gig worker actually has: "Which app should I be running right now?"

**Weekly comparison table:**

```
PLATFORM COMPARISON — This Week
──────────────────────────────────────────────────────
Platform    Trips   Gross ₹   Net ₹/hr   Avg Wait   Distance
──────────────────────────────────────────────────────
Blinkit       28     ₹1,960    ₹172/hr    12 min     89 km
Zepto         19     ₹1,330    ₹148/hr    19 min     67 km
Rapido        11     ₹880      ₹196/hr     4 min     41 km
──────────────────────────────────────────────────────
```

This table alone will tell you more about your business than any other screen in the app.

Over time, patterns will emerge — certain platforms pay better at certain times of day or in certain zones. The app surfaces this data. The rider makes the decision.

---

### 2.6 Break-Even Calculator

A simple tool that answers: "Have I covered my costs today?"

**Inputs (saved in settings, editable per day):**
- Daily fuel cost (auto or manual)
- Daily vehicle EMI contribution (optional, for loan repayment tracking)
- Phone recharge cost (prorated daily — optional)

**Output:**
- Break-even earnings amount for the day
- Visual indicator: below break-even / above break-even
- How many more trips (at average fare) to reach break-even

Example display:
```
Break-even today:  ₹ 180  (fuel + proration)
Earned so far:     ₹ 312
You are:           ₹ 132 in profit
Est. trips left needed to hit ₹500 target: 3 trips
```

---

### 2.7 Shift Report — Exportable Summary

At the end of any day or week, the user can export a clean summary as a PDF or share it as text.

**Contents of the report:**
- Date / period
- Total trips, total distance, total hours active
- Gross earnings per platform
- Fuel and estimated expenses
- Net earnings
- Unpaid wait time total

**Use cases:**
- Screenshot for personal records
- Proof of income for loan applications (banks in India increasingly ask for gig income proof)
- GST tracking if earnings exceed threshold
- Personal accountability / goal tracking

**Implementation:** Use Android's built-in PDF document API (`PdfDocument`) — no third-party library needed. Generate the document in-app and share via standard Android share sheet to WhatsApp, Drive, Files, etc.

---

### 2.8 Vehicle Maintenance Tracker — Corrected Version

The original spec tracked only GPS distance. That is not enough for realistic maintenance alerts.

**Corrected approach:**

User sets up their vehicle once:
- Vehicle type (scooter / motorcycle / bicycle / other)
- Current odometer reading (approximate is fine)
- Last service date
- Fuel type and tank capacity (optional)

The app accumulates GPS trip distance and adds it to the starting odometer value.

**Alert thresholds (user-configurable, with sensible defaults):**

```
Engine oil change:      every 2,000 km or 60 days (whichever comes first)
Air filter check:       every 5,000 km
Chain lubrication:      every 500 km (for motorcycles)
Tyre pressure check:    every 30 days (reminder only, not distance-based)
General service:        every 6,000 km or 6 months
```

**How alerts work:**
- Background service checks thresholds once per day
- Sends a push notification: "Engine oil is due — last changed 1,847 km ago"
- User can mark service as done (resets the counter) or snooze for 3 days

**What this does NOT claim to do:**
- It does not predict mechanical failure
- It does not access any hardware sensor
- It is a simple counter-based reminder system, nothing more

---

### 2.9 Crash Detection — Redesigned With False Positive Suppression

The original design would have sent emergency SMS on every speed bump. This version requires three simultaneous conditions before triggering.

**Trigger conditions (all three must be true within a 4-second window):**

1. Accelerometer spike: raw G-force reading exceeds 4G on any axis
2. GPS velocity drops from above 15 km/h to below 5 km/h within 4 seconds
3. Phone remains stationary (no movement) for 8 seconds after the spike

If all three conditions are met, a 30-second countdown starts on screen with a loud alarm and a large CANCEL button. If the user does not cancel, the app sends an SMS to emergency contacts with the last known GPS coordinates.

**Why this works for Hyderabad specifically:**
- Speed bumps cause a G-force spike but NOT a GPS velocity drop + sustained stillness
- Potholes cause a spike but the rider keeps moving
- A real crash typically involves all three: impact spike, sudden stop, and the rider being unable to respond

**User controls:**
- Set 1–3 emergency contact numbers
- Set the G-force threshold (default 4G, adjustable from 3G to 6G)
- Enable/disable the feature per shift
- Test mode: simulates the countdown without sending SMS

**Implementation uses:**
- `Sensor.TYPE_ACCELEROMETER` for G-force reading
- `FusedLocationProviderClient` for velocity data (already used for trip tracking)
- Android SMS Manager for emergency dispatch

---

### 2.10 Background Persistence and Battery Management — Realistic Version

**What the app actually does:**
- Runs as a persistent `ForegroundService` with an ongoing notification (required by Android)
- Uses a `WakeLock` to prevent the CPU from sleeping during active tracking
- Acquires GPS updates via `FusedLocationProviderClient` with two polling modes:
  - Fast mode (every 5 seconds): when the FSM is in DELIVERING or COMMUTING state
  - Slow mode (every 60 seconds): when in IDLE or WAITING state

**Battery temperature throttling (corrected):**
- Uses `BatteryManager.EXTRA_TEMPERATURE` — this is the only battery temperature value available without root access
- If battery temperature exceeds 43°C, switches GPS polling to slow mode regardless of FSM state
- Sends a notification: "Phone temperature high — GPS polling reduced to save battery"
- This is a simple, honest feature. It does not claim to access bus temperatures or hardware thermal sensors.

**GPS accuracy vs battery tradeoff settings (user-configurable):**
- High accuracy: best results, higher battery drain
- Balanced: default setting
- Battery saver: lower GPS frequency, slightly less accurate distance calculations

---

## 3. Features Removed and Why

| Feature | Why Removed |
|---|---|
| Accessibility Service screen scraper | Play Store removal risk, ToS violation on all target platforms, breaks on every app UI update |
| "Battery bus temperature" governor | Does not exist on unrooted Android. `BatteryManager.EXTRA_TEMPERATURE` is the only available battery thermal reading |
| Zero-touch earnings parsing via UI scraping | Replaced by notification parsing which is stable, approved, and does not violate ToS |

---

## 4. Technology Stack

| Layer | Technology | Reason |
|---|---|---|
| Language | Kotlin 2.x + Coroutines | Standard for Android, good async support |
| UI | Jetpack Compose | Declarative, less boilerplate than XML views |
| Database | Room (SQLite wrapper) | Offline-first, no internet needed for core function |
| Location | Fused Location Provider API | Better battery efficiency than raw GPS |
| Background | Foreground Service + WakeLock | Only reliable way to keep tracking alive on Android |
| Sensors | SensorManager (Accelerometer) | For crash detection |
| Preferences | DataStore | For anchor coordinates and user settings |
| PDF export | Android PdfDocument API | Built-in, no library needed |
| Charts | MPAndroidChart or Compose Charts | For earnings visualizations |
| Notifications | NotificationListenerService | For auto-tagging orders to platforms |
| Maps | Google Maps Compose | For trip polyline display |

---

## 5. Directory Structure

```
app/src/main/java/com/gigrun/
│
├── core/
│   ├── di/                         # Dependency injection (Hilt modules)
│   ├── utils/
│   │   ├── HaversineCalculator.kt  # Distance math
│   │   ├── NotificationParser.kt   # Regex-based fare extraction
│   │   └── PdfExporter.kt          # Report generation
│
├── data/
│   ├── database/
│   │   ├── AppDatabase.kt
│   │   ├── entities/
│   │   │   ├── Trip.kt
│   │   │   ├── Shift.kt
│   │   │   ├── Earning.kt
│   │   │   └── ServiceReminder.kt
│   │   └── dao/
│   │       ├── TripDao.kt
│   │       ├── ShiftDao.kt
│   │       └── EarningDao.kt
│   ├── preferences/
│   │   └── UserPreferences.kt      # Anchors, vehicle settings, fuel cost
│   └── repository/
│       ├── TripRepository.kt
│       ├── EarningsRepository.kt
│       └── MaintenanceRepository.kt
│
├── service/
│   ├── LocationTrackingService.kt  # Main foreground service + FSM engine
│   ├── NotificationScanner.kt      # NotificationListenerService implementation
│   ├── CrashDetectionService.kt    # Accelerometer + GPS velocity monitor
│   └── MaintenanceAlertWorker.kt   # Daily WorkManager job for service reminders
│
└── presentation/
    ├── dashboard/
    │   ├── DashboardScreen.kt      # ₹/hour, shift summary, break-even
    │   └── DashboardViewModel.kt
    ├── platforms/
    │   ├── PlatformCompareScreen.kt
    │   └── PlatformViewModel.kt
    ├── trips/
    │   ├── TripListScreen.kt
    │   ├── TripDetailScreen.kt     # Map + timeline for a single trip
    │   └── TripViewModel.kt
    ├── maintenance/
    │   ├── MaintenanceScreen.kt
    │   └── MaintenanceViewModel.kt
    ├── settings/
    │   ├── SettingsScreen.kt       # Anchors, fuel cost, vehicle, emergency contacts
    │   └── CrashDetectionSetup.kt
    └── components/
        ├── EarningsCard.kt
        ├── PlatformBadge.kt
        ├── TripPolylineMap.kt
        └── BreakEvenMeter.kt
```

---

## 6. Database Schema

### Table: shifts
```
id              INTEGER PRIMARY KEY
start_time      TIMESTAMP
end_time        TIMESTAMP (nullable — null if shift still active)
fuel_cost_inr   REAL (nullable — entered by user at end of shift)
notes           TEXT (nullable)
```

### Table: trips
```
id              INTEGER PRIMARY KEY
shift_id        INTEGER FOREIGN KEY → shifts.id
platform        TEXT ('rapido' | 'zepto' | 'blinkit' | 'untagged')
start_time      TIMESTAMP
end_time        TIMESTAMP
start_lat       REAL
start_lon       REAL
end_lat         REAL
end_lon         REAL
distance_km     REAL
wait_time_sec   INTEGER (time spent at store before this trip)
path_encoded    TEXT (compressed polyline string)
earning_inr     REAL (nullable)
earning_raw_notif TEXT (original notification text, for debugging)
```

### Table: earnings
```
id              INTEGER PRIMARY KEY
trip_id         INTEGER FOREIGN KEY → trips.id
amount_inr      REAL
source          TEXT ('notification' | 'manual')
timestamp       TIMESTAMP
platform        TEXT
```

### Table: service_reminders
```
id              INTEGER PRIMARY KEY
vehicle_name    TEXT
reminder_type   TEXT ('oil' | 'air_filter' | 'chain' | 'general' | 'tyre')
last_done_km    REAL
last_done_date  TIMESTAMP
interval_km     REAL
interval_days   INTEGER
is_snoozed      INTEGER (0 or 1)
snooze_until    TIMESTAMP (nullable)
```

---

## 7. Build Stages — Realistic Timeline

Assumes 8–10 hours of focused coding per week. No all-nighters, no skipping classes.

---

### Stage 1 — Foundation (Weeks 1 to 3)

**Goal:** App runs, saves data, basic UI works. No automation yet.

Tasks:
- Set up project with Hilt dependency injection
- Build Room database with all tables and DAOs from the schema above
- Build Settings screen: save home anchor, store anchor, college anchor via map tap
- Build basic Foreground Service that logs GPS coordinates to the database every 5 seconds
- Build a minimal Dashboard screen showing hardcoded placeholder values
- Verify GPS tracking works in background while phone is locked

**Deliverable:** You can open the app, save your locations, start a shift, and see raw GPS logs in a debug view.

---

### Stage 2 — FSM Engine and Trip Detection (Weeks 4 to 6)

**Goal:** App automatically detects trips and logs them correctly.

Tasks:
- Implement Haversine calculator as a utility class with unit tests
- Build the FSM state machine inside LocationTrackingService
- Handle all state transitions: IDLE → COMMUTE → WAITING → DELIVERING → COMPLETE
- Record trip start/end times and wait time at store
- Compress GPS path into polyline and store it
- Build Trip List screen showing all logged trips
- Build Trip Detail screen with Google Maps polyline overlay

**Deliverable:** Ride your actual Zepto or Rapido shift. Open the app afterwards. Every trip is logged with correct start/end times, distance, and wait time at each hub.

**Test this hard before moving to Stage 3.** The FSM is the backbone of everything else. If trip detection is wrong, all your earnings data will be wrong.

---

### Stage 3 — Earnings and Dashboard (Weeks 7 to 9)

**Goal:** The app shows real, useful financial data.

Tasks:
- Implement NotificationListenerService
- Write regex parsers for Rapido, Zepto, and Blinkit notification formats
- Log parsed earning amounts against the active trip
- Add manual earning entry fallback (for when parsing fails)
- Build the main Dashboard with: net ₹/hour, shift time, wait time, trips today
- Build Break-Even Calculator screen with fuel cost input
- Build Platform Comparison screen with weekly table

**Deliverable:** After a full day of multi-app riding, open GigRun and see your real ₹/hour per platform. This is the milestone where the app becomes genuinely useful.

---

### Stage 4 — Maintenance, Crash Detection, Export (Weeks 10 to 13)

**Goal:** Safety features and data portability.

Tasks:
- Build Vehicle Maintenance screen with service type configuration
- Implement MaintenanceAlertWorker using WorkManager (runs daily, checks thresholds)
- Implement Crash Detection: accelerometer + GPS velocity + countdown UI + SMS dispatch
- Add crash detection settings: emergency contacts, G-force threshold, enable/disable toggle
- Build weekly/monthly Shift Report generator using PdfDocument API
- Add share button to export report via Android share sheet

**Deliverable:** Full app is functional across all planned features.

---

### Stage 5 — Polish, Testing, Play Store Preparation (Weeks 14 to 16)

**Goal:** App is stable enough to use daily and potentially distribute.

Tasks:
- Field test across 5+ full delivery shifts, logging all bugs
- Add edge case handling: what if GPS is unavailable, what if notification is not parseable, what if shift is never ended
- Battery usage audit — check background drain over a 6-hour shift
- Write Play Store listing: justify NotificationListenerService usage clearly in the data safety section
- Create onboarding flow for first-time setup (anchor configuration, vehicle setup, emergency contacts)
- Add data backup: export all data to JSON for manual backup

---

## 8. Permissions Required

```xml
<!-- Location — core function -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION"/>

<!-- Foreground service — background tracking -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION"/>

<!-- WakeLock — prevent sleep during active shift -->
<uses-permission android:name="android.permission.WAKE_LOCK"/>

<!-- Notifications — send maintenance and crash alerts -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>

<!-- SMS — crash detection emergency dispatch only -->
<uses-permission android:name="android.permission.SEND_SMS"/>

<!-- Notification access — declared separately in device settings, not manifest -->
<!-- User must enable GigRun in Settings > Notification Access manually -->
<!-- No manifest permission for this — it is a special permission -->
```

---

## 9. Key Risks and How to Handle Them

**Risk 1: Notification parsing breaks when platforms update their apps.**

Handle this by storing the raw notification text alongside the parsed amount. When parsing fails, log it clearly and show a manual entry prompt. Review failed parses periodically to update your regex patterns. This will require maintenance over time — accept it.

**Risk 2: Background GPS tracking is killed by battery optimization on some Android skins (MIUI, One UI, ColorOS).**

Handle this with an onboarding screen that deep-links to battery optimization settings for the specific device. There are open-source libraries (like AutoStarter) that detect the device skin and show the correct settings path. Include this in Stage 5.

**Risk 3: Play Store review rejects NotificationListenerService.**

Write a detailed Play Store data safety declaration explaining exactly what notifications are read, what data is extracted, and that it is never shared externally. Apps like Splitwise and CRED use the same API for UPI payment parsing and have passed review.

**Risk 4: FSM enters a wrong state due to GPS inaccuracy in dense urban areas.**

Handle this with a confidence threshold — only trigger state transitions if the Haversine distance is consistently below the anchor radius for 15 seconds (3 consecutive 5-second GPS readings), not just a single reading. This prevents a momentary GPS jump from wrongly changing state.

---

## 10. What This App Does NOT Do

Being explicit about this prevents scope creep.

- It does not scrape any app's UI
- It does not access any hardware sensor beyond accelerometer and standard GPS
- It does not require an internet connection for core tracking (Maps display requires connectivity but tracking does not)
- It does not predict mechanical failure or engine health
- It does not offer route optimization or navigation
- It is not a financial advisor and does not file taxes
- It does not store data on any server — everything stays on the device

---

*Document version: 2.0 — Revised from original V1 spec*
*Estimated total build time: 14–16 weeks at 8–10 hours/week*
*Target platform: Android 9.0 (API 28) and above*
