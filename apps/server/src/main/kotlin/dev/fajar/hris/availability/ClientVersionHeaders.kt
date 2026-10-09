package dev.fajar.hris.availability

import dev.fajar.hris.administration.domain.entities.*
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** Bounded HTTP parsing only; version requirements belong to the admission use case. */
fun readClientVersion(request: HttpServletRequest): ClientVersion? {
    val platform = request.getHeaders("X-HRIS-Client-Platform").asSequence().take(2).toList()
    val build = request.getHeaders("X-HRIS-Client-Build").asSequence().take(2).toList()
    if (platform.isEmpty() && build.isEmpty()) return null
    if (
        platform.size != 1 ||
            build.size != 1 ||
            platform[0].length > 7 ||
            !build[0].matches(Regex("0|[1-9][0-9]{0,8}"))
    )
        throw ResponseStatusException(HttpStatus.BAD_REQUEST)
    val selected =
        ClientPlatform.entries.firstOrNull { it.name == platform[0] }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST)
    return ClientVersion(selected, build[0].toInt())
}
