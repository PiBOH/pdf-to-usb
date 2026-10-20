# Technical notes

Status: working vertical slice - screen, drive pickers, watcher, copy queue, updater.
The app copies PDFs from drive **A** (source) to `B/pdf-to-usb` (receiving drive).

## 1. How the app sees a PDF opened by any reader

Android has no API that reports which file another app opened. The four possible routes and
the choice made:

| Mechanism | Works? | Why |
|---|---|---|
| **FileObserver ACCESS (inotify)** - chosen | Yes, with caveats | The kernel reports `IN_ACCESS` to any process that reads the file, so also to Adobe/Foxit/Xodo. No special permission, no accessibility service, no warning. |
| VIEW/SEND intent on `application/pdf` | Only when the PDF is opened **with this app** | An app only receives the intents the system hands it; it cannot hook another app's open. Kept as the second channel. |
| Accessibility service | No | Needs manual activation with a system warning ("can read everything on screen") and does not reveal the opened file name. |
| atime polling / MediaStore | No | `relatime` updates atime only in some cases; MediaStore does not record "opens". |

Real limits of FileObserver ACCESS, to check on the device:

- reads go through FUSE (Android <=7) or sdcardfs (8): an event can be missed when the
  reader reads through a different mount (e.g. `/mnt/media_rw`) or with FUSE passthrough;
- an `open()` without `read()` produces no ACCESS event (some readers open and close to probe);
- readers that **copy** the PDF into their cache before showing it produce one ACCESS event on
  the original file (enough), but on a file that then disappears: the copy still happens;
- USB volumes get unmounted: the service rearms the watcher on `MEDIA_MOUNTED`.

So the app does not fail silently:

- **Test detection on A**: reads one PDF from A and waits for the ACCESS event, then logs
  `ACCESS detected` or `ACCESS not detected on this device`;
- **Copy all PDFs from A now**: copies everything, independent of the watcher.

This check **has not been run on a device**: no device or emulator is available in the usual
development environment, so kernel/storage behaviour is declared, not measured.

## 2. Writing to drive B: SAF, not paths

From Android 6.0 (and in practice already from 5.0) `WRITE_EXTERNAL_STORAGE` does **not**
grant write access to an arbitrary folder on a USB stick: secondary storage is read-only for
normal apps. The only public route is `ACTION_OPEN_DOCUMENT_TREE` plus a persistable
permission. Therefore:

- A and B are chosen **once** with the folder picker (persistable grant, no need to redo it
  after a reboot);
- copies go to `B/pdf-to-usb`, created when missing, through `DocumentFile`;
- the system exposes the sticks as removable volumes (`StorageManager`/`StorageVolume`); to
  know *which* volume is A the app reads the SAF tree id (`UUID:folder`) and derives
  `/storage/UUID` for `FileObserver` (reading is allowed).

### Automatic detection of A and B

Opening the app with A or B still missing runs `MainActivity.autoDetect()`:

1. `Volumes.removable()` lists the mounted removable volumes;
2. **A is the stick that holds the PDFs**: each volume is counted to two levels (cheap, off
   the UI thread) and the one with more PDFs wins, first one on a tie;
3. **B is the only other stick** (with three or more volumes the app does not guess);
4. A is stored straight away (reading uses plain `File` + `READ_EXTERNAL_STORAGE`), B is only
   *proposed*: the button "Scegli chiavetta B" opens the system picker already positioned on
   that volume (`DocumentsContract.EXTRA_INITIAL_URI`, API 26+) and the user confirms;
5. after that confirmation the foreground service starts by itself, and both trees are
   remembered by volume UUID, so a replug, a reboot or a different port needs nothing.

What cannot be automatic: writing into a removable volume needs the SAF grant, and only the
user can give it (Android 5.0+; a normal app cannot write to `/storage/UUID` by path). The
confirmation is therefore one tap, once per stick pair, not a missing feature. Everything
else - which stick is A, which is B, and which PDFs are opened - is detected by the app.

## 3. Compatibility

- `minSdk 21` (Android 5.0 Lollipop). **Android 4.x is not supported**: the SAF tree picker
  exists from API 21; on 4.4 secondary storage is already read-only for apps and there is no
  public way to write to the stick. Supporting 4.x would need root or system permissions, and
  is not implemented.
- `targetSdk 28`: no scoped storage (Android 10+ is out of target) and no foreground service
  type requirement. The supported range is Android **5.0 to 9.0** (API 21-28); on 6/7/8/9 the
  foreground service behaves as required.
- Verified by local unit tests (32 tests: copy engine, names, dedupe, partial-file cleanup,
  store, automatic A/B detection, language tags, screen, service strings, translations,
  updater version compare and JSON parsing).
  **Never executed on real hardware.**

## 4. What is not silent (and cannot be)

- The first setup needs two folder picker choices and the storage read permission (Android
  6+): those are system prompts, not app prompts.
- Installing an APK shows the "unknown sources" consent: it can only be avoided with
  `adb install` or an MDM.
- Updates: the "install unknown apps" toggle (once per app), the system install confirmation
  and Play Protect warnings cannot be suppressed without a system signature, root or an MDM.
  See section 6.

## 5. Flow

1. Install, open the app, grant the read permission (needed by `FileObserver`, not by writes).
2. With both sticks plugged in, A is detected by itself (the one with the PDFs) and B is
   proposed. "Choose drive B" is only the system confirmation of the volume already
   selected; the service then starts by itself.
   Without that automatic detection (one stick only, or a non-standard provider) the two
   pickers still work as before: A then B, then "Start listening".
3. "Start listening" (notification "Listening on A") is only needed when the service was
   stopped by hand.
5. Open a PDF of A with any reader: the file appears in `B/pdf-to-usb` and the log shows
   `name.pdf -> pdf-to-usb`.
6. Reboot or re-plug: the boot and mount receivers rearm the watcher by themselves.
   Dedupe: same name and same size already present means no second copy; same name with
   different content becomes `name (2).pdf`. Errors: 3 attempts, then a `x` row in the log.

## 6. Automatic update (GitHub release)

Implemented in `Update.java`:

1. `GET https://api.github.com/repos/PiBOH/pdf-to-usb/releases/latest` (public, no token); at
   most one check every 6 hours, from `MainActivity.onStart` and `CopyService.onCreate`.
   **Check for updates** in the bottom bar calls `Update.checkNow`, which ignores the limit
   and writes the outcome (`Already up to date (0.1.5)` or the error) in the log.
2. Comparison: tag `vX.Y.Z` becomes `major*10000 + minor*100 + patch` and is compared with the
   installed `versionCode` (built from `version.txt`). Non-numeric tags or a release without
   an `.apk` asset are ignored.
3. When the release is newer a notification appears (channel "Updates"); tapping it opens
   `MainActivity`, which downloads the APK into the cache, exposes it with `FileProvider`
   (`pdf.to.usb.fileprovider`) and fires `ACTION_VIEW` on
   `application/vnd.android.package-archive`.
4. Android 8: when `canRequestPackageInstalls()` is false the app opens
   `ACTION_MANAGE_UNKNOWN_APP_SOURCES` once for the package and re-posts the notification, so
   the second tap completes the install.

**What cannot be suppressed** without a system signature, root or an MDM (and is never
promised): the "install unknown apps" toggle (once per app, Android 8+), the system install
confirmation dialog, Play Protect scanning/warning for APKs that do not come from Play, and
the signature rule: an update installs only if the APK has **the same key** as the installed
app. An APK signed with another key forces an uninstall, which loses the A/B choices and the
log.

## 7. Build, versioning, signing, CI

- **`version.txt` (repository root) is the only place the version is written**: one line,
  `X.Y.Z`. `app/build.gradle` reads it, computes
  `versionCode = major*10000 + minor*100 + patch` (`0.1.0` = 100) and fails the build on a
  malformed value. The release tag is `vX.Y.Z`, which is exactly what the updater parses.
- **Signing**: `release.keystore` and `keystore.properties` live in the repository root and are
  gitignored. `app/build.gradle` enables the release signing config only when
  `keystore.properties` exists. The keystore is a 4096-bit RSA, PKCS12, alias `pdf-to-usb`.
  `storePassword` and `keyPassword` are the fixed word `android`, by owner's decision: the
  passwords are not secrets that change, and the release workflow writes them itself. Keep a
  backup of both files: without them you cannot ship an update that installs over the current
  app (Android refuses a different key).
- **CI**: `.github/workflows/build.yml` runs `assembleDebug`, unit tests and lint on every
  push and pull request. `.github/workflows/auto-release.yml` runs on a push whose commit
  subject starts with `v`, or manually: it reads `version.txt`, restores the keystore,
  builds `assembleRelease`, verifies the signature with `apksigner` and publishes tag
  `vX.Y.Z` with `pdf-to-usb-<version>.apk` attached. The only required secret is
  `RELEASE_KEYSTORE_BASE64` (base64 of `release.keystore`); the workflow writes
  `storePassword=android`, `keyPassword=android`, `keyAlias=pdf-to-usb` into
  `keystore.properties` itself, because those values are fixed. Before decoding it strips
  spaces, tabs, CR and LF (a base64 copied on Windows carries them and a single `\r` is
  enough to produce a corrupt file), and right after writing the keystore it runs
  `keytool -list -storepass android -alias pdf-to-usb`: if that fails, the job stops with
  "il secret RELEASE_KEYSTORE_BASE64 non contiene il keystore giusto o e' troncato"
  instead of dying later inside `packageRelease`. The workflow fails instead of publishing
  an unsigned APK. To fill the secret: `base64 -w0 release.keystore` (PowerShell:
  `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))`) from the GitHub
  web UI (Settings, Secrets and variables, Actions), never from a commit.

## 8. Localization

- **English is the base language** in `values/`; `values-en` mirrors it, `values-it` is the
  Italian translation and the other 55 folders come from `vivizzz007/vivi-music` (`ar as az
  b+sr+Latn be bg bn bn-rIN bs ca cs de el en-rCA es es-rUS et eu fa fi fil fr hi hr hu in it
  iw ja km ko lt mfe ml ms nb-rNO nl nn pa pl pt pt-rBR ro ru sk sl sv ta te th tr uk vi wae
  zh-rCN zh-rTW`). An English phone and an unknown language both fall back to English.
- **Language in the app**: the bottom bar opens a dialog with the 57 locales, each shown by
  its native name, plus "System language". The choice is stored in preferences
  (`Store.language`) and applied from `Lang.wrap` in `attachBaseContext` of the activity and
  of the service, so screen, log and notifications use it. The service applies a new choice
  from its next start; the activity calls `recreate()` immediately. No dependency is needed:
  the platform API 33 per-app language is out of the supported range.
- No locale was dropped: `b+sr+Latn`, `nb-rNO`, `bn-rIN`, `zh-rCN/TW`, `in`, `iw` are valid
  qualifiers; `mfe` and `wae` are 3-letter codes accepted by aapt2 (green build).
- `TranslationsTest` compares every locale with `values/strings.xml`: missing keys, extra keys
  and different placeholders (`%1$s`, `%1$d`, `%2$s`) **fail** the build. Proven by removing
  `copy_progress` from `values-de` and by changing `%2$s` into `%2$d` in `values-ja`.
- **Everything the user reads is a resource**, including the service log and notifications:
  `copy_ok`, `copy_error_detail`, `copy_progress`, `copy_error_file`, `copy_unreadable`,
  `drive_a_missing`, `drive_b_missing`, `scan_done`, `access_ok`, `access_missing`,
  `no_pdf_to_test`, `error_unknown`, `auto_source`, `auto_destination`, `auto_none`,
  `language`, `language_system`, `check_updates`, `update_latest`, `not_connected`,
  `volumes`, `volumes_none` (43 keys per locale). Only technical literals stay: the
  `document.pdf` fallback file name, the `pdf-to-usb` folder and the English messages of
  the SAF exceptions, which end up in the log through `copy_error_detail`.
- `Lang.TAGS` (one BCP 47 tag per folder) and `TranslationsTest.LOCALES` must stay in step:
  `languageListCoversEveryLocale` fails when a folder has no entry in the dialog.
- The lint check `AppBundleLocaleChanges` is disabled in `app/build.gradle`: it asks for the
  Play Core language download, which only applies to an App Bundle. This project publishes
  an APK (`auto-release.yml`) with all 57 locales inside and
  `bundle.language.enableSplit = false` set.

## 9. Icons

`matrix_logo.svg` at the repository root is the source of the mark. `tools/icons.py`
(python3, standard library only) renders it into `icon.png` (1024 px), `logo.png` (512 px,
the README mark), `mipmap-*/ic_launcher.png` and `mipmap-*/ic_launcher_foreground.png`:
all the PNGs, so regenerate with `python3 tools/icons.py` instead of hand-editing files.
The SVG shapes are redrawn with the same geometry (rounded square, page with folded corner,
red PDF badge, matrix rain) and the `<text>` elements are read from the SVG itself; the
script uses a small built-in 5x7 font because no font or renderer library is available.
Both root images are RGBA with transparent rounded corners. On Android 8+ the adaptive icon
combines `@color/icon_background` (`#111827`, the first stop of the SVG gradient) with the
foreground mark inside the central 66% safe zone. The notification icon is the vector
`drawable/ic_stat.xml`.

The manifest declares only `android:icon`, not `android:roundIcon`: pointing the round slot at
the same square artwork made lint report five `IconLauncherShape` warnings, and the launcher
masks the icon itself. Remaining lint warnings: the pre-Android-13 receiver export flag and the
missing monochrome layer (Android 13 only, out of the target range).
