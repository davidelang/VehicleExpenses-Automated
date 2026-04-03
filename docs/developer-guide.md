# Vehicle Expenses Automated — Developer Guide

## App Launcher Icon (new design)
The official launcher icon is the new design provided by the user (two cars + camera lens + green $ + symbol).

**Correct way to add it:**
Use Android Studio → New → Image Asset → Launcher Icons.

## Current Architecture (matches repo head da3a2b4f1829bf4dda99090b07105d3ba7134ce9)

### Permission Handling
- CAMERA is now requested in MainActivity onCreate.
- App continues to run even if permission is denied (photo features will be greyed out in future UI updates).

### Root Composable
- VehicleExpensesApp.kt: Simple NavHost root that launches QuickFillupScreen.

### Models
- Vehicle (with referenceDashPhotoUrl)
- FuelEntry

### Key Components
- **MainActivity.kt**: Permission request + VehicleExpensesApp root.
- **PhotoPicker.kt**: Camera-first flow.
- **OdometerOcrUtils.kt**: Automatic ML Kit OCR on every photo capture.
- **PhotoStorageManager.kt**: Handles both camera and gallery imports.
- **ImportOldPicturesScreen.kt**: Gallery-only with full automatic OCR.
- **QuickFillupScreen.kt**: Main screen with camera-first PhotoPicker + link to Import Old Pictures.

### OCR Flow
- On any photo capture: LaunchedEffect triggers OdometerOcrUtils.extractFromPhoto().
- Full extraction: odometer, gallons, cost.

### Build & Sync
- Use `./gradlew clean build` after every change.
- SyncWorker handles Google Sheets.

## Debugging with ADB logcat (Multiple Devices Attached)
When more than one device or emulator is connected:

1. List all attached devices:
    adb devices

2. Use the -s flag with the device serial number:
    adb -s <serial-number> logcat

Examples:
    adb -s emulator-5554 logcat
    adb -s 0123456789ABCDEF logcat

Optional filter to your app only:
    adb -s <serial> logcat | grep "com.davidelang.vehicleexpenses"

Tip: Open a separate terminal window for each device.

## Using Grok from the Command Line
Short answer: There is no official Grok CLI tool that lets you log in directly with your X account in the terminal.

Current status (March 2026):
- Use the Grok API at console.x.ai (log in with X and generate an API key).
- Community CLIs require the API key only.

How to use:
    npm install -g @superagent-ai/grok-cli
    export GROK_API_KEY=your-key-here
    grok "your question here"

## Managing Android Emulators from the Command Line
You can start and manage the exact same emulators (AVDs) already created in Android Studio.

1. List all available emulators:
    $ANDROID_HOME/emulator/emulator -list-avds
    (or just emulator -list-avds if in PATH)

2. Start a specific emulator:
    $ANDROID_HOME/emulator/emulator -avd <AVD_NAME>
    Example:
    $ANDROID_HOME/emulator/emulator -avd Pixel_6_API_34

3. Useful flags (add after -avd <name>):
    -wipe-data
    -no-snapshot
    -no-audio
    -gpu host

4. Stop a running emulator:
    adb -s emulator-5554 emu kill

5. See running emulators:
    adb devices

Tip: Add $ANDROID_HOME/emulator to your PATH.

See CONTRIBUTING.md for full contribution process.
