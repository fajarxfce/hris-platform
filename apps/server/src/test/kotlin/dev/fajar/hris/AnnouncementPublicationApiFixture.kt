package dev.fajar.hris

import dev.fajar.hris.communications.domain.usecases.*
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.database.datasources.PostgresChangeJournalDataSource
import dev.fajar.hris.core.database.repositories.PostgresChangeJournalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.data.datasources.PostgresJobDataSource
import dev.fajar.hris.jobs.data.repositories.PostgresJobRepository
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.usecases.LeaseJobs
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager
import tools.jackson.databind.JsonNode

@Import(AnnouncementPublicationProbeConfiguration::class, AccountLockProbeConfiguration::class)
abstract class AnnouncementPublicationApiFixture : PeopleApiFixture() {
    @Autowired protected lateinit var queue: QueueAnnouncement
    @Autowired protected lateinit var advance: AdvanceAnnouncementPublication
    @Autowired protected lateinit var abort: AbortAnnouncementPublication
    @Autowired protected lateinit var publicationProbe: AnnouncementPublicationProbe
    @Autowired protected lateinit var accountProbe: AccountLockProbe
    @Autowired protected lateinit var runtimeDatabase: JdbcTemplate
    @Autowired protected lateinit var transactions: TransactionRunner
    private val createdCompanies = mutableSetOf<UUID>()

    protected data class Fixture(
        val browser: HttpClient,
        val csrf: String,
        val company: UUID,
        val actor: Actor,
    ) {
        val path
            get() = "/api/v1/companies/$company/announcements"

        val groups
            get() = "/api/v1/companies/$company/communications/audience-groups"

        val inbox
            get() = "/api/v1/companies/$company/inbox"
    }

    protected data class Member(
        val account: UUID,
        val employment: UUID,
        val browser: HttpClient,
        val csrf: String,
    )

    @AfterEach
    fun closeAnnouncementFixtures() {
        publicationProbe.clear()
        accountProbe.current.getAndSet(null)?.release?.countDown()
        for (company in createdCompanies) database()
            .update(
                "update background_jobs set status='CANCELLED',finished_at=now(),lease_owner=null,lease_token=null,lease_until=null where company_id=? and status in ('QUEUED','RUNNING')",
                company,
            )
    }

    protected fun fixture(): Fixture {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        createdCompanies += company
        val me = ok(get(browser, "/api/v1/me"))
        val account = UUID.fromString(me["account"]["id"].asString())
        val version =
            database()
                .queryForObject(
                    "select security_version from accounts where id=?",
                    Long::class.java,
                    account,
                )!!
        return Fixture(
            browser,
            csrf,
            company,
            Actor(
                account,
                company,
                setOf("announcements.manage", "announcements.read"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = version,
            ),
        )
    }

    protected fun member(
        f: Fixture,
        permissions: List<String> = listOf("announcements.read"),
        start: String = "2026-01-01",
    ): Member {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Announcement recipient',password_hash from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                f.company,
                id,
            )
        for (permission in permissions) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                f.company,
                id,
                permission,
            )
        val employment = employee(f.browser, f.csrf, f.company, account = id, start = start)
        val browser = client()
        return Member(id, employment, browser, login(browser, "$id@example.test"))
    }

    protected fun draft(
        f: Fixture,
        kind: String = "COMPANY",
        targets: List<UUID> = emptyList(),
        acknowledge: Boolean = true,
    ): UUID {
        val id = UUID.randomUUID()
        ok(
            command(
                f.browser,
                "${f.path}/$id",
                json.writeValueAsString(
                    mapOf(
                        "title" to "Office update",
                        "body" to "Office information for the team.",
                        "audience" to mapOf("kind" to kind, "targetIds" to targets),
                        "acknowledgementRequired" to acknowledge,
                        "reason" to "Company communication",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        return id
    }

    protected fun group(
        f: Fixture,
        id: UUID = UUID.randomUUID(),
        members: List<UUID>,
        version: Long? = null,
        active: Boolean = true,
    ): UUID {
        ok(
            command(
                f.browser,
                "${f.groups}/$id",
                json.writeValueAsString(
                    mapOf(
                        "name" to "Office team",
                        "employmentIds" to members,
                        "expectedVersion" to version,
                        "active" to active,
                        "reason" to "Audience configuration",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        return id
    }

    protected fun publish(
        f: Fixture,
        id: UUID,
        version: Long = 0,
        scheduledFor: Instant? = null,
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            f.browser,
            "${f.path}/$id/publish",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to version,
                    "scheduledFor" to scheduledFor?.toString(),
                    "reason" to "Publish office information",
                )
            ),
            f.csrf,
            key,
        )

    protected fun action(
        f: Fixture,
        id: UUID,
        action: String,
        version: Long,
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            f.browser,
            "${f.path}/$id/$action",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "reason" to "Communication update")
            ),
            f.csrf,
            key,
        )

    protected fun view(f: Fixture, id: UUID) = ok(get(f.browser, "${f.path}/$id"))

    protected fun inbox(f: Fixture, member: Member) = ok(get(member.browser, f.inbox))["items"]

    protected fun inboxAction(
        f: Fixture,
        member: Member,
        id: UUID,
        action: String,
        version: Long,
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            member.browser,
            "${f.inbox}/$id/$action",
            """{"expectedVersion":$version}""",
            member.csrf,
            key,
        )

    protected fun job(f: Fixture, jobId: UUID) =
        ok(get(f.browser, "/api/v1/companies/${f.company}/jobs/$jobId"))

    protected fun cancel(f: Fixture, jobId: UUID) =
        ok(
            post(
                f.browser,
                "/api/v1/companies/${f.company}/jobs/$jobId/cancel",
                """{"expectedVersion":${job(f, jobId)["version"].asLong()}}""",
                f.csrf,
            )
        )

    protected fun ok(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    protected fun error(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals(code, json.readTree(response.body())["code"].asString(), response.body())
    }

    protected fun failure(result: Result<*>, code: String) {
        assertTrue(result is Result.Failed, result.toString())
        assertEquals(code, (result as Result.Failed).failure.code)
    }

    protected fun count(f: Fixture, table: String) =
        database()
            .queryForObject(
                "select count(*) from $table where company_id=?",
                Int::class.java,
                f.company,
            )!!

    protected fun claim(): List<JobLease> {
        database()
            .execute(
                """DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='hris_communications_fixture_worker') THEN CREATE ROLE hris_communications_fixture_worker LOGIN PASSWORD 'fixture-worker-only' INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS; END IF; END $$"""
            )
        database().execute("GRANT hris_worker_capability TO hris_communications_fixture_worker")
        database().execute("GRANT USAGE ON SCHEMA public TO hris_communications_fixture_worker")
        database()
            .execute(
                "GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO hris_communications_fixture_worker"
            )
        val data =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "hris_communications_fixture_worker",
                "fixture-worker-only",
            )
        val jdbc = JdbcTemplate(data)
        val sql =
            DSL.using(
                TransactionAwareDataSourceProxy(data),
                SQLDialect.POSTGRES,
                Settings().withExecuteLogging(false),
            )
        val jobs = PostgresJobRepository(PostgresJobDataSource(sql), json)
        val tx = PostgresTransactionRunner(JdbcTransactionManager(data), jdbc)
        val result =
            LeaseJobs(
                    jobs,
                    tx,
                    PostgresChangeJournalRepository(PostgresChangeJournalDataSource(sql, json)),
                    clock,
                    JobRetryPolicy(),
                )
                .execute(UUID.randomUUID(), 2, 120, setOf(JobKind.ANNOUNCEMENT_PUBLISH))
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    protected fun published(f: Fixture, id: UUID): JobLease {
        ok(publish(f, id))
        val lease = claim().single()
        assertEquals(Result.Success(JobStep(1, true)), advance.execute(f.actor, lease))
        return lease
    }
}
