# EXIF Pure v1.4.0

## What's New

### Silent Share Mode
A new setting in **Export Behavior** lets you process single shared images instantly, without ever opening the app UI. When enabled, sharing one photo to EXIF Pure from any app automatically strips its metadata and launches the system share chooser with the clean copy ready to send. For multiple images or when the app lock is active, the full share UI still appears so you can review and confirm.

## Improvements

- **Security:** ShareActivity now blocks screenshots with `FLAG_SECURE`
- **Security:** Lock screen re-engages automatically when the app resumes
- **Reliability:** Share intents now handle `ClipData` from modern apps (Chrome, Files, Google Photos)
- **Reliability:** Content URIs are validated and non-image MIME types are filtered before processing
- **UX:** Settings version label now reads dynamically from `BuildConfig.VERSION_NAME`
- **UX:** All user-facing strings in the share flow and lock screen are now localizable

## SHA-256 Checksums

```
af8dd3c949c55e186a1f103c83588ddf0c9a9d71befea65c75b5f36ace4c0b8e  app-release.apk
```

## Installation

Download `app-release.apk` below, verify the SHA-256, then install:

```bash
adb install app-release.apk
```

Or add `https://github.com/x144k/ExifPure` to [Obtainium](https://github.com/ImranR98/Obtainium) for automatic update tracking.
