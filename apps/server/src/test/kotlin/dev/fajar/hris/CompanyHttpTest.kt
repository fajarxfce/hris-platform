package dev.fajar.hris

import java.net.http.HttpResponse
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CompanyHttpTest : ApiIntegrationTest() {
    @Test
    fun concurrentCreationReplaysOneCompanyAndOneJournal() {
        val client = client()
        val csrf = login(client)
        val operation = UUID.randomUUID()
        val code = "C" + operation.toString().take(8).uppercase()
        val body = """{"code":"$code","name":"Example Company","timezone":"Asia/Jakarta"}"""
        val start = java.util.concurrent.CountDownLatch(1)
        val responses =
            java.util.concurrent.Executors.newFixedThreadPool(2).use { executor ->
                val tasks =
                    (1..2).map {
                        executor.submit<HttpResponse<String>> {
                            start.await()
                            command(client, "/api/v1/companies", body, csrf, operation)
                        }
                    }
                start.countDown()
                tasks.map { it.get(20, java.util.concurrent.TimeUnit.SECONDS) }
            }
        responses.forEach { assertEquals(200, it.statusCode(), it.body()) }
        assertEquals(responses[0].body(), responses[1].body())
        val id = UUID.fromString(json.readTree(responses[0].body()).get("id").asString())
        assertEquals(
            1,
            database()
                .queryForObject("select count(*) from companies where id=?", Int::class.java, id),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='organization.company_created'",
                    Int::class.java,
                    id,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from outbox_events where resource_id=? and event_type='organization.company_created'",
                    Int::class.java,
                    id,
                ),
        )
        val access = get(client, "/api/v1/companies/$id/me/access")
        assertEquals(200, access.statusCode(), access.body())
        assertFalse(access.body().contains("payroll.finalize"))
        val mismatch =
            command(
                client,
                "/api/v1/companies",
                body.replace("Example Company", "Changed Name"),
                csrf,
                operation,
            )
        assertEquals(409, mismatch.statusCode(), mismatch.body())
        assertEquals(
            "operation_payload_mismatch",
            json.readTree(mismatch.body()).get("code").asString(),
        )
    }

    @Test
    fun updatesRejectStaleVersionAndReplayOriginalOutcome() {
        val client = client()
        val csrf = login(client)
        val code = "C" + UUID.randomUUID().toString().take(8).uppercase()
        val created =
            command(
                client,
                "/api/v1/companies",
                """{"code":"$code","name":"Initial","timezone":"Asia/Jakarta"}""",
                csrf,
                UUID.randomUUID(),
            )
        val id = json.readTree(created.body()).get("id").asString()
        val body = """{"code":"$code","name":"Revised","timezone":"Asia/Makassar","version":0}"""
        val key = UUID.randomUUID()
        val changed = command(client, "/api/v1/companies/$id", body, csrf, key, "PUT")
        assertEquals(200, changed.statusCode(), changed.body())
        assertEquals(1, json.readTree(changed.body()).get("version").asLong())
        val stale = command(client, "/api/v1/companies/$id", body, csrf, UUID.randomUUID(), "PUT")
        assertEquals(409, stale.statusCode(), stale.body())
        assertEquals("stale_version", json.readTree(stale.body()).get("code").asString())
        assertEquals(
            changed.body(),
            command(client, "/api/v1/companies/$id", body, csrf, key, "PUT").body(),
        )
        assertEquals(
            "Revised",
            json.readTree(get(client, "/api/v1/companies/$id").body()).get("name").asString(),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='organization.company_updated'",
                    Int::class.java,
                    UUID.fromString(id),
                ),
        )
    }

    @Test
    fun invalidTimezoneAndDuplicateCodeLeaveNoPartialCompany() {
        val client = client()
        val csrf = login(client)
        val code = "C" + UUID.randomUUID().toString().take(8).uppercase()
        val body = """{"code":"$code","name":"Example","timezone":"Asia/Jakarta"}"""
        val invalid =
            command(
                client,
                "/api/v1/companies",
                body.replace("Asia/Jakarta", "UTC+07"),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(422, invalid.statusCode(), invalid.body())
        assertEquals(
            200,
            command(client, "/api/v1/companies", body, csrf, UUID.randomUUID()).statusCode(),
        )
        val failedOperation = UUID.randomUUID()
        assertEquals(
            409,
            command(client, "/api/v1/companies", body, csrf, failedOperation).statusCode(),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from operation_receipts where operation_id=?",
                    Int::class.java,
                    failedOperation,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from companies where code=?",
                    Int::class.java,
                    code,
                ),
        )
    }
}
