package dev.fajar.hris

import dev.fajar.hris.communications.domain.repositories.AnnouncementRepository
import dev.fajar.hris.communications.domain.repositories.AudienceGroupRepository
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate

@Import(AccountLockProbeConfiguration::class)
class AnnouncementAuthoringHttpTest : PeopleApiFixture() {
    @Autowired private lateinit var probe: AccountLockProbe
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var announcements: AnnouncementRepository
    @Autowired private lateinit var groups: AudienceGroupRepository
    @Autowired private lateinit var runtimeDatabase: JdbcTemplate

    private data class Fixture(val browser: HttpClient, val csrf: String, val company: UUID) {
        val path
            get() = "/api/v1/companies/$company/announcements"

        val groups
            get() = "/api/v1/companies/$company/communications/audience-groups"
    }

    private fun fixture(): Fixture {
        val browser = client()
        val csrf = login(browser)
        return Fixture(browser, csrf, company(browser, csrf))
    }

    private fun body(version: Long? = null, changes: Map<String, Any?> = emptyMap()) =
        json.writeValueAsString(
            mapOf(
                "title" to "Office information",
                "body" to "Updated office hours.\nPlease review.",
                "audience" to mapOf("kind" to "COMPANY", "targetIds" to emptyList<UUID>()),
                "acknowledgementRequired" to true,
                "expectedVersion" to version,
                "reason" to "Office update",
            ) + changes
        )

    private fun save(
        f: Fixture,
        id: UUID,
        version: Long? = null,
        changes: Map<String, Any?> = emptyMap(),
        key: UUID = UUID.randomUUID(),
    ) = command(f.browser, "${f.path}/$id", body(version, changes), f.csrf, key, "PUT")

    private fun group(
        f: Fixture,
        id: UUID,
        employees: List<UUID> = emptyList(),
        version: Long? = null,
        active: Boolean = true,
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            f.browser,
            "${f.groups}/$id",
            json.writeValueAsString(
                mapOf(
                    "name" to "Office team",
                    "employmentIds" to employees,
                    "expectedVersion" to version,
                    "active" to active,
                    "reason" to "Audience maintenance",
                )
            ),
            f.csrf,
            key,
            "PUT",
        )

    private fun adminId() =
        database()
            .queryForObject(
                "select id from accounts where email='admin@example.test'",
                UUID::class.java,
            )!!

    private fun actor(f: Fixture) =
        Actor(
            adminId(),
            f.company,
            setOf("announcements.manage"),
            clock.instant(),
            UUID.randomUUID(),
            credentialVersion = 0,
        )

    private fun count(table: String, column: String, id: UUID) =
        database()
            .queryForObject("select count(*) from $table where $column=?", Int::class.java, id)!!

    @Test
    fun draftsAndGroupsRetainRevisionsAndListOnlyBoundedSummaries() {
        val f = fixture()
        val employee = employee(f.browser, f.csrf, f.company)
        val group = UUID.randomUUID()
        assertEquals(200, group(f, group, listOf(employee)).statusCode())
        assertEquals(200, group(f, group, version = 0).statusCode())
        val original = json.readTree(get(f.browser, "${f.groups}/$group/revisions/0").body())
        assertEquals(employee.toString(), original.get("employmentIds")[0].asString())
        assertEquals(
            0,
            json.readTree(get(f.browser, "${f.groups}/$group").body()).get("employmentIds").size(),
        )
        val groupList = json.readTree(get(f.browser, f.groups).body()).get("items")[0]
        assertEquals(0, groupList.get("memberCount").asInt())
        assertFalse(groupList.has("employmentIds"))
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val first = save(f, id, key = key)
        assertEquals(200, first.statusCode(), first.body())
        assertEquals(
            200,
            save(
                    f,
                    id,
                    0,
                    mapOf("title" to "Updated information", "body" to "日本語\nInformasi Élodie"),
                )
                .statusCode(),
        )
        assertEquals(first.body(), save(f, id, key = key).body())
        assertEquals(
            409,
            save(f, id, changes = mapOf("title" to "Different request"), key = key).statusCode(),
        )
        val old = json.readTree(get(f.browser, "${f.path}/$id/revisions/0").body())
        assertEquals("Office information", old.get("title").asString())
        assertEquals("DRAFT", old.get("status").asString())
        val latest = json.readTree(get(f.browser, "${f.path}/$id").body())
        assertEquals("日本語\nInformasi Élodie", latest.get("body").asString())
        val history = json.readTree(get(f.browser, "${f.path}/$id/history?limit=1").body())
        assertEquals("0", history.get("nextCursor").asString())
        assertFalse(history.get("items")[0].has("body"))
        assertEquals(
            1,
            json
                .readTree(get(f.browser, "${f.path}/$id/history?after=0").body())
                .get("items")[0]
                .get("version")
                .asInt(),
        )
        val next = UUID.randomUUID()
        assertEquals(200, save(f, next).statusCode())
        val page = json.readTree(get(f.browser, "${f.path}?limit=1").body())
        val last =
            json.readTree(
                get(f.browser, "${f.path}?limit=1&after=${page.get("nextCursor").asString()}")
                    .body()
            )
        assertTrue(last.get("nextCursor").isNull)
        assertFalse(page.get("items")[0].has("body"))
        assertEquals(422, get(f.browser, "${f.path}?limit=201").statusCode())
        assertEquals(422, get(f.browser, "${f.path}/$id/history?after=-1").statusCode())
        assertThrows(DataAccessException::class.java) {
            database().update("update announcement_revisions set body='Changed' where id=?", id)
        }
        assertThrows(DataAccessException::class.java) {
            database().update("delete from audience_group_revisions where id=?", group)
        }
        assertThrows(DataAccessException::class.java) {
            database().update("delete from announcement_heads where id=?", id)
        }
    }

    @Test
    fun emptyDuplicateForeignAndWrongKindAudiencesAreRejectedWithoutConsumingOperations() {
        val f = fixture()
        val other = fixture()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        for (kind in listOf("BRANCH", "DEPARTMENT", "GROUP")) {
            val rejected =
                save(f, id, changes = mapOf("audience" to mapOf("kind" to kind)), key = key)
            assertEquals(422, rejected.statusCode(), rejected.body())
            assertEquals(
                "invalid_announcement",
                json.readTree(rejected.body()).get("code").asString(),
            )
            assertEquals(
                "invalid_selection",
                json.readTree(rejected.body()).get("fields").get("audience.targetIds").asString(),
            )
        }
        val group = UUID.randomUUID()
        assertEquals(200, group(other, group).statusCode())
        assertEquals(
            422,
            save(
                    f,
                    id,
                    changes =
                        mapOf("audience" to mapOf("kind" to "GROUP", "targetIds" to listOf(group))),
                    key = key,
                )
                .statusCode(),
        )
        val foreign = employee(other.browser, other.csrf, other.company)
        assertEquals(422, group(f, UUID.randomUUID(), listOf(foreign)).statusCode())
        val local = employee(f.browser, f.csrf, f.company)
        assertEquals(422, group(f, UUID.randomUUID(), listOf(local, local)).statusCode())
        val branch = UUID.randomUUID()
        val unit =
            command(
                f.browser,
                "/api/v1/companies/${f.company}/organization-units/$branch",
                json.writeValueAsString(
                    mapOf(
                        "code" to "HQ",
                        "name" to "Office",
                        "kind" to "BRANCH",
                        "timezone" to "Asia/Jakarta",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, unit.statusCode(), unit.body())
        assertEquals(
            422,
            save(
                    f,
                    id,
                    changes =
                        mapOf(
                            "audience" to
                                mapOf("kind" to "DEPARTMENT", "targetIds" to listOf(branch))
                        ),
                    key = key,
                )
                .statusCode(),
        )
        assertEquals(0, count("announcement_heads", "id", id))
        assertEquals(0, count("operation_receipts", "operation_id", key))
        val valid =
            save(
                f,
                id,
                changes =
                    mapOf("audience" to mapOf("kind" to "BRANCH", "targetIds" to listOf(branch))),
                key = key,
            )
        assertEquals(200, valid.statusCode(), valid.body())
        assertEquals(1, count("announcement_revisions", "id", id))
        assertEquals(
            422,
            save(f, UUID.randomUUID(), changes = mapOf("body" to "bad\u0000text")).statusCode(),
        )
        assertEquals(
            422,
            save(f, UUID.randomUUID(), changes = mapOf("title" to "a".repeat(201))).statusCode(),
        )
    }

    @Test
    fun inactiveGroupsBlockNewDraftsButNotTheReplayOfAnAcceptedDraft() {
        val f = fixture()
        val group = UUID.randomUUID()
        assertEquals(200, group(f, group).statusCode())
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val audience = mapOf("audience" to mapOf("kind" to "GROUP", "targetIds" to listOf(group)))
        val first = save(f, id, changes = audience, key = key)
        assertEquals(200, first.statusCode(), first.body())
        assertEquals(200, group(f, group, version = 0, active = false).statusCode())
        assertEquals(first.body(), save(f, id, changes = audience, key = key).body())
        val unavailable = save(f, id, 0, audience)
        assertEquals(422, unavailable.statusCode(), unavailable.body())
        assertEquals(
            "announcement_audience_unavailable",
            json.readTree(unavailable.body()).get("code").asString(),
        )
        assertEquals(
            0,
            json.readTree(get(f.browser, "${f.path}/$id").body()).get("version").asInt(),
        )
    }

    @Test
    fun competingEditorsCannotOverwriteEachOtherOrDuplicateTheJournal() {
        val f = fixture()
        val id = UUID.randomUUID()
        assertEquals(200, save(f, id).statusCode())
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { pool ->
            val tasks =
                (1..2).map { index ->
                    pool.submit<HttpResponse<String>> {
                        ready.countDown()
                        check(start.await(5, TimeUnit.SECONDS))
                        save(f, id, 0, mapOf("title" to "Update $index"))
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val results = tasks.map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(listOf(200, 409), results.map { it.statusCode() }.sorted())
            assertEquals(
                "stale_version",
                json
                    .readTree(results.single { it.statusCode() == 409 }.body())
                    .get("code")
                    .asString(),
            )
        }
        assertEquals(2, count("announcement_revisions", "id", id))
        assertEquals(2, count("audit_entries", "resource_id", id))
        assertEquals(2, count("outbox_events", "resource_id", id))
    }

    @Test
    fun aFailedAuditRollsBackTheHeadRevisionAndOperationReceipt() {
        val f = fixture()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        database()
            .execute(
                """create function fail_announcement_test_audit() returns trigger language plpgsql as $$ begin
            if NEW.resource_id='$id'::uuid then raise exception 'Test journal failure' using errcode='23514'; end if; return NEW; end $$"""
            )
        database()
            .execute(
                "create trigger fail_announcement_test_audit before insert on audit_entries for each row execute function fail_announcement_test_audit()"
            )
        try {
            val failed = save(f, id, key = key)
            assertEquals(409, failed.statusCode(), failed.body())
            assertEquals(0, count("announcement_heads", "id", id))
            assertEquals(0, count("announcement_revisions", "id", id))
            assertEquals(0, count("operation_receipts", "operation_id", key))
            assertEquals(0, count("outbox_events", "resource_id", id))
        } finally {
            database().execute("drop trigger fail_announcement_test_audit on audit_entries")
            database().execute("drop function fail_announcement_test_audit()")
        }
        assertEquals(200, save(f, id, key = key).statusCode())
    }

    @Test
    fun pendingReadsAndWritesRecheckCredentialRevocationAndReplaysRequireCurrentGrants() {
        val f = fixture()
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val original = save(f, id, key = key)
        assertEquals(200, original.statusCode(), original.body())
        val account = adminId()
        val barrier = AccountLockProbe.Barrier(account)
        probe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> {
                    save(f, id, 0, mapOf("title" to "Revoked update"))
                }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "delete from membership_permissions where company_id=? and account_id=? and permission='announcements.manage'",
                        f.company,
                        account,
                    )
            } finally {
                probe.current.set(null)
                barrier.release.countDown()
            }
            assertEquals(403, pending.get(15, TimeUnit.SECONDS).statusCode())
        }
        assertEquals(403, save(f, id, key = key).statusCode())
        assertEquals(403, get(f.browser, f.path).statusCode())
        assertEquals(1, count("announcement_revisions", "id", id))
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'announcements.manage')",
                f.company,
                account,
            )
        val readBarrier = AccountLockProbe.Barrier(account)
        probe.current.set(readBarrier)
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<HttpResponse<String>> { get(f.browser, "${f.path}/$id/revisions/0") }
            try {
                assertTrue(readBarrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "update accounts set security_version=security_version+1 where id=?",
                        account,
                    )
            } finally {
                probe.current.set(null)
                readBarrier.release.countDown()
            }
            val denied = pending.get(15, TimeUnit.SECONDS)
            assertEquals(401, denied.statusCode(), denied.body())
            assertFalse(denied.body().contains("Updated office hours"))
        }
    }

    @Test
    fun runtimeRlsAndDatabaseReferencesProtectOtherCompanies() {
        val f = fixture()
        val other = fixture()
        val id = UUID.randomUUID()
        val group = UUID.randomUUID()
        val foreignEmployee = employee(other.browser, other.csrf, other.company)
        assertEquals(200, save(other, id).statusCode())
        assertEquals(200, group(other, group, listOf(foreignEmployee)).statusCode())
        assertEquals(404, get(f.browser, "${f.path}/$id").statusCode())
        assertEquals(404, get(f.browser, "${f.groups}/$group").statusCode())
        assertEquals(
            Result.Success(null),
            transactions.run(actor(f)) { announcements.find(other.company, id) },
        )
        assertEquals(
            Result.Success(null),
            transactions.run(actor(f)) { groups.find(other.company, group) },
        )
        val localGroup = UUID.randomUUID()
        assertEquals(200, group(f, localGroup).statusCode())
        val result =
            transactions.run(actor(f)) {
                safeDatabaseCall {
                    runtimeDatabase.update(
                        "update audience_group_heads set version=1 where company_id=? and id=?",
                        f.company,
                        localGroup,
                    )
                    runtimeDatabase.update(
                        """insert into audience_group_revisions(company_id,id,version,name,active,member_ids,recorded_at,actor_id,reason)
                values(?,?,1,'Invalid direct write',true,?::jsonb,now(),?,'Reference test')""",
                        f.company,
                        localGroup,
                        json.writeValueAsString(listOf(foreignEmployee)),
                        adminId(),
                    )
                }
            }
        assertTrue(result is Result.Failed, result.toString())
        assertEquals(
            0,
            json.readTree(get(f.browser, "${f.groups}/$localGroup").body()).get("version").asInt(),
        )
    }
}
