package dev.fajar.hris.collaboration.data.matrix

import tools.jackson.databind.JsonNode

/** Whitelisted DIRECT receipt metadata. Private receipts never expose another participant. */
internal fun mapCollaborationReadReceipts(
    events: List<JsonNode>,
    participants: Set<String>,
    ownUser: String,
): List<Map<String, Any>> {
    require(ownUser in participants && participants.size == 2)
    if (events.size > 32) throw MatrixResponseException()
    val output = mutableListOf<Map<String, Any>>()
    var totalEvents = 0
    for (event in events) {
        if (!event.isObject) throw MatrixResponseException()
        if (event.path("type").asString() != "m.receipt") continue
        val content = event.path("content")
        if (!content.isObject) throw MatrixResponseException()
        totalEvents += content.size()
        if (totalEvents > 256) throw MatrixResponseException()
        val mapped = linkedMapOf<String, Any>()
        for ((eventId, types) in content.properties()) {
            if (
                eventId.length !in 2..255 ||
                    !eventId.startsWith('$') ||
                    eventId.any { it.code !in 33..126 } ||
                    !types.isObject
            )
                throw MatrixResponseException()
            val receipts = linkedMapOf<String, Any>()
            for (type in listOf("m.read", "m.read.private")) {
                val users = types.path(type)
                if (users.isMissingNode) continue
                if (!users.isObject || users.size() > 256) throw MatrixResponseException()
                val admitted = linkedMapOf<String, Any>()
                for ((user, metadata) in users.properties()) {
                    if (user !in participants || (type == "m.read.private" && user != ownUser))
                        continue
                    if (!metadata.isObject) throw MatrixResponseException()
                    // Threads are a separate protocol feature; only unthreaded receipts pass.
                    if (!metadata.path("thread_id").isMissingNode) continue
                    val timestamp = metadata.path("ts")
                    val value = linkedMapOf<String, Any>()
                    if (!timestamp.isMissingNode) {
                        if (
                            !timestamp.isIntegralNumber ||
                                !timestamp.canConvertToLong() ||
                                timestamp.asLong() !in 0..9_007_199_254_740_991L
                        )
                            throw MatrixResponseException()
                        value["ts"] = timestamp.asLong()
                    }
                    admitted[user] = value
                }
                if (admitted.isNotEmpty()) receipts[type] = admitted
            }
            if (receipts.isNotEmpty()) mapped[eventId] = receipts
        }
        if (mapped.isNotEmpty()) output += mapOf("type" to "m.receipt", "content" to mapped)
    }
    return output
}
