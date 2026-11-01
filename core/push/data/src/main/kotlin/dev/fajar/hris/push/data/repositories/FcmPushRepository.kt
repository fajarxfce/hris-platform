package dev.fajar.hris.push.data.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.push.data.datasources.*
import dev.fajar.hris.push.data.mappers.*
import dev.fajar.hris.push.data.safePushCall
import dev.fajar.hris.push.data.transport.remainingPushDispatch
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
        require(message.dispatchDeadline <= message.expiresAt)
        remainingPushDispatch(message.dispatchDeadline, clock.instant())
        val token = credentials.accessToken()
        // OAuth refresh is independently bounded and may outlive a short caller budget.
        // It must never start a late FCM send, or shorten provider storage to that budget.
        remainingPushDispatch(message.dispatchDeadline, clock.instant())
        val body = json.writeValueAsBytes(fcmMessage(message, clock.instant()))
        val response = source.send(token, body, message.dispatchDeadline)
        remainingPushDispatch(message.dispatchDeadline, clock.instant())
        val outcome = fcmOutcome(response, json, clock.instant())
        remainingPushDispatch(message.dispatchDeadline, clock.instant())
        outcome
    }
}
