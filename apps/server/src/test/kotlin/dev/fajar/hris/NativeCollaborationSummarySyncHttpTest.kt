package dev.fajar.hris

import dev.fajar.hris.collaboration.domain.entities.CollaborationSummaryOwner
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSummaryReadRepository
import dev.fajar.hris.core.domain.*
import java.net.URI
import java.net.URLEncoder
import java.net.http.*
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.context.*
import tools.jackson.databind.JsonNode

@TestPropertySource(
    properties =
        [
            "HRIS_COLLABORATION_ENABLED=true",
            "HRIS_MATRIX_ENABLED=true",
            "HRIS_MATRIX_ORIGIN=http://127.0.0.1:9",
            "HRIS_MATRIX_SERVER_NAME=matrix.example.test",
            "HRIS_MATRIX_JWT_ISSUER=urn:hris:matrix:fixture",
            "HRIS_MATRIX_JWT_AUDIENCE=matrix:fixture",
        ]
)
@Import(CollaborationSummaryReadProbeConfiguration::class)
class NativeCollaborationSummarySyncHttpTest : NativeCollaborationApiFixture() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun matrix(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_MATRIX_JWT_SECRET_FILE") {
                Path.of(
                        requireNotNull(
                                NativeCollaborationSummarySyncHttpTest::class
                                    .java
                                    .getResource("/matrix-signing.fixture")
                            )
                            .toURI()
                    )
                    .toString()
            }
        }
    }

    @Autowired private lateinit var probe: CollaborationSummaryReadProbe
    @Autowired private lateinit var summaries: CollaborationSummaryReadRepository
    @Autowired private lateinit var transactions: TransactionRunner

    @BeforeEach
    fun retirePreviousFixtureSessions() {
        // Keep the production cap; each test owns a fresh set of fictional parent sessions.
        database()
            .update(
                "update native_sessions set revoked_at=now(),version=version+1 where revoked_at is null"
            )
    }

    @AfterEach fun clearProbe() = probe.clear()

    private fun path(n: Native) =
        "${n.fixture.root}/collaboration/direct-conversations/summary-sync"

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun request(
        n: Native,
        suffix: String,
        platform: String = "ANDROID",
        build: String = "1",
    ) =
        n.fixture.browser.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port${path(n)}$suffix"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer ${n.access}")
                .header("X-HRIS-Client-Platform", platform)
                .header("X-HRIS-Client-Build", build)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun success(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
        return json.readTree(response.body())
    }

    private fun bootstrap(n: Native, limit: Int = 30, cursor: String? = null) =
        success(
            request(n, "/bootstrap?limit=$limit" + (cursor?.let { "&cursor=${encode(it)}" } ?: ""))
        )

    private fun changes(n: Native, cursor: String, limit: Int = 30) =
        success(request(n, "/changes?limit=$limit&cursor=${encode(cursor)}"))

    private fun cursor(n: Native) = bootstrap(n)["changesCursor"].asString()

    private data class PairFixture(val id: UUID, val peer: UUID)

    private fun pair(
        n: Native,
        id: UUID = UUID.randomUUID(),
        database: JdbcTemplate = database(),
    ): PairFixture {
        val peer = UUID.randomUUID()
        database.update(
            "insert into accounts(id,email,display_name) values(?,?,'Summary peer')",
            peer,
            "$peer@example.test",
        )
        val ordered = listOf(n.fixture.account, peer).sortedBy { it.toString() }
        database.update(
            "insert into collaboration_direct_conversations(company_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,now())",
            n.fixture.company,
            id,
            ordered[0],
            ordered[1],
            n.fixture.account,
        )
        return PairFixture(id, peer)
    }

    private fun project(
        n: Native,
        pair: PairFixture,
        name: String = "Summary peer",
        database: JdbcTemplate = database(),
    ) {
        database.update(
            "insert into collaboration_direct_summary_records(company_id,conversation_id,owner_account_id,peer_account_id,version,peer_display_name) values(?,?,?,?,0,?)",
            n.fixture.company,
            pair.id,
            n.fixture.account,
            pair.peer,
            name,
        )
        database.update(
            "update collaboration_summary_refresh_queue set due_at=null where company_id=? and conversation_id=?",
            n.fixture.company,
            pair.id,
        )
    }

    private fun revise(n: Native, pair: PairFixture, name: String?) {
        database()
            .update(
                "update collaboration_direct_summary_records set peer_display_name=?,version=version+1 where company_id=? and owner_account_id=? and conversation_id=?",
                name,
                n.fixture.company,
                n.fixture.account,
                pair.id,
            )
    }

    private fun publish() {
        repeat(20) {
            if (
                database()
                    .queryForObject("select publish_mobile_sync_changes(200)", Int::class.java) == 0
            )
                return
        }
        fail<Unit>("Fictional publication did not drain within its bound")
    }

    @Test
    fun bootstrapPendingAndChangedOnlyPagesPreserveIndependentRevisions() {
        val n = native()
        val pair = pair(n)
        val initial = bootstrap(n)
        assertTrue(initial["items"].isEmpty)
        assertTrue(initial["pending"].asBoolean())
        project(n, pair)
        val beforePublish = changes(n, initial["changesCursor"].asString())
        assertTrue(beforePublish["items"].isEmpty)
        assertTrue(beforePublish["pending"].asBoolean())
        publish()
        val received = changes(n, beforePublish["nextCursor"].asString())
        assertFalse(received["pending"].asBoolean())
        assertFalse(received["hasMore"].asBoolean())
        val fact = received["items"][0]["fact"]
        assertEquals(pair.id.toString(), fact["conversationId"].asString())
        assertEquals(0L, fact["revision"].asLong())
        assertEquals(0L, fact["summary"]["conversation"]["version"].asLong())
        val noChange = changes(n, received["nextCursor"].asString())
        assertTrue(noChange["items"].isEmpty)
        revise(n, pair, "Renamed peer")
        publish()
        val renamed = changes(n, noChange["nextCursor"].asString())
        assertEquals(1, renamed["items"].size())
        assertEquals(1L, renamed["items"][0]["fact"]["revision"].asLong())
        assertEquals(
            "Renamed peer",
            renamed["items"][0]["fact"]["summary"]["peer"]["displayName"].asString(),
        )
    }

    @Test
    fun tombstoneAndRestorationHydrateCurrentRevisionsInOneBatch() {
        val n = native()
        val pair = pair(n)
        project(n, pair)
        publish()
        val start = cursor(n)
        revise(n, pair, null)
        publish()
        val removed = changes(n, start)
        assertTrue(removed["items"][0]["fact"]["summary"].isNull)
        assertEquals(1L, removed["items"][0]["fact"]["revision"].asLong())
        revise(n, pair, "Restored")
        publish()
        probe.hydrationCalls.set(0)
        val replay = changes(n, start)
        assertEquals(2, replay["items"].size())
        assertEquals(1, probe.hydrationCalls.get())
        replay["items"].forEach {
            assertEquals(2L, it["fact"]["revision"].asLong())
            assertEquals("Restored", it["fact"]["summary"]["peer"]["displayName"].asString())
        }
        assertEquals(1, bootstrap(n)["items"].size())
    }

    @Test
    fun publicationDuringPagingWaitsForTheNextFiniteChangesCycle() {
        val n = native()
        val start = cursor(n)
        val original = List(3) { pair(n).also { project(n, it) } }
        publish()
        val first = changes(n, start, 1)
        assertTrue(first["hasMore"].asBoolean())
        val added = pair(n)
        project(n, added)
        publish()
        val second = changes(n, first["nextCursor"].asString(), 1)
        val third = changes(n, second["nextCursor"].asString(), 1)
        assertTrue(second["hasMore"].asBoolean())
        assertFalse(third["hasMore"].asBoolean())
        val ids =
            listOf(first, second, third)
                .map { it["items"][0]["fact"]["conversationId"].asString() }
                .toSet()
        assertEquals(original.map { it.id.toString() }.toSet(), ids)
        val later = changes(n, third["nextCursor"].asString())
        assertEquals(1, later["items"].size())
        assertEquals(added.id.toString(), later["items"][0]["fact"]["conversationId"].asString())
        val page1 = bootstrap(n, 2)
        assertFalse(page1["nextPage"].isNull)
        assertTrue(page1["changesCursor"].isNull)
        val page2 = bootstrap(n, 2, page1["nextPage"].asString())
        assertTrue(page2["nextPage"].isNull)
        assertFalse(page2["changesCursor"].isNull)
        assertEquals(
            4,
            (page1["items"]
                    .iterator()
                    .asSequence()
                    .map { it["conversationId"].asString() }
                    .toList() +
                    page2["items"]
                        .iterator()
                        .asSequence()
                        .map { it["conversationId"].asString() }
                        .toList())
                .toSet()
                .size,
        )
    }

    @Test
    fun aLateCommitIsPublishedAfterTheCapturedEmptySnapshot() {
        val n = native()
        DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .connection
            .use { connection ->
                connection.autoCommit = false
                val sql =
                    JdbcTemplate(
                        org.springframework.jdbc.datasource.SingleConnectionDataSource(
                            connection,
                            true,
                        )
                    )
                try {
                    val pair = pair(n, database = sql)
                    project(n, pair, database = sql)
                    val before = bootstrap(n)
                    assertTrue(before["items"].isEmpty)
                    connection.commit()
                    publish()
                    val after = changes(n, before["changesCursor"].asString())
                    assertEquals(1, after["items"].size())
                    assertEquals(
                        pair.id.toString(),
                        after["items"][0]["fact"]["conversationId"].asString(),
                    )
                } finally {
                    connection.rollback()
                }
            }
    }

    @Test
    fun onlyRelevantAccountFanoutAndOwnedPairsReportPendingWork() {
        val n = native()
        val pair = pair(n)
        project(n, pair)
        publish()
        assertFalse(bootstrap(n)["pending"].asBoolean())
        val unrelated = native()
        pair(unrelated)
        assertFalse(bootstrap(n)["pending"].asBoolean())
        database()
            .update("update accounts set display_name='Changed public name' where id=?", pair.peer)
        assertTrue(bootstrap(n)["pending"].asBoolean())
        database()
            .update(
                "update collaboration_summary_account_queue set due_at=null,after_company_id=null,after_conversation_id=null where account_id=?",
                pair.peer,
            )
        assertFalse(bootstrap(n)["pending"].asBoolean())
        database()
            .update(
                "update collaboration_summary_refresh_queue set due_at='-infinity' where company_id=? and conversation_id=?",
                n.fixture.company,
                pair.id,
            )
        assertTrue(bootstrap(n)["pending"].asBoolean())
    }

    private fun exchange(f: Fixture): Pair<Native, String> {
        val response =
            command(
                f.browser,
                "/api/v1/auth/native/exchange",
                """{"deviceName":"Summary sync fixture"}""",
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, response.statusCode(), response.body())
        val body = json.readTree(response.body())
        return Native(
            f,
            UUID.fromString(body["sessionId"].asString()),
            body["accessToken"].asString(),
        ) to body["refreshToken"].asString()
    }

    private fun refresh(n: Native, token: String): Pair<Native, String> {
        val response =
            client()
                .send(
                    HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/v1/auth/native/refresh"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .POST(
                            HttpRequest.BodyPublishers.ofString(
                                json.writeValueAsString(mapOf("refreshToken" to token))
                            )
                        )
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
        assertEquals(200, response.statusCode(), response.body())
        val body = json.readTree(response.body())
        assertEquals(n.session.toString(), body["sessionId"].asString())
        return Native(n.fixture, n.session, body["accessToken"].asString()) to
            body["refreshToken"].asString()
    }

    @Test
    fun ordinaryNativeRotationKeepsProgressButANewParentAndExpiredCursorDoNot() {
        val (n, refreshToken) = exchange(native().fixture)
        val start = cursor(n)
        clock.set(clock.instant().plusSeconds(11))
        val (rotated, nextRefresh) = refresh(n, refreshToken)
        assertTrue(changes(rotated, start)["items"].isEmpty)
        val (newParent, _) = exchange(n.fixture)
        failure(
            request(newParent, "/changes?cursor=${encode(start)}"),
            422,
            "invalid_collaboration_sync_cursor",
        )
        clock.set(clock.instant().plusSeconds(604800))
        val (current, _) = refresh(rotated, nextRefresh)
        failure(
            request(current, "/changes?cursor=${encode(start)}"),
            409,
            "collaboration_summary_cursor_expired",
        )
    }

    @Test
    fun transportBoundsModuleAndCurrentMembershipApplyToEveryPage() {
        val n = native()
        val start = cursor(n)
        failure(get(n.fixture.browser, path(n) + "/bootstrap"), 403, "invalid_session_transport")
        failure(request(n, "/bootstrap", platform = "WEB"), 422, "invalid_client_version")
        for (limit in listOf(0, 51, Int.MAX_VALUE)) failure(
            request(n, "/bootstrap?limit=$limit"),
            422,
            "invalid_collaboration_conversation_query",
        )
        failure(request(n, "/changes?cursor=invalid"), 422, "invalid_collaboration_sync_cursor")
        val other = native()
        failure(
            request(other, "/changes?cursor=${encode(start)}"),
            422,
            "invalid_collaboration_sync_cursor",
        )
        assertEquals(
            200,
            save(n.fixture, changes = mapOf("disabledModules" to listOf("COLLABORATION")))
                .statusCode(),
        )
        failure(request(n, "/changes?cursor=${encode(start)}"), 403, "company_module_disabled")
        assertEquals(
            200,
            save(n.fixture, 0, mapOf("disabledModules" to emptyList<String>())).statusCode(),
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                n.fixture.company,
                n.fixture.account,
            )
        failure(request(n, "/changes?cursor=${encode(start)}"), 403, "access_denied")
    }

    @Test
    fun admissionIsRecheckedAfterBothSuccessfulAndFailedDatabaseReads() {
        for (kind in listOf("snapshot", "head")) {
            val n = native()
            val now = clock.instant()
            try {
                probe.afterRead.set { read ->
                    if (read == kind) {
                        clock.set(now.plusSeconds(601))
                        if (kind == "head") throw IllegalStateException("Fictional read failure")
                    }
                }
                failure(request(n, "/bootstrap"), 401, "native_session_invalid")
            } finally {
                probe.clear()
                clock.set(now)
            }
        }
    }

    @Test
    fun anotherAdmittedAccountCannotReadOrResumeThisParticipantsMetadata() {
        val n = native()
        val pair = pair(n)
        project(n, pair)
        publish()
        val start = cursor(n)
        val account = UUID.randomUUID()
        val email = "$account@example.test"
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Unrelated fixture',password_hash from accounts where id=?",
                account,
                email,
                n.fixture.account,
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                n.fixture.company,
                account,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                n.fixture.company,
                account,
            )
        val browser = client()
        val csrf = login(browser, email)
        val actor =
            Actor(
                account,
                n.fixture.company,
                setOf("collaboration.chat"),
                clock.instant(),
                UUID.randomUUID(),
            )
        val (stranger, _) = exchange(Fixture(browser, csrf, n.fixture.company, account, actor))
        assertTrue(bootstrap(stranger)["items"].isEmpty)
        failure(
            request(stranger, "/changes?cursor=${encode(start)}"),
            422,
            "invalid_collaboration_sync_cursor",
        )
        val claimed = CollaborationSummaryOwner(n.fixture.company, n.fixture.account, n.session)
        val raw = transactions.run(actor) { summaries.snapshot(claimed, null, 50) }
        assertEquals(Result.Success(emptyList<Any>()), raw)
        val foreignCompany = actor.copy(companyId = UUID.randomUUID())
        assertEquals(
            Result.Success(emptyList<Any>()),
            transactions.run(foreignCompany) { summaries.facts(claimed, listOf(pair.id)) },
        )
    }
}
