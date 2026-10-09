package dev.fajar.hris

import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RequestBoundaryHttpTest : ApiIntegrationTest() {
    @Test
    fun malformedRouteQueryAndHeaderReturnClientErrors() {
        val client = client()
        val csrf = login(client)
        val created =
            command(
                client,
                "/api/v1/companies",
                """{"code":"INPUTS","name":"Input test","timezone":"UTC"}""",
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, created.statusCode(), created.body())
        val company = json.readTree(created.body()).get("id").asString()
        assertEquals(400, get(client, "/api/v1/companies/not-a-uuid").statusCode())
        for (path in
            listOf(
                "employees?asOf=not-a-date",
                "organization-units?limit=invalid",
                "workforce/employees/not-a-uuid/calendar?from=2026-10-01&until=2026-10-02",
            )) {
            val response = get(client, "/api/v1/companies/$company/$path")
            assertEquals(400, response.statusCode(), response.body())
            assertEquals("invalid_request", json.readTree(response.body()).get("code").asString())
            assertFalse(json.readTree(response.body()).get("correlationId").isNull)
        }
        val badHeader =
            client.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/v1/companies"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("X-CSRF-TOKEN", csrf)
                    .header("Idempotency-Key", "not-a-uuid")
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            """{"code":"BAD","name":"Bad","timezone":"UTC"}"""
                        )
                    )
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(400, badHeader.statusCode(), badHeader.body())
    }

    @Test
    fun declaredAndChunkedBodiesAreBoundedWithoutWritingOrBreakingTheSession() {
        val client = client()
        val csrf = login(client)
        val body =
            ("{\"code\":\"OVERSIZED\",\"name\":\"" +
                    "a".repeat(1_100_000) +
                    "\",\"timezone\":\"UTC\"}")
                .toByteArray()
        val before = database().queryForObject("select count(*) from companies", Int::class.java)
        for (publisher in
            listOf(
                HttpRequest.BodyPublishers.ofByteArray(body),
                HttpRequest.BodyPublishers.ofInputStream { ByteArrayInputStream(body) },
            )) {
            val response =
                client.send(
                    HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/v1/companies"))
                        .timeout(Duration.ofSeconds(15))
                        .expectContinue(true)
                        .header("Content-Type", "application/json")
                        .header("X-CSRF-TOKEN", csrf)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .POST(publisher)
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertEquals(413, response.statusCode(), response.body())
            assertEquals(
                "request_body_too_large",
                json.readTree(response.body()).get("code").asString(),
            )
            assertEquals(
                "1048576",
                json.readTree(response.body()).get("parameters").get("maximumBytes").asString(),
            )
            assertTrue(json.readTree(response.body()).get("fields").isObject)
            assertTrue(
                response.headers().firstValue("Cache-Control").orElse("").contains("no-store")
            )
            assertFalse(response.body().contains("OVERSIZED"))
            assertEquals(200, get(client, "/api/v1/me").statusCode())
        }
        assertEquals(
            before,
            database().queryForObject("select count(*) from companies", Int::class.java),
        )
    }
}
