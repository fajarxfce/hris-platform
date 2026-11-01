package dev.fajar.hris.collaboration.delivery.requests

import dev.fajar.hris.collaboration.domain.policies.COLLABORATION_SYNC_ROOM_LIMIT
import tools.jackson.databind.JsonNode

fun matrixDirectMetadataRequest(value: JsonNode): Map<String, List<String>> {
    require(value.isObject && value.size() <= COLLABORATION_SYNC_ROOM_LIMIT)
    var total = 0L
    return value.properties().associate { (user, rooms) ->
        require(user.length in 2..255 && rooms.isArray)
        total += rooms.size()
        require(total <= COLLABORATION_SYNC_ROOM_LIMIT)
        user to
            rooms
                .iterator()
                .asSequence()
                .map {
                    require(it.isString && it.asString().length in 2..255)
                    it.asString()
                }
                .toList()
    }
}
