package dev.fajar.hris.collaboration.delivery.requests

import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixKeyClaim
import tools.jackson.databind.JsonNode

fun matrixKeyClaimRequest(body: JsonNode): CollaborationMatrixKeyClaim {
    require(body.isObject && body.propertyNames().all { it in setOf("one_time_keys", "timeout") })
    val users = body.path("one_time_keys")
    require(users.isObject && users.size() in 1..100)
    body.get("timeout")?.let {
        require(it.isIntegralNumber && it.canConvertToInt() && it.asInt() in 0..30_000)
    }
    return CollaborationMatrixKeyClaim(
        users.properties().associate { (user, devices) ->
            require(user.length in 1..255 && devices.isObject && devices.size() in 1..5)
            user to
                devices.properties().associate { (device, algorithm) ->
                    require(
                        device.length in 1..128 &&
                            algorithm.isString &&
                            algorithm.asString() == "signed_curve25519"
                    )
                    device to algorithm.asString()
                }
        }
    )
}
