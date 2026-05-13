# EXIF Pure v1.4.1

## What's New

### Secure Photo Deletion
Deleting photos now works correctly on Android 10 and later. The app uses the modern MediaStore deletion API, which shows a system confirmation dialog so you stay in control. Previously, deletion silently failed with a "Deleted 0 photos" toast on devices running scoped storage.

### Smarter Lock Screen
The biometric lock no longer re-prompts you after system dialogs (like the deletion confirmation). A short grace period distinguishes genuine backgrounding from transient overlays, so you only authenticate when it actually matters.

### Lock Hardening
The biometric prompt has been hardened across the board:
- Switches to BIOMETRIC_STRONG for stronger authentication
- Adds dedicated handling for lockout, unenrolled biometrics, and missing hardware
- Prevents saved-state crashes when the activity is finishing
- Cancels stale prompts on timeout or screen disposal
- Fixes lock bypass on API < 28 and during split-screen use

## Improvements

- **Security:** Background detection moved to ON_PAUSE with state save/restore for reliability
- **UX:** Lock screen text now uses theme colors for proper contrast on light dynamic themes
- **UX:** All lock screen strings are now localizable
- **Dependencies:** AndroidX, Compose, Coil, and ExifInterface updated to current stable versions

## SHA-256 Checksums

```
bffe65271820ab27c2b6d7a530abddd6d6551f4bf7012f728735734d565ab8f5  app-release.apk
```

## Installation

Download `app-release.apk` below, verify the SHA-256, then install:

```bash
adb install app-release.apk
```

Or add `https://github.com/x144k/ExifPure` to [Obtainium](https://github.com/ImranR98/Obtainium) for automatic update tracking.
