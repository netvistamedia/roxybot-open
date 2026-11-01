# Architectuur (open deel)

```
┌──────────────────────┐   WebSocket, JSON    ┌───────────────────────────┐
│ RoxyBot-launcher     │ ◀──────────────────▶ │ Bot / bridge              │
│ (gesloten)           │  {"type","payload"}  │ (mock-bot hier; eigen bot │
│                      │                      │  of betaalde bridge)      │
└──────────────────────┘                      └───────────────────────────┘
          ▲  alle berichttypes staan in protocol/ (com.proxy.protocol)
```

## Berichtcategorieën

| Richting | Type | Bron |
|---|---|---|
| bot → launcher | Botacties: `speak`, `listen`, `confirm_request`, `call`, `sms`, `photo`, `navigate`, `content_show` | `BotAction.kt` (LS-9) |
| bot → launcher | Gespreksberichten: WebRTC-answer, ICE, gespreksstatus | `CallMessage.kt` |
| bot → launcher | BioBASE-verzoek (relay, geen botactie) | `BioBaseRelay.kt` |
| launcher → bot | `utterance`, `confirm_response`, `action_result`, `action_request`, `dial`, `hangup`, WebRTC-offer, ICE | `LauncherMessage.kt` |

Elk bericht staat op de draad als `{"type": "<type>", "payload": {...}}`. `Protocol.kt` parseert
binnenkomende berichten; onbekende actietypes worden geweigerd (LS-10) in plaats van genegeerd.

## Bevestiging

`call` en `sms` voert de launcher alleen uit met een geldig `ConfirmationToken`: eenmalig, korte
ttl, met een hash van doelwit en payload. Het token ontstaat op de telefoon na "Ja" op de
bevestigingskaart (LS-13/LS-14, MSG-1/MSG-2). Een bot kan het niet zelf maken.

## Specs

De specs in `specs/` beschrijven de modules van de launcher. Ze verwijzen naar bronpaden
(`app/src/...`) en verify-commando's van de gesloten app; die zijn hier ter referentie. Verwijzingen
naar `agent-xai`, `gemini-live`, `telephony-bridge` en `mcp-client` gaan naar specs die niet
openbaar zijn.
