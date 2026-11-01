# Drip, working notes

Vaste afspraken voor dit project. Lees dit voordat je iets aanpast.

## Wat dit is

Een dynamisch eiland voor Android, in de vorm van een notch voor telefoons zonder cameragat (Sony Xperia 1 VI)
en als pil rond het cameragat op telefoons die er een hebben (Pixel 10). Het past zich vanzelf aan: de vorm komt
uit de cutout, de statusbalk en de schermhoeken van het toestel, niet uit vaste maten.

```
app/src/main/java/nl/markmaaktmedia/drip/
  island/     het eiland: vorm, overlay (AccessibilityService), animatie, gebaren, haptiek, kaarten
  sources/    wat het eiland voedt: mediasessies, meldingen, gesprekken, batterij, zaklamp, systeemsignalen
  data/       DripPrefs: alle instellingen, IslandSettings als één waarde
  ui/         de instellingen-app (Compose Material 3 Expressive)
  update/     de updater van Tandem: GitHub-releases, PackageInstaller
```

Het eiland leest alleen `IslandHub` (state flows) en `IslandSettings`. Bronnen schrijven in de hub en weten niets
van het eiland. Zo kan de debug-demo een telefoongesprek nadoen.

## Schrijfstijl

- **Nooit em-dashes of en-dashes.** Ook niet in code, comments, commits of UI-teksten. Komma, punt of haakjes.
- Comments leggen uit waarom, niet wat. Kort houden, geen AI-riedels.
- Engels als basis (`values`), Nederlands in `values-nl`. Elk scherm of onderdeel heeft een eigen strings-bestand
  (`strings_island.xml`, `strings_sources.xml`, `strings_app.xml`).

## Designtaal

Zelfde taal als Tandem en MarkMaaktAI: Google Sans Flex met ROND 100, Material Symbols Rounded via `DripIcons`
(nooit `Icons.Rounded`), gegroepeerde slabs (24dp buiten, 4dp binnen, 2dp ertussen), pure zwart in het donker,
alles beweegt via `DripMotion` (springs voor positie en grootte, tweens voor kleur en opacity), `bouncyClickable`.
Material 3 Expressive (1.5): `MaterialShapes`, golvende voortgang, button groups, `MotionScheme.expressive()`.

**Het eiland zelf:**
- Eén doorlopende vorm (`IslandShape`), geanimeerd via getallen (breedte, hoogte, bovenkant, schouder, hoeken), nooit
  door vormen te wisselen. Animatie in de teken- en layoutfase, niet via recomposition en nooit `updateViewLayout`
  per frame. Het venster groeit voor het openen en krimpt na het sluiten.
- Bovenhoeken van een geopend eiland lopen concentrisch met de schermhoeken (straal scherm min marge). Onderhoeken
  mogen ronder. Knoppen op de onderste rij hebben buitenhoeken die concentrisch zijn met het eiland.
- Haptiek bij openen, sluiten, meldingen en gebaren (`Haptics`).

## Techniek

- Package `nl.markmaaktmedia.drip`, minSdk 31, compileSdk 36, arm64 only, JDK 21.
- Bouwen: `export ANDROID_HOME=$HOME/Library/Android/sdk JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`,
  dan `./gradlew --console=plain :app:testDebugUnitTest :app:assembleDebug`.
- Debug-demo (alleen debug-build): `adb shell am broadcast -a nl.markmaaktmedia.drip.DEMO --es kind music -p nl.markmaaktmedia.drip.debug`
  met `kind` = `music`, `call`, `incoming`, `charging`, `timer`, `alert`, `volume`, `torch`, `expand`, `collapse`, `clear`.
- Rechten in de emulator: `adb shell settings put secure enabled_accessibility_services nl.markmaaktmedia.drip.debug/nl.markmaaktmedia.drip.island.IslandService`,
  `adb shell settings put secure accessibility_enabled 1`,
  `adb shell cmd notification allow_listener nl.markmaaktmedia.drip.debug/nl.markmaaktmedia.drip.sources.DripListener`.
- Notch testen: een emulator zonder cutout. Pixel 10 nadoen: `adb shell cmd overlay enable com.android.internal.display.cutout.emulation.hole`.

## Signing en releases

- Release-sleutel `~/keystores/drip-release.jks`, wachtwoord in `drip-release.password` ernaast, als GitHub-secrets
  `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Nooit kwijtraken.
- Taggen (`vX.Y.Z`) bouwt en publiceert `Drip.apk`. Repo `Marukiee/Drip` is publiek, anders werkt de updater niet.
- `changelog.json` heel kort houden. Nieuwe wensen op `docs/ROADMAP.md`.
- Nooit `git add -A` met half werk. Agents committen met expliciete paden en pushen of taggen nooit.

## Werkwijze

Fase 1 vragen, fase 2 doorbouwen zonder te stoppen. Bij twijfel kiezen en achteraf melden.
