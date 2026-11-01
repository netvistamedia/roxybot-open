# Spec — camera-tool

Module `com.proxy.camera` · bron `app/src/main/kotlin/com/proxy/camera/` · spec-ID's: CT-x

## Doel
Foto's maken op spraakcommando, zonder bevestigingsstap. "Selfie" is de frontcamera,
"foto" de achtercamera, en bij twijfel wint de achtercamera.

## Lenskeuze — bindende tabel
| Uiting (intent van de bot) | Lens |
|---|---|
| selfie | LENS_FACING_FRONT |
| foto | LENS_FACING_BACK |
| onbepaald / overig | LENS_FACING_BACK (default) |

## Gedrag
- CT-1 MOET: CameraX; bij botactie `photo {lens}` opent de zoeker full-screen ≤500 ms na ontvangst — lokaal pad, het denken zat al bij de bot.
- CT-2 MOET: geen bevestigingskaart, nooit — regel 2 zondert de camera expliciet uit.
- CT-3 MOET: sluiter via tik op het scherm óf lokaal herkend "klik"; vastleggen ≤400 ms na de trigger.
- CT-4 MOET: na vastleggen 2 s miniatuurbevestiging, daarna terug naar IDLE; verder praten sluit de zoeker direct (CONTENT-regel LS-4).
- CT-5 MOET: opslag in MediaStore (Pictures/Proxy), JPEG; flits automatisch, frontlens gebruikt schermverlichting.
- CT-6 MAG NIET: locatie in EXIF schrijven, en MAG NIET foto's automatisch naar bot of server sturen; delen gebeurt alleen op expliciet verzoek (v2).
- CT-7 MOET: ontbreekt de camerapermissie → gesproken uitleg + systeemdialoog; nooit stil falen.
- CT-8 MOET: de zoeker toont nul tekst; alleen beeld en een subtiele sluiterindicatie.

## Buiten scope (v1)
Video, zoomgebaren, HDR-instellingen, delen of versturen van foto's.

## Raakvlakken
launcher-shell: CONTENT-staat en sluitregel LS-4 · bot: actie `photo` met lensveld volgens de tabel.

## Acceptatie
Testklasse `com.proxy.camera.CameraToolSpec`: per CT-MOET-regel één test, genoemd naar het ID. Kerninvariant: de lenstabel, inclusief default-achtercamera bij onbepaalde intent.

## Verify
Faalt nu; slaagt pas als de lenstabel en het gedrag door tests gedekt zijn en de permissie gedeclareerd is:

    ./gradlew :app:testDebugUnitTest --tests "com.proxy.camera.CameraToolSpec" && grep -q "android.permission.CAMERA" app/src/main/AndroidManifest.xml
