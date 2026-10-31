package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Public vectors independently signed with OpenSSL 3.6.5 and Python sorted compact JSON. */
class CollaborationMatrixKeyPolicyTest {
    private val user = "@fixture:proof.invalid"
    private val device = "HRIS_00000000000000000000000000000000"
    private val publicKey = "eLvsDCta9GPRTl8vA9HtMt0jxnNlpQZytC722lYzaio"
    private val curve = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
    private val oneTime = "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8"
    private val original =
        "VaEnyZyRRLkYdUaPcXo9xwPGV5ruRvNGxbpbQSGFyIgjDYF2XvoeMi06wfvgptXgIOgmmO3DyMqeGbwI4Gf/AA"
    private val reordered =
        "t5TScqvNRNeCLC9wvyjF/iFfEA8kVQDrwYl+nnn6/NMAI4AH0FGc7BTC9duHGmYiRI6gHYknFMwG5lpi7E2TDQ"
    private val padded =
        "N5Ex81rU/7tItMyyYWK+zUZmjtHVaH2p8DN0MqjKgWHUQAutc/CF92fIFY7aXUFfCD/UfMIsf2wmCfjcfaL2Bg"
    private val ordinary =
        "9PKhNWQwsYa5QQ/Pd4Fw1guMUfsYKSVf1fn14iQXTpnXk6agr3EK+KfcqbkXqeHTCGLqmSBy2LjLORh/HBYMBA"
    private val explicitFalse =
        "sRpp5SFTEuwy6/MGou4v4oijp2utLW+8wmfluK5xZhjo0WEAgYgdBbvge9Js40YoTD3xJtJJxU1JPjxd2sOnBg"
    private val fallback =
        "GRqVcUcbutK5b3bfOvyh5V+IQBYcuFUMsvug+akxSLBM5n5Z+D9HWXHYSNge7dT1vGaeCMHVoTZWa92Y2jytCQ"

    private fun identity(signature: String = original) =
        CollaborationMatrixDeviceKeys(
            user,
            device,
            listOf("m.olm.v1.curve25519-aes-sha2", "m.megolm.v1.aes-sha2"),
            mapOf("ed25519:$device" to publicKey, "curve25519:$device" to curve),
            mapOf(user to mapOf("ed25519:$device" to signature)),
        )

    private fun key(signature: String = ordinary, flag: Boolean? = null) =
        CollaborationMatrixSignedKey(
            oneTime,
            mapOf(user to mapOf("ed25519:$device" to signature)),
            flag,
        )

    @Test
    fun independentCanonicalVectorsRetainOneFingerprintAcrossOrderAndBase64Spelling() {
        val base = validateCollaborationDeviceKeys(identity(), user, device)
        assertInstanceOf(Result.Success::class.java, base)
        assertEquals(
            "5e3760881fb604f42c280b7d5d74b989948a068cf70f0012ac8a57cf03908abf",
            (base as Result.Success).value,
        )
        assertEquals(
            base,
            validateCollaborationDeviceKeys(
                identity(reordered).copy(algorithms = identity().algorithms.reversed()),
                user,
                device,
            ),
        )
        assertEquals(
            base,
            validateCollaborationDeviceKeys(
                identity(padded)
                    .copy(keys = identity().keys.mapValues { (_, value) -> value + "=" }),
                user,
                device,
            ),
        )
    }

    @Test
    fun payloadOrScopeChangesCannotReuseDeviceSignature() {
        for (invalid in
            listOf(
                identity().copy(userId = "@other:proof.invalid"),
                identity().copy(deviceId = device + "x"),
                identity().copy(algorithms = identity().algorithms.reversed()),
                identity().copy(algorithms = listOf("m.megolm.v1.aes-sha2")),
                identity().copy(keys = identity().keys + ("curve25519:$device" to oneTime)),
                identity().copy(keys = identity().keys + ("ed25519:foreign" to publicKey)),
                identity().copy(signatures = mapOf(user to mapOf("ed25519:foreign" to original))),
                identity()
                    .copy(
                        signatures =
                            mapOf("@other:proof.invalid" to mapOf("ed25519:$device" to original))
                    ),
            )) assertInstanceOf(
            Result.Failed::class.java,
            validateCollaborationDeviceKeys(invalid, user, device),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            validateCollaborationDeviceKeys(identity(), "@other:proof.invalid", device),
        )
    }

    @Test
    fun signedOneTimeAndFallbackKeysPreserveAbsentAndFalseMembers() {
        for (k in listOf(key(), key(explicitFalse, false))) assertEquals(
            Result.Success(Unit),
            validateCollaborationSignedKeys(
                CollaborationMatrixKeyUpload(oneTimeKeys = mapOf("signed_curve25519:1" to k)),
                identity(),
            ),
        )
        assertEquals(
            Result.Success(Unit),
            validateCollaborationSignedKeys(
                CollaborationMatrixKeyUpload(
                    fallbackKeys = mapOf("signed_curve25519:2" to key(fallback, true))
                ),
                identity(),
            ),
        )
        for (upload in
            listOf(
                CollaborationMatrixKeyUpload(
                    oneTimeKeys = mapOf("signed_curve25519:1" to key().copy(fallback = false))
                ),
                CollaborationMatrixKeyUpload(
                    oneTimeKeys = mapOf("signed_curve25519:1" to key().copy(key = curve))
                ),
                CollaborationMatrixKeyUpload(
                    oneTimeKeys = mapOf("signed_curve25519:1" to key(fallback, true))
                ),
                CollaborationMatrixKeyUpload(fallbackKeys = mapOf("signed_curve25519:2" to key())),
                CollaborationMatrixKeyUpload(
                    fallbackKeys = mapOf("signed_curve25519:2" to key(explicitFalse, false))
                ),
            )) assertInstanceOf(
            Result.Failed::class.java,
            validateCollaborationSignedKeys(upload, identity()),
        )
    }

    @Test
    fun boundsRejectAmbiguousIdsAndExcessiveCollectionsBeforeSignatureWork() {
        val valid = key()
        for (upload in
            listOf(
                CollaborationMatrixKeyUpload(
                    oneTimeKeys = (0..100).associate { "signed_curve25519:$it" to valid }
                ),
                CollaborationMatrixKeyUpload(
                    fallbackKeys =
                        mapOf(
                            "signed_curve25519:1" to key(fallback, true),
                            "signed_curve25519:2" to key(fallback, true),
                        )
                ),
                CollaborationMatrixKeyUpload(oneTimeKeys = mapOf("curve25519:1" to valid)),
                CollaborationMatrixKeyUpload(
                    oneTimeKeys = mapOf("signed_curve25519:" + "x".repeat(129) to valid)
                ),
                CollaborationMatrixKeyUpload(
                    oneTimeKeys = mapOf("signed_curve25519:1" to valid.copy(key = oneTime + "==="))
                ),
                CollaborationMatrixKeyUpload(
                    oneTimeKeys =
                        mapOf(
                            "signed_curve25519:1" to
                                valid.copy(signatures = mapOf(user to emptyMap()))
                        )
                ),
                CollaborationMatrixKeyUpload(
                    oneTimeKeys = mapOf("signed_curve25519:1" to valid),
                    fallbackKeys = mapOf("signed_curve25519:1" to key(fallback, true)),
                ),
            )) assertInstanceOf(Result.Failed::class.java, snapshotCollaborationKeyUpload(upload))
        assertInstanceOf(
            Result.Success::class.java,
            snapshotCollaborationKeyUpload(CollaborationMatrixKeyUpload()),
        )
    }

    @Test
    fun callerMutationCannotReplaceAdmittedKeySnapshot() {
        val proofs = mutableMapOf("ed25519:$device" to original)
        val algorithms = identity().algorithms.toMutableList()
        val keys = identity().keys.toMutableMap()
        val oneTimes = mutableMapOf("signed_curve25519:1" to key())
        val upload =
            CollaborationMatrixKeyUpload(
                identity()
                    .copy(algorithms = algorithms, keys = keys, signatures = mapOf(user to proofs)),
                oneTimes,
            )
        val copied = (snapshotCollaborationKeyUpload(upload) as Result.Success).value
        proofs.clear()
        algorithms.clear()
        keys.clear()
        oneTimes.clear()
        assertInstanceOf(
            Result.Success::class.java,
            validateCollaborationDeviceKeys(requireNotNull(copied.deviceKeys), user, device),
        )
        assertEquals(
            Result.Success(Unit),
            validateCollaborationSignedKeys(copied, requireNotNull(copied.deviceKeys)),
        )
        assertFalse(copied.toString().contains(publicKey))
    }
}
