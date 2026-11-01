# Spec — launcher-shell

Module `com.proxy.launcher` · bron `app/src/main/kotlin/com/proxy/launcher/` · spec-ID's: LS-x

## Doel
Vervangt de Android-home door een pure agent-interface: donker scherm, één micknop,
spraak in en uit. Geen app-iconen, geen app-drawer, geen menu's, geen tekst in rust.

## Scope
In: HOME-rol, schermstaten, spraakopname, live transcriptie, bevestigingsframework,
full-screen contenthost, botkanaal, verborgen instellingen, betaalgedrag (niet-blokkeren).
Uit: in-call-logica (telephony-bridge), SMS (messaging), camera, navigatie, BioBASE (mcp-client).

## Schermstaten — state machine, één bron van waarheid
| Staat | Weergave |
|---|---|
| IDLE | warm donker #1C1813 met vignet (achtergrond van de Method4Life-pauze), dunne eigen statusstrip (tijd, batterij, signaal), in het midden de cirkel (altijd-luisteren: ademend, 9 s per ademhaling; push-to-talk: stil met microfoonicoon in de kern), daaronder "Houd ingedrukt voor instellingen". Geen losse micknop: tik op de cirkel = praten, lang drukken = instellingen |
| LISTENING | crème deeltjeszwerm (238,230,214) op mic-amplitude; live transcriptie in grote letters |
| THINKING | deeltjeszwerm in rust (verzameld, langzaam draaiend); laatste transcript blijft staan |
| CONFIRM | bevestigingskaart, zie onder |
| IN_CALL | alleen naam + timer + mute/ophangen (inhoud van telephony-bridge) |
| CONTENT | full-screen foto/kaart/document |
| SETTINGS | verborgen menu, exact zes opties |

Spreekt de bot (TTS) in IDLE of THINKING, dan verschijnt een hologramgezicht dat de cirkel
tijdelijk vervangt en 1,1 s na het einde vervaagt; nooit over CONFIRM, IN_CALL of CONTENT. Het
gezicht is een gestippelde deeltjeswolk uit een masker (`res/raw/face_mask_blue.png`, gemaakt met
`tools/face-mask/make_mask.py`): dichtheid en deeltjesgrootte volgen de helderheid, één tint, gloed,
scanband. De mond volgt de visemen uit de audio (spec lipsync): kaak, lipbreedte, ronding,
boven-/onderlip per Oculus-viseem, geschaald op de luidheid; zonder visemen (Android-stem) een
gesimuleerd spraakritme. Het gezicht ademt, wiegt licht, knippert, kijkt rond en toont glimlach of
frons naar de stemming van de tekst of de uitkomst.
*(Aangepast 2026-10-31: stip → ademende cirkel, waveform → deeltjes, overgenomen uit Method4Life;
kleizwerm van de bot → deeltjesgezicht. Aangepast 2026-11-01: kubusraster op spraakniveau →
hologram met visemen en expressies.)*

- LS-1 MOET: launcher-activity met intent-categorieën HOME en DEFAULT, `launchMode=singleTask`, `stateNotNeeded=true`.
- LS-2 MOET: systeembalken verborgen (immersive); eigen statusstrip leest klok, BatteryManager en TelephonyManager-signaal.
- LS-3 MOET (aangepast 2026-10-31): IDLE bevat buiten de statusstrip alleen de tekst "Houd ingedrukt voor instellingen" en, zolang de eerste-keer-instelling niet af is, een subtiele herinnering. *(Was: nul tekstelementen.)*
- LS-4 MOET: CONTENT sluit onmiddellijk bij overgang naar LISTENING — verder praten is sluiten.
- LS-5 MAG NIET: app-drawer, iconen, widgets of notificatie-shade-vervanging bevatten.

## Spraak
- LS-6 MOET (aangepast 2026-10-31): twee modi, keuze in instellingenoptie 4, standaard altijd-luisteren. In beide modi start een tik op de cirkel het luisteren; altijd-luisteren toont een ademende cirkel, push-to-talk een stille cirkel met microfoonicoon. Het on-device hotword ("Roxy", Vosk) volgt als eigen stap; audio verlaat het toestel dan pas ná het hotword.
- LS-7 MOET: live transcriptie via on-device partials (SpeechRecognizer); definitieve intentbepaling door de bot. Partials op scherm ≤300 ms na spraak.
- LS-8 MOET: tik op de cirkel → LISTENING ≤150 ms, volledig lokaal pad. Regel 3: alles onder een seconde is hardware of systeem; alles dat denkt is de bot.

## Botkanaal
- LS-9 MOET: één botkanaal met getypeerde acties: {speak, listen, confirm_request, call, sms, photo, navigate, content_show}. Met LLM-sleutel is dat kanaal de directe Grok-route (agent-xai, AX-1), anders één WSS-verbinding met de Communication Partner Bot (debug: mock-bot).
- LS-10 MAG NIET: een actietype buiten deze lijst uitvoeren. Onbekend type of type `payment` → weigeren en uitspreken: "Betalen doe je met de zijknop."
- LS-11 MOET: geen gesprekscontext op schijf aan telefoonzijde; het botkanaal is stateless op de telefoon.

## Bevestigingsframework (ConfirmationGate)
- LS-12 MOET: kaart groot, titel in doeltaal ("Bel Sarah?"), exact twee knoppen: Ja en Nee.
- LS-13 MOET: ook spraakbaar; "ja"/"nee" wordt lokaal (on-device grammatica) herkend ≤300 ms, zonder botronde.
- LS-14 MOET: bij Ja geeft de gate een ConfirmationToken af — {actie, doelwit, payload-hash, ttl 60 s, eenmalig}. telephony-bridge en messaging voeren niets uit zonder geldig token.
- LS-15 MOET: timeout 15 s zonder antwoord = Nee; geen actie is de veilige default.

## Instellingen — verborgen
- LS-16 MOET (aangepast 2026-10-31): bereikbaar via lange druk (≥800 ms) op de cirkel óf de zin "open instellingen".
- LS-17 MOET: exact zes opties, niet meer: (1) zijknop-toewijzing → opent systeeminstelling gebaren, (2) betaalvoorkeur → opent systeem-NFC/Wallet-instelling, (3) standaard navigatie-app, (4) altijd-luisteren vs push-to-talk, (5) BioBASE-verbindingsstatus (gevoed door mcp-client), (6) Bot & Keys: Grok-bot agent-ID, LLM-API-sleutel en TTS-API-sleutel, versleuteld met een Android Keystore-sleutel, plus botstatus en "Test verbinding".
  *(Aangepast 2026-10-31 op verzoek: optie 6 toegevoegd; was "exact vijf".)*

## Eerste-keer-wizard
- LS-20 MOET (toegevoegd 2026-10-31): bij de allereerste start, zolang er nog geen naam en geen TTS-sleutel is, volgt de wizard: (1) LLM-API-sleutel (xAI) en TTS-API-sleutel, (2) optioneel de Grok-bot agent-ID, (3) de stem (xAI "ara", anders de Android-stem) vraagt "Hey, Roxy hier. Wat is jouw naam?", (4) de naam inspreken of typen, (5) bevestiging in dezelfde stem: "Leuk je te ontmoeten, [naam].", (6) alles versleuteld opslaan (Android Keystore) en naar IDLE.
- LS-21 MOET: de wizard is overslaanbaar; tot naam én TTS-sleutel zijn ingevuld toont IDLE een subtiele, tikbare herinnering die de wizard opnieuw opent.

## Betalen — hardwaregebaar, nooit bemiddeld
- LS-18 MAG NIET: zijknop-events onderscheppen, een AccessibilityService registreren of overlays tekenen boven Google Wallet. Dubbele druk op de zijknop blijft een pure systeemfunctie.
- LS-19 MOET: elk betaalverzoek via spraak beantwoorden met verwijzing naar het zijknop-gebaar (zie LS-10); de bot bemiddelt nooit bij betalingen.

## Latencybudget (lokaal, geen botronde)
mic → LISTENING ≤150 ms · partial op scherm ≤300 ms · kaart renderen ≤100 ms na botbesluit · ja/nee-herkenning ≤300 ms · CONTENT sluiten ≤200 ms.

## Fouten
Botkanaal weg → IDLE met rode rand om de cirkel + gesproken melding bij de eerstvolgende interactie; nooit stille no-op. Mic-permissie geweigerd → de enige schermtekst ooit in IDLE: korte uitleg + knop naar de systeemdialoog.

## Acceptatie
Testklasse `com.proxy.launcher.LauncherShellSpec` dekt elke LS-MOET-regel met minstens één test, genoemd naar het ID. Reviewer controleert dekking per ID.

## Verify
Faalt nu (geen implementatie); slaagt pas als het contract én de manifest-invarianten kloppen:

    ./gradlew :app:testDebugUnitTest --tests "com.proxy.launcher.LauncherShellSpec" && grep -q "android.intent.category.HOME" app/src/main/AndroidManifest.xml && ! grep -qi "accessibilityservice" app/src/main/AndroidManifest.xml
