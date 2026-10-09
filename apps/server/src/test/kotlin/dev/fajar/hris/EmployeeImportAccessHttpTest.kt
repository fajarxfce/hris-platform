package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException

@Import(AccountLockProbeConfiguration::class, JobQueueProbeConfiguration::class)
class EmployeeImportAccessHttpTest : EmployeeImportApiFixture() {
    @Autowired private lateinit var accountProbe: AccountLockProbe
    @Autowired private lateinit var queueProbe: JobQueueProbe

    private data class ImportCommand(
        val f: ImportFixture,
        val id: UUID,
        val path: String,
        val body: String,
        val key: UUID = UUID.randomUUID(),
    )

    private fun independentFixture(): ImportFixture {
        val account = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Import access fixture',password_hash from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into platform_permissions(account_id,permission) select ?,permission from platform_permissions where account_id=(select id from accounts where email='admin@example.test')",
                account,
            )
        return fixture("$account@example.test")
    }

    private fun prepare(mode: String): ImportCommand {
        val f = independentFixture()
        if (mode == "start") {
            val id = UUID.randomUUID()
            return ImportCommand(
                f,
                id,
                f.path,
                json.writeValueAsString(
                    mapOf(
                        "id" to id,
                        "fileName" to "employees.csv",
                        "csv" to csv("G01"),
                        "reason" to "Reviewed employee import",
                    )
                ),
            )
        }
        val id = begin(f, csv("G01"))
        if (mode == "apply") drain(f, lease(id, JobKind.EMPLOYEE_IMPORT_PREVIEW))
        if (mode == "resume") {
            val running = lease(id, JobKind.EMPLOYEE_IMPORT_PREVIEW)
            assertEquals(
                Result.Success(Unit),
                abort.execute(running, Failure(FailureKind.UNEXPECTED, "fixture_stopped")),
            )
        }
        val version = details(f, id)["batch"]["version"].asLong()
        val input =
            mutableMapOf<String, Any>(
                "expectedVersion" to version,
                "reason" to "Reviewed import transition",
            )
        if (mode == "apply") input["allowPartial"] = false
        return ImportCommand(f, id, "${f.path}/$id/$mode", json.writeValueAsString(input))
    }

    private fun send(c: ImportCommand, csrf: String = c.f.csrf) =
        command(c.f.browser, c.path, c.body, csrf, c.key)

    private fun waiting(
        account: UUID,
        change: () -> Unit,
        occurrence: Int = 1,
        request: () -> HttpResponse<String>,
    ): HttpResponse<String> {
        val barrier = AccountLockProbe.Barrier(account, occurrence = occurrence)
        accountProbe.current.set(barrier)
        return Executors.newSingleThreadExecutor().use { pool ->
            val pending = pool.submit<HttpResponse<String>> { request() }
            try {
                assertTrue(
                    barrier.entered.await(5, TimeUnit.SECONDS),
                    "The import operation must guard current account access",
                )
                change()
                barrier.release.countDown()
                pending.get(10, TimeUnit.SECONDS)
            } finally {
                barrier.release.countDown()
                accountProbe.current.set(null)
            }
        }
    }

    @Test
    fun managementCommandsAndReplayRequireCurrentImportAuthority() {
        for (mode in listOf("start", "apply", "resume", "cancel")) {
            val c = prepare(mode)
            val f = c.f
            try {
                val denied =
                    waiting(
                        f.actor.accountId,
                        {
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=? and permission='people.import'",
                                    f.company,
                                    f.actor.accountId,
                                )
                        },
                    ) {
                        send(c)
                    }
                assertEquals(403, denied.statusCode(), "$mode ${denied.body()}")
                assertEquals(
                    "employee_import_access_required",
                    json.readTree(denied.body())["code"].asString(),
                )
                database()
                    .update(
                        "insert into membership_permissions(company_id,account_id,permission) values(?,?,'people.import')",
                        f.company,
                        f.actor.accountId,
                    )
                val saved = send(c)
                assertEquals(200, saved.statusCode(), "$mode ${saved.body()}")
                val revoked =
                    waiting(
                        f.actor.accountId,
                        {
                            database()
                                .update(
                                    "update accounts set security_version=security_version+1 where id=?",
                                    f.actor.accountId,
                                )
                        },
                    ) {
                        send(c)
                    }
                assertEquals(401, revoked.statusCode(), "$mode ${revoked.body()}")
            } finally {
                settleImportJobs()
            }
        }
    }

    @Test
    fun readsRevalidatePermissionsCredentialsMembershipAndCompanyState() {
        for (mode in
            listOf("list", "detail", "rows", "attempts", "credentials", "membership", "company")) {
            val f = independentFixture()
            val id = begin(f, csv("G01"))
            val path =
                when (mode) {
                    "list" -> f.path
                    "rows" -> "${f.path}/$id/rows"
                    "attempts" -> "${f.path}/$id/attempts"
                    else -> "${f.path}/$id"
                }
            val response =
                waiting(
                    f.actor.accountId,
                    {
                        when (mode) {
                            "credentials" ->
                                database()
                                    .update(
                                        "update accounts set security_version=security_version+1 where id=?",
                                        f.actor.accountId,
                                    )
                            "membership" ->
                                database()
                                    .update(
                                        "update company_memberships set active=false,version=version+1 where company_id=? and account_id=?",
                                        f.company,
                                        f.actor.accountId,
                                    )
                            "company" ->
                                database()
                                    .update(
                                        "update companies set active=false where id=?",
                                        f.company,
                                    )
                            else ->
                                database()
                                    .update(
                                        "delete from membership_permissions where company_id=? and account_id=? and permission='people.profile.read'",
                                        f.company,
                                        f.actor.accountId,
                                    )
                        }
                    },
                ) {
                    get(f.browser, path)
                }
            assertEquals(
                if (mode == "credentials") 401 else 403,
                response.statusCode(),
                "$mode ${response.body()}",
            )
        }
    }

    @Test
    fun queueWaitCannotOutliveTheAuthenticationUsedToAuthorizeAJob() {
        for (mode in listOf("start", "apply", "resume")) {
            val c = prepare(mode)
            val f = c.f
            val barrier = JobQueueProbe.Barrier(f.company)
            queueProbe.current.set(barrier)
            try {
                val response =
                    Executors.newSingleThreadExecutor().use { pool ->
                        val pending = pool.submit<HttpResponse<String>> { send(c) }
                        try {
                            assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                            clock.set(clock.instant().plus(Duration.ofMinutes(31)))
                            barrier.release.countDown()
                            pending.get(10, TimeUnit.SECONDS)
                        } finally {
                            barrier.release.countDown()
                            queueProbe.current.set(null)
                        }
                    }
                assertEquals(403, response.statusCode(), "$mode ${response.body()}")
                assertEquals(
                    "recent_authentication_required",
                    json.readTree(response.body())["code"].asString(),
                )
                val fresh = login(f.browser, "${f.actor.accountId}@example.test")
                val recovered = send(c, fresh)
                assertEquals(200, recovered.statusCode(), "$mode ${recovered.body()}")
            } finally {
                settleImportJobs()
            }
        }
    }

    @Test
    fun cancellationAlsoRechecksAuthenticationAfterWaiting() {
        val c = prepare("cancel")
        val response =
            waiting(
                c.f.actor.accountId,
                { clock.set(clock.instant().plus(Duration.ofMinutes(31))) },
            ) {
                send(c)
            }
        assertEquals(403, response.statusCode(), response.body())
        assertEquals(
            "recent_authentication_required",
            json.readTree(response.body())["code"].asString(),
        )
        assertFalse(
            database()
                .queryForObject(
                    "select cancellation_requested from background_jobs where company_id=? and id=(select job_id from employee_imports where company_id=? and id=?)",
                    Boolean::class.java,
                    c.f.company,
                    c.f.company,
                    c.id,
                )!!
        )
    }

    @Test
    fun detailReadsHoldTheBatchStableAcrossHeaderAndOutcomeQueries() {
        val f = independentFixture()
        val id = begin(f, csv("G01"))
        val response =
            waiting(
                f.actor.accountId,
                {
                    assertThrows(DataAccessException::class.java) {
                        database()
                            .queryForObject(
                                "select id from employee_imports where company_id=? and id=? for update nowait",
                                UUID::class.java,
                                f.company,
                                id,
                            )
                    }
                },
                occurrence = 2, // The first guard belongs to company client admission.
            ) {
                get(f.browser, "${f.path}/$id")
            }
        assertEquals(200, response.statusCode(), response.body())
        val batch = json.readTree(response.body())
        assertEquals("PREVIEWING", batch["batch"]["status"].asString())
        assertEquals(1, batch["counts"]["PENDING"].asInt())
    }
}
