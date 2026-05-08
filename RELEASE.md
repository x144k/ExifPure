# EXIF Pure v1.3.0

## What's New

### Share Intent Processing
Share photos directly from any app to EXIF Pure. When you tap **Share** in your camera roll, gallery, or messaging app, EXIF Pure appears as a destination. Shared images are automatically processed, metadata is stripped using your default strip mode, and a clean copy is saved to your configured output directory. Supports single images and batch sharing.

### Photo Detail View
A fully redesigned detail screen replaces the basic viewer:
- **Full-screen image preview** with pinch-to-zoom support
- **Complete EXIF metadata table** showing camera make/model, lens, exposure, ISO, focal length, GPS coordinates, and orientation
- **Strip mode selector** - choose *All Metadata* or *GPS Only* before exporting
- **One-tap export** - save a clean copy instantly
- **Before / After comparison card** - see exactly what metadata was removed

### Unit Test Coverage
Added the project's first unit tests for core algorithmic logic:
- `JpegStripperTest` - 9 test cases covering segment removal, passthrough, entropy preservation, and multi-segment handling
- `FilenameGeneratorTest` - 4 test cases for timestamped filename generation

## Improvements

- **Bug fix:** Fixed main-thread deletion ANR when removing photos from the gallery
- **Bug fix:** Added Android 14 `READ_MEDIA_VISUAL_USER_SELECTED` permission support
- **Bug fix:** `FallbackStripper` now guards against OOM on extremely large images and cleans up temp files on crash
- **Build:** Bumped Compose Compiler plugin to 2.2.21 to fix CLI builds with AGP 9.2.1

## SHA-256 Checksums

```
40212128fe2f43fcbf562229e6f49ce2410d04b2c300dbd58ea1a36ff9f04fc0  app-release.apk
```

## Installation

Download `app-release.apk` below, verify the SHA-256, then install:

```bash
adb install app-release.apk
```

Or add `https://github.com/x144k/ExifPure` to [Obtainium](https://github.com/ImranR98/Obtainium) for automatic update tracking.
