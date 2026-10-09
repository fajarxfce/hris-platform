package dev.fajar.hris

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode

class MobileProblemHttpTest : ApiIntegrationTest() {
    @Test
    fun clientLocaleDoesNotChangeTheDomainCodeFieldsOrParameters() {
        val client = client()
        val csrf = login(client)
        val company = company(client, csrf)
        for (language in listOf("en-US", "id-ID")) {
            val request =
                HttpRequest.newBuilder(
                        URI(
                            "http://127.0.0.1:$port/api/v1/companies/$company/workforce/employees/${UUID.randomUUID()}/attendance?from=2026-01-01&until=2026-12-31"
                        )
                    )
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept-Language", language)
                    .GET()
                    .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            val problem = problem(response, 422, "invalid_attendance_range")
            assertEquals("31", problem.get("parameters").get("maximumDays").asString())
            val invalid =
                command(
                    client,
                    "/api/v1/companies",
                    """{"code":"bad code","name":"","timezone":"not-a-zone"}""",
                    csrf,
                    UUID.randomUUID(),
                )
            val fields = problem(invalid, 422, "invalid_company").get("fields")
            assertEquals("invalid_code", fields.get("code").asString())
            assertEquals("invalid_name", fields.get("name").asString())
            assertEquals("invalid_timezone", fields.get("timezone").asString())
            assertFalse(invalid.body().contains("not-a-zone"))
        }
    }

    @Test
    fun authenticationCsrfAndNativeFiltersUseTheSameContract() {
        val anonymous = client()
        problem(get(anonymous, "/api/v1/me"), 401, "authentication_required")
        val csrfDenied =
            post(
                anonymous,
                "/api/v1/auth/login",
                """{"email":"private@example.test","password":"private-secret"}""",
            )
        problem(csrfDenied, 403, "access_denied")
        assertFalse(csrfDenied.body().contains("private"))
        val native =
            anonymous.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/v1/me"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer invalid-private-token")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        problem(native, 401, "native_session_invalid")
        assertFalse(native.body().contains("invalid-private-token"))
        val csrf = json.readTree(get(anonymous, "/api/v1/auth/csrf").body()).get("token").asString()
        problem(post(anonymous, "/api/v1/auth/login", "{malformed", csrf), 400, "invalid_request")
    }

    @Test
    fun malformedQueryFieldsAndUnknownRoutesRemainSanitizedClientErrors() {
        val client = client()
        val csrf = login(client)
        val company = company(client, csrf)
        val response =
            get(client, "/api/v1/companies/$company/organization-units?limit=private-query")
        val fields = problem(response, 400, "invalid_request").get("fields")
        assertEquals("invalid_value", fields.get("limit").asString())
        assertFalse(response.body().contains("private-query"))
        problem(get(client, "/api/v1/not-a-real-endpoint"), 404, "not_found")
    }

    @Test
    fun concurrentFailuresKeepTheirOwnRequestCorrelation() {
        val client = client()
        login(client)
        val gate = CountDownLatch(1)
        Executors.newFixedThreadPool(4).use { executor ->
            val calls =
                (1..4).map {
                    val reference = UUID.randomUUID().toString()
                    executor.submit {
                        assertTrue(gate.await(5, TimeUnit.SECONDS))
                        val response =
                            client.send(
                                HttpRequest.newBuilder(
                                        URI("http://127.0.0.1:$port/api/v1/companies/not-a-uuid")
                                    )
                                    .timeout(Duration.ofSeconds(15))
                                    .header("X-Request-ID", reference)
                                    .GET()
                                    .build(),
                                HttpResponse.BodyHandlers.ofString(),
                            )
                        assertEquals(
                            reference,
                            problem(response, 400, "invalid_request")
                                .get("correlationId")
                                .asString(),
                        )
                    }
                }
            gate.countDown()
            calls.forEach { it.get(20, TimeUnit.SECONDS) }
        }
    }

    private fun company(client: HttpClient, csrf: String): String {
        val response =
            command(
                client,
                "/api/v1/companies",
                """{"code":"M${UUID.randomUUID().toString().take(8).uppercase()}","name":"Mobile contract","timezone":"UTC"}""",
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body()).get("id").asString()
    }

    private fun problem(response: HttpResponse<String>, status: Int, code: String): JsonNode {
        assertEquals(status, response.statusCode(), response.body())
        assertTrue(
            response
                .headers()
                .firstValue("Content-Type")
                .orElse("")
                .startsWith("application/problem+json")
        )
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
        val body = json.readTree(response.body())
        assertEquals(status, body.get("status").asInt())
        assertEquals(code, body.get("code").asString())
        assertEquals("urn:hris:problem:$code", body.get("type").asString())
        assertTrue(body.get("fields").isObject)
        assertTrue(body.get("parameters").isObject)
        assertEquals(
            response.headers().firstValue("X-Request-ID").orElseThrow(),
            body.get("correlationId").asString(),
        )
        UUID.fromString(body.get("correlationId").asString())
        assertNull(body.get("exception"))
        assertNull(body.get("trace"))
        return body
    }
}
