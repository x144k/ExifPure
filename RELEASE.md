# EXIF Pure v1.1.0

## What's New

### Folder / Album View
Photos are now grouped by album (bucket) instead of a single flat grid. The gallery shows your albums first-tap one to drill down into its contents. Navigate back up with the in-app back button or gesture.

### First Functional Release
This is the first APK release containing the full EXIF Pure application. All core privacy features are now available:

- **Gallery Browser** - Browse all device photos via MediaStore with scoped-storage compliance on Android 10+.
- **EXIF Inspector** - View camera make/model, lens, exposure, ISO, focal length, GPS coordinates, and orientation.
- **Lossless Metadata Stripper** - JPEG and PNG are rewritten segment-by-segment with zero re-compression. WEBP/HEIF fall back to high-quality re-encode.
- **Strip Modes** - Choose *All Metadata* to remove everything, or *GPS Only* to preserve camera info while stripping location data.
- **Batch Export** - Select multiple photos and share clean copies in one action.
- **Favorites & Search** - Star photos, filter by favorites, and search by filename or album name.
- **Sort & Grid Density** - Sort by date, name, or size. Toggle small / medium / large grid thumbnails.
- **Biometric Lock** - Require fingerprint or face unlock to open the app. Re-locks automatically when backgrounded.
- **Encrypted Preferences** - Settings, favorites, and export history stored with AES-256 SIV/GCM via `EncryptedSharedPreferences`.
- **Export History** - Log of every clean copy exported, with timestamp and strip mode used.
- **Edge-to-Edge UI** - Material 3 design with full edge-to-edge layout and dynamic color support on Android 12+.

## Improvements

- **Documentation** - Added comprehensive KDoc for the lossless JPEG/PNG metadata strippers, including segment layout tables and state machine explanations.
- **Documentation** - Added KDoc for Gallery and Detail screen ViewModels, documenting state flow architecture and selection mode lifecycle.
- **Docs & URLs** - Fixed GitHub username references across README and installation URLs (`pureframe` → `x144k`).
- **Build** - Upgraded Android Gradle Plugin and Kotlin toolchain for improved build performance and compatibility.

## SHA-256 Checksums

```
98fea067db81594e66434d658e468d6b1108dc9e2dde557d5c24b614ea8f93e2  app-release.apk
```

## Installation

Download `app-release.apk` below, verify the SHA-256, then install:

```bash
adb install app-release.apk
```

Or add `https://github.com/x144k/ExifPure` to [Obtainium](https://github.com/ImranR98/Obtainium) for automatic update tracking.
