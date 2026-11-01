package dev.fajar.hris.push.data.mappers

import dev.fajar.hris.push.domain.entities.PushDelivery
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
    val delivery = message.delivery
    val visible =
        when (delivery) {
            is PushDelivery.Background -> null
            is PushDelivery.Visible -> delivery
        }
    val collapse = delivery.collapseKey
    require(collapse == null || collapse.matches(Regex("[A-Za-z0-9_.:-]{1,64}")))
    visible?.let {
        require(
            validFallback(it.fallbackTitle, 80, 240) && validFallback(it.fallbackBody, 200, 600)
        )
    }
    return mapOf(
        "message" to
            mapOf(
                "token" to message.token,
                "data" to message.data,
                // Never add FCM notification fields: Android validates and decrypts the signal
                // locally.
                "android" to
                    buildMap<String, String> {
                        put("ttl", "${ttl}s")
                        put("priority", if (visible == null) "NORMAL" else "HIGH")
                        collapse?.let { put("collapse_key", it) }
                    },
                "apns" to
                    mapOf(
                        "headers" to
                            buildMap<String, String> {
                                put(
                                    "apns-push-type",
                                    if (visible == null) "background" else "alert",
                                )
                                put("apns-priority", if (visible == null) "5" else "10")
                                put("apns-expiration", message.expiresAt.epochSecond.toString())
                                collapse?.let { put("apns-collapse-id", it) }
                            },
                        "payload" to
                            mapOf(
                                "aps" to
                                    buildMap<String, Any> {
                                        put("content-available", 1)
                                        visible?.let {
                                            put(
                                                "alert",
                                                mapOf(
                                                    "title" to it.fallbackTitle,
                                                    "body" to it.fallbackBody,
                                                ),
                                            )
                                            put("mutable-content", 1)
                                            put("sound", "default")
                                        }
                                    }
                            ),
                    ),
            )
    )
}

private fun validFallback(value: String, characters: Int, bytes: Int): Boolean =
    value.length in 1..characters &&
        value.isNotBlank() &&
        value.none { it.isISOControl() } &&
        value.toByteArray(Charsets.UTF_8).size <= bytes
