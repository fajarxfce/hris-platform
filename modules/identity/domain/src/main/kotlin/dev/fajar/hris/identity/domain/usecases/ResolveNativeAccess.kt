package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock

class ResolveNativeAccess(private val sessions: NativeSessionRepository, private val clock: Clock) {
    fun execute(token: String): Result<NativeAccess> {
        if (!validNativeToken(token)) return nativeAuthenticationRequired()
        return sessions.findAccess(token).flatMap { session ->
            val now = clock.instant()
            if (
                session == null ||
                    !nativeSessionActive(session, now) ||
                    !session.accessExpiresAt.isAfter(now)
            )
                nativeAuthenticationRequired()
            else Result.Success(nativeAccess(session))
        }
    }
}
