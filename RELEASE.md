# EXIF Pure v1.4.2

## What's New

### Hardened Export & Share Handling
The export and share pipeline has been hardened against edge cases that could cause crashes or leaks:
- Sharing photos to EXIF Pure from other apps no longer crashes on malformed share intents
- Exported filenames are properly sanitized to prevent path traversal
- Shared image URIs are handled more safely, preventing potential metadata leakage
- The export screen now remembers your chosen strip mode after rotating the device

### Stronger Metadata Stripping
The lossless JPEG and PNG strippers now reject malformed images instead of producing corrupt output:
- Better handling of truncated or corrupted image segments
- Improved EOF detection during strip operations
- GPS-only strip mode is now covered by dedicated unit tests for both JPEG and PNG

### Misc
- Random filenames for exported images now use more entropy, making collisions extremely unlikely
- Documentation and code comments cleaned up across the stripper modules

## SHA-256 Checksums

```
2fb64b93daf26dc1b64e0e45191a993ffc7f054066025b1257904c7f2d55d628  app-release.apk
```

## Installation

Download `app-release.apk` below, verify the SHA-256, then install:

```bash
adb install app-release.apk
```

Or add `https://github.com/x144k/ExifPure` to [Obtainium](https://github.com/ImranR98/Obtainium) for automatic update tracking.
