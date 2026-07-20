# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial project structure with MVVM + Hilt architecture
- Region bypass manager with multiple strategies (Software Spoof, HMS Reflection, ADB, Magisk, Test Mode)
- HMS SMC service connection manager with Messenger/IPC
- Satellite search and tracking with CompassView
- Emergency SOS with countdown and location sharing
- Message history with local Room database
- Compass calibration with figure-8 detection
- Comprehensive logging to SD card (general, network, sensor, message, HMS, error, crash, performance)
- Material Design 3 UI with dark/light theme
- GitHub Actions CI/CD pipeline

### Changed
- N/A

### Deprecated
- N/A

### Removed
- N/A

### Fixed
- N/A

### Security
- N/A

---

## [1.0.0] - 2024-XX-XX

### Added
- Initial release
- BeiDou satellite messaging via HMS Core
- Region bypass for global access
- Emergency SOS via satellite
- Message history with SQLite (Room)
- Compass calibration
- SD card logging
- Test mode for development

---

## Template for Future Releases

### [X.Y.Z] - YYYY-MM-DD

#### Added
- New features

#### Changed
- Changes in existing functionality

#### Deprecated
- Soon-to-be removed features

#### Removed
- Removed features

#### Fixed
- Bug fixes

#### Security
- Security improvements