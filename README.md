# EXIF Pure

> **Your photos. Your privacy.**

A local-only, zero-permission-excess photo organizer for Android. Browse your gallery, inspect EXIF metadata, and export clean copies with all identifying data stripped; without ever sending a single byte to the cloud.

![Splash Screen](docs/splash_still.png)

---

## Privacy Manifesto

- **No accounts.** No email, no phone number, no signup.
- **No network calls.** The app never connects to the internet.
- **No ads or trackers.** No Firebase, no analytics, no crash reporters.
- **Local-only processing.** All EXIF stripping happens on your device.
- **Originals untouched.** Export creates a new file; your source image is never modified.

---

## Features

| Feature | Description |
|---------|-------------|
| **Gallery Browser** | Browse all device photos and albums via MediaStore. Sort by date, name, or size. |
| **Photo Detail View** | Full-screen preview with a complete EXIF metadata table, strip mode selection, one-tap export, and a post-export metadata comparison card. |
| **EXIF Inspector** | View camera make/model, lens, exposure, ISO, focal length, GPS coordinates, and orientation. |
| **Lossless Metadata Stripper** | JPEG and PNG are rewritten segment-by-segment with zero re-compression. WEBP/HEIF fall back to high-quality re-encode. |
| **Strip Modes** | `All Metadata` removes everything. `GPS Only` removes only location data while preserving camera info. |
| **Batch Export** | Select multiple photos and share clean copies in one action. |
| **Share Intent Processing** | Share images from any app directly to EXIF Pure. Metadata is automatically stripped and a clean copy is saved. |
| **Silent Share Mode** | Share a single image to EXIF Pure and have it processed instantly without opening the app UI. The system share chooser launches with the clean copy ready to send. |
| **Favorites** | Star photos and filter your gallery to show only favorites. |
| **Biometric Lock** | Require fingerprint or face unlock to open the app. Re-locks automatically when backgrounded. |
| **Export History** | Review a log of every clean copy exported, with timestamp and strip mode. |

---

## Permissions

| Permission | Usage |
|------------|-------|
| `READ_MEDIA_IMAGES` (Android 13+) | Read photos from device storage |
| `READ_MEDIA_VISUAL_USER_SELECTED` (Android 14+) | Partial photo access support |
| `READ_EXTERNAL_STORAGE` (Android 12 and below) | Legacy storage read access |
| `WRITE_EXTERNAL_STORAGE` (Android 9 and below) | Required for photo deletion on legacy storage |
| Biometric hardware | Optional; used only if app lock is enabled in Settings |

---

## Installation

### Manual APK

Download the latest APK from the [Releases](https://github.com/x144k/ExifPure/releases) page.

**Verify the APK:**
```bash
sha256sum app-release.apk
```
Compare the output against the SHA-256 listed in the release notes.

**Install via ADB:**
```bash
adb install app-release.apk
```

### Obtainium

Add EXIF Pure to [Obtainium](https://github.com/ImranR98/Obtainium) for automatic updates directly from GitHub releases.

1. Install Obtainium from its [releases page](https://github.com/ImranR98/Obtainium/releases).
2. Open Obtainium; tap **Add App**.
3. Paste the repository URL: `https://github.com/x144k/ExifPure`
4. Obtainium will auto-detect GitHub releases and pull the latest APK; tap **Add** to track it.
5. Future updates appear in Obtainium as soon as a new release is published.

If auto-detection fails, use the fallback URL: `https://github.com/x144k/ExifPure/releases`

---

## Building from Source

```bash
git clone https://github.com/x144k/ExifPure.git
cd ExifPure
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

For a signed release build:
```bash
./gradlew :app:assembleRelease
```

---

## Inspiration

Some features in this roadmap were inspired by [Imagepipe](https://codeberg.org/Starfish/Imagepipe), a lightweight Android utility for resizing and stripping metadata from shared images. While Imagepipe focuses on aggressive re-encoding for data savings, EXIF Pure prioritizes lossless metadata removal and preserving original image quality wherever possible.

Additional inspiration for privacy-focused editing tools comes from [ImageToolbox](https://github.com/T8RIN/ImageToolbox), a powerful open-source image manipulation suite. EXIF Pure borrows the concept of pre-export markup and adjustment, but narrows the scope strictly to privacy protection rather than general creative editing.

## Upcoming Features

- **Privacy Score** - See at a glance how much identifying metadata each photo contains.
- **Camera Model Name Resolution** - View readable camera names instead of cryptic manufacturer model codes.
- **Per-Photo Strip Mode Override** - Choose a different strip mode for a specific photo and remember it for next time.
- **EXIF Field Tooltips** - Tap any metadata entry to learn what it means and why it matters for your privacy.
- **Batch Metadata Summary** - Select multiple photos and see instant stats about their metadata.
- **Original Integrity Hash** - Verify that your original photo files have never been modified.
- **Before/After Metadata Preview** - Review exactly what metadata will remain before you export or share.
- **Before/After Comparison** - Side-by-side viewer comparing the original photo against the cleaned export, so you can verify exactly what was removed or redacted.
- **Export Filename Templates** - Customize filenames using date, camera name, counters, or random strings.
- **Metadata Presets** - Save named configurations like Social Post or Archive for quick one-tap selection.
- **Privacy Watermark** - Add a subtle text or timestamp watermark to exported images (e.g., "Shared via EXIF Pure - 2026-05-10"). Not for branding; for traceability and leak detection.
- **Search by EXIF Field** - Find photos by camera model, GPS presence, date range, and more.
- **Resize on export** - Set a maximum image size so large photos are scaled down before saving or sharing.
- **Quick Crop & Rotate** - Simple pre-export cropping and rotation, plus one-tap auto-straighten for sideways photos. Bake the orientation into the image so it displays correctly everywhere, without modifying the original.
- **Convert format on export** - Save clean copies as JPEG, PNG, or WebP instead of keeping the original file format.
- **In-app EXIF editor** - Edit specific metadata fields, such as adding copyright or correcting the date taken, instead of only removing data.
- **Choose what metadata to keep** - Preserve selected information, like copyright or camera model, while stripping everything else.
- **Duplicate detection** - Find visually similar or exact-duplicate photos to help clean up storage.
- **Privacy Redaction Tool** - Blur or pixelate faces, license plates, text, or any sensitive region before exporting. A privacy-first markup layer that redacts rather than decorates.
- **HEIC/HEIF lossless stripping** - Strip metadata from HEIC/HEIF images without quality loss. Experimental.

---

## Contributing

This project is maintained by a single developer. If you have a bug report or a small fix, open an issue or pull request directly. For significant changes, please open an issue first to discuss the approach.

---

## License

MIT License; see [LICENSE](LICENSE) for details.

---

*EXIF Pure is not affiliated with Google, Samsung, or any camera manufacturer. All trademarks belong to their respective owners.*
