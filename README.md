<div align="center">
  <img src="https://raw.githubusercontent.com/Chatur7x/GigRun/main/assets/icon.png" alt="GigRun Logo" width="120" height="120" style="border-radius: 20px;">
  <h1>GigRun (V2)</h1>
  <p><strong>The Intelligent Rider Ecosystem</strong></p>
  <p>An offline-first, background automation utility engineered for multi-apping gig workers and commuting students. It automatically runs mileage calculations, income scraping, context state switching, and vehicle health profiling with zero manual interaction required during a shift.</p>
</div>

<hr/>

## ✨ Features

- **🔋 Zero-Interaction Tracking:** Fully automated background tracking that doesn't drain your battery.
- **💰 Smart Income Scraping:** Automatically reads notification data to log earnings across Blinkit, Zepto, Rapido, Uber, and more.
- **🗺️ GPS & Polyline Compression:** Efficiently maps out trips using the Google Encoded Polyline Algorithm to save database space.
- **📊 Advanced Analytics:** Generates real-time net-hourly calculations subtracting accurate local fuel costs.
- **📄 Shift PDF Reports:** Automatically generate detailed PDF shift reports directly from the device.
- **💥 Crash Detection:** Intelligent accelerometer-based anomaly detection to trigger SOS mechanisms in an emergency, with a countdown to prevent false positives.
- **🌐 Offline-First:** Fully functional without an internet connection using Room Database and DataStore.

## 🛠️ Architecture & Tech Stack

Built on modern Android development standards focusing on performance, reliability, and background execution:

- **Language:** Kotlin
- **UI Toolkit:** Jetpack Compose (Material 3)
- **Dependency Injection:** Hilt (Dagger)
- **Local Persistence:** Room Database & Jetpack DataStore
- **Background Processing:** Foreground Services & FusedLocationProviderClient
- **Navigation:** Jetpack Navigation Compose
- **Architecture Pattern:** MVVM (Model-View-ViewModel)

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17+
- Android SDK (API 34+)

### Building the App
1. Clone the repository:
   ```bash
   git clone https://github.com/Chatur7x/GigRun.git
   ```
2. Open the `app-root` folder in Android Studio.
3. Sync the project with Gradle files.
4. Build and run the app on an emulator or physical device.

## 📱 Screenshots

*(Add screenshots here)*

## 🤝 Contributing

Contributions are always welcome! Feel free to open an issue or submit a pull request if you have any ideas, bug reports, or feature requests.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---
<div align="center">
  <p>Built with ❤️ for Gig Workers</p>
</div>
