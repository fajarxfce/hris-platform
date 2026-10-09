package dev.fajar.hris.push.data.mappers

import dev.fajar.hris.push.domain.entities.PushMessage
import java.time.Duration
import java.time.Instant

fun fcmMessage(message: PushMessage, now: Instant): Map<String, Any> {
    require(message.token.length in 16..2048 && message.token.all { it.code in 33..126 })
    require(
        message.data.size in 1..16 &&
            message.data.all { (key, value) ->
                key.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")) &&
                    key !in setOf("from", "message_type") &&
                    !key.startsWith("google.") &&
                    !key.startsWith("gcm.") &&
                    value.length <= 256
            }
    )
    require(
        message.data.entries.sumOf { (key, value) ->
            key.toByteArray(Charsets.UTF_8).size + value.toByteArray(Charsets.UTF_8).size
        } <= 1024
    )
    val ttl = Duration.between(now, message.expiresAt).seconds
    require(ttl in 1..300)
    return mapOf(
        "message" to
            mapOf(
                "token" to message.token,
                "data" to message.data,
                "android" to
                    mapOf(
                        "ttl" to "${ttl}s",
                        "priority" to "NORMAL",
                        "collapse_key" to "hris_inbox",
                    ),
                "apns" to
                    mapOf(
                        "headers" to
                            mapOf(
                                "apns-push-type" to "background",
                                "apns-priority" to "5",
                                "apns-expiration" to message.expiresAt.epochSecond.toString(),
                            ),
                        "payload" to mapOf("aps" to mapOf("content-available" to 1)),
                    ),
            )
    )
}
