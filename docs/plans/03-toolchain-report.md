# Plan 03 — Toolchain pin report (D2 re-check)

**Date:** 2026-11-11. **Nothing was upgraded or changed.** This is a report only.

**How it was checked:** release-notes pages and GitHub release pages read directly, plus Maven
Central metadata for Kotlin, KSP, kotlinx-serialization, Robolectric, and the Gradle version
service. Google's Maven host could not be reached from the review environment, so AndroidX and
AGP facts come from developer.android.com release notes, not from the artifacts themselves.
**The project cannot be built in the planning environment.** CI is the proof: on 2026-11-11 the
unit tests compiled and passed on Linux with these exact pins (`06-ci.md` §6).

## 1. Verdict per pin

| Pin | Pinned | Exists / stable? | Latest stable | Verdict |
|---|---|---|---|---|
| AGP | 9.5.1 | Yes, stable | 9.5.x (newest line) | OK. Max API 37, needs Gradle ≥ 9.6.0, JDK 17, build-tools 36.0.0 — matches D2. |
| Gradle | 9.9.0 | Yes | 9.9.1 | OK. Above AGP's minimum. No statement found that 9.9.x is tested with AGP 9.5. |
| Kotlin | 2.3.21 | Yes (Apr 2026) | 2.4.21 (Oct 2026) | **Stale by one minor line.** Works, but see serialization. |
| Compose / serialization compiler plugins | 2.3.21 | Yes | follow Kotlin | OK. They are versioned with Kotlin. |
| KSP | 2.3.13 | Yes, latest (30 Oct) | 2.3.13 | **Risky.** Known regression, see §2.2. |
| Room 3 | 3.0.3 | Yes, stable (Sep 2026) | 3.0.3 | OK. `@Fts5` confirmed. `room3-testing` has `MigrationTestHelper`. |
| sqlite-bundled | 2.7.1 | Yes, stable | 2.7.1 | OK. The `-jvm` artifact name was not confirmed independently. |
| Compose BOM | 2026.09.00 | Listed | 2026.09.00 | OK. |
| activity-compose | 1.13.0 | Yes | 1.13.0 | OK. |
| lifecycle-runtime-compose | 2.11.0 | Yes | 2.11.0 | OK. |
| core-ktx | 1.19.1 | Yes | 1.19.1 | OK. |
| kotlinx-coroutines | 1.11.0 | Yes | 1.11.0 | OK (built on Kotlin 2.2.20). |
| kotlinx-serialization-json | 1.12.0 | Yes | 1.12.0 | **Risky.** Built on Kotlin 2.4.10, see §2.1. |
| Robolectric | 4.17 | Yes | 4.17 | OK. Release notes say SDK 37 is supported. |
| androidx.test core / ext-junit | 1.7.0 / 1.3.0 | Yes | same | OK. |
| JUnit | 4.13.2 | Yes | 4.13.2 | OK. |

## 2. Flags

### 2.1 kotlinx-serialization 1.12.0 with Kotlin 2.3.21 (medium risk)
1.12.0 was built with Kotlin 2.4.10. The release built for Kotlin 2.3.x is **1.11.0**. The project
uses the 2.3.21 serialization compiler plugin with the 1.12.0 runtime. The unit tests pass in CI
with this pairing, so it works today; the risk is a subtle mismatch later. This is an inference from
the release notes, not an observed failure.
**Options (owner decision OD-1):** (a) pin serialization to 1.11.0; (b) move Kotlin to 2.4.21;
(c) leave it and rely on tests. Recommended: (a), the smallest change.

### 2.2 KSP 2.3.13 regression (low exposure, worth knowing)
KSP issue 3286: library symbols can go missing when several KSP tasks run concurrently in one
process, producing silently incomplete generated code. A fix was merged on 9 Nov; no release
containing it was found. 2.3.12 is not affected. This project runs one processor (Room) in one
module, so exposure is low. If Room ever "forgets" a DAO or entity at build time, suspect this
first. Recommended: pin 2.3.12 in the same change as OD-1, or wait for 2.3.14.

### 2.3 `compileSdk = 37` and the SDK folder name (CI risk)
The Android SDK now installs API 37 as `platforms/android-37.0`. Some setups failed with "Failed
to find target android-37". Whether AGP 9.5.1 resolves plain `compileSdk = 37` could not be
confirmed without building. If CI fails this way, the fix is
`compileSdk { version = release(37) { minorApiLevel = 0 } }`. Do not apply it pre-emptively.

### 2.4 Kotlin is one minor line behind
2.4.21 is stable and 2.5.0 is due in December. No action needed now. Which Kotlin Gradle plugin
version AGP 9.5 bundles for built-in Kotlin was not confirmed (Google's sample pairs AGP 9.5.0
with Kotlin 2.4.10).

### 2.5 Settled by the first CI run
- `androidx.sqlite:sqlite-bundled-jvm` does carry **Linux** natives: the Robolectric database
  tests pass on Ubuntu. (The catalog comment still says "macOS"; fix the comment.)
- Plain `compileSdk = 37` resolves on the runner's SDK: compilation succeeded. §2.3 needs no
  action.
- Gradle 9.9.0 with AGP 9.5.1 works in practice. No official "tested together" statement was
  found.

### 2.6 Pins added after D2 (commits of 2026-11-09 and 11-10)

| Pin | Pinned | Checked? | Note |
|---|---|---|---|
| `com.google.mlkit:text-recognition` | 16.0.1 | Listed as current in search results (medium confidence) | Bundled model. Pulls in a usage-reporting component that declares `INTERNET` and `ACCESS_NETWORK_STATE`; see audit P1 / OD-4. |
| `com.google.mlkit:digital-ink-recognition` | 19.0.0 | **Not verified** (Google's developer site was unreachable) | Model is download-only (D26). |
| `androidx.work:work-runtime` | 2.12.0 | Latest stable | Its manifest declares `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` (read from source). All approved. |
| `androidx.room3:room3-testing` | 3.0.3 | Stable | `MigrationTestHelper` API matches what `MigrationTest` uses. |
| NDK | 29.0.14206865 | **Not verified** | CI result in `06-ci.md` §6 shows whether the runner has or fetches it. |
| CMake | 3.22.1 | Present on GitHub runners | |
| llama.cpp (vendored source) | "v0.9.0" per D22 | **Not verified.** llama.cpp normally tags builds as `b<number>`; confirm the tag or record the commit hash so the source can be re-fetched. | 70 MB, 3,855 files in git. |
| Gradle daemon JVM | 25, via `gradle/gradle-daemon-jvm.properties` | — | On a machine without JDK 25, Gradle downloads one from `api.foojay.io` at build time. That is a build-time network dependency; CI installs JDK 25 itself to avoid it. |

## 3. Versions for libraries the later plans add

Not pinned yet; listed so plans 07–13 start from checked numbers. Each must be re-checked when
its plan is executed (PLAN.md §16.8).

| Library | Latest stable (2026-11-11) | Used by | Permission impact |
|---|---|---|---|
| lifecycle-viewmodel-compose | 2.11.0 | 07 | none |
| navigation-compose | 2.10.2 | 07 | none |
| navigation3 | 1.2.1 (stable 4 Nov) | 07 (alternative) | none |
| camera (CameraX) | 1.6.2 | 09 (only if in-app camera) | none merged; app must declare `CAMERA` |
| glance-appwidget | 1.2.0 | 13 (not recommended there) | depends on work-runtime, whose four permissions are already approved in this app |
| compose ui-test-junit4, ui-test-manifest | from the Compose BOM | 16 (test only, needs owner approval) | none |

## 4. Sources

- AGP 9.5: https://developer.android.com/build/releases/agp-9-5-0-release-notes
- Built-in Kotlin: https://developer.android.com/build/migrate-to-built-in-kotlin
- Kotlin releases: https://kotlinlang.org/docs/releases.html
- KSP regression: https://github.com/google/ksp/issues/3286
- Room 3: https://developer.android.com/jetpack/androidx/releases/room3
- SQLite: https://developer.android.com/jetpack/androidx/releases/sqlite
- AndroidX stable channel: https://developer.android.com/jetpack/androidx/versions/stable-channel
- Compose BOM map: https://developer.android.com/develop/ui/compose/bom/bom-mapping
- kotlinx-serialization: https://github.com/Kotlin/kotlinx.serialization/releases
- Robolectric: https://github.com/robolectric/robolectric/releases
- WorkManager manifest: https://raw.githubusercontent.com/androidx/androidx/androidx-main/work/work-runtime/src/main/AndroidManifest.xml
- Glance build file: https://raw.githubusercontent.com/androidx/androidx/androidx-main/glance/glance-appwidget/build.gradle
- API 37 folder name: https://github.com/actions/runner-images/issues/13859
