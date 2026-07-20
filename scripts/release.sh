#!/bin/bash
# Release management script for Beidou Satellite Messenger
# Usage: ./scripts/release.sh [patch|minor|major] [--dry-run]

set -e

DRY_RUN=false
RELEASE_TYPE="${1:-patch}"

# Parse arguments
for arg in "$@"; do
    case $arg in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        patch|minor|major)
            RELEASE_TYPE=$arg
            shift
            ;;
    esac
done

echo "========================================"
echo "Beidou Satellite Messenger Release"
echo "========================================"
echo "Release type: $RELEASE_TYPE"
echo "Dry run: $DRY_RUN"
echo ""

# Get current version from build.gradle.kts
CURRENT_VERSION=$(grep -E "versionName\s*=" app/build.gradle.kts | sed -E 's/.*"([^"]+)".*/\1/')
CURRENT_CODE=$(grep -E "versionCode\s*=" app/build.gradle.kts | sed -E 's/.*=\s*([0-9]+).*/\1/')

echo "Current version: $CURRENT_VERSION (code: $CURRENT_CODE)"

# Calculate new version
IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VERSION"
case $RELEASE_TYPE in
    major)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        ;;
    minor)
        MINOR=$((MINOR + 1))
        PATCH=0
        ;;
    patch)
        PATCH=$((PATCH + 1))
        ;;
    *)
        echo "Invalid release type: $RELEASE_TYPE"
        echo "Usage: $0 [patch|minor|major] [--dry-run]"
        exit 1
        ;;
esac

NEW_VERSION="$MAJOR.$MINOR.$PATCH"
NEW_CODE=$((CURRENT_CODE + 1))

echo "New version: $NEW_VERSION (code: $NEW_CODE)"

if [ "$DRY_RUN" = true ]; then
    echo ""
    echo "[DRY RUN] Would update version to $NEW_VERSION ($NEW_CODE)"
    exit 0
fi

# Update version in build.gradle.kts
sed -i.bak -E "s/versionName\s*=\s*\".*\"/versionName = \"$NEW_VERSION\"/" app/build.gradle.kts
sed -i.bak -E "s/versionCode\s*=\s*[0-9]+/versionCode = $NEW_CODE/" app/build.gradle.kts

echo "Updated app/build.gradle.kts"

# Create changelog entry
DATE=$(date '+%Y-%m-%d')
CHANGELOG_ENTRY="## [$NEW_VERSION] - $DATE\n\n### Changed\n- Version bump to $NEW_VERSION\n\n"

# Prepend to CHANGELOG.md if exists
if [ -f CHANGELOG.md ]; then
    sed -i "1i $CHANGELOG_ENTRY" CHANGELOG.md
else
    echo -e "# Changelog\n\n$CHANGELOG_ENTRY" > CHANGELOG.md
fi

echo "Updated CHANGELOG.md"

# Commit changes
git add app/build.gradle.kts CHANGELOG.md
git commit -m "chore: release v$NEW_VERSION"

# Create tag
git tag -a "v$NEW_VERSION" -m "Release v$NEW_VERSION"

echo ""
echo "========================================"
echo "Release v$NEW_VERSION prepared!"
echo "========================================"
echo ""
echo "To push and trigger CI/CD:"
echo "  git push origin main --tags"
echo ""
echo "This will:"
echo "  1. Run all tests and linting"
echo "  2. Build debug APK"
echo "  3. Build signed release AAB/APK"
echo "  4. Create GitHub Release with artifacts"
echo "  5. (Optional) Upload to Play Store Internal Testing"