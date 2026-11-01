package com.proxy.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * BioBASE-relay (mcp-client MC-4): een aparte berichtcategorie, geen botactie.
 * De bot vraagt, de launcher roept BioBASE via MCP aan en stuurt een efemeer antwoord terug.
 */
@Serializable
data class BioBaseRequest(
    val id: String,
    val tool: String,
    val argumenten: JsonObject = JsonObject(emptyMap()),
) {
    companion object {
        const val TYPE: String = "biobase_request"
    }
}

@Serializable
data class BioBaseResponse(
    val id: String,
    val resultaat: JsonElement? = null,
    val fout: String? = null,
    /** Contractvlag: de bot-server mag dit antwoord niet bewaren (MC-4). */
    val efemeer: Boolean = true,
) : LauncherMessage {
    override val type: String get() = TYPE

    companion object {
        const val TYPE: String = "biobase_response"
    }
}

/** Canonieke toolnamen (mcp-client toolcontract). */
object BioBaseTools {
    const val CONTACTS_RESOLVE: String = "contacts_resolve"
    const val PROFILE_GET: String = "profile_get"
    const val MEMORY_QUERY: String = "memory_query"
    const val LOG_APPEND: String = "log_append"

    /** De bot mag alleen lezen; log_append is voorbehouden aan de telefoon zelf. */
    val BOT_READABLE: Set<String> = setOf(CONTACTS_RESOLVE, PROFILE_GET, MEMORY_QUERY)
}

/** Benoemde relayfouten; "geheugen_offline" is wat de bot hoort bij MC-8. */
object BioBaseErrors {
    const val OFFLINE: String = "geheugen_offline"
    const val NOT_ALLOWED: String = "niet_toegestaan"
    const val UNKNOWN_TOOL: String = "onbekende_tool"
    const val TIMEOUT: String = "time-out"
    const val FAILED: String = "fout"
}
