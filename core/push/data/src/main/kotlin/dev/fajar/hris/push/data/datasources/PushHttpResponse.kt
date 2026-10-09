package dev.fajar.hris.push.data.datasources

class PushHttpResponse(val status: Int, val body: ByteArray, val retryAfter: String?) {
    override fun toString() = "PushHttpResponse(status=$status, <redacted>)"
}
