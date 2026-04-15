# Keyboard Toggle Plan

## What this app does

This project creates a tiny Android app whose launcher activity has no visible UI. When Zebra launches that shortcut, the activity immediately exits and asks an always-enabled accessibility service to toggle the soft keyboard inside the current foreground app.

The service works like this:

1. If the keyboard window is already visible, it sends `BACK` only in that case, so it hides the IME without navigating away when the IME is not open.
2. If the keyboard is hidden, it finds the currently focused editable field and clicks or taps that field to bring the IME up.
3. If the shortcut activity briefly steals focus and the live accessibility node is no longer available, it can safely fall back to the last known editable field coordinates, but only when the same foreground app is still active.
4. It forces `SHOW_MODE_IGNORE_HARD_KEYBOARD` so Android is willing to show the soft keyboard even on rugged hardware that may report a physical keyboard.

This approach is required on Android 13 because a normal background app cannot reliably call `InputMethodManager.showSoftInput(...)` into another app's focused window.

## Files in this repo

- `app/src/main/java/com/bbecker/whmkeyboardtoggle/TriggerProxyActivity.kt`: no-UI shortcut entry point.
- `app/src/main/java/com/bbecker/whmkeyboardtoggle/KeyboardToggleAccessibilityService.kt`: background accessibility service that toggles the IME.
- `app/src/main/res/xml/keyboard_toggle_accessibility_service.xml`: accessibility service capabilities.
- `app/src/main/AndroidManifest.xml`: app and service registration.

## Manual build and install

Because this machine does not have Gradle or an Android SDK configured, the repo currently contains the project source but not a built APK. For a single-device test, do this manually:

1. Install Android Studio on your Windows machine if it is not already installed.
2. Open this repo folder in Android Studio.
3. Let Android Studio install the required SDK pieces when prompted.
   - Install at least Android SDK Platform 34.
   - Use the bundled JDK that ships with Android Studio.
4. Build the debug APK.
   - Menu: `Build` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)`
5. Connect the Zebra TC8300 with USB debugging enabled.
6. Install the APK.
   - Android Studio can install directly with `Run`.
   - Or use `adb install -r app\build\outputs\apk\debug\app-debug.apk`.

## Device setup

1. On the TC8300, open `Settings` -> `Accessibility`.
2. Find `Keyboard Toggle Service` and turn it on.
   - This is required because the accessibility service performs the cross-app keyboard toggle.
3. Confirm the accessibility permission prompt.
4. Open any app and place the cursor in a normal text field.
5. In Zebra's button mapping tool, map `GRIP_TRIGGER_2` to launch the `Keyboard Toggle` app shortcut.
6. Press the trigger.
   - First press when a text field is active: keyboard should open.
   - Next press while keyboard is visible: keyboard should hide.
   - The foreground app should remain in place after the helper activity finishes.

## Expected limitations

- If the accessibility service is disabled, the shortcut will do nothing.
- If an app renders its input area in a custom surface or other accessibility-invisible control, the service may not be able to find the active text field. In that case, the next fallback would be a device-specific coordinate tap strategy.
- The helper intentionally does not open the keyboard when no editable field is available, because blindly sending `BACK` or arbitrary taps would be risky in a warehouse workflow.

## Debug logging

Debug builds write useful events to Android logcat with these tags:

- `KeyboardToggleSvc`
- `TriggerProxyActivity`

Debug builds also mirror those entries into a plain text file on the device:

- `Downloads/KeyboardToggle/keyboard-toggle.log`

Release builds are intended to stay silent and do not write routine logs or the device log file.

Examples:

- `adb logcat -s KeyboardToggleSvc TriggerProxyActivity`
- `adb logcat | findstr /I "KeyboardToggleSvc TriggerProxyActivity"`

## First test checklist

1. Install and enable the accessibility service.
2. Open an app with a normal text field.
3. Tap into a normal text field.
4. Press `GRIP_TRIGGER_2`.
5. Verify that the IME opens and closes without leaving the current app.

Use this to build debug:
```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug --console=plain --no-daemon
```

Use this to build an installable release-variant APK for local testing:
```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$sdkRoot = 'C:\Users\bbecker\AppData\Local\Android\Sdk'
$buildTools = Get-ChildItem "$sdkRoot\build-tools" -Directory | Sort-Object Name -Descending | Select-Object -First 1
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
$unsigned = '.\app\build\outputs\apk\release\app-release-unsigned.apk'
$signed = '.\app\build\outputs\apk\release\app-release-debugsigned.apk'
.\gradlew.bat assembleRelease --console=plain --no-daemon
& $apksigner sign --ks "$HOME\.android\debug.keystore" --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android --out $signed $unsigned
```

That produces `app\\build\\outputs\\apk\\release\\app-release-debugsigned.apk`.

`assembleRelease` by itself only produces `app-release-unsigned.apk`, which is not installable until it is signed.