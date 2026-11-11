# Drip

[![Release](https://img.shields.io/github/v/release/Marukiee/Drip?label=release)](https://github.com/Marukiee/Drip/releases/latest)
[![License](https://img.shields.io/badge/license-AGPL--3.0-blue)](LICENSE)
[![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)](#install)

English | [Nederlands](README.nl.md)

**A dynamic island for Android.** Music, calls, timers, live updates and notifications show up in one pill at the top of your screen. It opens with a tap and moves on springs at the refresh rate of your screen, with haptic feedback that rises when it opens and falls when it closes.

The shape comes from your phone: its camera cutout, its status bar and the radius of its screen corners, so an opened island keeps an even gap to the rounded edge. On a phone with a camera hole, like the Pixel 10, it is a pill around the camera. On a phone without one, like the Sony Xperia 1 VI, it is a notch that hangs from the top edge.

## What it does

- **Music.** Cover, a wavy progress line, controls and a volume bar
- **Calls.** Answer and hang up from the island
- **Live updates.** Timers, rides, deliveries and other updates from your apps
- **Charging and torch.** Charging status, and the torch with its strength
- **Notifications.** Shown on the island, with the camera and microphone dots when an app uses them
- **Short announcements.** Volume, ringer, do not disturb, rotation lock, battery saver, headphones and more
- **Your own actions.** Tap to open, swipe up or tap beside it to close, hold or swipe for actions you choose
- **Out of the way.** It shows nothing when there is nothing to show, and steps aside in full screen apps

## Install

1. Download `Drip.apk` from the [latest release](https://github.com/Marukiee/Drip/releases/latest) on your phone.
2. Open it. Android asks once whether the app you open it with (your browser or files app) may install apps from unknown sources. Turn that on, go back and tap Install.
3. Open Drip and follow the steps to give it the two permissions below.

After that you never need GitHub again: the app updates itself from the releases. You can also add the repository to [Obtainium](https://obtainium.imranr.dev).

### What Drip asks for

- **Accessibility service.** The only way an app may draw above the status bar. Drip uses it for the island, its gestures, and to see where windows are so it can step aside in full screen apps. In a music app that plays, it also looks for the seek bar and the song's title, so the island can make way for that app's own player. It reads nothing else on screen and keeps nothing
- **Notification access.** For music, calls and live updates

Nothing leaves the phone.

## Not supported yet

A camera in the corner of the screen. The closed island sits on the camera, so there it runs off the screen. Choose Notch
in the settings to keep it in the middle.

## Build

You need JDK 21 and the Android SDK.

```sh
./gradlew :app:assembleDebug
```

## License

AGPL-3.0, see [LICENSE](LICENSE). Made by Mark Maakt Media.
