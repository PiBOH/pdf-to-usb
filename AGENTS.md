# AGENTS.md

Instructions for AI coding agents working in this repository.

## 1. Project overview

**Pdf To Usb** is a small Android app (Java, no Kotlin, no Compose) that copies the PDFs
opened from drive **A** into the folder `pdf-to-usb` of drive **B**, listens through a
foreground service, and updates itself from this repository's GitHub releases.

- **A** is the source drive: the stick that holds the PDFs. Any reader can open them.
- **B** is the receiving drive: copies land in `B/pdf-to-usb`.

- **Package**: `pdf.to.usb`
- **Build**: Gradle 8.9, AGP 8.7.2, Java 11 source level, JDK 17+ (CI uses 21)
- **Android**: `minSdk 21`, `targetSdk 28`, `compileSdk 35` (the target devices run Android
  5.0 to 9.0)
- **Dependencies**: `androidx.documentfile`, `androidx.core` (FileProvider). Nothing else.
- **UI**: one Activity, one layout, platform Material light theme, no AppCompat: header,
  a status card, the action rows, the copy log and a bottom bar with language and updates.
- **Locales**: 57 `values-*` folders, base language English in `values/`.
- **Language**: English is the default everywhere: app, docs, code comments, commit
  messages. Android picks the system language when it matches a `values-*` folder; the
  screen also offers an in-app choice (`Lang.java`).
- **License**: UNLICENSE (public domain dedication). The program is distributed this way on
  purpose: use is at the user's own risk and peril, the creator claims no copyright and
  accepts no liability of any kind. Never add a license header or a copyleft notice.

## 2. Repository layout

| Path | Contents |
|---|---|
| `app/src/main/java/pdf/to/usb/` | all the code, one file per role (see §3) |
| `app/src/main/res/values*/strings.xml` | user-visible strings, 57 locales |
| `app/src/test/java/pdf/to/usb/` | JVM unit tests (JUnit 4 + Robolectric) |
| `version.txt` | the only place the version is written (§6) |
| `.github/workflows/build.yml` | debug build + tests + lint on every push and PR |
| `.github/workflows/auto-release.yml` | signed release on a `v...` commit or manual run |
| `icon.png`, `logo.png` | artwork: launcher source and README mark (§8) |
| `tools/icons.py` | regenerates the artwork and every launcher mipmap (§8) |
| `docs/NOTE.md` | technical notes, limits, release and signing details |
| `README.md`, `AGENTS.md`, `LICENSE` | the only documentation files at the root |

**Documentation lives in English and inside `docs/`.** The only Markdown files at the
repository root are `README.md`, `AGENTS.md` and `LICENSE`. A new document goes in `docs/`
with an English name; a document that grows beyond one screen starts with a summary table.

## 3. Architecture

| File | Role |
|---|---|
| `Copy.java` | copy engine, pure `java.io`: unique names, dedupe, partial-file cleanup |
| `SafSink.java` | destination `B/pdf-to-usb` through SAF `DocumentFile` |
| `PdfWatcher.java` | `FileObserver.ACCESS` watcher on drive A |
| `CopyService.java` | foreground service: watches every removable volume except B, queue, retries, notification |
| `Volumes.java` | `StorageManager`/`StorageVolume` detection, SAF tree to filesystem path |
| `Store.java` | preferences (trees A/B, language, copy log, update timestamp) |
| `Lang.java` | language list, BCP 47 tags, context with the chosen language |
| `Update.java` | GitHub release check, version compare, APK download, install intent |
| `MainActivity.java` | the four screens (drives, log, language, updates), automatic drive detection, pickers |
| `BootReceiver.java` | boot and media mount/unmount, rearms the service |

Tests: `CopyTest` (engine, names, dedupe, partial cleanup), `StoreTest` (preferences and
log), `VolumesTest` (automatic A/B assignment, tree URI, PDF count), `LangTest` (tags and
names), `ScreenTest` (Robolectric screen, in-app language), `ServiceStringsTest`
(formatted service strings in German and Japanese), `TranslationsTest` (key and
placeholder parity across 57 locales, language list), `UpdateTest` (tag to version) and
`UpdateParseTest` (release JSON). Design constraints that must survive:

- `Copy.java` stays free of Android classes so it can be tested on the JVM.
- The Activity holds no copy logic; the service owns the queue.
- Resource keys are the contract between the app and 57 locales.

## 4. Code conventions - MANDATORY

- Java 11 syntax, existing style, one-line comments only where the why is not obvious.
- Code comments, docs and commit messages are in English. Every user-visible string
  (screen, log lines, notifications) lives in `res/values*/strings.xml`, never inline in
  Java.
- No em dash in new text (owner preference): use `-`, `:`, parentheses or a full stop.
- Apply the `ponytail` skill on every coding task: smallest working change, reuse what is
  already in the repo, no new dependency for a few lines.
- Verify non-trivial changes with
  `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
  Lint must stay at zero errors (the two known warnings, the receiver export flag and the
  missing monochrome icon layer, are tolerated; do not add more). `AppBundleLocaleChanges`
  is disabled in `app/build.gradle` because the release is an APK with every locale inside.
- `minSdk 21` and `targetSdk 28` are deliberate: raising `targetSdk` changes storage
  access and foreground-service rules for the target devices. Do not raise it as a
  drive-by change.
- Remove unused resources when you touch them; do not reformat untouched files.

## 5. Golden rule: "If it works, don't touch it"

Copy engine, volume detection, SAF paths, PDF intent filters and the layout are verified
by unit tests and (partially) on real hardware. Do not refactor them while doing something
else, do not change their behaviour without a concrete bug, and never add a feature that
the request did not ask for. If a change can break an existing path, it needs a test and a
device run, not a "should be fine".

## 6. Versioning, signing and releases - MANDATORY

### Version

- **`version.txt` in the repository root is the only place the version is written**: one
  line, `X.Y.Z`, nothing else. `app/build.gradle` reads it and derives `versionName` and
  `versionCode`. Never write `versionName` or `versionCode` anywhere else; they are not
  literals in the build file.
- `versionCode = major*10000 + minor*100 + patch` (`0.1.0` = 100). It must never decrease:
  an installed app only accepts an APK with a higher `versionCode`. Each part is capped at
  99 and the build fails otherwise.
- Bump SemVer as part of the change that warrants it: PATCH for a fix, MINOR for a feature,
  MAJOR only for something genuinely breaking. A docs-only change bumps nothing.
- A release tag is `vX.Y.Z` and the release must carry an `.apk` asset. `Update.java` reads
  `https://api.github.com/repos/PiBOH/pdf-to-usb/releases/latest` and compares the tag code
  with the installed `versionCode`. Non-numeric tags or a release without an `.apk` are
  ignored by the app.

### Signing

- Release APKs **must** be signed with the release keystore. `release.keystore` (PKCS12,
  RSA 4096, alias `pdf-to-usb`) and `keystore.properties` live in the repository root and
  are **gitignored**: they never enter a commit, an issue, a log or a chat.
- `app/build.gradle` enables the release signing config only when `keystore.properties`
  exists, so a clean clone can still run `assembleDebug`. Before publishing, verify the
  output with `apksigner verify --print-certs`.
- An update installs only if the new APK has **the same key** as the installed app. Changing
  the key (a debug keystore, a different machine, a regenerated keystore) forces an uninstall
  and loses the app's preferences, so the keystore is backed up outside the repository.
- The release keystore is restored from the single secret `RELEASE_KEYSTORE_BASE64`.
  `storePassword`, `keyPassword` and `keyAlias` are written by the workflow as the fixed
  values `android`, `android` and `pdf-to-usb` (owner's decision, see `docs/NOTE.md` §7);
  there are no password secrets. The base64 is decoded after stripping spaces, tabs, CR
  and LF, so a copy taken on Windows cannot corrupt the file, and `keytool -list` runs
  before the build: a keystore that does not open with those values fails the job with a
  clear message. CI **fails instead of publishing** an unsigned APK.

### Release flow

- `build.yml` runs on every push to `main` and on every pull request: debug build, unit
  tests, lint, and uploads the debug APK as an artifact.
- `auto-release.yml` runs on a push whose **commit subject starts with `v`**, or manually
  from the Actions tab. It validates `version.txt`, restores the keystore, builds
  `assembleRelease`, verifies the signature with `apksigner` and publishes tag `vX.Y.Z` with
  `pdf-to-usb-X.Y.Z.apk` attached.
- Release notes are not parsed by the app; they are for people.

## 7. Localization - MANDATORY

- Base strings are **English** in `app/src/main/res/values/strings.xml`. `values-en` mirrors
  the base, `values-it` is the Italian translation, the other 55 folders are the locales of
  `vivizzz007/vivi-music`.
- Every locale must have exactly the same keys and the same format placeholders as the
  base. `TranslationsTest` fails the build otherwise: never add a string to `values/`
  without adding it to all 57 folders. The locale list in `TranslationsTest` is the
  canonical list.
- `Lang.TAGS` in `Lang.java` is the language list shown by the **Language** dialog, one
  BCP 47 tag per folder (`b+sr+Latn` becomes `sr-Latn`, `en-rCA` becomes `en-CA`).
  `TranslationsTest.languageListCoversEveryLocale` fails if a folder is missing from that
  list: keep the two in step.
- Do not renumber or reformat `%1$s`; do not translate `app_name`, `notification_title`
  or the folder name `pdf-to-usb`.

## 8. Icons and artwork

- `icon.png` (1024 px, launcher source) and `logo.png` (512 px, the README mark) are the
  artwork and stay at the repository root; both are RGBA, transparent outside the rounded
  square.
- `mipmap-*/ic_launcher.png` and `ic_launcher_foreground.png` are generated from
  `matrix_logo.svg`; `mipmap-anydpi-v26/ic_launcher.xml` is the adaptive icon (background
  `@color/icon_background`, the first stop of the SVG gradient). `python3 tools/icons.py`
  (standard library only) redraws every PNG from the SVG geometry, so regenerate with it
  instead of hand-editing the files.
- Launcher icons live in `mipmap`: `drawable` is for the notification vector only. The
  manifest declares `android:icon` and no `android:roundIcon` (a square round icon makes lint
  report five `IconLauncherShape` warnings).
- `drawable/ic_stat.xml` is the monochrome notification icon.
- Never replace the artwork with a placeholder: the launcher icon and the README image are
  the same mark.

## 9. Update flow and what cannot be silent

`Update.check` runs at most once every 6 hours, from `MainActivity.onStart` and
`CopyService.onCreate`, and posts a notification when a newer release exists. Tapping it
downloads the APK to the cache, exposes it with `FileProvider` and opens the system
installer.

Not suppressible without a system signature, root or an MDM: the Android 8 "install unknown
apps" toggle (one time per app), the system install confirmation dialog, and Play Protect
scanning/warning for APKs that do not come from Play. Never promise a silent update in
code, docs or comments. Details in `docs/NOTE.md` §6.

## 10. Testing - MANDATORY

- All tests run offline. `Update.check` / `Update.checkNow` never run in tests;
  Robolectric tests call
  `Store.markUpdateCheck(...)` in `@Before` so the service and the screen never fetch.
- Updater tests cover tag-to-version mapping and release JSON parsing only. Network,
  download and the installer are not exercised by tests.
- There is no device or emulator in the usual development environment: report behaviour
  as "not verified on device" instead of assuming it.

## 11. Ask before assuming - MANDATORY

- The owner writes in Italian and expects answers in Italian; the code and the docs follow
  the language rules above.
- Ask before changing the A/B drive model, the storage strategy, `targetSdk`, the signing
  setup, the repository or the release process.
- Keep `docs/NOTE.md` updated with every deliberate deviation from what was requested, and
  why.

## 12. Known limits

- Android 4.x is not supported: the SAF tree picker starts at API 21 and secondary storage
  is read-only for apps before that.
- `FileObserver.ACCESS` is best effort across FUSE/sdcardfs; the app offers "Copy all PDFs
  from A now" and a detection self-test for the devices where it does not fire.
- Log lines and notifications are resource strings: adding one means translating it in all
  57 locales, and `TranslationsTest` fails until you do.
