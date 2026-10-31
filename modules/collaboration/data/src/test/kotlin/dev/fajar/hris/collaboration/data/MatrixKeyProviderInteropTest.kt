package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.matrix.HttpMatrixClientDataSource
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationMatrixKeyRepository
import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixKeyUpload
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MatrixKeyProviderInteropTest {
    @Test
    fun actualProviderRetainsSignedKeysAcrossRetryPrivateBearerRotationAndRestart() {
        MatrixProviderFixture("10m").use { f ->
            f.start()
            var token = f.login()
            fun repository() =
                InternalCollaborationMatrixKeyRepository(
                    HttpMatrixClientDataSource(f.client, f.config, f.clock)
                )
            fun deadline() = f.clock.instant().plusSeconds(20)
            fun query() =
                repository()
                    .ownDeviceKeys(token.userId, token.deviceId, token.accessToken, deadline())
            assertEquals(Result.Success(null), query())
            val keys = MatrixKeyFixture(token.userId, token.deviceId)
            val fingerprint =
                validateCollaborationDeviceKeys(keys.device, token.userId, token.deviceId)
            assertInstanceOf(Result.Success::class.java, fingerprint)
            val upload =
                CollaborationMatrixKeyUpload(
                    keys.device,
                    mapOf(
                        "signed_curve25519:1" to keys.signed(),
                        "signed_curve25519:2" to keys.signed(false),
                    ),
                    mapOf("signed_curve25519:fallback" to keys.signed(true)),
                )
            assertEquals(Result.Success(Unit), validateCollaborationSignedKeys(upload, keys.device))
            repeat(2) {
                val result = repository().upload(upload, token.accessToken, deadline())
                assertInstanceOf(Result.Success::class.java, result)
                assertEquals(
                    2,
                    (result as Result.Success).value.oneTimeKeyCounts["signed_curve25519"],
                )
                val read = query()
                assertEquals(Result.Success(keys.device), read)
                assertEquals(
                    fingerprint,
                    validateCollaborationDeviceKeys(
                        requireNotNull((read as Result.Success).value),
                        token.userId,
                        token.deviceId,
                    ),
                )
            }
            token = f.login()
            assertEquals(Result.Success(keys.device), query())
            f.restart()
            assertEquals(Result.Success(keys.device), query())
            val counts =
                repository().upload(CollaborationMatrixKeyUpload(), token.accessToken, deadline())
            assertInstanceOf(Result.Success::class.java, counts)
            assertEquals(2, (counts as Result.Success).value.oneTimeKeyCounts["signed_curve25519"])
            // No logout cleanup: owned provider destruction retires only this fictional fixture.
        }
    }

    @Test
    fun providerAllowsIdentityReplacementSoHrisMustFenceItBeforeUpload() {
        MatrixProviderFixture("10m").use { f ->
            f.start()
            val token = f.login()
            val repo =
                InternalCollaborationMatrixKeyRepository(
                    HttpMatrixClientDataSource(f.client, f.config, f.clock)
                )
            val first = MatrixKeyFixture(token.userId, token.deviceId)
            val replacement = MatrixKeyFixture(token.userId, token.deviceId)
            fun upload(keys: MatrixKeyFixture) =
                repo.upload(
                    CollaborationMatrixKeyUpload(keys.device),
                    token.accessToken,
                    f.clock.instant().plusSeconds(20),
                )
            assertInstanceOf(Result.Success::class.java, upload(first))
            assertInstanceOf(Result.Success::class.java, upload(replacement))
            val actual =
                repo.ownDeviceKeys(
                    token.userId,
                    token.deviceId,
                    token.accessToken,
                    f.clock.instant().plusSeconds(20),
                )
            assertEquals(Result.Success(replacement.device), actual)
            assertNotEquals(
                validateCollaborationDeviceKeys(first.device, token.userId, token.deviceId),
                validateCollaborationDeviceKeys(replacement.device, token.userId, token.deviceId),
            )
        }
    }
}
