# Drip

A dynamic island for Android, shaped for your phone. On a phone without a camera hole, like the Sony Xperia 1 VI, it is a
notch that hangs from the top edge of the screen. On a phone with one, like the Pixel 10, it is a pill around the camera.
The shape comes from the phone itself: its camera cutout, its status bar and the radius of its screen corners, so an opened
island keeps an even gap to the rounded edge of the screen.

What it shows: music with cover, a wavy progress line, controls and a volume bar; phone calls with their buttons; timers,
rides, deliveries and other live updates; charging; the torch with its strength; notifications; the camera and microphone
dots; and short announcements for volume, ringer, do not disturb, rotation lock, battery saver, headphones and more.

Tap to open, swipe up or tap beside it to close, hold or swipe for your own actions. It moves on springs, at the refresh rate
of the screen, with haptic feedback that rises when it opens and falls when it closes.

## Install

Download `Drip.apk` from the [latest release](https://github.com/Marukiee/Drip/releases/latest). The app updates itself
from there.

Drip asks for two things: the accessibility service, which is the only way an app may draw above the status bar and is used
for the island and its gestures (it never reads the screen), and notification access, for music, calls and live updates.
Nothing leaves the phone.

## Build

JDK 21 and the Android SDK, then `./gradlew :app:assembleDebug`.

MIT licensed. Made by Mark Maakt Media.
