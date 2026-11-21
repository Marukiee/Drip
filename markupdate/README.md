# markupdate

De updater en de updatekaart van al Marks apps, in één map die in elke app **precies hetzelfde** is.

> **Niet aanpassen in een app.** Elke kleine afwijking maakt de apps weer verschillend. Wijzigingen worden alleen in **Keeper**
> (`android/markupdate`) gemaakt, getest en gecommit, en daarna met `sync.sh` naar alle apps gekopieerd. In een app staat daarom een
> bestand `markupdate/BRON` met de Keeper-commit waar de kopie vandaan komt.

## Wat het doet

Zoekt de nieuwste release op GitHub (`releases/latest`), laat een kaart bovenaan de app zien ("Nieuwe versie X", twee regels uit de
releasetekst, Later en Bijwerken), downloadt het apk met een controle op de SHA-256, en installeert het via een PackageInstaller-sessie met
`USER_ACTION_NOT_REQUIRED`. Zonder tweede tik: Android vraagt de eerste keer zelf om een bevestiging en daarna niet meer, zodra de app de
installer van zichzelf is.

## Twee projecten in één map

| Project | Wat | Heeft Compose nodig |
|---|---|---|
| `:markupdate:core` | `MarkUpdate`, `MarkUpdater`, `UpdateState`, de ontvanger van de installer, de releasetekst, teksten voor meldingen en foutredenen (en/nl) | nee |
| `:markupdate:card` | `UpdateCard` en `UpdateCardStyle`, teksten van de kaart (en/nl); haalt de kern mee | ja |

Een app zonder Compose (MarkMySteps, Capacitor) neemt alleen de kern.

## Instellen in een app

`settings.gradle.kts` (of `settings.gradle`, dan `include ':markupdate:core'`):

```kotlin
include(":markupdate:core", ":markupdate:card")
```

`app/build.gradle.kts`:

```kotlin
implementation(project(":markupdate:card"))   // zonder Compose: ":markupdate:core"
```

In `Application.onCreate` (de ontvanger van de installer gebruikt hetzelfde exemplaar, ook als Android het proces er alleen voor wakker maakt):

```kotlin
MarkUpdate.init(
    this,
    MarkUpdateConfig(
        owner = "Marukiee", repo = "Keeper", apkAsset = "Keeper.apk",
        currentVersion = BuildConfig.VERSION_NAME, appName = "Keeper", notificationIcon = R.drawable.ic_stat_keeper,
    ),
)
```

Bij het openen van de app (bijvoorbeeld in de root van de UI) en bovenaan het hoofdscherm:

```kotlin
LaunchedEffect(Unit) { MarkUpdate.get().checkIfDue() }      // hooguit elke 6 uur, en alleen als autoCheck aan staat
UpdateCard(Modifier.padding(top = 8.dp))                    // of UpdateCard(style = UpdateCardStyle(successColor = ...))
```

Een instellingenrij "Nu controleren" gebruikt `MarkUpdate.get().check()`, `state` en `autoCheck` / `setAutoCheck(on)`.

De module brengt zelf mee (via de manifest-merge): `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, `INTERNET`,
`POST_NOTIFICATIONS` en de ontvanger `InstallResultReceiver`. Een app declareert die niet zelf en heeft er geen eigen ontvanger voor.

`UpdateCardStyle` is voor wat per app anders is: de vorm van de kaart, het groen van het vinkje, hoe ver een knop krimpt en `UpdateCardMotion`
(veer- en tweenwaarden). Alles is standaard Keeper. Kleuren, lettertypen en vormen komen uit `MaterialTheme`.

### Vanuit Java (zonder Compose)

```java
MarkUpdater updater = MarkUpdate.init(application,
    new MarkUpdateConfig("Marukiee", "MarkMySteps", "MarkMySteps.apk", BuildConfig.VERSION_NAME, "MarkMySteps", R.drawable.ic_stat, "update", 6L * 60 * 60 * 1000));
updater.checkIfDueAsync();
Closeable stop = updater.addListener(state -> { /* UpdateState: Available, Downloading, Installing, Failed, ... */ });
updater.startUpdate(release);   // release uit UpdateState.Available
```

## Wat een release nodig heeft

Een tag `v*` met het apk als asset `<apkAsset>` (en `<apkAsset>.sha256`, met de som en de bestandsnaam zoals `sha256sum` het schrijft). De kaart toont de
eerste twee punten uit de releasetekst, in de taal van de app als de tekst een deel `## Wat is nieuw (Nederlands)` heeft, anders Engels:
schrijf ze dus kort, ze worden op één regel afgekapt.

## Wijzigen en syncen

1. Wijzig in Keeper, draai de tests (`:markupdate:core:testDebugUnitTest`, `:app:testDebugUnitTest`) en bekijk de kaart op een emulator.
   Zonder echte release: `MarkUpdate.get().showPreview()` toont een nepversie 9.9.9 (22 MB) die je met Bijwerken door alles heen kunt laten lopen,
   `showFailurePreview("...")` toont de foutkaart. Keeper heeft daar een debugcommando voor (`--es update preview|fail|clear`, zie CLAUDE.md).
2. Commit in Keeper. `sync.sh` weigert een map met niet-gecommitte wijzigingen.
3. Per app: `android/markupdate/sync.sh <android-map van de app>`. Dat kopieert de map ongewijzigd (zonder `build/` en `.gradle`), schrijft `BRON` en
   drukt af wat de app zelf nog moet doen (de `include`, de dependency en de `init`). Het script past de settings of build van de app nooit aan.
4. `sync.sh --check <android-map van de app>` vergelijkt de kopie met Keeper en stopt met een fout bij elk verschil. Draai het als je twijfelt of een app nog gelijk is.

Het script weigert te draaien vanuit een kopie (een map met `BRON`).

## Eisen aan de app

- Android Gradle Plugin 8.x met de Kotlin-plugin (`org.jetbrains.kotlin.android`) op de classpath van de root; de kaart ook `org.jetbrains.kotlin.plugin.compose`.
  De buildbestanden van de module zijn zelfstandig: geen `libs.`-verwijzingen, geen `repositories {}`, plugins zonder versie.
- `minSdk` van de app 24 of hoger, `compileSdk` 36 of hoger. De module gebruikt `compileSdk = 36`, `minSdk = 24`, Java 17.
- **AGP 9 (GuitarTuner)** heeft Kotlin ingebouwd en weigert (of hoeft niet) de plugin `org.jetbrains.kotlin.android`: de buildbestanden van de module
  zijn voor AGP 8 en niet getest met AGP 9. Daar moet eerst in Keeper een variant bij.
- **MarkMySteps (Capacitor, Java, Groovy-Gradle)** heeft geen Kotlin-plugin op de classpath: voeg in de root `build.gradle` bij `buildscript.dependencies`
  `classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21'` toe, dan kan `:markupdate:core` bouwen (hij komt met zijn eigen Kotlin-stdlib).

## Lessen (uit Keeper en MarkMySteps)

1. **Eigen scope.** De download draait in een scope van de updater, niet in `rememberCoroutineScope` van een scherm, ook het doorgaan na het
   scherm "onbekende apps installeren". Anders breekt wisselen van tab de download af en toont de kaart een fout.
2. **Succes zet de kaart uit.** `STATUS_SUCCESS` van de installer zet de toestand terug naar `Idle`. Leeft het proces nog (een debug-build installeert de
   release als tweede app), dan blijft de kaart anders op "Installeren…" staan.
3. **Geen herstart beloven.** Android sluit een app die zichzelf bijwerkt en start hem niet vanzelf opnieuw; ook een `MY_PACKAGE_REPLACED`-receiver
   mag het scherm niet vanuit de achtergrond openen ("Background activity launch blocked"). De tekst is daarom "De app sluit zo even, daarna is hij
   bijgewerkt". Een receiver mag hooguit achtergronddiensten weer starten.
4. **Stil installeren** werkt pas als de app zelf de vorige versie installeerde. Een versie die met `adb install -i ...` is neergezet telt niet als door
   de app zelf geïnstalleerd: de eerstvolgende update vraagt dan nog één keer.
5. **Tempo.** Android remt als dezelfde app zichzelf binnen ongeveer 30 seconden twee keer bijwerkt; dan komt de systeemvraag toch. De kaart handelt dat
   af: de vraag opent vanzelf (staat de app niet voorin, dan komt er een melding en opent hij bij terugkomen; de balk is dan tikbaar).
6. **Debug naast release.** Een debug-build heeft een ander package dan de release. De module telt een `-debug`-achtervoegsel niet mee bij de
   versievergelijking, maar installeert de release wel als aparte app. Een echte update zie je pas met de release over de release.
7. **iOS (TestFlight)** toont geen kaart: de app kan geen nieuwere build zien.
8. Of een stille update doorgaat terwijl de app voorin staat is niet bekeken; zo niet, dan opent de systeemvraag vanzelf.
