package dev.fajar.hris

import dev.fajar.hris.identity.delivery.oidc.*
import jakarta.servlet.FilterChain
import java.io.IOException
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class OidcTransportTest {
    @Test
    fun chunkedResponsesCannotReadPastTheConfiguredBodyLimit() {
        val valid = BoundedOidcResponse(MockClientHttpResponse(ByteArray(1048576), HttpStatus.OK))
        try {
            assertEquals(1048576, valid.body.readAllBytes().size)
        } finally {
            valid.close()
        }
        val oversized =
            BoundedOidcResponse(MockClientHttpResponse(ByteArray(1048577), HttpStatus.OK))
        try {
            assertThrows(IOException::class.java) { oversized.body.readAllBytes() }
        } finally {
            oversized.close()
        }
    }

    @Test
    fun callbackCapacityHasNoWaitingQueueAndReleasesPermitsAfterFailure() {
        val filter = OidcCallbackLimitFilter()
        val entered = CountDownLatch(8)
        val release = CountDownLatch(1)
        fun request() =
            MockHttpServletRequest().apply { servletPath = "/login/oauth2/code/company" }
        Executors.newFixedThreadPool(8).use { pool ->
            val waiting =
                (1..8).map {
                    pool.submit {
                        filter.doFilter(
                            request(),
                            MockHttpServletResponse(),
                            FilterChain { _, _ ->
                                entered.countDown()
                                check(release.await(5, TimeUnit.SECONDS))
                            },
                        )
                    }
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val response = MockHttpServletResponse()
                filter.doFilter(
                    request(),
                    response,
                    FilterChain { _, _ -> fail("Saturated callback must not execute") },
                )
                assertEquals(503, response.status)
            } finally {
                release.countDown()
            }
            waiting.forEach { it.get(5, TimeUnit.SECONDS) }
        }
        assertThrows(IOException::class.java) {
            filter.doFilter(
                request(),
                MockHttpServletResponse(),
                FilterChain { _, _ -> throw IOException("fixture") },
            )
        }
        val response = MockHttpServletResponse()
        filter.doFilter(
            request(),
            response,
            FilterChain { _, res -> (res as jakarta.servlet.http.HttpServletResponse).status = 204 },
        )
        assertEquals(204, response.status)
    }

    @Test
    fun insecureProviderConfigurationIsRejectedAndSecretsAreNotPrinted() {
        val valid =
            OidcClientSettings(
                "https://id.example.test",
                "client",
                "fixture-secret",
                "https://hr.example.test",
                "https://id.example.test/authorize",
                "https://id.example.test/token",
                "https://id.example.test/jwks",
                "client_secret_basic",
            )
        assertFalse(valid.toString().contains("fixture-secret"))
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(tokenUri = "http://external.example.test/token", allowLoopbackHttp = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(publicUrl = "https://hr.example.test/redirect?target=other")
        }
    }
}
