# Release Process Guide

This document describes how to build, sign, and release Beidou Satellite Messenger.

## 📋 Prerequisites

### Local Development
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17+
- Android SDK 34 (API 34)
- Git
- OpenSSL (for keystore generation)

### GitHub Repository Secrets
Configure these in **Settings → Secrets and variables → Actions**:

| Secret | Description | Required |
|--------|-------------|----------|
| `KEYSTORE_BASE64` | Base64-encoded release.keystore file | ✅ For releases |
| `KEYSTORE_PASSWORD` | Keystore store password | ✅ For releases |
| `KEY_ALIAS` | Key alias (e.g., `beidou-satellite-key`) | ✅ For releases |
| `KEY_PASSWORD` | Key password | ✅ For releases |
| `PLAY_SERVICE_ACCOUNT_JSON` | Google Play Service Account JSON | 🔸 For Play Store |
| `SLACK_WEBHOOK_URL` | Slack webhook for notifications | 🔸 Optional |

## 🔐 Keystore Setup (One-time)

### Generate Keystore
```bash
chmod +x scripts/generate-keystore.sh
./scripts/generate-keystore.sh
```

**Output example:**
```
File: release.keystore
Alias: beidou-satellite-key
Key Password: xK9mP2qR5vB8nL3s
Store Password: aB4cD7eF9gH2jK5m
```

### Add to GitHub Secrets
Go to **Settings → Secrets and variables → Actions → New repository secret**:

| Name | Value |
|------|-------|
| `KEYSTORE_BASE64` | Run `base64 -w 0 release.keystore` and paste output |
| `KEYSTORE_PASSWORD` | `aB4cD7eF9gH2jK5m` |
| `KEY_ALIAS` | `beidou-satellite-key` |
| `KEY_PASSWORD` | `xK9mP2qR5vB8nL3s` |

### Backup Keystore
```bash
# Store securely (encrypted USB, password manager, etc.)
cp release.keystore ~/secure-backup/
# NEVER commit to git!
```

## 🚀 Creating a Release

### Automated (Recommended)

```bash
# Patch release (1.0.0 -> 1.0.1)
./scripts/release.sh patch

# Minor release (1.0.0 -> 1.1.0)
./scripts/release.sh minor

# Major release (1.0.0 -> 2.0.0)
./scripts/release.sh major

# Dry run to preview changes
./scripts/release.sh patch --dry-run
```

This will:
1. Bump version in `app/build.gradle.kts`
2. Update `CHANGELOG.md`
3. Commit changes
4. Create annotated tag `vX.Y.Z`
5. Push to trigger CI/CD

### Manual Process
```bash
# 1. Update version manually in app/build.gradle.kts
# versionName = "1.2.3"
# versionCode = 123

# 2. Update CHANGELOG.md

# 3. Commit and tag
git add app/build.gradle.kts CHANGELOG.md
git commit -m "chore: release v1.2.3"
git tag -a "v1.2.3" -m "Release v1.2.3"

# 4. Push to trigger workflow
git push origin main --tags
```

## 🤖 CI/CD Pipeline

The GitHub Actions workflow (`.github/workflows/ci-cd.yml`) runs on:
- **Push to main/develop**: Lint + Tests + Debug APK
- **Tags (v*)**: Full release pipeline
- **Manual dispatch**: On-demand builds

### Pipeline Stages

```
┌─────────────┐   ┌─────────────┐   ┌─────────────────┐   ┌────────────────┐
│   LINT      │──▶│   TEST      │──▶│  BUILD DEBUG    │   ┌────────────┐
│  (30 min)   │   │  (30 min)   │   │     APK         │   │ BUILD REL  │
└─────────────┘   └─────────────┘   └─────────────────┘   │  (60 min)  │
                                                          └─────┬──────┘
                                                                │
                                                          ┌─────▼──────┐
                                                          │  RELEASE   │
                                                          │  (15 min)  │
                                                          └────────────┘
```

### Artifacts Produced

| Artifact | Location | Retention |
|----------|----------|-----------|
| Debug APK | `app/build/outputs/apk/debug/` | 30 days |
| Release AAB | `app/build/outputs/bundle/release/` | 90 days |
| Release APK | `app/build/outputs/apk/release/` | 90 days |
| Mapping File | `app/build/outputs/mapping/release/` | 90 days |
| Lint Report | `app/build/reports/lint-results*.html` | 7 days |
| Test Reports | `app/build/reports/tests/` | 7 days |

### GitHub Release

On tag push, automatically creates:
- GitHub Release with changelog
- Uploads AAB, APK, mapping.txt
- Marks as pre-release for alpha/beta/rc tags

## 📱 Distribution

### Direct APK Install
1. Download `app-release.apk` from GitHub Release
2. Enable "Install unknown apps" on device
3. Install APK
4. Grant permissions: Location, SMS, Phone, Sensors, Storage

### Google Play Store (Optional)
Configure `PLAY_SERVICE_ACCOUNT_JSON` secret for automatic upload to Internal Testing track.

### F-Droid / Alternative Stores
Upload AAB or APK to your preferred store.

## 🔍 Verification

### Verify Signed APK
```bash
# Check signature
apksigner verify --print-certs app-release.apk

# Or with jarsigner
jarsigner -verify -verbose -certs app-release.apk
```

### Verify Signature on Device
```bash
# On device via ADB
adb shell pm list packages -U | grep beidousatellite
```

## 📝 Versioning Scheme

We follow **Semantic Versioning** (MAJOR.MINOR.PATCH):

| Type | Example | When |
|------|---------|------|
| **MAJOR** | 1.0.0 → 2.0.0 | Breaking API changes, major architecture rewrite |
| **MINOR** | 1.0.0 → 1.1.0 | New features, backward compatible |
| **PATCH** | 1.0.0 → 1.0.1 | Bug fixes, backward compatible |

**Version Code**: Integer, increments by 1 each release (required by Play Store).

## 🛠️ Troubleshooting

### Build Fails: Keystore Not Found
```
Error: Keystore file not found for signing config 'release'
```
**Fix**: Ensure `KEYSTORE_BASE64` secret is set correctly.

### Build Fails: Wrong Password
```
Error: Failed to read key from store
```
**Fix**: Verify `KEYSTORE_PASSWORD` and `KEY_PASSWORD` match keystore.

### Version Code Conflict
```
Error: Version code 123 has already been used
```
**Fix**: Increment `versionCode` in `app/build.gradle.kts`.

### Tests Fail
Check test reports in Actions artifacts. Common issues:
- Missing permissions in test manifest
- Hilt test setup missing
- Coroutine test dispatcher issues

## 📊 Monitoring

### Crash Reporting
Crashes are logged to SD card:
```
/sdcard/Android/data/com.huawei.beidousatellite/files/Documents/BeidouSatellite/Logs/crash/
```

### Performance Logs
```
/sdcard/Android/data/com.huawei.beidousatellite/files/Documents/BeidouSatellite/Logs/performance/
```

### Network Logs
```
/sdcard/Android/data/com.huawei.beidousatellite/files/Documents/BeidouSatellite/Logs/network/
```

## 🔄 Rollback Process

If a release has critical issues:

1. **Revert tag locally**:
   ```bash
   git tag -d v1.2.3
   git push origin :refs/tags/v1.2.3
   ```

2. **Delete GitHub Release** (if created)

3. **Fix issues** and create new patch release

4. **Force push** if needed (coordinate with team):
   ```bash
   git push origin main --force-with-lease
   ```

## 📞 Support

- **Issues**: GitHub Issues
- **CI/CD Logs**: GitHub Actions tab
- **Crash Reports**: Check device logs folder
- **Performance**: Review performance logs

---

*Last updated: 2024*