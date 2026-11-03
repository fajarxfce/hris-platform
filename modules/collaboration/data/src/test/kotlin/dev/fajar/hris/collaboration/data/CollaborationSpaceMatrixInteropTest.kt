package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.collaborationSpaceMatrixLocalpart
import dev.fajar.hris.core.domain.Result
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceMatrixInteropTest {
    @Test
    fun actualProviderKeepsRetainedUserDeviceAndKeysWhileAddingDistinctOrganizationIdentities() {
        MatrixProviderFixture("10m").use { f ->
            f.start()
            val original = f.login()
            val originalKeys = MatrixKeyFixture(original.userId, original.deviceId)
            fun keys() =
                InternalCollaborationMatrixKeyRepository(
                    HttpMatrixClientDataSource(f.client, f.config, f.clock)
                )
            fun repository() =
                InternalCollaborationSpaceMatrixRepository(
                    HmacMatrixJwtDataSource(f.config),
                    HttpMatrixClientDataSource(f.client, f.config, f.clock),
                    f.clock,
                )
            fun deadline() = f.clock.instant().plusSeconds(20)
            success(
                keys()
                    .upload(
                        CollaborationMatrixKeyUpload(originalKeys.device),
                        original.accessToken,
                        deadline(),
                    )
            )
            val space = UUID.randomUUID()
            fun request(
                legacyCompany: UUID?,
                retainedDevice: CollaborationLegacyDevicePosition?,
            ): CollaborationSpaceMatrixLogin {
                val issuedAt = f.clock.instant()
                return CollaborationSpaceMatrixLogin(
                    space,
                    f.request.accountId,
                    UUID.randomUUID(),
                    legacyCompany,
                    retainedDevice,
                    issuedAt,
                    issuedAt.plusSeconds(60),
                )
            }
            val retained =
                success(
                    repository()
                        .login(
                            request(
                                f.request.companyId,
                                CollaborationLegacyDevicePosition(
                                    f.request.companyId,
                                    f.request.deviceId,
                                ),
                            )
                        )
                )
            assertEquals(original.userId, retained.userId)
            assertEquals(original.deviceId, retained.deviceId)
            val freshRequest = request(f.request.companyId, null)
            val fresh = success(repository().login(freshRequest))
            assertEquals(original.userId, fresh.userId)
            assertEquals("HRISG_${compact(freshRequest.deviceId)}", fresh.deviceId)
            assertNotEquals(original.deviceId, fresh.deviceId)
            val organizationRequest = request(null, null)
            val organization = success(repository().login(organizationRequest))
            assertEquals(
                "@${collaborationSpaceMatrixLocalpart(space, f.request.accountId)}:matrix.example.test",
                organization.userId,
            )
            assertEquals("HRISG_${compact(organizationRequest.deviceId)}", organization.deviceId)
            assertNotEquals(original.userId, organization.userId)
            val organizationKeys = MatrixKeyFixture(organization.userId, organization.deviceId)
            success(
                keys()
                    .upload(
                        CollaborationMatrixKeyUpload(organizationKeys.device),
                        organization.accessToken,
                        deadline(),
                    )
            )
            val query =
                CollaborationMatrixKeyQuery(
                    mapOf(
                        original.userId to listOf(original.deviceId),
                        organization.userId to listOf(organization.deviceId),
                    )
                )
            val expected =
                mapOf(
                    original.userId to mapOf(original.deviceId to originalKeys.device),
                    organization.userId to mapOf(organization.deviceId to organizationKeys.device),
                )
            for (token in
                listOf(
                    original.accessToken,
                    retained.accessToken,
                    fresh.accessToken,
                    organization.accessToken,
                )) {
                assertEquals(
                    expected,
                    success(keys().deviceKeys(query, token, deadline())).deviceKeys,
                )
                val identity = success(f.repository.whoAmI(token, deadline()))
                assertFalse(identity.isGuest)
            }
            f.restart()
            assertEquals(
                expected,
                success(keys().deviceKeys(query, retained.accessToken, deadline())).deviceKeys,
            )
            val replayAt = f.clock.instant()
            val replay =
                success(
                    repository()
                        .login(
                            freshRequest.copy(
                                issuedAt = replayAt,
                                expiresAt = replayAt.plusSeconds(60),
                            )
                        )
                )
            assertEquals(fresh.userId, replay.userId)
            assertEquals(fresh.deviceId, replay.deviceId)
            assertEquals(
                expected,
                success(keys().deviceKeys(query, replay.accessToken, deadline())).deviceKeys,
            )
            // Resource cleanup stops only this owned fixture. No Matrix logout or key deletion.
        }
    }

    private fun compact(id: UUID) = id.toString().replace("-", "")

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
