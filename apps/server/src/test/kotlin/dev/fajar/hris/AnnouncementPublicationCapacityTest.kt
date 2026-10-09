package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.jobs.domain.entities.JobStep
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate

@TestPropertySource(
    properties =
        ["spring.datasource.hikari.connection-init-sql=SET plan_cache_mode TO force_generic_plan"]
)
class AnnouncementPublicationCapacityTest : AnnouncementPublicationApiFixture() {
    private fun seedRecipients(f: Fixture, count: Int) {
        val db = database()
        TransactionTemplate(JdbcTransactionManager(requireNotNull(db.dataSource)))
            .executeWithoutResult {
                db.execute(
                    "create temporary table communication_fixture_people(account uuid,person uuid,employment uuid,number integer) on commit drop"
                )
                db.update(
                    "insert into communication_fixture_people select gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),number from generate_series(1,?) number",
                    count,
                )
                db.update(
                    "insert into accounts(id,email,display_name) select account,account::text||'@example.test','Capacity fixture' from communication_fixture_people"
                )
                db.update(
                    "insert into company_memberships(company_id,account_id) select ?,account from communication_fixture_people",
                    f.company,
                )
                db.update(
                    "insert into membership_permissions(company_id,account_id,permission) select ?,account,'announcements.read' from communication_fixture_people",
                    f.company,
                )
                db.update(
                    "insert into persons(id,owner_company_id,account_id,legal_name,nationality) select person,?,account,'Capacity employee','ID' from communication_fixture_people",
                    f.company,
                )
                db.update(
                    "insert into person_profile_revisions(person_id,revision,owner_company_id,account_id,legal_name,nationality,actor_id,reason) select person,0,?,account,'Capacity employee','ID',?,'Initial fixture profile' from communication_fixture_people",
                    f.company,
                    f.actor.accountId,
                )
                db.update(
                    "insert into employments(company_id,id,person_id,employee_number) select ?,employment,person,'CAP'||number::text from communication_fixture_people",
                    f.company,
                )
                db.update(
                    "insert into employment_revisions(company_id,employment_id,revision,effective_from,contract_kind,start_date,status,actor_id,reason) select ?,employment,0,'2026-01-01','PERMANENT','2026-01-01','ACTIVE',?,'Initial fixture employment' from communication_fixture_people",
                    f.company,
                    f.actor.accountId,
                )
            }
    }

    @Test
    fun fiveThousandRecipientsPublishWithinTheExistingWorkerTransactionBudget() {
        val f = fixture()
        seedRecipients(f, 5000)
        val id = draft(f)
        ok(publish(f, id))
        val lease = claim().single()
        val start = System.nanoTime()
        val result = advance.execute(f.actor, lease)
        val elapsed = Duration.ofNanos(System.nanoTime() - start)
        assertEquals(Result.Success(JobStep(1, true)), result)
        assertTrue(elapsed < Duration.ofSeconds(30), "Bounded publication took $elapsed")
        println(
            "announcement_publication_5000 elapsed_ms=${elapsed.toMillis()} stages_ms=${publicationProbe.timings}"
        )
        assertEquals(5000, count(f, "inbox_items"))
        assertEquals(5000, count(f, "inbox_push_dispatches"))
        assertEquals(5000, view(f, id)["recipientCount"].asInt())
        assertEquals("SUCCEEDED", job(f, lease.job.request.id)["status"].asString())
        val pushOwners = (1..5).map { UUID.randomUUID() }
        val workerJdbc =
            org.springframework.jdbc.core.JdbcTemplate(
                org.springframework.jdbc.datasource.DriverManagerDataSource(
                    postgres.jdbcUrl,
                    "hris_communications_fixture_worker",
                    "fixture-worker-only",
                )
            )
        try {
            val ready = java.util.concurrent.CountDownLatch(3)
            val startClaims = java.util.concurrent.CountDownLatch(1)
            val concurrent =
                java.util.concurrent.Executors.newFixedThreadPool(3).use { pool ->
                    val calls =
                        pushOwners.take(3).map { owner ->
                            pool.submit<Int> {
                                ready.countDown()
                                check(startClaims.await(5, java.util.concurrent.TimeUnit.SECONDS))
                                requireNotNull(
                                    workerJdbc.queryForObject(
                                        "select count(*) from claim_inbox_push(?,8,120,8)",
                                        Int::class.java,
                                        owner,
                                    )
                                )
                            }
                        }
                    assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    startClaims.countDown()
                    calls.sumOf { it.get(10, java.util.concurrent.TimeUnit.SECONDS) }
                }
            assertTrue(concurrent in 1..16)
            for (owner in pushOwners.drop(3)) workerJdbc.queryForObject(
                "select count(*) from claim_inbox_push(?,8,120,8)",
                Int::class.java,
                owner,
            )
            assertEquals(
                16,
                database()
                    .queryForObject(
                        "select count(*) from inbox_push_dispatches where state='LEASED'",
                        Int::class.java,
                    ),
            )
            assertEquals(
                0,
                workerJdbc.queryForObject(
                    "select count(*) from claim_inbox_push(?,1,120,8)",
                    Int::class.java,
                    UUID.randomUUID(),
                ),
            )
        } finally {
            for (owner in pushOwners) database()
                .update(
                    "update inbox_push_dispatches set state='FAILED',finished_at=now(),failure_code='fixture_cleanup',lease_owner=null,lease_token=null,lease_until=null where lease_owner=?",
                    owner,
                )
        }
        ok(action(f, id, "archive", 2))
        assertEquals(
            5000,
            database()
                .queryForObject(
                    "select count(*) from inbox_items where company_id=? and withdrawn",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun aLargerAudienceStopsBeforeAnyPublicationWrites() {
        val f = fixture()
        seedRecipients(f, 5001)
        val id = draft(f)
        error(
            get(f.browser, "${f.path}/$id/audience-preview?expectedVersion=0"),
            409,
            "announcement_audience_limit",
        )
        ok(publish(f, id))
        failure(advance.execute(f.actor, claim().single()), "announcement_audience_limit")
        assertEquals(0, count(f, "announcement_publications"))
        assertEquals(0, count(f, "inbox_items"))
        assertEquals("QUEUED", view(f, id)["status"].asString())
    }

    @Test
    fun maximalOverlappingGroupsProduceOneRecipientSetWithinTheWorkerBudget() {
        val f = fixture()
        seedRecipients(f, 5000)
        val employees =
            database()
                .queryForList(
                    "select id from employments where company_id=? order by id",
                    UUID::class.java,
                    f.company,
                )
        val groups = (1..32).map { group(f, members = employees.map { requireNotNull(it) }) }
        val id = draft(f, "GROUP", groups)
        val previewStarted = System.nanoTime()
        val preview = ok(get(f.browser, "${f.path}/$id/audience-preview?expectedVersion=0"))
        assertEquals(5000, preview["recipientCount"].asInt())
        println(
            "announcement_group_preview_32x5000 elapsed_ms=${Duration.ofNanos(System.nanoTime() - previewStarted).toMillis()}"
        )
        ok(publish(f, id))
        val lease = claim().single()
        val start = System.nanoTime()
        val result = advance.execute(f.actor, lease)
        val elapsed = Duration.ofNanos(System.nanoTime() - start)
        assertEquals(Result.Success(JobStep(1, true)), result)
        assertTrue(elapsed < Duration.ofSeconds(30), "Bounded group publication took $elapsed")
        println(
            "announcement_groups_32x5000 elapsed_ms=${elapsed.toMillis()} stages_ms=${publicationProbe.timings}"
        )
        assertEquals(5000, count(f, "inbox_items"))
        assertEquals(5000, count(f, "inbox_push_dispatches"))
        assertEquals(
            32,
            database()
                .queryForObject(
                    "select count(*) from announcement_publications p cross join lateral jsonb_each(p.audience_versions) where p.company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }
}
