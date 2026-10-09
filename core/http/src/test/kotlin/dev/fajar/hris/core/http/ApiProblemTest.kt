package dev.fajar.hris.core.http

import dev.fajar.hris.core.domain.*
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import tools.jackson.databind.json.JsonMapper

class ApiProblemTest {
    private val json = JsonMapper.builder().build()

    @Test
    fun domainProblemsKeepMachineCodesFieldsAndUnlocalizedParameters() {
        val reference = UUID.randomUUID().toString()
        val failure =
            Failure(
                FailureKind.VALIDATION,
                "amount_limit",
                mapOf("amount" to "maximum"),
                mapOf("maximum" to "1234.50", "currency" to "IDR"),
            )
        MDC.putCloseable("correlationId", reference).use {
            val problem =
                ApiExceptionHandler()
                    .domain(DomainFailureException(failure), MockHttpServletResponse())
            assertEquals(422, problem.status)
            assertEquals("urn:hris:problem:amount_limit", problem.type.toString())
            assertEquals(failure.fields, problem.properties?.get("fields"))
            assertEquals(failure.parameters, problem.properties?.get("parameters"))
            assertEquals(reference, problem.properties?.get("correlationId"))
            assertNull(problem.detail)
        }
        assertNull(MDC.get("correlationId"))
    }

    @Test
    fun domainRetryMetadataRequiresAnExplicitBoundedTemporaryFailure() {
        for ((kind, delay, expected) in
            listOf(
                Triple(FailureKind.UNAVAILABLE, "120", 120L),
                Triple(FailureKind.RATE_LIMITED, "1", 1L),
                Triple(FailureKind.FORBIDDEN, "120", null),
                Triple(FailureKind.UNAVAILABLE, "-1", null),
                Triple(FailureKind.UNAVAILABLE, "99999999999999999999", null),
                Triple(FailureKind.UNAVAILABLE, "604801", null),
            )) {
            val response = MockHttpServletResponse()
            val failure =
                Failure(
                    kind,
                    "bounded_retry_fixture",
                    parameters = mapOf("retryAfterSeconds" to delay),
                )
            val body = ApiExceptionHandler().domain(DomainFailureException(failure), response)
            assertEquals(expected?.toString(), response.getHeader("Retry-After"))
            assertEquals(expected, body.properties?.get("retryAfterSeconds"))
            assertEquals("no-store", response.getHeader("Cache-Control"))
        }
    }

    @Test
    fun filterProblemsHaveTheMvcShapeAndMatchingRetryDelayWithoutCacheStorage() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val reference = UUID.randomUUID()
        request.setAttribute("hris.correlationId", reference)
        writeApiProblem(
            request,
            response,
            json,
            HttpStatus.TOO_MANY_REQUESTS,
            "upload_busy",
            retryAfterSeconds = 1,
        )
        val body = json.readTree(response.contentAsString)
        assertEquals(429, response.status)
        assertEquals("application/problem+json", response.contentType)
        assertEquals("no-store", response.getHeader("Cache-Control"))
        assertEquals("1", response.getHeader("Retry-After"))
        assertEquals(1, body.get("retryAfterSeconds").asInt())
        assertEquals("urn:hris:problem:upload_busy", body.get("type").asString())
        assertEquals(reference.toString(), body.get("correlationId").asString())
        assertTrue(body.get("fields").isObject)
        assertTrue(body.get("parameters").isObject)
        assertNull(body.get("properties"))
    }

    @Test
    fun frameworkValidationOmitsRejectedValuesMessagesAndUnboundedFieldPaths() {
        val errors = BeanPropertyBindingResult(emptyMap<String, String>(), "body")
        errors.addError(
            FieldError(
                "body",
                "items[1].amount",
                "private-value",
                false,
                arrayOf("SensitiveProviderCode"),
                null,
                "secret message",
            )
        )
        errors.addError(FieldError("body", "entries[private-key]", "hidden"))
        repeat(100) { errors.addError(FieldError("body", "field$it", "ignored")) }
        val method = MethodParameter(javaClass.getDeclaredMethod("input", String::class.java), 0)
        val problem = ApiExceptionHandler().invalid(MethodArgumentNotValidException(method, errors))
        val fields = problem.properties?.get("fields") as Map<*, *>
        assertEquals(49, fields.size)
        assertEquals("invalid_value", fields["items[1].amount"])
        assertFalse(fields.keys.any { it.toString().contains("private") })
        assertFalse(problem.toString().contains("private-value"))
        assertFalse(problem.toString().contains("secret message"))
        assertFalse(problem.toString().contains("SensitiveProviderCode"))
    }

    @Test
    fun unexpectedProblemsContainAReferenceWithoutExceptionMessages() {
        val problem =
            ApiExceptionHandler().unexpected(IllegalStateException("password=private-value"))
        assertEquals(500, problem.status)
        assertEquals("unexpected_error", problem.properties?.get("code"))
        UUID.fromString(problem.properties?.get("correlationId").toString())
        assertFalse(problem.toString().contains("private-value"))
        assertNull(problem.detail)
    }

    @Test
    fun cancellationAndInterruptionNeverBecomeRetryableApiProblems() {
        val handler = ApiExceptionHandler()
        val cancelled = CancellationException("cancelled")
        assertSame(
            cancelled,
            assertThrows(CancellationException::class.java) { handler.unexpected(cancelled) },
        )
        try {
            val interrupted = InterruptedException("stopped")
            assertSame(
                interrupted,
                assertThrows(InterruptedException::class.java) { handler.unexpected(interrupted) },
            )
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    private fun input(value: String) = value
}
