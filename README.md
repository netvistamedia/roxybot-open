# RoxyBot — open onderdelen

[RoxyBot](https://github.com/netvistamedia) is een Android-launcher die je telefoon verandert in een
pure agent-interface: je praat, de bot voert uit. Geen app-iconen, geen menu's.

Deze repository bevat de **open onderdelen** van RoxyBot: het berichtenprotocol tussen launcher en
bot, een mock-bot om lokaal mee te testen, en de specificaties van de app-modules. Daarmee kun je een
eigen bot of bridge bouwen die met RoxyBot praat, of het gedrag van de launcher nagaan.

De launcher zelf, de bridges en de betaalde diensten (zoals Roxy-Go) zijn **niet open source**.
De volledige app komt via de Google Play Store en via het betaalde Roxy-Go.

## Wat is open en wat niet

| Open (MIT, in deze repo) | Gesloten (niet publiek) |
|---|---|
| `protocol/`: botacties, launcherberichten, gespreks- en BioBASE-relayberichten | De Android-launcher (`app/`) |
| `tools/mock-bot/`: lokale WebSocket-bot met vaste scenario's en een WebRTC-echo | De Roxy-bridge en alle bridge-/VPS-code |
| `specs/`: specs van launcher-shell, camera, lipsync, messaging en navigatie | Betaalde features (Roxy-Go, remote bridge, …) |
| `docs/`: architectuur van het protocol | Specs van de bridge en de modelroutes (xAI, Gemini Live, telefonie, MCP-transport) |
| | Productieconfiguratie en secrets |

## Projectstructuur

```
protocol/         Gedeeld berichtenmodel (Kotlin/JVM, kotlinx.serialization)
tools/mock-bot/   Lokale bot voor tests: vaste scenario's en een WebRTC-echo-bridge
specs/            Specificaties van de open beschreven modules, met eisen-ID's
docs/             Architectuur en het protocol op de draad
```

## Bouwen en testen

Vereist: JDK 17.

```bash
./gradlew :protocol:test                  # protocoltests
cd tools/mock-bot && ./gradlew test       # mock-bot-tests
```

## De mock-bot draaien

```bash
cd tools/mock-bot && ./gradlew installDist && ./build/install/mock-bot/bin/mock-bot
# luistert op ws://localhost:8765
```

Een debug-build van RoxyBot (of je eigen client) verbindt met `ws://localhost:8765`; op een
Android-toestel of emulator met `adb reverse tcp:8765 tcp:8765`.

Scenario's: "Bel Sarah", "Stuur een bericht naar Mark: ik ben onderweg" (ook "op WhatsApp", …),
"Maak een selfie", "Maak een foto", "Breng me naar het station" en "Wat weet je over …".

## Principes die het protocol vastlegt

- **Bevestiging bij bellen en berichten.** Een bot vraagt met `confirm_request`; de telefoon toont
  een kaart en geeft alleen na "Ja" een eenmalig token. Zonder geldig token voert de launcher niets
  uit, ook als de bot erom vraagt. Voor berichten kan de gebruiker de kaart per kanaal uitzetten
  (MSG-8): de telefoon geeft het token dan zonder kaart, hoogstens één per zin die de gebruiker
  zegt. Bellen vraagt altijd de kaart.
- **Betalen blijft van het systeem.** Geen enkele botactie raakt betalingen.
- **Persoonlijke data blijft lokaal.** BioBASE-verzoeken zijn een aparte, efemere berichtcategorie.

Zie [docs/architecture.md](docs/architecture.md).

## Licentie

MIT, zie [LICENSE](LICENSE). Dit geldt alleen voor de inhoud van deze repository. De RoxyBot-app,
de bridges en de betaalde diensten vallen niet onder deze licentie.
