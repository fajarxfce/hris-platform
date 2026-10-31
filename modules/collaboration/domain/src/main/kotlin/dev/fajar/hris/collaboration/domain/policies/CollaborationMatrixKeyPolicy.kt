package dev.fajar.hris.collaboration.domain.policies

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.HexFormat

private val keySuffix = Regex("[A-Za-z0-9_+/=-]{1,128}")
private val algorithms = setOf("m.olm.v1.curve25519-aes-sha2", "m.megolm.v1.aes-sha2")

/** Bounds precede copying, iteration and signature work. Never retain caller-owned maps. */
fun snapshotCollaborationKeyUpload(
    request: CollaborationMatrixKeyUpload
): Result<CollaborationMatrixKeyUpload> {
    if (request.oneTimeKeys.size > 100 || request.fallbackKeys.size > 1) return invalidKeys()
    val device = request.deviceKeys
    if (device != null && !deviceShape(device)) return invalidKeys()
    for ((id, key) in request.oneTimeKeys) {
        if (!signedId(id) || !signedShape(key) || key.fallback == true) return invalidKeys()
    }
    for ((id, key) in request.fallbackKeys) {
        if (!signedId(id) || !signedShape(key) || key.fallback != true) return invalidKeys()
    }
    if (request.oneTimeKeys.keys.any { it in request.fallbackKeys }) return invalidKeys()
    return Result.Success(
        CollaborationMatrixKeyUpload(
            device?.copy(
                algorithms = device.algorithms.toList(),
                keys = device.keys.toMap(),
                signatures = device.signatures.detached(),
            ),
            request.oneTimeKeys.mapValues { (_, key) ->
                key.copy(signatures = key.signatures.detached())
            },
            request.fallbackKeys.mapValues { (_, key) ->
                key.copy(signatures = key.signatures.detached())
            },
        )
    )
}

/** The fingerprint is independent of signature ordering and padded/unpadded base64 spelling. */
fun validateCollaborationDeviceKeys(
    keys: CollaborationMatrixDeviceKeys,
    userId: String,
    deviceId: String,
): Result<String> {
    if (
        !deviceShape(keys) ||
            keys.userId != userId ||
            keys.deviceId != deviceId ||
            keys.algorithms.size != algorithms.size ||
            keys.algorithms.toSet() != algorithms ||
            keys.keys.keys != setOf("ed25519:$deviceId", "curve25519:$deviceId") ||
            keys.signatures.keys != setOf(userId)
    )
        return invalidKeys()
    val signing = bytes(keys.keys.getValue("ed25519:$deviceId"), 32) ?: return invalidKeys()
    val curve = bytes(keys.keys.getValue("curve25519:$deviceId"), 32) ?: return invalidKeys()
    val proof = keys.signatures[userId]?.get("ed25519:$deviceId") ?: return invalidKeys()
    if (!verify(signing, canonicalDevice(keys), proof)) return invalidKeys()
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update("org.hris.matrix.device-keys.v1\u0000".toByteArray(Charsets.UTF_8))
    digest.update(userId.toByteArray(Charsets.UTF_8))
    digest.update(0.toByte())
    digest.update(deviceId.toByteArray(Charsets.UTF_8))
    digest.update(0.toByte())
    digest.update(signing)
    digest.update(curve)
    return Result.Success(HexFormat.of().formatHex(digest.digest()))
}

/** Fresh one-time key material must be signed by the retained device identity. */
fun validateCollaborationSignedKeys(
    upload: CollaborationMatrixKeyUpload,
    device: CollaborationMatrixDeviceKeys,
): Result<Unit> {
    val shape = snapshotCollaborationKeyUpload(upload)
    if (shape is Result.Failed) return shape
    val identity = validateCollaborationDeviceKeys(device, device.userId, device.deviceId)
    if (identity is Result.Failed) return identity
    val signing = requireNotNull(bytes(device.keys.getValue("ed25519:${device.deviceId}"), 32))
    for (key in upload.oneTimeKeys.values + upload.fallbackKeys.values) {
        if (key.signatures.keys != setOf(device.userId)) return invalidKeys()
        val proof =
            key.signatures[device.userId]?.get("ed25519:${device.deviceId}") ?: return invalidKeys()
        val canonical = buildString {
            append('{')
            key.fallback?.let {
                append("\"fallback\":")
                append(it)
                append(',')
            }
            append("\"key\":")
            append(quoted(key.key))
            append('}')
        }
        if (!verify(signing, canonical, proof)) return invalidKeys()
    }
    return Result.Success(Unit)
}

private fun deviceShape(keys: CollaborationMatrixDeviceKeys): Boolean =
    printable(keys.userId, 255) &&
        printable(keys.deviceId, 128) &&
        keys.algorithms.size in 1..8 &&
        keys.algorithms.all { printable(it, 100) } &&
        keys.keys.size == 2 &&
        keys.keys.all { (id, key) -> printable(id, 160) && bytes(key, 32) != null } &&
        signatureShape(keys.signatures)

private fun signedId(id: String): Boolean =
    id.length <= 146 &&
        id.startsWith("signed_curve25519:") &&
        keySuffix.matches(id.substringAfter(':'))

private fun signedShape(key: CollaborationMatrixSignedKey) =
    bytes(key.key, 32) != null && signatureShape(key.signatures)

private fun signatureShape(value: Map<String, Map<String, String>>): Boolean =
    value.size == 1 &&
        value.all { (user, signatures) ->
            printable(user, 255) &&
                signatures.size in 1..8 &&
                signatures.all { (id, signature) ->
                    id.length <= 136 &&
                        id.startsWith("ed25519:") &&
                        keySuffix.matches(id.substringAfter(':')) &&
                        bytes(signature, 64) != null
                }
        }

private fun printable(value: String, maximum: Int) =
    value.length in 1..maximum && value.all { it.code in 33..126 }

private fun Map<String, Map<String, String>>.detached() = mapValues { (_, value) -> value.toMap() }

private fun bytes(value: String, count: Int): ByteArray? {
    if (value.length !in (count * 8 + 5) / 6..((count + 2) / 3 * 4)) return null
    val decoded =
        try {
            Base64.getDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            return null
        }
    if (decoded.size != count) return null
    val unpadded = Base64.getEncoder().withoutPadding().encodeToString(decoded)
    val padded = Base64.getEncoder().encodeToString(decoded)
    return decoded.takeIf { value == unpadded || value == padded }
}

// Matrix canonical JSON for these fixed, bounded public-key shapes only. No floats,
// nullable members, unknown fields or locale-dependent ordering enter signed content.
private fun canonicalDevice(keys: CollaborationMatrixDeviceKeys): String = buildString {
    append("{\"algorithms\":[")
    append(keys.algorithms.joinToString(",") { quoted(it) })
    append("],\"device_id\":")
    append(quoted(keys.deviceId))
    append(",\"keys\":{")
    append(
        keys.keys.toSortedMap().entries.joinToString(",") {
            quoted(it.key) + ":" + quoted(it.value)
        }
    )
    append("},\"user_id\":")
    append(quoted(keys.userId))
    append('}')
}

private fun quoted(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

private fun verify(publicKey: ByteArray, content: String, encodedSignature: String): Boolean {
    val signature = bytes(encodedSignature, 64) ?: return false
    return try {
        val prefix = HexFormat.of().parseHex("302a300506032b6570032100")
        val key =
            KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(prefix + publicKey))
        Signature.getInstance("Ed25519").run {
            initVerify(key)
            update(content.toByteArray(Charsets.UTF_8))
            verify(signature)
        }
    } catch (_: java.security.GeneralSecurityException) {
        false
    }
}

private fun invalidKeys() =
    Result.Failed(Failure(FailureKind.VALIDATION, "collaboration_device_keys_invalid"))
