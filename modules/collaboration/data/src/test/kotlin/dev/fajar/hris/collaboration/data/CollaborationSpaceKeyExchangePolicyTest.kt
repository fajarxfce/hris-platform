package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceKeyExchangePolicyTest {
    private val space = UUID.randomUUID()
    private val account = UUID.randomUUID()
    private val groupDevice = UUID.randomUUID()
    private val server = "matrix.example.test"
    private val at = Instant.parse("2026-11-04T12:00:00Z")
    private val user = "@${collaborationMatrixLocalpart(UUID.randomUUID(),account)}:$server"
    private val alias = "@${collaborationSpaceMatrixLocalpart(space,account)}:$server"
    private val device = collaborationMatrixDeviceId(UUID.randomUUID())
    private val fixture = MatrixKeyFixture(user, device)
    private val row =
        CollaborationSpaceRecipientDevice(
            space,
            account,
            groupDevice,
            server,
            user,
            device,
            success(validateCollaborationDeviceKeys(fixture.device, user, device)),
            at.plusSeconds(600),
            true,
        )
    private val selection =
        success(
            selectCollaborationSpaceKeys(
                space,
                server,
                CollaborationMatrixKeyQuery(mapOf(user to emptyList(), alias to emptyList())),
                listOf(row),
                at,
                true,
            )
        )

    @Test
    fun retainedDeviceKeysAppearOnlyUnderTheirOriginalAliasAndDeviceId() {
        val result = success(verifyCollaborationSpaceKeyQuery(selection, response()))
        assertEquals(fixture.device, result.deviceKeys[user]?.get(device))
        assertTrue(result.deviceKeys.getValue(alias).isEmpty())
        assertEquals(setOf(device), result.deviceKeys.getValue(user).keys)
    }

    @Test
    fun missingReservedIdentityIsAllowedButMissingConfirmedAndForeignKeysFail() {
        val pending =
            selection.copy(devices = mapOf(groupDevice to row.copy(publicationConfirmed = false)))
        assertTrue(
            success(
                    verifyCollaborationSpaceKeyQuery(
                        pending,
                        CollaborationMatrixKeyQueryResult(mapOf(user to emptyMap())),
                    )
                )
                .deviceKeys
                .getValue(user)
                .isEmpty()
        )
        for (response in
            listOf(
                CollaborationMatrixKeyQueryResult(mapOf(user to emptyMap())),
                CollaborationMatrixKeyQueryResult(emptyMap()),
                CollaborationMatrixKeyQueryResult(
                    mapOf(user to mapOf(device to fixture.device), alias to emptyMap())
                ),
                CollaborationMatrixKeyQueryResult(
                    mapOf(user to mapOf("HRIS_unknown" to fixture.device))
                ),
            )) assertEquals(
            "collaboration_provider_invalid_response",
            failed(verifyCollaborationSpaceKeyQuery(selection, response)),
        )
        assertEquals(
            fixture.device,
            success(verifyCollaborationSpaceKeyQuery(pending, response()))
                .deviceKeys[user]
                ?.get(device),
        )
    }

    @Test
    fun wrongSignaturesChangedPinsAndForeignIdentityCannotBeReturned() {
        for (invalid in
            listOf(
                fixture.device.copy(userId = alias),
                fixture.device.copy(deviceId = "HRISG_" + groupDevice.toString().replace("-", "")),
                fixture.device.copy(signatures = emptyMap()),
                fixture.device.copy(algorithms = fixture.device.algorithms.reversed()),
            )) {
            assertEquals(
                "collaboration_provider_invalid_response",
                failed(verifyCollaborationSpaceKeyQuery(selection, response(invalid))),
            )
        }
        assertEquals(
            "collaboration_space_device_keys_changed",
            failed(
                verifyCollaborationSpaceKeyQuery(
                    selection,
                    response(MatrixKeyFixture(user, device).device),
                )
            ),
        )
    }

    @Test
    fun mutableProviderMapsCannotChangeVerifiedDeviceOrSignedClaimAfterReturn() {
        val algorithms = fixture.device.algorithms.toMutableList()
        val publicKeys = fixture.device.keys.toMutableMap()
        val signatures = fixture.device.signatures.getValue(user).toMutableMap()
        val signaturesByUser = mutableMapOf(user to signatures)
        val devices =
            mutableMapOf(
                device to
                    fixture.device.copy(
                        algorithms = algorithms,
                        keys = publicKeys,
                        signatures = signaturesByUser,
                    )
            )
        val users = mutableMapOf(user to devices)
        val verified =
            success(
                verifyCollaborationSpaceKeyQuery(
                    selection,
                    CollaborationMatrixKeyQueryResult(users),
                )
            )
        algorithms.clear()
        publicKeys.clear()
        signatures.clear()
        signaturesByUser.clear()
        devices.clear()
        users.clear()
        assertEquals(fixture.device, verified.deviceKeys[user]?.get(device))
        val key = fixture.signed(true)
        val proof = key.signatures.getValue(user).toMutableMap()
        val keys =
            mutableMapOf(
                "signed_curve25519:fallback" to key.copy(signatures = mapOf(user to proof))
            )
        val claims = mutableMapOf(device to keys)
        val byUser = mutableMapOf(user to claims)
        val claimed =
            success(
                verifyCollaborationSpaceKeyClaim(
                    selection,
                    verified,
                    CollaborationMatrixKeyClaimResult(byUser),
                )
            )
        proof.clear()
        keys.clear()
        claims.clear()
        byUser.clear()
        assertEquals(key, claimed.oneTimeKeys[user]?.get(device)?.get("signed_curve25519:fallback"))
        assertTrue(claimed.oneTimeKeys.getValue(alias).isEmpty())
    }

    @Test
    fun oneTimeAndFallbackClaimsRequireTheVerifiedSigningIdentityAndAtMostOneKey() {
        val verified = success(verifyCollaborationSpaceKeyQuery(selection, response()))
        for (flag in listOf(null, false, true)) {
            val signed = fixture.signed(flag)
            val response = claim(mapOf("signed_curve25519:one" to signed))
            assertEquals(
                signed,
                success(verifyCollaborationSpaceKeyClaim(selection, verified, response))
                    .oneTimeKeys[user]
                    ?.get(device)
                    ?.get("signed_curve25519:one"),
            )
        }
        assertTrue(
            success(
                    verifyCollaborationSpaceKeyClaim(
                        selection,
                        verified,
                        CollaborationMatrixKeyClaimResult(emptyMap()),
                    )
                )
                .oneTimeKeys
                .values
                .all { it.isEmpty() }
        )
        val signed = fixture.signed()
        for (response in
            listOf(
                claim(mapOf("signed_curve25519:a" to signed, "signed_curve25519:b" to signed)),
                claim(mapOf("curve25519:unsigned" to signed)),
                claim(mapOf("signed_curve25519:a" to signed.copy(fallback = true))),
                claim(mapOf("signed_curve25519:a" to MatrixKeyFixture(user, device).signed())),
                CollaborationMatrixKeyClaimResult(
                    mapOf(alias to mapOf(device to mapOf("signed_curve25519:a" to signed)))
                ),
                CollaborationMatrixKeyClaimResult(
                    mapOf(user to mapOf("HRIS_foreign" to mapOf("signed_curve25519:a" to signed)))
                ),
            )) {
            assertEquals(
                "collaboration_provider_invalid_response",
                failed(verifyCollaborationSpaceKeyClaim(selection, verified, response)),
            )
        }
    }

    private fun response(value: CollaborationMatrixDeviceKeys = fixture.device) =
        CollaborationMatrixKeyQueryResult(mapOf(user to mapOf(device to value)))

    private fun claim(keys: Map<String, CollaborationMatrixSignedKey>) =
        CollaborationMatrixKeyClaimResult(mapOf(user to mapOf(device to keys)))

    private fun failed(result: Result<*>) = (result as Result.Failed).failure.code

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
