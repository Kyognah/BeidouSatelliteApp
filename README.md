# Beidou Satellite Messenger

A standalone Android application that replicates and extends MeeTime's BeiDou-3 satellite messaging functionality, with **full region bypass** to enable satellite features worldwide.

## 🎯 Features

- **BeiDou-3 Satellite Messaging**: Send/receive messages via BeiDou Navigation Satellite System (BDS-3)
- **Region Bypass**: Works outside Chinese Mainland without Huawei ID region restrictions
- **Emergency SOS**: One-tap emergency alerts with location via satellite
- **Satellite Search & Tracking**: Real-time compass with satellite direction guidance
- **Message History**: Local database with sync capabilities
- **Compass Calibration**: Built-in sensor calibration for accurate pointing
- **Test Mode**: Simulated satellite connection for development/testing
- **Modern UI**: Material Design 3 with dark/light theme support

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                              │
│  Activities • Fragments • ViewModels • Compose (planned)    │
├─────────────────────────────────────────────────────────────┤
│                      Domain Layer                            │
│  Repository • Use Cases • Models                            │
├─────────────────────────────────────────────────────────────┤
│                       Data Layer                             │
│  Room DB • DataStore • HMS SMC Service • Sensors            │
├─────────────────────────────────────────────────────────────┤
│                    Platform Layer                            │
│  Android SDK • HMS Core • BeiDou Hardware                   │
└─────────────────────────────────────────────────────────────┘
```

### Key Components

| Component | Purpose |
|-----------|---------|
| `RegionBypassManager` | Detects and spoofs region for satellite access |
| `HmsSmcManager` | Manages HMS Core SMC service connection |
| `SatelliteRepository` | Local data persistence (Room) |
| `CompassView` | Custom view for satellite direction guidance |
| `SatelliteLogger` | Centralized logging (logcat + file) |

## 🔓 Region Bypass Methods

The app implements multiple bypass strategies (in order of preference):

1. **Software Spoof** (Default): Modifies app-internal region detection via `Locale` and `SharedPreferences`
2. **HMS Reflection** (Root): Attempts to override HMS Core region via Java reflection
3. **ADB Settings**: Executes `settings put global huawei_id_region CN` via shell
4. **Test Mode**: Simulates satellite connection without hardware

```kotlin
// Enable bypass
regionManager.setBypassEnabled(true, BypassMethod.SOFTWARE_SPOOF)

// Check if satellite features are available
val supported = regionManager.isSatelliteSupported()
```

## 📱 Building & Running

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17+
- Android SDK 34
- Device with HMS Core (or Test Mode)

### Build
```bash
./gradlew assembleDebug
```

### Install
```bash
./gradlew installDebug
```

### Run Tests
```bash
./gradlew test
```

## 📋 Dependencies

- **Kotlin**: 1.9.22
- **Hilt**: 2.48 (Dependency Injection)
- **Room**: 2.6.1 (Local Database)
- **DataStore**: 1.1.1 (Preferences)
- **Coroutines/Flow**: 1.8.0 (Async)
- **Navigation**: 2.7.7
- **WorkManager**: 2.9.0 (Background)
- **Moshi**: 1.15.1 (JSON)
- **Protobuf**: 3.25.3 (Message serialization)
- **Coil**: 2.6.0 (Images)
- **Material3**: 1.12.0 (UI)

## 🔧 Configuration

### HMS Core Setup
1. Add your HMS App ID in `AndroidManifest.xml`:
   ```xml
   <meta-data
       android:name="com.huawei.hms.client.appid"
       android:value="appid=YOUR_APP_ID" />
   ```

2. Enable SMC service in AppGallery Connect

### Region Bypass Settings
Configure in `SettingsActivity` or programmatically:
```kotlin
val regionManager = hiltEntryPoint.regionBypassManager()
regionManager.setBypassEnabled(true)
regionManager.setBypassMethod(BypassMethod.SOFTWARE_SPOOF)
```

## 📖 Usage

### Sending a Message
```kotlin
val message = SmcMessage(
    senderNumber = "+8613800000000",
    recipientNumber = "+8613900000000",
    content = "Hello via BeiDou!",
    priority = MessagePriority.NORMAL,
    location = currentLocation
)
hmsManager.sendMessage(message)
```

### Emergency SOS
```kotlin
val emergency = SmcMessage(
    senderNumber = myNumber,
    recipientNumber = "110", // Police
    content = buildEmergencyContent(location),
    priority = MessagePriority.EMERGENCY,
    messageType = MessageType.EMERGENCY_SOS,
    location = currentLocation
)
hmsManager.sendMessage(emergency)
```

### Satellite Search
```kotlin
// Start searching for satellite
hmsManager.startSatelliteSearch()

// Observe signal
hmsManager.signalInfo.collect { signal ->
    updateCompass(signal.azimuthDeg, signal.elevationDeg)
}
```

## 🧪 Test Mode

For development without hardware:
```kotlin
regionManager.setTestMode(true)
```

This simulates:
- HMS SMC service connection
- BeiDou satellite signals (random PRN 1-63)
- Direct send capability (searchMode = 2)
- Compass sensor data

## 📁 Project Structure

```
app/src/main/
├── java/com/huawei/beidousatellite/
│   ├── BeidouSatelliteApplication.kt
│   ├── data/
│   │   ├── hms/HmsSmcManager.kt
│   │   ├── region/RegionBypassManager.kt
│   │   ├── model/SatelliteModels.kt
│   │   └── repository/SatelliteRepository.kt
│   ├── ui/
│   │   ├── main/MainActivity.kt + HomeFragment.kt
│   │   ├── satellite/SatelliteSearchActivity.kt + CompassView.kt
│   │   ├── emergency/EmergencySosActivity.kt
│   │   ├── settings/SettingsActivity.kt
│   │   └── message/MessageHistoryActivity.kt
│   ├── di/AppModule.kt
│   └── util/SatelliteLogger.kt
├── res/
│   ├── layout/ (XML layouts)
│   ├── values/ (strings, colors, themes, dimen)
│   ├── navigation/ (Nav graphs)
│   ├── menu/ (Bottom nav, drawer, toolbar)
│   ├── xml/ (preferences, backup rules, file paths)
│   └── drawable/ (icons, backgrounds)
└── AndroidManifest.xml
```

## ⚠️ Important Notes

1. **Legal**: Region bypass may violate Huawei Terms of Service. Use at your own risk.
2. **Hardware**: Requires device with BeiDou-3 satellite messaging hardware (Huawei Pura 70, Mate 60 series, etc.)
3. **HMS Core**: Must be installed and updated on device
4. **Permissions**: Location, SMS, Phone, Sensors required
5. **Battery**: Satellite communication consumes significant battery

## 🛣️ Roadmap

- [ ] Jetpack Compose UI migration
- [ ] End-to-end encryption for messages
- [ ] Mesh networking via satellite
- [ ] Voice message support
- [ ] Image compression & sending
- [ ] Automatic retry with exponential backoff
- [ ] Multi-language support (i18n)
- [ ] Wear OS companion app

## 📄 License

MIT License - See LICENSE file for details

## 🙏 Acknowledgments

- MeeTime/华为畅连 for reverse engineering reference
- BeiDou Navigation Satellite System
- HMS Core documentation
- Material Design 3 guidelines# Trigger workflow
