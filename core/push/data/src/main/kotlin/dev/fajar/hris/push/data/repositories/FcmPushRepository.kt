package dev.fajar.hris.push.data.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.push.data.datasources.*
import dev.fajar.hris.push.data.mappers.*
import dev.fajar.hris.push.data.safePushCall
import dev.fajar.hris.push.domain.entities.*
import dev.fajar.hris.push.domain.repositories.PushRepository
import java.time.Clock
import tools.jackson.databind.ObjectMapper

class FcmPushRepository(
    private val credentials: PushCredentialDataSource,
    private val source: PushDataSource,
    private val json: ObjectMapper,
    private val clock: Clock,
) : PushRepository {
    override fun send(message: PushMessage): Result<PushOutcome> = safePushCall {
        val token = credentials.accessToken()
        val body = json.writeValueAsBytes(fcmMessage(message, clock.instant()))
        fcmOutcome(source.send(token, body), json)
    }
}
