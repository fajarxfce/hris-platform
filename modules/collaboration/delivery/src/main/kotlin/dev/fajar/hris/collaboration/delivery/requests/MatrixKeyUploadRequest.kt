package dev.fajar.hris.collaboration.delivery.requests

import dev.fajar.hris.collaboration.domain.entities.*
import tools.jackson.databind.JsonNode

/** Strict client input, distinct from the provider's optional unsigned/cross-signing projection. */
fun matrixKeyUploadRequest(body: JsonNode): CollaborationMatrixKeyUpload {
    fields(body, emptySet(), setOf("device_keys", "one_time_keys", "fallback_keys"))
    val device =
        body.get("device_keys")?.let { value ->
            fields(value, setOf("user_id", "device_id", "algorithms", "keys", "signatures"))
            val algorithms = value.path("algorithms")
            require(algorithms.isArray && algorithms.size() in 1..8)
            CollaborationMatrixDeviceKeys(
                text(value.path("user_id"), 255),
                text(value.path("device_id"), 128),
                algorithms.iterator().asSequence().map { text(it, 100) }.toList(),
                strings(value.path("keys"), 2, 160, 44),
                signatures(value.path("signatures")),
            )
        }
    return CollaborationMatrixKeyUpload(
        device,
        signedKeys(body.get("one_time_keys"), 100),
        signedKeys(body.get("fallback_keys"), 1),
    )
}

private fun signedKeys(value: JsonNode?, maximum: Int): Map<String, CollaborationMatrixSignedKey> {
    if (value == null) return emptyMap()
    require(value.isObject && value.size() <= maximum)
    return value.properties().associate { (name, key) ->
        require(name.length in 1..146)
        fields(key, setOf("key", "signatures"), setOf("fallback"))
        val fallback =
            key.get("fallback")?.let {
                require(it.isBoolean)
                it.asBoolean()
            }
        name to
            CollaborationMatrixSignedKey(
                text(key.path("key"), 44),
                signatures(key.path("signatures")),
                fallback,
            )
    }
}

private fun signatures(value: JsonNode): Map<String, Map<String, String>> {
    require(value.isObject && value.size() in 1..8)
    return value.properties().associate { (user, signatures) ->
        require(user.length in 1..255)
        user to strings(signatures, 8, 136, 88)
    }
}

private fun strings(
    value: JsonNode,
    maximum: Int,
    keyMaximum: Int,
    valueMaximum: Int,
): Map<String, String> {
    require(value.isObject && value.size() in 1..maximum)
    return value.properties().associate { (key, value) ->
        require(key.length in 1..keyMaximum)
        key to text(value, valueMaximum)
    }
}

private fun text(value: JsonNode, maximum: Int): String {
    require(value.isString)
    return value.asString().also { require(it.length in 1..maximum) }
}

private fun fields(value: JsonNode, required: Set<String>, optional: Set<String> = emptySet()) {
    require(value.isObject)
    val names = value.propertyNames().toSet()
    require(names.containsAll(required) && names.all { it in required || it in optional })
}
