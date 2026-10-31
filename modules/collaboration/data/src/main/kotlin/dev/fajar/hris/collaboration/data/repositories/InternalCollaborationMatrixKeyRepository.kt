package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixKeyRepository
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import tools.jackson.databind.JsonNode

class InternalCollaborationMatrixKeyRepository(private val source: MatrixKeyDataSource) :
    CollaborationMatrixKeyRepository {
    override fun claimKeys(
        request: CollaborationMatrixKeyClaim,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixKeyClaimResult> = safeMatrixProviderCall {
        if (request.oneTimeKeys.size !in 1..100) invalid()
        for ((user, devices) in request.oneTimeKeys) {
            if (
                user.length !in 1..255 ||
                    devices.size !in 1..5 ||
                    devices.any { (id, algorithm) ->
                        id.length !in 1..128 || algorithm != "signed_curve25519"
                    }
            )
                invalid()
        }
        val captured = request.oneTimeKeys.mapValues { it.value.toMap() }
        val body = source.claimKeys(captured, token, deadline)
        val failures = body.path("failures")
        if (!failures.isMissingNode && (!failures.isObject || !failures.isEmpty)) invalid()
        val users = body.path("one_time_keys")
        if (!users.isObject || users.propertyNames().any { it !in captured }) invalid()
        CollaborationMatrixKeyClaimResult(
            captured.mapValues { (user, requested) ->
                val devices = users.path(user)
                if (devices.isMissingNode) emptyMap()
                else {
                    if (!devices.isObject || devices.propertyNames().any { it !in requested })
                        invalid()
                    devices.properties().associate { (device, keys) ->
                        if (!keys.isObject || keys.size() > 1) invalid()
                        device to
                            keys.properties().associate { (id, value) ->
                                if (
                                    id.length !in 1..146 ||
                                        !id.startsWith("signed_curve25519:") ||
                                        id.any { it.code !in 33..126 }
                                )
                                    invalid()
                                id to readSignedKey(value, user)
                            }
                    }
                }
            }
        )
    }

    private fun readSignedKey(value: JsonNode, user: String): CollaborationMatrixSignedKey {
        if (
            !value.isObject ||
                !value.propertyNames().containsAll(setOf("key", "signatures")) ||
                value.propertyNames().any {
                    it !in setOf("key", "signatures", "fallback", "unsigned")
                }
        )
            invalid()
        val signatures = value.path("signatures")
        if (!signatures.isObject || signatures.size() !in 1..8) invalid()
        val fallback =
            value.get("fallback")?.let {
                if (!it.isBoolean) invalid()
                it.asBoolean()
            }
        return CollaborationMatrixSignedKey(
            text(value.path("key"), 44),
            mapOf(user to stringMap(signatures.path(user), 8, 136, 88)),
            fallback,
        )
    }

    override fun deviceKeys(
        request: CollaborationMatrixKeyQuery,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixKeyQueryResult> = safeMatrixProviderCall {
        if (request.devices.size !in 1..100) invalid()
        for ((user, devices) in request.devices) {
            if (
                user.length !in 1..255 ||
                    devices.size !in 1..5 ||
                    devices.any { it.length !in 1..128 } ||
                    devices.toSet().size != devices.size
            )
                invalid()
        }
        val captured = request.devices.mapValues { it.value.toList() }
        val body = source.queryDevices(captured, token, deadline)
        val failures = body.path("failures")
        if (!failures.isMissingNode && (!failures.isObject || !failures.isEmpty)) invalid()
        val users = body.path("device_keys")
        if (!users.isObject || users.propertyNames().any { it !in captured }) invalid()
        CollaborationMatrixKeyQueryResult(
            captured.mapValues { (user, requested) ->
                val devices = users.path(user)
                if (devices.isMissingNode) emptyMap()
                else {
                    if (!devices.isObject || devices.propertyNames().any { it !in requested })
                        invalid()
                    devices.properties().associate { (device, value) ->
                        val keys = readDevice(value)
                        if (keys.userId != user || keys.deviceId != device) invalid()
                        device to keys
                    }
                }
            }
        )
    }

    override fun ownDeviceKeys(
        userId: String,
        deviceId: String,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixDeviceKeys?> = safeMatrixProviderCall {
        val body = source.queryDeviceKeys(userId, deviceId, token, deadline)
        val failures = body.path("failures")
        if (!failures.isMissingNode && (!failures.isObject || !failures.isEmpty)) invalid()
        val users = body.path("device_keys")
        if (!users.isObject || users.propertyNames().any { it != userId }) invalid()
        val devices = users.path(userId)
        if (devices.isMissingNode) return@safeMatrixProviderCall null
        if (!devices.isObject || devices.propertyNames().any { it != deviceId }) invalid()
        val value = devices.path(deviceId)
        if (value.isMissingNode) return@safeMatrixProviderCall null
        readDevice(value)
    }

    private fun readDevice(value: JsonNode): CollaborationMatrixDeviceKeys {
        val required = setOf("user_id", "device_id", "algorithms", "keys", "signatures")
        if (
            !value.isObject ||
                !value.propertyNames().containsAll(required) ||
                value.propertyNames().any { it !in required && it != "unsigned" }
        )
            invalid()
        val user = text(value.path("user_id"), 255)
        val device = text(value.path("device_id"), 128)
        val algorithms = value.path("algorithms")
        if (!algorithms.isArray || algorithms.size() !in 1..8) invalid()
        val keys = stringMap(value.path("keys"), 2, 160, 44)
        val signatures = value.path("signatures")
        if (!signatures.isObject || signatures.size() !in 1..8) invalid()
        // Other users' signatures/unsigned labels do not enter this own-device projection.
        // The use case still verifies the self signature, expected identity and fingerprint.
        val ownSignatures = stringMap(signatures.path(user), 8, 136, 88)
        return CollaborationMatrixDeviceKeys(
            user,
            device,
            algorithms.iterator().asSequence().map { text(it, 100) }.toList(),
            keys,
            mapOf(user to ownSignatures),
        )
    }

    override fun upload(
        request: CollaborationMatrixKeyUpload,
        token: String,
        deadline: Instant,
    ): Result<CollaborationMatrixKeyCounts> = safeMatrixProviderCall {
        if (request.oneTimeKeys.size > 100 || request.fallbackKeys.size > 1) invalid()
        request.deviceKeys?.let {
            if (
                it.userId.length > 255 ||
                    it.deviceId.length > 128 ||
                    it.algorithms.size > 8 ||
                    it.algorithms.any { name -> name.length > 100 } ||
                    it.keys.size > 2 ||
                    it.keys.any { (name, key) -> name.length > 160 || key.length > 44 }
            )
                invalid()
            boundSignatures(it.signatures)
        }
        for ((name, key) in request.oneTimeKeys + request.fallbackKeys) {
            if (name.length > 146 || key.key.length > 44) invalid()
            boundSignatures(key.signatures)
        }
        val body =
            buildMap<String, Any> {
                request.deviceKeys?.let { keys ->
                    put(
                        "device_keys",
                        mapOf(
                            "user_id" to keys.userId,
                            "device_id" to keys.deviceId,
                            "algorithms" to keys.algorithms,
                            "keys" to keys.keys,
                            "signatures" to keys.signatures,
                        ),
                    )
                }
                if (request.oneTimeKeys.isNotEmpty())
                    put("one_time_keys", request.oneTimeKeys.mapValues { (_, key) -> key.body() })
                if (request.fallbackKeys.isNotEmpty())
                    put("fallback_keys", request.fallbackKeys.mapValues { (_, key) -> key.body() })
            }
        val response = source.uploadKeys(body, token, deadline).path("one_time_key_counts")
        if (!response.isObject || response.size() > 2) invalid()
        val counts =
            response.properties().associate { (name, value) ->
                if (
                    name !in setOf("curve25519", "signed_curve25519") ||
                        !value.isIntegralNumber ||
                        !value.canConvertToInt() ||
                        value.asInt() !in 0..1_000_000
                )
                    invalid()
                name to value.asInt()
            }
        CollaborationMatrixKeyCounts(counts)
    }

    private fun CollaborationMatrixSignedKey.body(): Map<String, Any> = buildMap {
        put("key", key)
        put("signatures", signatures)
        fallback?.let { put("fallback", it) }
    }

    private fun stringMap(
        value: JsonNode,
        maximum: Int,
        keyLength: Int,
        valueLength: Int,
    ): Map<String, String> {
        if (!value.isObject || value.size() !in 1..maximum) invalid()
        return value.properties().associate { (key, child) ->
            if (key.length !in 1..keyLength || key.any { it.code !in 33..126 }) invalid()
            key to text(child, valueLength)
        }
    }

    private fun text(value: JsonNode, maximum: Int): String {
        if (!value.isString) invalid()
        return value.asString().also {
            if (it.length !in 1..maximum || it.any { c -> c.code !in 33..126 }) invalid()
        }
    }

    private fun boundSignatures(signatures: Map<String, Map<String, String>>) {
        if (signatures.size > 8) invalid()
        for ((user, values) in signatures) {
            if (
                user.length > 255 ||
                    values.size > 8 ||
                    values.any { (name, value) -> name.length > 136 || value.length > 88 }
            )
                invalid()
        }
    }

    private fun invalid(): Nothing = throw MatrixResponseException()
}
