package dev.fajar.hris.identity.data.mappers

import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.validNativeToken
import java.util.UUID

fun nativeTokenPurpose(session: NativeSession, operationId: UUID): String =
    "hris:native:${session.accountId}:${session.id}:$operationId"

fun decodeNativeTokens(session: NativeSession, plain: String): NativeTokens {
    val tokens = plain.split('\n')
    require(tokens.size == 2 && tokens.all(::validNativeToken))
    return NativeTokens(
        session.id,
        tokens[0],
        tokens[1],
        session.accessExpiresAt,
        session.expiresAt,
    )
}
