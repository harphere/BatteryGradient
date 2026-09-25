# Battery Gradient 1.0.6

Standalone **LSPosed** battery icon module for Pixel/AOSP SystemUI. Intended for
Android 16 on Pixel 8 Pro with Infinity-X; also targets Android 12 and later.

## Styles

- **Filled circle:** a round icon filled from the bottom according to charge.
- **Dashed circle:** a 24-segment ring that tracks charge.
- **Circle:** a continuous ring that tracks charge.

The icon colour changes continuously: red at 0%, amber at 50%, green at 100%.
Each style also has a subtle top-to-bottom highlight and a percentage at its
centre. A tiny dot indicates charging. The app previews any level, but the
SystemUI icon always reads the actual battery broadcast. No root commands or
Magisk overlay are used by this APK.

## Build on GitHub

1. Create an empty GitHub repository and upload the **contents of this folder**
   at the repository root. Keep `.github/workflows/build.yml` in place.
2. Open **Actions → Build Battery Gradient APK → Run workflow**.
3. When the job finishes, open its run and download the artifact named
   **BatteryGradient-v1.0.6** at the bottom of the run summary. Inside is
   `BatteryGradient-v1.0.6.apk`.

The source archive is `BatteryGradient-v1.0.6.zip`. GitHub downloads the
artifact as a ZIP with that same name; its contents are the versioned APK.

The app's version name and code live in `gradle.properties`. The workflow uses
the same version for the downloadable artifact and APK filename. There is no
run or attempt suffix.

The build uses Java 17, Gradle 8.11.1, Android Gradle Plugin 8.7.3, Android
platform 35 and build tools 35.0.0. The runner's existing command-line SDK
installs the exact platform and build-tools packages. No `tools` package or
`android-actions/setup-android` invocation appears in this workflow. The APK
uses a bundled, openly shared **debug-only signing key** to keep future builds
upgradable. Do not treat this key as a production signing secret.

**Upgrading from 1.0.0:** GitHub may sign the old APK with a different temporary
debug key. If Android rejects the update with a signing or update-incompatible
error, uninstall Battery Gradient 1.0.0, install 1.0.6, re-enable its System UI
scope in LSPosed, and restart System UI. Later builds from this source use the
same debug signing key. Versions 1.0.1 through 1.0.4 can update directly to 1.0.6.

## Install and use

1. Install the versioned debug APK, then enable **Battery Gradient** in LSPosed.
2. Scope it to **System UI** (`com.android.systemui`) only.
3. Restart System UI once (or reboot) after enabling the module.
4. Open **Battery Gradient** in your launcher and choose a style. Later style
   changes take effect live; the percentage and colour follow battery updates.

Disable Iconify's **custom battery style** and any other battery-icon module
while trying this one, so both modules do not alter the same SystemUI view.
If your ROM shows a separate percent outside the battery icon, turn off that
ROM battery percentage option to avoid displaying it twice.

To undo: disable Battery Gradient in LSPosed and restart System UI. The module
only adds a view inside the existing `BatteryMeterView`, and restores the
native children when that view detaches. It does not install an overlay.

## Compatibility notes

The primary hook targets `PhoneStatusBarViewController.onViewAttached` and adds
the icon to `system_icons`. The `BatteryMeterView` hook is also active as a
fallback when the controller exists but its view path differs. Open the app
after reboot and read **System UI:** at the bottom. If it says "No System UI
event received yet", verify that the LSPosed module and its System UI scope
are enabled. Otherwise share that line and the LSPosed log lines containing
`BatteryGradient` for diagnosis. There is no device
build/test in this package; a successful Actions compile does not prove
behavior on a specific Infinity-X release. If it does not appear, capture
LSPosed logs containing `BatteryGradient` and your Android/ROM build details.

The implementation was informed by [Iconify's battery view manager](https://github.com/Mahmud0808/Iconify/blob/main/app/src/main/java/com/drdisagree/iconify/xposed/modules/statusbar/BatteryStyleManager.kt)
and its battery style options. All Java drawing code in this package is new;
no Iconify source files or resources are bundled.
