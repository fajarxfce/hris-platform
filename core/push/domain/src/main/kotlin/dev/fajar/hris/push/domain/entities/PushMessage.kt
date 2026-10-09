package dev.fajar.hris.push.domain.entities

import java.time.Instant

class PushMessage(val token: String, data: Map<String, String>, val expiresAt: Instant) {
    val data: Map<String, String> = data.toMap()

    override fun toString() = "PushMessage(<redacted>)"
}
