package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.sync.domain.entities.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@org.springframework.test.context.TestPropertySource(
    properties = ["HRIS_IDENTITY_KEYS=v1:AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI="]
)
class MobileSyncHttpTest : MobileSyncApiFixture() {
    @Test
    fun nativeClientsCanBootstrapAndReplayOfflineCommandsWithoutACookieSession() {
        val f = expenseFixture()
        // Keep the frozen issuance time exactly representable by PostgreSQL.
        clock.set(clock.instant().plusSeconds(1).truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
        val exchanged =
            body(
                command(
                    f.worker,
                    "/api/v1/auth/native/exchange",
                    "{\"deviceName\":\"Sync fixture\"}",
                    f.workerCsrf,
                    UUID.randomUUID(),
                )
            )
        val access = exchanged.get("accessToken").asString()
        val native = client()
        val key = UUID.randomUUID()
        val request =
            java.net.http.HttpRequest.newBuilder(
                    java.net.URI("http://127.0.0.1:$port${f.path}/${f.claim}/draft")
                )
                .timeout(java.time.Duration.ofSeconds(15))
                .header("Authorization", "Bearer $access")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key.toString())
                .PUT(java.net.http.HttpRequest.BodyPublishers.ofString(expenseBody(f)))
                .build()
        val first = body(native.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()))
        assertEquals(
            first,
            body(native.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())),
        )
        assertEquals(1, queueCount(f))
        val bootstrap =
            java.net.http.HttpRequest.newBuilder(
                    java.net.URI(
                        "http://127.0.0.1:$port/api/v1/companies/${f.company}/sync/bootstrap"
                    )
                )
                .timeout(java.time.Duration.ofSeconds(15))
                .header("Authorization", "Bearer $access")
                .GET()
                .build()
        val loaded =
            body(native.send(bootstrap, java.net.http.HttpResponse.BodyHandlers.ofString()))
        assertEquals(listOf(f.claim.toString()), ids(loaded))
    }

    @Test
    fun bootstrapAndChangesExportOnlyOwnedReferencesAndSupportLostResponseReplay() {
        val f = expenseFixture()
        body(saveExpense(f))
        val other =
            f.copy(claim = UUID.randomUUID(), line = UUID.randomUUID(), employee = f.manager)
        body(saveExpense(other, asAdmin = true))
        val before = body(bootstrap(f))
        assertEquals(
            listOf("EXPENSE_CLAIMS"),
            before.get("collections").iterator().asSequence().map { it.asString() }.toList(),
        )
        assertEquals(listOf(f.claim.toString()), ids(before))
        assertFalse(before.toString().contains("Travel reimbursement"))
        assertFalse(before.toString().contains(f.manager.toString()))
        assertEquals(
            setOf("collection", "id", "version"),
            items(before).single().propertyNames().toSet(),
        )
        val cursor = before.get("changesCursor").asString()
        val waiting = body(changes(f, cursor))
        assertTrue(waiting.get("pendingPublication").asBoolean())
        assertFalse(waiting.get("hasMore").asBoolean())
        assertEquals(5, waiting.get("pollAfterSeconds").asInt())
        assertEquals(emptyList<String>(), ids(waiting))
        assertEquals(2, success(publisher.publish()))
        val first = body(changes(f, cursor))
        val replay = body(changes(f, cursor))
        assertEquals(first.get("items"), replay.get("items"))
        assertEquals(listOf(f.claim.toString()), ids(first))
        assertEquals("UPSERT", items(first).single().get("operation").asString())
        assertFalse(first.get("pendingPublication").asBoolean())
        assertTrue(items(body(changes(f, first.get("cursor").asString()))).isEmpty())
        val details = expenseDetails(f)
        assertEquals(items(first).single().get("version").asLong(), details.get("version").asLong())
        for (browser in listOf(f.admin, f.supervisor)) error(
            get(browser, "/api/v1/companies/${f.company}/sync/bootstrap"),
            403,
            "sync_access_denied",
        )
    }

    @Test
    fun bootstrapAnchorCatchesEditsAndNewKeysBeforeEarlierPages() {
        val f =
            expenseFixture().copy(claim = UUID.fromString("10000000-0000-0000-0000-000000000001"))
        val last =
            f.copy(
                claim = UUID.fromString("20000000-0000-0000-0000-000000000001"),
                line = UUID.randomUUID(),
            )
        body(saveExpense(f))
        body(saveExpense(last))
        assertEquals(2, success(publisher.publish()))
        val first = body(bootstrap(f, limit = 1))
        assertEquals(listOf(f.claim.toString()), ids(first))
        assertTrue(first.get("changesCursor").isNull)
        val inserted =
            f.copy(
                claim = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                line = UUID.randomUUID(),
            )
        body(saveExpense(inserted))
        body(saveExpense(f, version = 0, changes = mapOf("title" to "Updated title")))
        val final = body(bootstrap(f, first.get("nextCursor").asString(), 1))
        assertEquals(listOf(last.claim.toString()), ids(final))
        assertTrue(final.get("nextCursor").isNull)
        assertEquals(2, success(publisher.publish()))
        val delta = body(changes(f, final.get("changesCursor").asString()))
        assertEquals(setOf(inserted.claim.toString(), f.claim.toString()), ids(delta).toSet())
        assertEquals(
            1,
            items(delta)
                .single { it.get("id").asString() == f.claim.toString() }
                .get("version")
                .asLong(),
        )
    }

    @Test
    fun deltaPaginationUsesAFiniteUpperBoundWhileLaterPublicationsRemainAvailable() {
        val f = expenseFixture()
        val baseline = token(f)
        body(saveExpense(f))
        body(saveExpense(f, version = 0))
        body(saveExpense(f, version = 1))
        assertEquals(3, success(publisher.publish()))
        val first = body(changes(f, baseline, 1))
        assertTrue(first.get("hasMore").asBoolean())
        body(saveExpense(f, version = 2))
        assertEquals(1, success(publisher.publish()))
        val second = body(changes(f, first.get("cursor").asString(), 1))
        val third = body(changes(f, second.get("cursor").asString(), 1))
        assertTrue(second.get("hasMore").asBoolean())
        assertFalse(third.get("hasMore").asBoolean())
        assertEquals(
            listOf(0L, 1L, 2L),
            listOf(first, second, third).map { items(it).single().get("version").asLong() },
        )
        val next = body(changes(f, third.get("cursor").asString(), 1))
        assertEquals(3, items(next).single().get("version").asLong())
        assertFalse(next.get("hasMore").asBoolean())
    }

    @Test
    fun commandReplayDoesNotCreateAnotherChangeAndAuditFailureRollsBackTheEntireCommand() {
        val f = expenseFixture()
        val key = UUID.randomUUID()
        syncProbe.beforeJournal = { row ->
            if (row.resourceId == f.claim) throw IllegalStateException("private-audit-fixture")
        }
        val failed = saveExpense(f, key = key)
        assertEquals(500, failed.statusCode(), failed.body())
        assertFalse(failed.body().contains("private-audit-fixture"))
        assertEquals(0, queueCount(f))
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from expense_claims where id=?",
                    Int::class.java,
                    f.claim,
                ),
        )
        syncProbe.clear()
        val saved = body(saveExpense(f, key = key))
        val replay = body(saveExpense(f, key = key))
        assertEquals(saved, replay)
        assertEquals(1, queueCount(f))
        body(cancelExpense(f, 0))
        assertEquals(2, queueCount(f))
        val bootstrap = body(bootstrap(f))
        assertEquals(listOf(f.claim.toString()), ids(bootstrap))
        assertEquals(1, items(bootstrap).single().get("version").asLong())
    }

    @Test
    fun invalidTokensModesAndLimitsUseStableLocalizableErrors() {
        val f = expenseFixture()
        val cursor = token(f)
        for (limit in listOf(0, 201)) error(bootstrap(f, limit = limit), 422, "invalid_sync_limit")
        for (bad in listOf("invalid", cursor.dropLast(10), "x".repeat(2049))) error(
            changes(f, bad),
            422,
            "invalid_sync_cursor",
        )
        error(get(f.worker, "/api/v1/companies/${f.company}/sync/changes"), 400, "invalid_request")
        error(bootstrap(f, cursor), 422, "invalid_sync_cursor")
        body(saveExpense(f))
        body(saveExpense(f.copy(claim = UUID.randomUUID(), line = UUID.randomUUID())))
        val snapshot = body(bootstrap(f, limit = 1)).get("nextCursor").asString()
        error(changes(f, snapshot), 422, "invalid_sync_cursor")
        val decoded = success(cursors.decode(snapshot))
        val capped = success(cursors.encode(decoded.copy(after = null, page = 999)))
        error(bootstrap(f, capped, 1), 409, "sync_bootstrap_limit")
        clock.set(clock.instant().plusSeconds(901))
        error(bootstrap(f, snapshot, 1), 409, "sync_cursor_expired")
        val beyond = success(cursors.encode(success(cursors.decode(cursor)).copy(position = 999)))
        error(changes(f, beyond), 409, "sync_cursor_out_of_range")
    }

    @Test
    fun tombstonesAreDeliveredAsReferencesWithoutInventingBusinessDeletionEndpoints() {
        val f = expenseFixture()
        val cursor = token(f)
        val deleted = UUID.randomUUID()
        // Owned infrastructure fixture: business history is not deleted by this API.
        database()
            .update(
                "insert into mobile_sync_changes(company_id,collection,resource_id,employment_id,resource_version,operation) values(?,'EXPENSE_CLAIMS',?,?,7,'DELETE')",
                f.company,
                deleted,
                f.employee,
            )
        assertEquals(1, success(publisher.publish()))
        val change = items(body(changes(f, cursor))).single()
        assertEquals(deleted.toString(), change.get("id").asString())
        assertEquals("DELETE", change.get("operation").asString())
        assertEquals(7, change.get("version").asLong())
    }
}
