package dev.fajar.hris.core.http

import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.io.BufferedReader
import java.io.InputStreamReader

class BoundedBodyRequest(
    request: HttpServletRequest,
    maximum: Long,
    budget: java.time.Duration = java.time.Duration.ofSeconds(30),
) : HttpServletRequestWrapper(request) {
    private val stream = BoundedRequestStream(request.inputStream, maximum, budget)

    override fun getInputStream(): ServletInputStream = stream

    override fun getReader(): BufferedReader =
        BufferedReader(InputStreamReader(stream, characterEncoding ?: "UTF-8"))
}
