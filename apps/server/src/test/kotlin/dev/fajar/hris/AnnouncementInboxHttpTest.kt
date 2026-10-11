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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
    fun unreadTotalIncludesUnloadedPagesAndChangesOnlyForTheOwningAccount() {
        val f = fixture()
        val recipient = member(f)
        val other = member(f)
        val first = draft(f)
        published(f, first)
        published(f, draft(f))
        val path = "${f.inbox}/unread-count"
        assertEquals(2L, ok(get(recipient.browser, path))["unreadCount"].asLong())
        val page = ok(get(recipient.browser, "${f.inbox}?limit=1"))
        assertEquals(1, page["items"].size())
        assertFalse(page["nextCursor"].isNull)
        val item = UUID.fromString(page["items"][0]["id"].asString())
        ok(get(recipient.browser, "${f.inbox}/$item"))
        assertEquals(2L, ok(get(recipient.browser, path))["unreadCount"].asLong())
        val key = UUID.randomUUID()
        ok(inboxAction(f, recipient, item, "read", 0, key))
        ok(inboxAction(f, recipient, item, "read", 0, key))
        assertEquals(1L, ok(get(recipient.browser, path))["unreadCount"].asLong())
        assertEquals(2L, ok(get(other.browser, path))["unreadCount"].asLong())
        ok(action(f, first, "archive", 2))
        assertEquals(1L, ok(get(other.browser, path))["unreadCount"].asLong())
        val remaining = UUID.fromString(inbox(f, other).single()["id"].asString())
        ok(inboxAction(f, other, remaining, "acknowledge", 0))
        assertEquals(0L, ok(get(other.browser, path))["unreadCount"].asLong())
        val foreign = fixture()
        assertEquals(403, get(recipient.browser, "${foreign.inbox}/unread-count").statusCode())
    }

    @ParameterizedTest
    @ValueSource(strings = ["permission", "credential", "membership"])
    fun unreadCountRechecksAccessAfterWaiting(change: String) {
        val f = fixture()
        val recipient = member(f)
        published(f, draft(f))
        val barrier = AccountLockProbe.Barrier(recipient.account)
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { executor ->
                val pending =
                    executor.submit<HttpResponse<String>> {
                        get(recipient.browser, "${f.inbox}/unread-count")
                    }
                try {
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    when (change) {
                        "credential" ->
                            database()
                                .update(
                                    "update accounts set security_version=security_version+1 where id=?",
                                    recipient.account,
                                )
                        "membership" ->
                            database()
                                .update(
                                    "update company_memberships set active=false where company_id=? and account_id=?",
                                    f.company,
                                    recipient.account,
                                )
                        else ->
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=?",
                                    f.company,
                                    recipient.account,
                                )
                    }
                } finally {
                    barrier.release.countDown()
                }
                val response = pending.get(15, TimeUnit.SECONDS)
                assertEquals(
                    if (change == "credential") 401 else 403,
                    response.statusCode(),
                    response.body(),
                )
                assertFalse(response.body().contains("unreadCount"))
            }
        } finally {
            accountProbe.current.set(null)
            barrier.release.countDown()
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
