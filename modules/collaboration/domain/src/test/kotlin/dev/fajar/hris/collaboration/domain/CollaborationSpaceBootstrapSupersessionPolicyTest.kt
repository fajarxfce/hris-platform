package dev.fajar.hris.collaboration.domain

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceGatewayBootstrapIntent
import dev.fajar.hris.collaboration.domain.policies.validateCollaborationSpaceBootstrapSupersession
import dev.fajar.hris.core.domain.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceBootstrapSupersessionPolicyTest {
    private val original =
        CollaborationSpaceGatewayBootstrapIntent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            null,
            ClientVersion(ClientPlatform.ANDROID, 1),
        )
    private val replacement =
        original.copy(operationId = UUID.randomUUID(), client = original.client.copy(build = 50))

    @Test
    fun historicalBuildDoesNotNeedCurrentAdmissionAndBothInitiallyAbsentAndAdvancedHeadsAreAllowed() {
        for (platform in listOf(ClientPlatform.ANDROID, ClientPlatform.IOS)) {
            for (old in listOf(null, 0L, 9L, 999998L)) {
                for (observed in listOf(null, 0L, 9L, 999998L)) {
                    val before =
                        original.copy(
                            expectedGatewayVersion = old,
                            client = ClientVersion(platform, 1),
                        )
                    val next =
                        replacement.copy(
                            expectedGatewayVersion = observed,
                            client = ClientVersion(platform, 999999999),
                        )
                    val result = validateCollaborationSpaceBootstrapSupersession(before, next)
                    assertEquals((observed ?: -1) >= (old ?: -1), result is Result.Success)
                }
            }
        }
    }

    @Test
    fun supersessionNeverCrossesAnAccountSpaceParentDeviceOrPlatform() {
        val changed =
            listOf(
                replacement.copy(spaceId = UUID.randomUUID()),
                replacement.copy(accountId = UUID.randomUUID()),
                replacement.copy(nativeSessionId = UUID.randomUUID()),
                replacement.copy(deviceId = UUID.randomUUID()),
                replacement.copy(client = ClientVersion(ClientPlatform.IOS, 50)),
                replacement.copy(operationId = original.operationId),
            )
        changed.forEach {
            assertEquals(
                "collaboration_space_bootstrap_replacement_changed",
                (validateCollaborationSpaceBootstrapSupersession(original, it) as Result.Failed)
                    .failure
                    .code,
            )
        }
    }

    @Test
    fun futureOrOutOfRangeDeviceObservationsCannotBeLaunderedIntoAReplacement() {
        for (version in listOf(-1L, 1000000L, Long.MAX_VALUE)) {
            assertInstanceOf(
                Result.Failed::class.java,
                validateCollaborationSpaceBootstrapSupersession(
                    original.copy(expectedDeviceVersion = version),
                    replacement,
                ),
            )
            assertInstanceOf(
                Result.Failed::class.java,
                validateCollaborationSpaceBootstrapSupersession(
                    original,
                    replacement.copy(expectedDeviceVersion = version),
                ),
            )
        }
        assertInstanceOf(
            Result.Failed::class.java,
            validateCollaborationSpaceBootstrapSupersession(
                original.copy(expectedDeviceVersion = 2),
                replacement,
            ),
        )
        assertInstanceOf(
            Result.Success::class.java,
            validateCollaborationSpaceBootstrapSupersession(
                original,
                replacement.copy(expectedDeviceVersion = 2),
            ),
        )
    }

    @Test
    fun malformedGatewayVersionsAndNonNativeBuildMetadataAreRejectedOnEitherSide() {
        for (version in listOf(-1L, 999999L, Long.MAX_VALUE)) {
            assertInstanceOf(
                Result.Failed::class.java,
                validateCollaborationSpaceBootstrapSupersession(
                    original.copy(expectedGatewayVersion = version),
                    replacement,
                ),
            )
            assertInstanceOf(
                Result.Failed::class.java,
                validateCollaborationSpaceBootstrapSupersession(
                    original,
                    replacement.copy(expectedGatewayVersion = version),
                ),
            )
        }
        for (client in
            listOf(
                ClientVersion(ClientPlatform.WEB, 50),
                ClientVersion(ClientPlatform.ANDROID, 0),
                ClientVersion(ClientPlatform.ANDROID, 1000000000),
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                validateCollaborationSpaceBootstrapSupersession(
                    original.copy(client = client),
                    replacement,
                ),
            )
            assertInstanceOf(
                Result.Failed::class.java,
                validateCollaborationSpaceBootstrapSupersession(
                    original,
                    replacement.copy(client = client),
                ),
            )
        }
    }
}
