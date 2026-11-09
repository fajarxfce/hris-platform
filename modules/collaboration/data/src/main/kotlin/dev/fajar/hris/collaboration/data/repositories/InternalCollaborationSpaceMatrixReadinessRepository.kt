package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceMatrixReadinessRepository
import dev.fajar.hris.core.domain.Result
import java.time.Instant

class InternalCollaborationSpaceMatrixReadinessRepository(
    private val source: MatrixSpaceReadinessControlDataSource
) : CollaborationSpaceMatrixReadinessRepository {
    override fun send(
        control: CollaborationSpaceReadinessControl,
        owner: CollaborationSpaceHistoryOwner,
        token: String,
        deadline: Instant,
    ): Result<Unit> = safeMatrixProviderCall {
        require(validCollaborationSpaceHistoryIdentity(owner))
        require(
            control.spaceId == owner.spaceId &&
                control.accountId == owner.accountId &&
                control.deviceId == owner.deviceId
        )
        val response =
            source.sendSpaceReadinessControl(
                "hris_space_ready_${control.eventId}_${control.deviceId}",
                mapOf(
                    "messages" to
                        mapOf(
                            owner.matrixUserId to
                                mapOf(
                                    owner.matrixDeviceId to
                                        collaborationSpaceReadinessControlContent(control)
                                )
                        )
                ),
                token,
                deadline,
            )
        if (!response.isObject || !response.isEmpty) throw MatrixResponseException()
    }
}
