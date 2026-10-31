# Fitbit Air Heart Rate Alerts

An Android app that reads the live heart rate of a Google Fitbit Air over Bluetooth and alerts you when you leave a target zone. It plays a sound, and it can also vibrate.

It is a fork of [Heart Alert](https://github.com/nikashitsa/heart-alert-android) by Nikita Verkhovin. This version has no ads, no payments and no Internet permission.

## What it does

- Connects to the band without other apps in the middle. It reads the standard Bluetooth Heart Rate Service, so other heart rate monitors should work too. Only the Fitbit Air was tested.
- Shows what is missing before the search: the permission and Bluetooth.
- Alerts you when your heart rate goes above or below the range. You type the range, or you pick a zone.
- Calculates zones Z1 to Z5 with the Karvonen formula. The resting heart rate comes from Health Connect, and the highest heart rate comes from your age. You can replace each value.

## Install

You need Android 13 or later. Build the app with JDK 21 and the Android SDK:

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Use

1. In Google Health, open Fitbit Air, then Share heart rate. Turn on Always visible.
2. Open the app, tap **Connect**, and choose the band.
3. Type your age, and tap a zone or type Min and Max.
4. Tap **Start**.

## Limits

- The app was tested on one phone only: a HONOR Magic6 Pro with Android 16.
- The app has no foreground service. Android can close it during a long session.
- The zones are an estimate and do not come from Google Health. The highest heart rate from age can be wrong by 10 BPM or more.
- This app is not a medical device. With a heart problem or medication, ask your doctor before you use the zones.
- This project has no connection to Google or Fitbit.

## License

[MIT](LICENSE)
