package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixPusherRepository
import dev.fajar.hris.core.domain.Result
import java.time.Instant

/** Event-only notifications; the native FCM/APNs token never crosses this provider boundary. */
class InternalCollaborationMatrixPusherRepository(
    private val source: MatrixPusherDataSource,
    private val configuration: MatrixPushGatewayConfiguration,
) : CollaborationMatrixPusherRepository {
    override fun set(pushKey: String, bearer: String, deadline: Instant): Result<Unit> =
        update(pushKey, bearer, deadline, enabled = true)

    override fun remove(pushKey: String, bearer: String, deadline: Instant): Result<Unit> =
        update(pushKey, bearer, deadline, enabled = false)

    private fun update(
        pushKey: String,
        bearer: String,
        deadline: Instant,
        enabled: Boolean,
    ): Result<Unit> = safeMatrixProviderCall {
        require(pushKey.matches(Regex("hriscp1_[a-f0-9]{64}")))
        val body =
            buildMap<String, Any?> {
                put("app_id", "dev.fajar.hris.chat")
                put("pushkey", pushKey)
                put("kind", if (enabled) "http" else null)
                if (enabled) {
                    put("append", true)
                    put("app_display_name", "HRIS")
                    put("device_display_name", "HRIS")
                    put("lang", "en")
                    put(
                        "data",
                        mapOf(
                            "url" to configuration.endpoint.toASCIIString(),
                            "format" to "event_id_only",
                        ),
                    )
                }
            }
        val response = source.setPusher(body, bearer, deadline)
        if (!response.isObject || !response.isEmpty) throw MatrixResponseException()
    }
}
