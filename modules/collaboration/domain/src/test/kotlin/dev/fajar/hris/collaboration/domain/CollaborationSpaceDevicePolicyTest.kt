package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceDevicePolicyTest {
    private val now = Instant.parse("2026-11-03T20:00:00Z")
    private val space = UUID.randomUUID()
    private val account = UUID.randomUUID()
    private val parent = UUID.randomUUID()
    private val legacy = CollaborationLegacyDevicePosition(UUID.randomUUID(), UUID.randomUUID())
    private val fingerprint = "a".repeat(64)
    private val identity =
        CollaborationSpaceIdentity(
            space,
            account,
            "matrix.example.test",
            "@${collaborationMatrixLocalpart(legacy.companyId, account)}:matrix.example.test",
            CollaborationSpaceIdentityOrigin.LEGACY,
            legacy,
            now.minusSeconds(10),
        )
    private val source =
        CollaborationLegacyParent(
            CollaborationLegacyDevice(
                legacy,
                account,
                identity.matrixUserId,
                collaborationMatrixDeviceId(legacy.deviceId),
                CollaborationPlatform.ANDROID,
                CollaborationDeviceState.ACTIVE,
                1,
                0,
                now.minusSeconds(60),
                now.minusSeconds(30),
                null,
                null,
            ),
            parent,
            now.minusSeconds(30),
            CollaborationLegacyParentKey(fingerprint, 0, now.minusSeconds(20), null),
        )
    private val client = ClientVersion(ClientPlatform.ANDROID, 28)

    @Test
    fun explicitOriginRequiresItsExactFieldsAndNativePlatform() {
        fun request(
            origin: CollaborationSpaceDeviceOrigin = CollaborationSpaceDeviceOrigin.SPACE,
            position: CollaborationLegacyDevicePosition? = null,
            key: String? = null,
            platform: CollaborationPlatform = CollaborationPlatform.ANDROID,
            label: String = " Phone ",
            version: ClientVersion? = client,
        ) = validateCollaborationSpaceDeviceRequest(origin, position, key, platform, label, version)
        assertEquals("Phone", (request() as Result.Success).value)
        assertInstanceOf(
            Result.Success::class.java,
            request(CollaborationSpaceDeviceOrigin.LEGACY, legacy, fingerprint),
        )
        for (invalid in
            listOf(
                request(position = legacy),
                request(key = fingerprint),
                request(CollaborationSpaceDeviceOrigin.LEGACY),
                request(CollaborationSpaceDeviceOrigin.LEGACY, legacy, "invalid"),
                request(platform = CollaborationPlatform.WEB),
                request(platform = CollaborationPlatform.IOS),
                request(version = null),
                request(label = " "),
                request(label = "a\nb"),
                request(label = "a".repeat(65)),
            )) assertInstanceOf(Result.Failed::class.java, invalid)
    }

    @Test
    fun retainedMetadataMustStillMatchTheParentCanonicalUserAndUnretiredKeyAtCompletion() {
        fun validate(value: CollaborationLegacyParent?) =
            validateCollaborationSpaceLegacyDevice(
                identity,
                value,
                parent,
                CollaborationPlatform.ANDROID,
                legacy,
                fingerprint,
                now,
            )
        assertInstanceOf(Result.Success::class.java, validate(source))
        assertInstanceOf(
            Result.Success::class.java,
            validate(
                source.copy(key = requireNotNull(source.key).copy(version = 1, publishedAt = now))
            ),
        )
        val invalid =
            listOf(
                null,
                source.copy(nativeSessionId = UUID.randomUUID()),
                source.copy(key = null),
                source.copy(boundAt = now.plusSeconds(1)),
                source.copy(device = source.device.copy(accountId = UUID.randomUUID())),
                source.copy(
                    device = source.device.copy(matrixUserId = "@foreign:matrix.example.test")
                ),
                source.copy(device = source.device.copy(matrixDeviceId = "HRIS_wrong")),
                source.copy(
                    device =
                        source.device.copy(
                            state = CollaborationDeviceState.REVOKED,
                            deviceRevokedAt = now,
                        )
                ),
                source.copy(device = source.device.copy(credentialRetiredAt = now)),
                source.copy(device = source.device.copy(platform = CollaborationPlatform.IOS)),
                source.copy(key = requireNotNull(source.key).copy(fingerprint = "b".repeat(64))),
                source.copy(key = requireNotNull(source.key).copy(version = 1)),
                source.copy(key = requireNotNull(source.key).copy(reservedAt = now.plusSeconds(1))),
                source.copy(
                    key =
                        requireNotNull(source.key)
                            .copy(version = 1, publishedAt = now.plusSeconds(1))
                ),
            )
        for (value in invalid) assertInstanceOf(Result.Failed::class.java, validate(value))
    }

    @Test
    fun existingBindingCannotChangeOriginProviderDeviceOrCurrentNativeOwner() {
        val id = UUID.randomUUID()
        val device =
            CollaborationSpaceDevice(
                id,
                space,
                account,
                identity.matrixUserId,
                collaborationMatrixDeviceId(legacy.deviceId),
                CollaborationSpaceDeviceOrigin.LEGACY,
                legacy,
                fingerprint,
                parent,
                CollaborationPlatform.ANDROID,
                "Phone",
                CollaborationDeviceState.PROVISIONING,
                0,
                now,
                now,
                null,
            )
        fun validate(value: CollaborationSpaceDevice) =
            validateExistingCollaborationSpaceDevice(
                identity,
                value,
                parent,
                CollaborationPlatform.ANDROID,
                CollaborationSpaceDeviceOrigin.LEGACY,
                legacy,
                fingerprint,
                now,
            )
        assertInstanceOf(Result.Success::class.java, validate(device))
        assertInstanceOf(
            Result.Success::class.java,
            validate(device.copy(state = CollaborationDeviceState.ACTIVE, version = 1)),
        )
        for (invalid in
            listOf(
                device.copy(origin = CollaborationSpaceDeviceOrigin.SPACE),
                device.copy(originNativeSessionId = UUID.randomUUID()),
                device.copy(spaceId = UUID.randomUUID()),
                device.copy(accountId = UUID.randomUUID()),
                device.copy(matrixDeviceId = "HRIS_changed"),
                device.copy(legacyKeyFingerprint = "b".repeat(64)),
                device.copy(state = CollaborationDeviceState.REVOKED, revokedAt = now),
                device.copy(updatedAt = now.plusSeconds(1)),
                device.copy(version = 1000000),
            )) assertInstanceOf(Result.Failed::class.java, validate(invalid))
    }
}
