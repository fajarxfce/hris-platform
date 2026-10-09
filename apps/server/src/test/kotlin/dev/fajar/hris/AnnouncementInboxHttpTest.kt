package dev.fajar.hris

import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

class AnnouncementInboxHttpTest : AnnouncementPublicationApiFixture() {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun keys(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_IDENTITY_KEYS") {
                "v1:" + Base64.getEncoder().encodeToString(ByteArray(32) { 23 })
            }
        }
    }

    @Test
    fun readingAndAcknowledgingAreExplicitVersionedReplayableCommands() {
        val f = fixture()
        val recipient = member(f)
        published(f, draft(f))
        val itemId = UUID.fromString(inbox(f, recipient).single()["id"].asString())
        val path = "${f.inbox}/$itemId"
        assertTrue(ok(get(recipient.browser, path))["readAt"].isNull)
        assertTrue(ok(get(recipient.browser, path))["readAt"].isNull)
        val readKey = UUID.randomUUID()
        val read = inboxAction(f, recipient, itemId, "read", 0, readKey)
        assertEquals(1, ok(read)["version"].asInt())
        val readAt = ok(get(recipient.browser, path))["readAt"]
        val acknowledge = inboxAction(f, recipient, itemId, "acknowledge", 1)
        assertEquals(2, ok(acknowledge)["version"].asInt())
        assertEquals(read.body(), inboxAction(f, recipient, itemId, "read", 0, readKey).body())
        error(inboxAction(f, recipient, itemId, "acknowledge", 0), 409, "stale_version")
        assertEquals(2, ok(inboxAction(f, recipient, itemId, "acknowledge", 2))["version"].asInt())
        val final = ok(get(recipient.browser, path))
        assertEquals(readAt, final["readAt"])
        assertFalse(final["acknowledgedAt"].isNull)
        assertEquals(2, final["version"].asInt())
        val mismatch = inboxAction(f, recipient, itemId, "acknowledge", 0, readKey)
        assertEquals(409, mismatch.statusCode())
    }

    @Test
    fun anotherAccountCannotReadOrAcknowledgeAndArchivalWithdrawsOriginalRecipients() {
        val f = fixture()
        val recipient = member(f)
        val other = member(f)
        val id = draft(f)
        published(f, id)
        val item = UUID.fromString(inbox(f, recipient).single()["id"].asString())
        error(get(other.browser, "${f.inbox}/$item"), 404, "inbox_item_not_found")
        error(inboxAction(f, other, item, "acknowledge", 0), 404, "inbox_item_not_found")
        val key = UUID.randomUUID()
        ok(inboxAction(f, recipient, item, "read", 0, key))
        val foreign = fixture()
        assertEquals(403, get(recipient.browser, "${foreign.inbox}/$item").statusCode())
        ok(action(f, id, "archive", 2))
        assertEquals(0, inbox(f, recipient).size())
        assertEquals(0, inbox(f, other).size())
        error(get(recipient.browser, "${f.inbox}/$item"), 404, "inbox_item_not_found")
        error(inboxAction(f, recipient, item, "read", 0, key), 404, "inbox_item_not_found")
        error(inboxAction(f, recipient, item, "acknowledge", 1), 404, "inbox_item_not_found")
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from inbox_items where company_id=? and withdrawn",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun competingReadAndAcknowledgementObserveOneVersionAndAcknowledgementImpliesRead() {
        val f = fixture()
        val recipient = member(f)
        published(f, draft(f))
        val item = UUID.fromString(inbox(f, recipient).single()["id"].asString())
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val pending =
                listOf("read", "acknowledge").map { action ->
                    executor.submit<Int> {
                        assertTrue(start.await(5, TimeUnit.SECONDS))
                        inboxAction(f, recipient, item, action, 0).statusCode()
                    }
                }
            start.countDown()
            assertEquals(listOf(200, 409), pending.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        val detail = ok(get(recipient.browser, "${f.inbox}/$item"))
        assertEquals(1, detail["version"].asInt())
        assertFalse(detail["readAt"].isNull)
        if (detail["acknowledgedAt"].isNull) ok(inboxAction(f, recipient, item, "acknowledge", 1))
        assertFalse(ok(get(recipient.browser, "${f.inbox}/$item"))["acknowledgedAt"].isNull)
    }

    @Test
    fun optionalAcknowledgementHasADomainErrorAndDoesNotImplicitlyRead() {
        val f = fixture()
        val recipient = member(f)
        published(f, draft(f, acknowledge = false))
        val id = UUID.fromString(inbox(f, recipient).single()["id"].asString())
        error(
            inboxAction(f, recipient, id, "acknowledge", 0),
            409,
            "inbox_acknowledgement_not_required",
        )
        val item = ok(get(recipient.browser, "${f.inbox}/$id"))
        assertTrue(item["readAt"].isNull)
        assertEquals(0, item["version"].asInt())
    }

    @Test
    fun nativeCommandsUseTheSameScopeAndLocalizableErrorsWithoutCookieCsrf() {
        val f = fixture()
        val recipient = member(f)
        published(f, draft(f))
        val id = UUID.fromString(inbox(f, recipient).single()["id"].asString())
        val exchange =
            command(
                recipient.browser,
                "/api/v1/auth/native/exchange",
                """{"deviceName":"Inbox mobile test"}""",
                recipient.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, exchange.statusCode())
        val root = json.readTree(exchange.body())
        val token = (root["credentials"] ?: root)["accessToken"].asString()
        val mobile = client()
        val operation = UUID.randomUUID()
        fun request(key: UUID, locale: String) =
            mobile.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port${f.inbox}/$id/acknowledge"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .header("Accept-Language", locale)
                    .header("Idempotency-Key", key.toString())
                    .POST(HttpRequest.BodyPublishers.ofString("""{"expectedVersion":0}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        val first = request(operation, "id-ID")
        assertEquals(1, ok(first)["version"].asInt())
        assertEquals(first.body(), request(operation, "en-US").body())
        val stale = request(UUID.randomUUID(), "id-ID")
        error(stale, 409, "stale_version")
        val problem = json.readTree(stale.body())
        assertTrue(problem.has("correlationId"))
        assertFalse(problem.has("stackTrace"))
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='announcements.read'",
                f.company,
                recipient.account,
            )
        error(request(operation, "id-ID"), 403, "access_denied")
    }
}
