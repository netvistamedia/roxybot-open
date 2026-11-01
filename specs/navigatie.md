# Spec — navigatie

Module `com.proxy.navigation` · bron `app/src/main/kotlin/com/proxy/navigation/` · spec-ID's: NAV-x

## Doel
Op verzoek een full-screen kaart tonen en turn-by-turn overdragen aan de standaard
navigatie-app uit de instellingen. Zonder bevestiging; wandelen is de default.

## Twee functies
1. Kaartweergave: ingebed, full-screen in de launcher (CONTENT-staat) — plek, routeoverzicht, huidige positie. Sluit zodra de gebruiker verder praat.
2. Turn-by-turn: intent naar de in instellingen gekozen navigatie-app (default Google Maps, `google.navigation:q={doel}&mode=w`).

## Eisen
- NAV-1 MOET: géén bevestigingskaart, voor geen enkele navigatievorm; wandelmodus is default, andere modi alleen op expliciet verzoek.
- NAV-2 MOET: bestemmingen met persoonlijke betekenis ("thuis", "werk", "Sarah") resolven via BioBASE (`profile_get`/`contacts_resolve`); zulke adressen verlaten de Mac alleen als efemeer antwoord en worden nooit op de telefoon of bij de bot opgeslagen.
- NAV-3 MOET (aangepast 2026-11-01): niet-persoonlijke geocoding (openbare plekken) doet in de directe xAI-route de telefoon via OpenStreetMap Nominatim (AX-6), in de bridge-route de bot; de ingebedde kaart gebruikt MapLibre met OSM-tegels, zonder sleutel van een advertentieplatform.
- NAV-4 MOET: kaart zichtbaar ≤1 s na botactie `navigate {doel, modus, weergave}`; sluiten bij LISTENING ≤200 ms (LS-4).
- NAV-5 MOET: locatie alleen while-in-use (ACCESS_FINE_LOCATION); nooit achtergrondlocatie.
- NAV-6 MOET: na overdracht aan de externe app eindigt de spraaksessie netjes; terugkeer naar de launcher via het home-gebaar of het hotword (de launcher ís home).
- NAV-7 MOET: gekozen navigatie-app ontbreekt of de intent faalt → gesproken fout + ingebedde kaart als terugvaloptie.

## Buiten scope (v1)
Eigen turn-by-turn-engine, offline kaarten, OV-routering, Android Auto.

## Raakvlakken
launcher-shell: CONTENT-staat en instellingenoptie 3 (standaard navigatie-app) · mcp-client: `profile_get`, `contacts_resolve` · bot: actie `navigate`.

## Acceptatie
Testklasse `com.proxy.navigation.NavigationSpec`: per NAV-MOET-regel één test, genoemd naar het ID. Kerninvarianten: geen bevestiging (NAV-1), wandelmodus default en geen achtergrondlocatie (NAV-5).

## Verify
Faalt nu; slaagt pas als de contracttests slagen en de locatie-invariant klopt:

    ./gradlew :app:testDebugUnitTest --tests "com.proxy.navigation.NavigationSpec" && ! grep -q "ACCESS_BACKGROUND_LOCATION" app/src/main/AndroidManifest.xml
