package dev.fajar.hris.push.data.datasources

fun interface PushDataSource {
    fun send(accessToken: String, body: ByteArray): PushHttpResponse
}
