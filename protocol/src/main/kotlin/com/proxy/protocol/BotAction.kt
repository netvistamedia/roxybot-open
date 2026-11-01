package com.proxy.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Getypeerde botacties (LS-9). Op de draad: {"type": "<type>", "payload": {...}}.
 * Andere types worden door de launcher geweigerd (LS-10).
 */
sealed interface BotAction {
    val type: String

    /** De bot spreekt tekst uit. Zonder lopend verzoek is het een mededeling met notificatiegeluid. */
    @Serializable
    data class Speak(
        val tekst: String,
        val urgent: Boolean = false,
    ) : BotAction {
        override val type: String get() = TYPE

        companion object {
            const val TYPE: String = "speak"
        }
    }

    /** De bot vraagt de launcher opnieuw te luisteren (vervolgvraag). */
    @Serializable
    data object Listen : BotAction {
        override val type: String get() = TYPE
        const val TYPE: String = "listen"
    }

    /**
     * Bevestigingskaart (LS-12). [soort] bepaalt welke actie het token later dekt;
     * bij berichten bepaalt [kanaal] via welke app (standaard sms).
     */
    @Serializable
    data class ConfirmRequest(
        val id: String,
        val titel: String,
        val soort: ConfirmKind,
        val naam: String,
        val nummer: String,
        val tekst: String? = null,
        val kanaal: Channel = Channel.SMS,
        /** Account-id in de doel-app (bijv. numeriek X-id); alleen nodig waar de app dat vraagt. */
        val account: String? = null,
    ) : BotAction {
        override val type: String get() = TYPE

        companion object {
            const val TYPE: String = "confirm_request"
        }
    }

    /** Uitgaand gesprek; [bevestiging] verwijst naar een eerder bevestigde [ConfirmRequest.id]. */
    @Serializable
    data class Call(
        val bevestiging: String,
        val naam: String,
        val nummer: String,
    ) : BotAction {
        override val type: String get() = TYPE

        companion object {
            const val TYPE: String = "call"
        }
    }

    /** Bericht; [kanaal] = sms (standaard) of een messaging-app. Eén bericht per bevestiging (MSG-3). */
    @Serializable
    data class Sms(
        val bevestiging: String,
        val ontvanger: Recipient,
        val tekst: String,
        val kanaal: Channel = Channel.SMS,
    ) : BotAction {
        override val type: String get() = TYPE

        companion object {
            const val TYPE: String = "sms"
        }
    }

    /** Camera; lens "front" of "back", ontbrekend of onbekend = achtercamera (camera-tool). */
    @Serializable
    data class Photo(
        @SerialName("lens") val lensValue: String? = null,
    ) : BotAction {
        override val type: String get() = TYPE
        val lens: Lens get() = Lens.fromWire(lensValue)

        companion object {
            const val TYPE: String = "photo"
        }
    }

    /**
     * Navigatie; wandelen is de default (NAV-1). De bot geocodeert (NAV-3) en levert coördinaten,
     * ook voor persoonlijke bestemmingen die hij efemeer via de BioBASE-relay kreeg (NAV-2).
     * [weergave] "kaart" = ingebedde kaart, "navigatie" = turn-by-turn in de gekozen app.
     */
    @Serializable
    data class Navigate(
        val doel: String,
        val modus: String = MODE_WALKING,
        val weergave: String = DISPLAY_MAP,
        val lat: Double? = null,
        val lon: Double? = null,
        /** Routeoverzicht als [[lat, lon], …]; leeg = alleen bestemming en eigen positie. */
        val route: List<List<Double>> = emptyList(),
    ) : BotAction {
        override val type: String get() = TYPE

        companion object {
            const val TYPE: String = "navigate"
            const val MODE_WALKING: String = "lopen"
            const val MODE_CYCLING: String = "fietsen"
            const val MODE_DRIVING: String = "auto"
            const val DISPLAY_MAP: String = "kaart"
            const val DISPLAY_TURN_BY_TURN: String = "navigatie"
        }
    }

    /** Full-screen inhoud (CONTENT-staat). */
    @Serializable
    data class ContentShow(
        val tekst: String,
        val titel: String? = null,
    ) : BotAction {
        override val type: String get() = TYPE

        companion object {
            const val TYPE: String = "content_show"
        }
    }

    companion object {
        /** Exact de acht types uit LS-9. */
        val TYPES: List<String> =
            listOf(
                Speak.TYPE,
                Listen.TYPE,
                ConfirmRequest.TYPE,
                Call.TYPE,
                Sms.TYPE,
                Photo.TYPE,
                Navigate.TYPE,
                ContentShow.TYPE,
            )
    }
}

@Serializable
enum class ConfirmKind {
    @SerialName("call")
    CALL,

    @SerialName("sms")
    SMS,
}

@Serializable
data class Recipient(
    val naam: String,
    val nummer: String,
    /** Account-id in de doel-app (bijv. numeriek X-id); null = gebruiker kiest in de app. */
    val account: String? = null,
)

/** Berichtkanaal op `sms` en `confirm_request`; LS-9 blijft acht actietypes. */
@Serializable
enum class Channel(val wire: String) {
    @SerialName("sms")
    SMS("sms"),

    @SerialName("whatsapp")
    WHATSAPP("whatsapp"),

    @SerialName("telegram")
    TELEGRAM("telegram"),

    @SerialName("signal")
    SIGNAL("signal"),

    @SerialName("slack")
    SLACK("slack"),

    @SerialName("x")
    X("x"),
}

enum class Lens(val wire: String) {
    FRONT("front"),
    BACK("back"),
    ;

    companion object {
        fun fromWire(value: String?): Lens = if (value == FRONT.wire) FRONT else BACK
    }
}
