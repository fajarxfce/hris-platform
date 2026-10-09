package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.usecases.*
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.JsonNode

@Import(OvertimeProbeConfiguration::class, AccountLockProbeConfiguration::class)
@TestPropertySource(properties = [MOBILE_SYNC_TEST_KEYS])
abstract class OvertimeApiFixture : WorkPeriodApiFixture() {
    @Autowired protected lateinit var overtimeProbe: OvertimeProbe
    @Autowired protected lateinit var accountProbe: AccountLockProbe
    @Autowired protected lateinit var transactions: TransactionRunner
    @Autowired protected lateinit var runtimeJdbc: JdbcTemplate
    @Autowired protected lateinit var planUseCase: PlanOvertimeRequest
    @Autowired protected lateinit var advance: AdvanceWorkPeriodClose

    protected data class Reviewer(val id: UUID, val client: HttpClient, val csrf: String)

    @AfterEach
    fun clearOvertimeProbes() {
        overtimeProbe.clear()
        accountProbe.current.getAndSet(null)?.release?.countDown()
    }

    protected fun overtimeFixture(): Fixture {
        val f = fixture()
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'overtime.self.manage')",
                f.company,
                f.employeeAccount,
            )
        approvalPolicy(f, listOf(f.actor.accountId))
        return f
    }

    protected fun reviewer(
        f: Fixture,
        permissions: Set<String> = setOf("company.read", "overtime.approve", "approvals.read"),
    ): Reviewer {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Overtime reviewer',password_hash from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                f.company,
                id,
            )
        permissions.forEach {
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    f.company,
                    id,
                    it,
                )
        }
        val client = client()
        return Reviewer(id, client, login(client, "$id@example.test"))
    }

    protected fun approvalPolicy(
        f: Fixture,
        reviewers: List<UUID>,
        version: Long? = null,
        active: Boolean = true,
    ): UUID {
        val id =
            if (version == null) UUID.randomUUID()
            else
                database()
                    .queryForObject(
                        "select id from approval_templates where company_id=? and kind='OVERTIME'",
                        UUID::class.java,
                        f.company,
                    )!!
        overtimeBody(
            command(
                f.admin,
                "/api/v1/companies/${f.company}/approvals/templates/$id",
                json.writeValueAsString(
                    mapOf(
                        "kind" to "OVERTIME",
                        "name" to "Overtime review",
                        "effectiveFrom" to "2026-01-01",
                        "expectedVersion" to version,
                        "active" to active,
                        "stages" to
                            reviewers.map {
                                mapOf("assignment" to "NAMED", "accountIds" to listOf(it))
                            },
                        "reason" to "Review routing",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        return id
    }

    protected fun planBody(f: Fixture, id: UUID, changes: Map<String, Any?> = emptyMap()) =
        json.writeValueAsString(
            mapOf(
                "id" to id,
                "employeeId" to f.employee,
                "expectedEmploymentVersion" to 0,
                "workDate" to "2026-09-01",
                "requested" to interval("2026-09-01T01:00:00Z", "2026-09-01T04:00:00Z"),
                "reason" to "Inventory count",
            ) + changes
        )

    protected fun interval(start: String, end: String, rest: Int = 0): Map<String, Any> =
        mapOf("startsAt" to start, "endsAt" to end, "breakMinutes" to rest)

    protected fun plan(
        f: Fixture,
        id: UUID = UUID.randomUUID(),
        key: UUID = UUID.randomUUID(),
        changes: Map<String, Any?> = emptyMap(),
        admin: Boolean = false,
    ): HttpResponse<String> =
        command(
            if (admin) f.admin else f.employeeClient,
            "${f.path}/overtime",
            planBody(f, id, changes),
            if (admin) f.csrf else f.employeeCsrf,
            key,
        )

    protected fun actual(
        f: Fixture,
        id: UUID,
        key: UUID = UUID.randomUUID(),
        changes: Map<String, Any?> = emptyMap(),
        admin: Boolean = false,
    ): HttpResponse<String> =
        command(
            if (admin) f.admin else f.employeeClient,
            "${f.path}/overtime/$id/actual",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to 0,
                    "actual" to interval("2026-09-01T01:15:00Z", "2026-09-01T03:45:00Z", 15),
                    "reason" to "Work completed",
                ) + changes
            ),
            if (admin) f.csrf else f.employeeCsrf,
            key,
        )

    protected fun decide(
        f: Fixture,
        id: UUID,
        version: Long = 1,
        decision: String = "APPROVE",
        key: UUID = UUID.randomUUID(),
        by: Reviewer? = null,
    ): HttpResponse<String> =
        command(
            by?.client ?: f.admin,
            "${f.path}/overtime/$id/decisions",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to version,
                    "decision" to decision,
                    "reason" to "Work verified",
                )
            ),
            by?.csrf ?: f.csrf,
            key,
        )

    protected fun withdraw(
        f: Fixture,
        id: UUID,
        version: Long,
        key: UUID = UUID.randomUUID(),
    ): HttpResponse<String> =
        command(
            f.employeeClient,
            "${f.path}/overtime/$id/withdraw",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "reason" to "Request withdrawn")
            ),
            f.employeeCsrf,
            key,
        )

    protected fun pending(f: Fixture): UUID {
        val id = UUID.randomUUID()
        overtimeBody(plan(f, id))
        overtimeBody(actual(f, id))
        return id
    }

    protected fun details(f: Fixture, id: UUID, client: HttpClient = f.employeeClient): JsonNode =
        overtimeBody(get(client, "${f.path}/overtime/$id"))

    protected fun overtimeBody(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    protected fun assertCode(response: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode(), response.body())
        assertEquals(code, json.readTree(response.body())["code"].asString())
    }

    protected fun rows(f: Fixture, table: String): Int {
        require(
            table in
                setOf(
                    "overtime_requests",
                    "overtime_changes",
                    "approval_requests",
                    "approval_decisions",
                    "operation_receipts",
                    "audit_entries",
                    "outbox_events",
                    "mobile_sync_changes",
                    "background_jobs",
                    "work_period_snapshots",
                )
        )
        return database()
            .queryForObject(
                "select count(*) from $table where company_id=?",
                Int::class.java,
                f.company,
            )!!
    }

    protected fun reassign(f: Fixture, approval: UUID, accounts: Set<UUID>, version: Long = 0) =
        command(
            f.admin,
            "/api/v1/companies/${f.company}/approvals/$approval/reassign",
            json.writeValueAsString(
                mapOf("version" to version, "assignees" to accounts, "reason" to "Change reviewer")
            ),
            f.csrf,
            UUID.randomUUID(),
        )
}
