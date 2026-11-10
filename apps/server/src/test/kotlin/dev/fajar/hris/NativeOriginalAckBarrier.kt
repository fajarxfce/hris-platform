package dev.fajar.hris

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.filter.OncePerRequestFilter

/** Test-only second-transaction barrier. No payloads, credentials or raw paths are retained. */
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class NativeOriginalAckBarrier : OncePerRequestFilter() {
    private val permit = CountDownLatch(1)
    private val transactions = linkedSetOf<String>()
    private var active = false
    private var expectedOriginals = 2

    @Synchronized
    fun arm(originals: Int = 2) {
        require(originals in 2..3)
        check(!active && transactions.isEmpty() && permit.count == 1L)
        expectedOriginals = originals
        active = true
    }

    fun releaseSecond() {
        permit.countDown()
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (
            request.requestURI.startsWith("/_matrix/") &&
                request.requestURI.contains("/send/m.room.encrypted/")
        ) {
            val fingerprint =
                MessageDigest.getInstance("SHA-256")
                    .digest(request.requestURI.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            val wait =
                synchronized(this) {
                    if (!active) false
                    else {
                        if (fingerprint !in transactions) {
                            check(transactions.size < expectedOriginals)
                            transactions += fingerprint
                        }
                        transactions.indexOf(fingerprint) >= 1
                    }
                }
            if (wait)
                check(permit.await(25, TimeUnit.SECONDS)) { "Owned first receipt was not retained" }
        }
        chain.doFilter(request, response)
    }
}

@TestConfiguration(proxyBeanMethods = false)
class NativeOriginalAckBarrierConfiguration {
    @Bean fun originalAckBarrier() = NativeOriginalAckBarrier()
}
