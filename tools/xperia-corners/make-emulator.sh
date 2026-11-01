#!/bin/zsh
# Makes an emulator shaped like the Sony Xperia 1 VI: 1080 x 2340 at 400 dpi, no camera hole, and the screen corners of
# 16 dp that Sony sets in its own firmware (vendor and product overlays of pdx245). The corners go in as a static system
# overlay, the way Sony does it, because the shell can only fabricate overlays for colours and numbers.
set -euo pipefail
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Library/Android/sdk}
export PATH=$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$PATH
here=${0:A:h}
avd=drip_xperia
port=${PORT:-5602}
export ANDROID_SERIAL=emulator-$port
bt=$ANDROID_HOME/build-tools/36.0.0
work=$(mktemp -d)

echo no | avdmanager create avd -n $avd -k "system-images;android-36;google_apis;arm64-v8a" -d medium_phone --force >/dev/null
config=$HOME/.android/avd/$avd.avd/config.ini
sed -i '' -e 's/^hw.lcd.width=.*/hw.lcd.width=1080/' -e 's/^hw.lcd.height=.*/hw.lcd.height=2340/' -e 's/^hw.lcd.density=.*/hw.lcd.density=400/' \
  -e 's/^hw.gpu.enabled=.*/hw.gpu.enabled=yes/' -e 's/^hw.gpu.mode=.*/hw.gpu.mode=host/' $config

$bt/aapt2 compile --dir $here/res -o $work/res.zip
$bt/aapt2 link -o $work/unsigned.apk -I $ANDROID_HOME/platforms/android-36/android.jar --manifest $here/AndroidManifest.xml $work/res.zip
$bt/zipalign -f 4 $work/unsigned.apk $work/aligned.apk
$bt/apksigner sign --ks $HOME/.android/debug.keystore --ks-pass pass:android --key-pass pass:android --out $work/DripXperiaCorners.apk $work/aligned.apk

boot() { adb wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'; }
emulator -avd $avd -writable-system -no-snapshot-save -no-audio -no-boot-anim -gpu host -port $port >/dev/null 2>&1 &
boot; adb root >/dev/null; sleep 3; adb wait-for-device; adb remount >/dev/null; adb reboot; sleep 8; boot
adb root >/dev/null; sleep 3; adb wait-for-device; adb remount >/dev/null
adb shell mkdir -p /product/overlay/DripXperiaCorners
adb push $work/DripXperiaCorners.apk /product/overlay/DripXperiaCorners/ >/dev/null
adb shell chmod 644 /product/overlay/DripXperiaCorners/DripXperiaCorners.apk
adb reboot; sleep 8; boot
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell dumpsys display | grep -oE "roundedCorners [^]]*\]" | head -1
echo "Ready: $ANDROID_SERIAL. Start it again later with: emulator -avd $avd -writable-system -gpu host -port $port"
