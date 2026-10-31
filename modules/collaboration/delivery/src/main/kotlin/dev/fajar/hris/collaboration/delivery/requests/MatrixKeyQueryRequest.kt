package dev.fajar.hris.collaboration.delivery.requests

import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixKeyQuery
import tools.jackson.databind.JsonNode

fun matrixKeyQueryRequest(body: JsonNode): CollaborationMatrixKeyQuery {
    require(body.isObject && body.propertyNames().all { it in setOf("device_keys", "timeout") })
    val devices = body.path("device_keys")
    require(devices.isObject && devices.size() in 1..100)
    body.get("timeout")?.let {
        require(it.isIntegralNumber && it.canConvertToInt() && it.asInt() in 0..30_000)
    }
    return CollaborationMatrixKeyQuery(
        devices.properties().associate { (user, ids) ->
            require(user.length in 1..255 && ids.isArray && ids.size() <= 5)
            user to
                ids.iterator()
                    .asSequence()
                    .map {
                        require(it.isString && it.asString().length in 1..128)
                        it.asString()
                    }
                    .toList()
        }
    )
}
