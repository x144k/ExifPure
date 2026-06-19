# EXIF Pure v1.4.3

## What's New

### Faster, More Reliable Startup
The app now initializes its encrypted storage in the background, so the splash screen and first launch feel snappier. If the system kills the app while it is in the background, the biometric lock now correctly measures the real time you were away instead of immediately asking again.

### Smoother Gallery
Sorting, filtering, and searching no longer run on the main thread, so the gallery stays responsive even with thousands of photos. Your selection now survives a full app restart or swipe-from-recents, and transient MediaStore errors are surfaced instead of showing an empty gallery.

### Safer, More Efficient Exports
Large batch exports are processed in chunks so memory use stays bounded. WEBP and HEIF fallback exports now preserve the original image orientation, and the export pipeline uses fewer redundant file operations.

### Tidier Internals
Repository methods now consistently perform storage and MediaStore work on background threads. Share-intent handling requests only the columns it actually needs and times out after 10 seconds to avoid hanging on misbehaving providers.

## SHA-256 Checksums

```
eaaa04184fcd174c6da851a73480e03e4e3378c8e7c65211647a1863b6e42057  app-release.apk
```

## Installation

Download `app-release.apk` below, verify the SHA-256, then install:

```bash
adb install app-release.apk
```

Or add `https://github.com/x144k/ExifPure` to [Obtainium](https://github.com/ImranR98/Obtainium) for automatic update tracking.
