package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationSpaceMatrixRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.Result
import java.time.*
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollaborationSpaceMatrixRepositoryTest {
    private val now = Instant.parse("2026-11-03T20:00:00Z")
    private val request =
        CollaborationSpaceMatrixLogin(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            null,
            now,
            now.plusSeconds(60),
        )

    @Test
    fun coordinatesAreDerivedFromTypedOwnersAndRejectAnUnrelatedRetainedNamespace() {
        val legacy = CollaborationLegacyDevicePosition(UUID.randomUUID(), UUID.randomUUID())
        val calls = mutableListOf<String>()
        val signing =
            object : MatrixJwtDataSource {
                override fun sign(subject: String, issuedAt: Instant, expiresAt: Instant): String {
                    calls += subject
                    return "fixture-jwt"
                }
            }
        val client =
            object : Client() {
                override fun login(
                    jwt: String,
                    deviceId: String,
                    deadline: Instant,
                ): MatrixLoginData {
                    assertEquals("fixture-jwt", jwt)
                    assertEquals(now.plusSeconds(60), deadline)
                    calls += deviceId
                    return MatrixLoginData(
                        "@fixture:matrix.example.test",
                        deviceId,
                        "fixture-token",
                        now.plusSeconds(600),
                    )
                }
            }
        val repo =
            InternalCollaborationSpaceMatrixRepository(
                signing,
                client,
                Clock.fixed(now, ZoneOffset.UTC),
            )
        assertInstanceOf(Result.Success::class.java, repo.login(request))
        assertEquals(
            listOf(
                "hrisg_${compact(request.spaceId)}_${compact(request.accountId)}",
                "HRISG_${compact(request.deviceId)}",
            ),
            calls,
        )
        calls.clear()
        assertInstanceOf(
            Result.Success::class.java,
            repo.login(
                request.copy(canonicalLegacyCompanyId = legacy.companyId, retainedDevice = legacy)
            ),
        )
        assertEquals(
            listOf(
                "hris_${compact(legacy.companyId)}_${compact(request.accountId)}",
                "HRIS_${compact(legacy.deviceId)}",
            ),
            calls,
        )
        assertThrows(IllegalArgumentException::class.java) { request.copy(retainedDevice = legacy) }
        assertThrows(IllegalArgumentException::class.java) {
            request.copy(canonicalLegacyCompanyId = UUID.randomUUID(), retainedDevice = legacy)
        }
    }

    @Test
    fun expiredObservationDoesNotSignOrCallProviderAndLateCandidatesRemainFencedByTheCaller() {
        val signing =
            object : MatrixJwtDataSource {
                override fun sign(subject: String, issuedAt: Instant, expiresAt: Instant) =
                    "fixture-jwt"
            }
        val client =
            object : Client() {
                override fun login(jwt: String, deviceId: String, deadline: Instant) =
                    MatrixLoginData(
                        "@fixture:matrix.example.test",
                        deviceId,
                        "fixture-token",
                        now.plusSeconds(600),
                    )
            }
        val expiredSigner =
            object : MatrixJwtDataSource {
                override fun sign(subject: String, issuedAt: Instant, expiresAt: Instant): String =
                    error("Expired observation signed")
            }
        val expired =
            InternalCollaborationSpaceMatrixRepository(
                    expiredSigner,
                    client,
                    Clock.fixed(now.plusSeconds(60), ZoneOffset.UTC),
                )
                .login(request)
        assertEquals("collaboration_admission_changed", (expired as Result.Failed).failure.code)
        var observedAt = now
        val clock =
            object : Clock() {
                override fun getZone(): ZoneId = ZoneOffset.UTC

                override fun withZone(zone: ZoneId): Clock = this

                override fun instant(): Instant = observedAt
            }
        val lateClient =
            object : Client() {
                override fun login(
                    jwt: String,
                    deviceId: String,
                    deadline: Instant,
                ): MatrixLoginData {
                    observedAt = now.plusSeconds(61)
                    return MatrixLoginData(
                        "@fixture:matrix.example.test",
                        deviceId,
                        "fixture-token",
                        now.plusSeconds(600),
                    )
                }
            }
        val candidate =
            InternalCollaborationSpaceMatrixRepository(signing, lateClient, clock).login(request)
        assertTrue(observedAt > request.expiresAt)
        assertEquals(now.plusSeconds(600), (candidate as Result.Success).value.expiresAt)
    }

    @Test
    fun providerFailuresAndInterruptionDoNotTriggerLogoutOrHiddenRetries() {
        val signing =
            object : MatrixJwtDataSource {
                override fun sign(subject: String, issuedAt: Instant, expiresAt: Instant) =
                    "fixture-jwt"
            }
        var calls = 0
        fun execute(error: Exception) =
            InternalCollaborationSpaceMatrixRepository(
                    signing,
                    object : Client() {
                        override fun login(
                            jwt: String,
                            deviceId: String,
                            deadline: Instant,
                        ): MatrixLoginData {
                            calls++
                            throw error
                        }
                    },
                    Clock.fixed(now, ZoneOffset.UTC),
                )
                .login(request)
        assertInstanceOf(Result.Failed::class.java, execute(MatrixStatusException(429)))
        assertThrows(InterruptedException::class.java) { execute(InterruptedException("fixture")) }
        assertThrows(CancellationException::class.java) {
            execute(CancellationException("fixture"))
        }
        assertEquals(3, calls)
    }

    private open class Client : MatrixClientDataSource {
        override fun login(jwt: String, deviceId: String, deadline: Instant): MatrixLoginData =
            error("Unexpected login")

        override fun whoAmI(accessToken: String, deadline: Instant): MatrixIdentityData =
            error("Unexpected identity call")

        override fun logoutDevice(accessToken: String) {
            error("A login candidate must never delete the device")
        }
    }

    private fun compact(id: UUID) = id.toString().replace("-", "")
}
