package dev.fajar.hris

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceProvisioningRepository
import dev.fajar.hris.collaboration.domain.usecases.*
import dev.fajar.hris.core.domain.*
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource

@TestPropertySource(
    properties =
        [
            "HRIS_COLLABORATION_ENABLED=true",
            "HRIS_MATRIX_ENABLED=true",
            "HRIS_COLLABORATION_SPACE_ENABLED=true",
            "HRIS_COLLABORATION_SPACE_MIN_ANDROID_BUILD=28",
            "HRIS_MATRIX_ORIGIN=http://127.0.0.1:9",
            "HRIS_MATRIX_SERVER_NAME=matrix.example.test",
            "HRIS_MATRIX_JWT_ISSUER=urn:hris:matrix:fixture",
            "HRIS_MATRIX_JWT_AUDIENCE=matrix:fixture",
        ]
)
class NativeCollaborationSpaceConfigurationTest : NativeCollaborationApiFixture() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun matrix(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_MATRIX_JWT_SECRET_FILE") {
                Path.of(
                        requireNotNull(
                                NativeCollaborationSpaceConfigurationTest::class
                                    .java
                                    .getResource("/matrix-signing.fixture")
                            )
                            .toURI()
                    )
                    .toString()
            }
        }
    }

    @Autowired private lateinit var selectSpace: SelectNativeCollaborationSpaceIdentity
    @Autowired private lateinit var registerSpace: RegisterNativeCollaborationSpaceDevice
    @Autowired private lateinit var retrySpace: RetryNativeCollaborationSpaceProvisioning
    @Autowired private lateinit var claimSpace: ClaimCollaborationSpaceProvisioning
    @Autowired private lateinit var queue: CollaborationSpaceProvisioningRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var policy: CollaborationSpacePolicy

    @Test
    fun actualApiGraphEnqueuesUnderCurrentNativeProofWithoutGrantingWorkerCapability() {
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS))
        assertTrue(policy.enabled)
        assertEquals(28, policy.minimumAndroidBuild)
        val n = native()
        val row =
            database()
                .queryForMap(
                    "select authenticated_at,mfa_verified_at,credential_version,version from native_sessions where id=?",
                    n.session,
                )
        val actor =
            Actor(
                n.fixture.account,
                null,
                emptySet(),
                (row["authenticated_at"] as java.sql.Timestamp).toInstant(),
                UUID.randomUUID(),
                mfaVerifiedAt = (row["mfa_verified_at"] as? java.sql.Timestamp)?.toInstant(),
                credentialVersion = (row["credential_version"] as Number).toLong(),
            )
        val version = (row["version"] as Number).toLong()
        val space =
            requireNotNull(
                database().queryForObject("select id from collaboration_spaces", UUID::class.java)
            )
        val client = ClientVersion(ClientPlatform.ANDROID, 28)
        assertInstanceOf(
            Result.Success::class.java,
            selectSpace.execute(actor, n.session, version, space, client, UUID.randomUUID()),
        )
        val operation = UUID.randomUUID()
        fun register() =
            registerSpace.execute(
                actor,
                n.session,
                version,
                space,
                client,
                operation,
                CollaborationPlatform.ANDROID,
                "Fictional API device",
                CollaborationSpaceDeviceOrigin.SPACE,
            )
        val registered = register()
        assertInstanceOf(Result.Success::class.java, registered) {
            if (registered is Result.Failed) registered.failure.code else "Unexpected result"
        }
        val receipt = (registered as Result.Success).value
        val head = transactions.run(actor) { queue.metadata(space, actor.accountId, receipt.id) }
        assertInstanceOf(Result.Success::class.java, head)
        val stored = requireNotNull((head as Result.Success).value)
        assertEquals(CollaborationSpaceProvisioningState.QUEUED, stored.state)
        assertEquals(actor.authenticatedAt, stored.request.authenticatedAt)
        assertEquals(actor.mfaVerifiedAt, stored.request.mfaVerifiedAt)
        assertEquals(receipt.copy(replayed = true), (register() as Result.Success).value)
        assertInstanceOf(Result.Failed::class.java, claimSpace.execute(UUID.randomUUID(), 1))
        assertInstanceOf(
            Result.Failed::class.java,
            retrySpace.execute(
                actor,
                n.session,
                version,
                space,
                client,
                UUID.randomUUID(),
                receipt.id,
                0,
                0,
            ),
        )
        assertEquals(
            stored,
            (transactions.run(actor) { queue.metadata(space, actor.accountId, receipt.id) }
                    as Result.Success)
                .value,
        )
    }
}
