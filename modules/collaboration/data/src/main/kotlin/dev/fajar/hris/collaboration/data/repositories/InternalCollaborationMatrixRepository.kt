package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRepository
import dev.fajar.hris.core.domain.Result
import java.time.Clock
import java.time.Instant

class InternalCollaborationMatrixRepository(
    private val signing: MatrixJwtDataSource,
    private val client: MatrixClientDataSource,
    private val clock: Clock,
) : CollaborationMatrixRepository {
    override fun login(request: CollaborationMatrixLogin): Result<CollaborationMatrixCredential> =
        safeMatrixProviderCall {
            val now = clock.instant()
            val deadline = Instant.ofEpochSecond(request.expiresAt.epochSecond)
            if (now < request.issuedAt || now >= deadline) throw MatrixDeadlineException()
            val jwt =
                signing.sign(
                    collaborationMatrixLocalpart(request.companyId, request.accountId),
                    request.issuedAt,
                    request.expiresAt,
                )
            val response =
                client.login(jwt, collaborationMatrixDeviceId(request.deviceId), deadline)
            // Do not discard a late successful token: the caller needs it for fenced publication
            // or expiry without deleting the device after rechecking current HRIS authority.
            CollaborationMatrixCredential(
                response.userId,
                response.deviceId,
                response.accessToken,
                response.expiresAt,
            )
        }

    override fun logoutDevice(accessToken: String): Result<Unit> = safeMatrixProviderCall {
        client.logoutDevice(accessToken)
    }
}
