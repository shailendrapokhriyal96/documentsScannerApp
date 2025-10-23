# OpenCV Android AAR Required

This directory should contain the OpenCV Android AAR file.

## How to get OpenCV AAR:

1. Download OpenCV Android SDK from: https://opencv.org/releases/
2. Extract the downloaded file
3. Look for the AAR file in the extracted folder (usually named `opencv-4.x.x-android-sdk.aar`)
4. Copy the AAR file to this `libs` directory and rename it to `opencv.aar`

## Alternative - Use Maven dependency:

If you prefer to use Maven instead of local AAR, replace the `fileTree` line in `build.gradle` with:

```gradle
implementation 'org.opencv:opencv-android:4.8.0'
```

## Current setup:

The `build.gradle` is configured to use local AAR files:
```gradle
implementation fileTree(dir: 'libs', include: ['*.aar'])
```

This will automatically pick up any `.aar` files in this directory.
