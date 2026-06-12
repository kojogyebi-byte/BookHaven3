# BookHaven 📚

*Created by Kwadwo Gyebi · Shamaapps*

An Apple Books–style e-reader for Android. Clean library shelf, distraction-free
reader, multiple typefaces and sizes, four page themes (White / Sepia / Gray /
Night), a light/dark app toggle, and resume-where-you-left-off progress tracking.

Built with Kotlin + Jetpack Compose (Material 3).

---

## Format support — read this first

| Format | Support | Notes |
|--------|---------|-------|
| **EPUB** (.epub) | ✅ Full | Paginated WebView reader, chapter navigation, embedded images & CSS |
| **PDF** (.pdf) | ✅ Full | Page-by-page rendering with pinch-to-zoom (built-in `PdfRenderer`) |
| **TXT** (.txt) | ✅ Full | Reflowable text with adjustable size/theme |
| **MOBI / PRC** (.mobi) | ⚠️ DRM-free only | Old Mobipocket/PalmDOC text is extracted; **DRM-protected files cannot be opened** |
| **AZW / AZW3** (.azw, .azw3) | ⚠️ DRM-free only | Same engine as MOBI; only works on non-DRM files |
| **KFX** (.kfx) | ❌ Not supported | Amazon's KFX uses proprietary DRM with **no legal open decoder**. No Android app can read these without stripping DRM. Convert to EPUB first. |

**Why KFX/DRM can't work:** Kindle KFX and DRM-protected MOBI/AZW are encrypted
with Amazon's keys. Decrypting them requires circumventing DRM, which isn't
something this app does. If a book opens fine in the Kindle app but not here,
it's almost certainly DRM-protected. Tools like Calibre (with DeDRM, where you
legally own the book) can convert your library to EPUB, which BookHaven reads
perfectly.

---

## Getting an installable APK (no computer needed)

This repo builds itself in the cloud via **GitHub Actions** — the same approach
you use for LiveDeck Studio. You never need Android Studio or a terminal.

1. **Create a new GitHub repository** (e.g. `bookhaven`) on github.com.
2. **Upload these files.** On the repo's main page click **Add file → Upload files**,
   drag in everything from this folder (keep the structure — the `.github`,
   `app`, and `gradle` folders must stay at the top level), and commit.
3. The upload triggers the workflow automatically. Open the **Actions** tab and
   watch the **Build BookHaven APK** run (≈3–5 min the first time).
4. When it finishes (green ✓), open the run and scroll to **Artifacts**.
   Download **`BookHaven-debug-apk`** and unzip it — inside is `app-debug.apk`.
5. **Install on your phone:** transfer the APK to an Android device, tap it, and
   allow "install from unknown sources" if prompted. (iPhone can't install
   Android APKs — this is an Android app.)

> If the Action doesn't start on upload, go to the **Actions** tab, pick
> **Build BookHaven APK**, and click **Run workflow** manually.

The `app-debug.apk` is signed with Android's debug key and installs directly.
The release APK in the artifacts is **unsigned** and is only useful if you set up
your own signing key for Play Store distribution.

---

## Building locally (optional, if you ever use Android Studio)

1. Open the project folder in Android Studio (Ladybug or newer).
2. Let it sync Gradle.
3. **Run ▶** to install on a connected device/emulator, or
   **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

---

## Using the app

- **Add books:** tap the **Add Book** button and pick one or more files. You can
  also open a supported file from any file manager and choose BookHaven.
- **Light / dark app theme:** tap the sun/moon icon at the top of the Library to
  switch the whole interface between light and dark. Your choice is remembered.
- **Read:** tap a cover. Tap left/right edges (or swipe) to turn pages; tap the
  centre to show/hide the toolbar.
- **Fonts & themes:** tap **Aa** in the reader to change the typeface
  (Original, Serif, Sans Serif, Slab, Mono), the text size, and the page color
  (White / Sepia / Gray / Night).
- **PDFs read like EPUBs:** the same Aa toolbar, tap-to-turn page zones, and the
  four page-color themes all work on PDFs too. Because a PDF's text is baked into
  each page image, font and size can't be changed for PDFs — but the page-color
  themes (including a true Night mode) recolor the page so it reads the same way.
- **Resume:** progress is saved automatically; the **Reading Now** row shows
  books in progress.

---

## Tech notes

- Kotlin 2.0.21 · AGP 8.7.2 · Gradle 8.10.2
- compileSdk/targetSdk 35 · minSdk 26 (Android 8.0+)
- Jetpack Compose (Material 3) · Navigation Compose
- Library persisted as JSON in app storage (kotlinx.serialization)
- Imported books copied into the app's private storage; no special permissions needed
- Package: `com.bookhaven.reader`

---

## Project layout

```
app/src/main/java/com/bookhaven/reader/
├─ MainActivity.kt            # nav host, file picker, "open with" intent
├─ data/                      # Book model + repository (import, persist, covers)
├─ format/                    # EPUB, MOBI/AZW parsers
├─ reader/                    # EPUB / PDF / Text reader screens + Aa settings
├─ ui/                        # Library shelf + book cover components
└─ viewmodel/                 # LibraryViewModel
.github/workflows/build.yml   # cloud APK build
```
