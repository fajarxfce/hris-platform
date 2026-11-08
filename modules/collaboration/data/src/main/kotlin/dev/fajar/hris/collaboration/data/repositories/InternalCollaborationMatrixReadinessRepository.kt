package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.CollaborationReadinessControl
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixReadinessRepository
import dev.fajar.hris.core.domain.Result
import java.time.Instant

class InternalCollaborationMatrixReadinessRepository(
    private val source: MatrixReadinessControlDataSource
) : CollaborationMatrixReadinessRepository {
    override fun send(
        control: CollaborationReadinessControl,
        serverName: String,
        token: String,
        deadline: Instant,
    ): Result<Unit> = safeMatrixProviderCall {
        require(
            serverName.length in 1..200 && serverName.all { it.isLetterOrDigit() || it in ".-:" }
        )
        val user =
            "@${collaborationMatrixLocalpart(control.companyId, control.accountId)}:$serverName"
        val device = collaborationMatrixDeviceId(control.deviceId)
        val response =
            source.sendReadinessControl(
                "hris_ready_${control.eventId}_${control.deviceId}",
                mapOf(
                    "messages" to
                        mapOf(
                            user to mapOf(device to collaborationReadinessControlContent(control))
                        )
                ),
                token,
                deadline,
            )
        if (!response.isObject || !response.isEmpty) throw MatrixResponseException()
    }
}
