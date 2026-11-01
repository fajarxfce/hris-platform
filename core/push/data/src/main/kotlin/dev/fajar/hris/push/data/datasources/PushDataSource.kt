package dev.fajar.hris.push.data.datasources

import java.time.Instant

fun interface PushDataSource {
    fun send(accessToken: String, body: ByteArray, deadline: Instant): PushHttpResponse
}
