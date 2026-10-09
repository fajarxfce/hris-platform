package dev.fajar.hris.push.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.push.domain.entities.*

fun interface PushRepository {
    fun send(message: PushMessage): Result<PushOutcome>
}
