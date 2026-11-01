# Spec — messaging

Module `com.proxy.messaging` · bron `app/src/main/kotlin/com/proxy/messaging/` · spec-ID's: MSG-x

## Doel
SMS versturen die de bot opstelt, altijd na expliciete bevestiging. Verzend-only:
Proxy wordt géén standaard-sms-app.

## Flow
1. Bot stelt het bericht op (ontvanger via BioBASE `contacts_resolve` of een gedicteerd nummer).
2. launcher-shell toont de kaart: naam + nummer + de volledige, letterlijke berichttekst; knoppen Ja/Nee, ook spraakbaar.
3. Ja → ConfirmationToken; verzending via SmsManager (multipart waar nodig).
4. Gesproken en visueel resultaat: "verzonden", "afgeleverd" of een benoemde fout.

## Eisen
- MSG-1 MOET: wat op de kaart staat is byte-identiek aan wat verzonden wordt; het ConfirmationToken bevat de hash van ontvanger + berichttekst en de verzending valideert die hash.
- MSG-2 MOET: elk verzendverzoek draagt een geldig, eenmalig token (ttl 120 s); zonder token lokaal weigeren, ook op botverzoek. Opnieuw versturen = nieuwe kaart, nieuw token.
- MSG-3 MOET: één bericht per bevestiging; batch- of lijstverzending bestaat niet. De bot kan dus nooit massaal sms'en zonder even zoveel keer een "ja".
- MSG-4 MOET: sent- en delivered-PendingIntents afhandelen en het resultaat uitspreken.
- MSG-5 MOET: verzending loggen naar BioBASE `log_append {type "sms", doel, ts, status, inhoud}` — nooit naar cloud. BioBASE offline → in-memory wachtrij, max 100, weg bij reboot.
- MSG-6 MAG NIET: de default-sms-rol claimen, inkomende sms lezen of RECEIVE_SMS/READ_SMS declareren; alleen SEND_SMS. Niet-default betekent ook: Android schrijft verzonden berichten niet in de sms-database — de telefoon bewaart geen context, BioBASE wel.
- MSG-7 MOET: kaart-Ja → verzendopdracht ≤500 ms (lokaal pad).

## Buiten scope (v1)
MMS, RCS, groepsberichten, inkomend berichtenverkeer, bijlagen.

## Raakvlakken
launcher-shell: ConfirmationGate (LS-12 t/m LS-15) · mcp-client: `contacts_resolve`, `log_append` · bot: actie `sms {ontvanger, tekst}`.

## Acceptatie
Testklasse `com.proxy.messaging.MessagingSpec`: per MSG-MOET-regel één test, genoemd naar het ID. Kerninvarianten: kaarttekst == verzonden tekst (MSG-1) en geen verzending zonder token (MSG-2).

## Verify
Faalt nu; slaagt pas als de contracttests slagen en de permissieset klopt:

    ./gradlew :app:testDebugUnitTest --tests "com.proxy.messaging.MessagingSpec" && grep -q "android.permission.SEND_SMS" app/src/main/AndroidManifest.xml && ! grep -qE "RECEIVE_SMS|READ_SMS" app/src/main/AndroidManifest.xml
