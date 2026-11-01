# Spec — lipsync

Module `com.proxy.lipsync` · bron `app/src/main/kotlin/com/proxy/lipsync/` · spec-ID's: LP-x
Gezicht: `com.proxy.launcher.ParticleFace` (zie ook launcher-shell, "Schermstaten").

## Doel
De mond van het hologramgezicht laat zien wát de stem zegt, niet alleen hóe hard: per 10 ms een
mengsel van de 15 Oculus/Meta-visemen (sil, PP, FF, TH, DD, kk, CH, SS, nn, RR, aa, E, ih, oh, ou),
synchroon met de audio, ongeacht de bron. Nu: de xAI-TTS-clip (mp3). Later: Gemini Live (24 kHz
PCM-stroom) of elke andere bron die PCM levert.

## Keuze van de viseembron (onderzoek 2026-11-01)
| Kandidaat | Bevinding | Bruikbaar? |
|---|---|---|
| xAI TTS (`/v1/tts`) | Levert alleen audio. De WebSocket-variant kent `with_timestamps` (tekens, in bursten), geen visemen of fonemen. | Alleen als latere tekstbron |
| Gemini Live | Alleen 24 kHz PCM plus een losse, niet-uitgelijnde transcriptie. Geen visemen, fonemen of woordtijden. | Nee |
| OpenFaceFX (MIT, alpha) | Python/numpy; zet *foneemtijden* om in viseemcurves met coarticulatie. Leest zelf geen audio; Android/Kotlin ontbreekt. | Alleen als curve-laag bovenop een foneembron |
| lipsync-engine (MIT) | Browser-JavaScript, FFT-bandregels, 12 van de 15 visemen bereikbaar, geen look-ahead, drie commits. | Nee, wel als voorbeeld van de aanpak |

Conclusie: geen van de bronnen levert visemen voor beide backends, dus de telefoon leidt ze zelf
af uit de audio. Dat is backend-onafhankelijk, werkt zonder server (privacy, geen extra
latentie) en past bij een hologramgezicht, dat geen fotorealistische precisie vraagt.

## Architectuur
- `VisemeAnalyzer` + `VisemeAnalyzerFactory`: PCM in, `VisemeFrame`s (10 ms, gewichten + luidheid) uit. Bronnen in `VisemeAnalyzers`: `Spectral` (standaard) en `Amplitude` (vangnet, oude klep-animatie).
- `SpectralVisemeAnalyzer`: Hann-venster (≥32 ms) → FFT → luidheid (adaptief op de piek), zwaartepunt, bandverdeling, nuldoorgangen, stemhebbendheid (autocorrelatie via het spectrum, geeft ook f0), pre-emfase, F1/F2 uit de gladgestreken omhullende. Regels: stil → sil; stemloos → SS/CH/FF naar zwaartepunt; stemhebbend met alles onder 1 kHz → nn/PP; anders klinker als mengsel van aa/E/ih/oh/ou op afstand tot prototypen in de log-F1/F2-ruimte. Korte gaten (≤150 ms) midden in spraak worden achteraf sluitingen (PP/DD/kk naar de inzet erna).
- `VisemeTrack`: tijdlijn per clip of stroom; `at(positionMs)` mengt buren binnen ±25 ms (coarticulatie); na `close()` sluit de mond voorbij het einde.
- `VisemeAudioPlayer`: speelt PCM via AudioTrack en voert dezelfde samples eerst door de analyse. Twee ingangen, één pad: `play(mp3)` (MediaCodec, in het geheugen) en `openStream(sampleRate)` → `PcmStreamSink.write/finish` (Gemini Live). De afspeelpositie komt uit `AudioTimestamp` (gehoorde positie, dus inclusief uitvoerlatentie).
- Launcher: `Speaker.visemes()` → `ShellScreen(botVisemes)` → `ParticleFace`. `LipSyncClipPlayer` valt terug op `MediaPlayerClipPlayer` als decoderen mislukt. `FaceRig` (gewichten per deeltje), `VisemeShapes` (doelstanden), `FaceAnimator` (idle en dynamiek), `FaceMood` (stemming uit tekst en uitkomst), `HologramStyle`.

## Regels
- LP-1 MOET: visemen komen uit de audio zelf; elke bron die 16-bit PCM levert gaat door hetzelfde pad (clip én stroom).
- LP-2 MOET: de viseembron is vervangbaar via `VisemeAnalyzerFactory`; `AppGraph` kiest er één. Een nieuwe backend (bijv. tekst + tijdstempels) implementeert `VisemeAnalyzer` of levert `VisemeFrame`s aan `VisemeTrack`.
- LP-3 MOET: de analyse loopt vóór op het geluid (afspeelbuffer ≥ 300 ms); de weergave vraagt het frame op de werkelijke afspeelpositie. Extra vertraging door lipsync: ≤ 1 frame (10 ms) plus de tekenlus.
- LP-4 MOET: een kort gat (stilte of dip ≤ 150 ms) tussen luide spraak is een sluiting (PP/DD/kk); een woordpauze blijft sil.
- LP-5 MOET: zonder visemen (Android-stem, decodeerfout) beweegt de mond op luidheid of op een gesimuleerd spraakritme; de stem valt nooit weg door lipsync.
- LP-6 MOET: niets op schijf (LS-11): mp3, PCM en frames blijven in het geheugen.
- LP-7 MOET: het gezicht is een hologram: gestippeld (dichtheid en deeltjesgrootte volgen de helderheid van het masker), één tint diepblauw → wit-cyaan, additieve gloed op de lichtste deeltjes, scanband, lichte flikkering, zeldzame korte storing. Idle: ademen (5 s), hoofdwiegen, knipperen (2,5–6,5 s, vaak bij het begin van spreken), blikverschuiving. Expressies: glimlach/frons (mondhoeken, wangen, binnenste wenkbrauwen) uit de stemming, wenkbrauwflits bij nadruk.
- LP-8 MAG NIET: audio naar een extra dienst sturen om visemen te krijgen.

## Gemini Live (nog niet in de app)
De spraak van Gemini Live komt als base64-PCM (24 kHz, 16-bit, mono) in `serverContent`-berichten.
Aansluiting: bij het eerste audiobericht `sink = player.openStream(24_000) { speaking = false }`,
per bericht `sink.write(pcm)`, bij `turnComplete` `sink.finish()`, bij onderbreking (`interrupted`)
`player.stop()`. `Speaker.speaking` en `visemes()` werken dan ongewijzigd; het gezicht ziet geen
verschil met een xAI-clip. Komt het antwoord als tekst, dan spreekt de bestaande TTS-route het uit.

## Beperkingen
- `TH` en `RR` zijn met audio alleen niet te scheiden van FF/DD en klinkers en komen niet voor; E/ih en oh/ou liggen dicht bij elkaar en mengen.
- Prototypen zijn afgesteld op vrouwenstemmen (macOS Samantha/Ellen, vergelijkbaar met "ara"); mannenstemmen ("sal", "leo", "rex") liggen ±15 % lager en mengen vaker naar E/oh.
- Neusklanken worden streng herkend (liever een iets open mond dan een dichte mond midden in een klinker).
- De Android-stem levert geen audio aan de app en houdt het gesimuleerde ritme (mogelijke uitbreiding: `synthesizeToFile` naar een pipe → hetzelfde PCM-pad).
- Afstelling gebeurde op macOS-TTS-clips, niet op "ara" zelf: `ROXY_SPEECH_DIR=<map met wav> ./gradlew :app:testDebugUnitTest --tests "com.proxy.lipsync.SpeechClipReport"` schrijft per clip de viseemreeks met meetwaarden (zie `SpeechClipReport`).

## Acceptatie
`com.proxy.lipsync.*` (analyse, tijdlijn, FFT, stroom in blokken) en `com.proxy.launcher.ParticleFaceTest`
(doelstanden, rig, knipperen, stemming) dekken de LP-regels; `IdleScreenshotTest` rendert visemen en
expressies naar `app/build/screenshots/face-*.png`. Op het toestel: debug-broadcast
`adb shell am broadcast -a com.proxy.debug.SPEAK --es text "Hallo" -n com.proxy/.launcher.DebugVoiceReceiver`
laat de bot een tekst uitspreken; logcat-tag `RoxyGrok` toont `lipsync:`-regels (formaat, buffer, duur, aantal frames).

## Verify
    ./gradlew :app:testDebugUnitTest --tests "com.proxy.lipsync.*" --tests "com.proxy.launcher.ParticleFaceTest" && grep -q "LipSyncClipPlayer" app/src/main/kotlin/com/proxy/RoxyBotApplication.kt
