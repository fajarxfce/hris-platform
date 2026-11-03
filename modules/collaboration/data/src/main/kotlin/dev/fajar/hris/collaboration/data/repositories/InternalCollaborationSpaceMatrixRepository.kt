package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceMatrixRepository
import dev.fajar.hris.core.domain.Result
import java.time.Clock
import java.time.Instant

class InternalCollaborationSpaceMatrixRepository(
    private val signing: MatrixJwtDataSource,
    private val client: MatrixClientDataSource,
    private val clock: Clock,
) : CollaborationSpaceMatrixRepository {
    override fun login(
        request: CollaborationSpaceMatrixLogin
    ): Result<CollaborationMatrixCredential> = safeMatrixProviderCall {
        val now = clock.instant()
        val deadline = Instant.ofEpochSecond(request.expiresAt.epochSecond)
        if (now < request.issuedAt || now >= deadline) throw MatrixDeadlineException()
        val subject =
            request.canonicalLegacyCompanyId?.let {
                collaborationMatrixLocalpart(it, request.accountId)
            } ?: collaborationSpaceMatrixLocalpart(request.spaceId, request.accountId)
        val device =
            request.retainedDevice?.let { collaborationMatrixDeviceId(it.deviceId) }
                ?: "HRISG_" + request.deviceId.toString().replace("-", "")
        val jwt = signing.sign(subject, request.issuedAt, request.expiresAt)
        val response = client.login(jwt, device, deadline)
        // A late response is still a candidate for the caller's fence, never a reason to logout.
        CollaborationMatrixCredential(
            response.userId,
            response.deviceId,
            response.accessToken,
            response.expiresAt,
        )
    }
}
