# Dunta SDK integration — reference parity

The reference implementation is `TrafficSignTestPakistanUrdu/app/src/main/java/com/trafficguru/pakistan/core/sdk`.

`DuntaSdkEngine.kt` is a direct port of that application's `DuntaSdkController.kt`. All SDK execution methods, state checks, call ordering, recovery scheduling, and diagnostic calls match the reference source. Adaptations are the package/class name, StatusSave BuildConfig, its language type, and localized notification/dialog strings.

`DuntaSdkController.kt` connects this engine to StatusSave Hilt and Compose state. `SupportModeDiagnostics.kt` and `BatteryOptimizationController.kt` match the reference after package renaming. The recovery worker uses Hilt to obtain the controller; its conditions, two unique periodic jobs, constraints, interval, and retry behavior match the reference.

Both projects use the identical `dunta_sdk-release.aar`, SHA-256 `df60bb623186ed8e9c1b0ed286a6ce399ef476f7244d3a1e53fdf979d358a374`.

## Behavior

- Accepting the support sheet requests notification permission. If granted, the engine prepares IDs and notification strings, sets PEER, starts the SDK using application context, and synchronizes recovery.
- Denying notification permission leaves the existing SDK choice unchanged, as in the reference. It does not call stop or convert NOT_INIT/PEER into NOT_PEER.
- On entry, a previous PEER choice follows the notification permission request/start flow again. On resume, a foreground restart is attempted only if notifications are permitted; recovery is synchronized independently.
- Background recovery is based on configured IDs, PEER state, and battery optimization exemption. There is no additional notification-permission condition in the worker or scheduler, matching the reference.
- Returning from battery settings refreshes recovery and calls start if the user is PEER.
- Decline and explicit disable persist NOT_PEER, call SDK stop, and cancel the two recovery jobs using the reference engine methods.
- One reminder can appear on the third launch after an initial decline. Explicit disabling ends reminders. Debug builds can preview the consent sheet with Enable disabled when IDs are absent.

The support sheet follows the reference layout: Optional badge, top Not now action, three icon rows, privacy link, and fixed green enable button. Texts use Status Saver branding and the application's English, Hindi, and Pakistani Urdu resources. Activity recreation and pending permission results are retained through StatusSave saved state.

## Configuration and manifest

Keep StatusSave-specific `DUNTA_PARTNER_ID` and `DUNTA_APPLICATION_ID`. The IDs are read from Gradle properties. Absent values default to zero; invalid/negative values fail configuration. Placeholder IDs have been removed. Existing StatusSave IDs are preserved. Another application's IDs must not be copied.

The SDK service uses the reference `specialUse` type and subtype declaration. The inherited `dataSync` service permission is removed. StatusSave retains the storage permissions it needs on older Android versions.

## Verification

Source comparison verifies every SDK-facing engine method against the reference, allowing only localized string lookup changes. AAR hashes and normalized diagnostics/battery helper files also match.

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
ANDROID_SERIAL=<test-emulator> ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ilya.safarshots.core.dunta.SupportModeConsentTest
```

The instrumented tests cover consent restoration, persistent decline, notification denial preserving the previous state, actual service startup/stop, and reminder suppression. Source parity and emulator checks do not establish backend registration, revenue, or identical long-running behavior on different physical devices.

Status auto-save work remains separate from SDK recovery work. Battery settings and SDK opt-out are available both before folder access is granted and in the application settings. SDK consent does not grant folder access or enable status auto-save. Release minification is retained with the existing SDK keep rules.

Verified 2026-09-08: current debug and minified unsigned release APKs built successfully; lint has 0 errors and 175 warnings; the existing JVM test and all 4 SDK instrumentation tests passed on a clean Android 16 emulator. Consent layout was also inspected visually. No backend-registration or long-duration physical-device claim is implied.
