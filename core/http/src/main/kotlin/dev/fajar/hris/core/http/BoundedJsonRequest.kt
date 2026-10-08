package dev.fajar.hris.core.http

import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.io.BufferedReader
import java.io.InputStreamReader

class BoundedJsonRequest(request: HttpServletRequest, maximum: Long) :
    HttpServletRequestWrapper(request) {
    private val stream = BoundedRequestStream(request.inputStream, maximum)

    override fun getInputStream(): ServletInputStream = stream

    override fun getReader(): BufferedReader =
        BufferedReader(InputStreamReader(stream, characterEncoding ?: "UTF-8"))
}
