package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.domain.entities.*
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import tools.jackson.databind.json.JsonMapper

/** Fictional in-memory keys. Sorted Jackson maps independently encode Matrix canonical JSON. */
class MatrixKeyFixture(val userId: String, val deviceId: String) {
    private val json = JsonMapper.builder().build()
    private val signing = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val encoding = Base64.getEncoder().withoutPadding()

    private fun curve(): String =
        encoding.encodeToString(
            KeyPairGenerator.getInstance("X25519")
                .generateKeyPair()
                .public
                .encoded
                .takeLast(32)
                .toByteArray()
        )

    private fun proof(body: Map<String, Any>) =
        mapOf(
            userId to
                mapOf(
                    "ed25519:$deviceId" to
                        Signature.getInstance("Ed25519").run {
                            initSign(signing.private)
                            update(json.writeValueAsBytes(body.toSortedMap()))
                            encoding.encodeToString(sign())
                        }
                )
        )

    val device: CollaborationMatrixDeviceKeys

    init {
        val algorithms = listOf("m.olm.v1.curve25519-aes-sha2", "m.megolm.v1.aes-sha2")
        val keys =
            sortedMapOf(
                "ed25519:$deviceId" to
                    encoding.encodeToString(signing.public.encoded.takeLast(32).toByteArray()),
                "curve25519:$deviceId" to curve(),
            )
        val body =
            mapOf(
                "user_id" to userId,
                "device_id" to deviceId,
                "algorithms" to algorithms,
                "keys" to keys,
            )
        device = CollaborationMatrixDeviceKeys(userId, deviceId, algorithms, keys, proof(body))
    }

    fun signed(fallback: Boolean? = null): CollaborationMatrixSignedKey {
        val key = curve()
        val body =
            buildMap<String, Any> {
                put("key", key)
                fallback?.let { put("fallback", it) }
            }
        return CollaborationMatrixSignedKey(key, proof(body), fallback)
    }

    fun body(): Map<String, Any> =
        mapOf(
            "user_id" to userId,
            "device_id" to deviceId,
            "algorithms" to device.algorithms,
            "keys" to device.keys,
            "signatures" to device.signatures,
        )
}
