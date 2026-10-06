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

## Upcoming Features

- **Camera Model Name Resolution** - View readable camera names instead of cryptic manufacturer model codes.
- **Original Integrity Hash** - Verify that your original photo files have never been modified.
- **Export Filename Templates** - Customize filenames using date, camera name, counters, or random strings.
- **Convert format on export** - Save clean copies as JPEG, PNG, or WebP instead of keeping the original file format.
- **Metadata Presets** - Save named configurations like *Social Post* or *Archive* for quick one-tap selection in the export flow.
- **Selective Metadata Whitelist** - Preserve specific information (e.g., copyright or camera model) while stripping everything else.
- **Quick Crop, Rotate & Resize on export** - Simple pre-export cropping, rotation, and resizing. Bake orientation into the image so it displays correctly everywhere, without modifying the original.
- **HEIC/HEIF lossless stripping** - Strip metadata from HEIC/HEIF images without quality loss. Experimental.

---

## Contributing

This project is maintained by a single developer. If you have a bug report or a small fix, open an issue or pull request directly. For significant changes, please open an issue first to discuss the approach.

---

## License

MIT License; see [LICENSE](LICENSE) for details.

---

*EXIF Pure is not affiliated with Google, Samsung, or any camera manufacturer. All trademarks belong to their respective owners.*
