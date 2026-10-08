package dev.fajar.hris

import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.util.UUID
import org.junit.jupiter.api.Assertions.*

abstract class ApprovalApiFixture : LeaveApiFixture() {
    protected data class Reviewer(val account: UUID, val client: HttpClient, val csrf: String)

    protected fun reviewer(
        company: UUID,
        permissions: List<String> = listOf("approvals.read", "leave.approve"),
    ): Reviewer {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Approval reviewer',password_hash from accounts where email='admin@example.test'",
                id,
                "$id@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                id,
            )
        permissions.forEach {
            database()
                .update(
                    "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                    company,
                    id,
                    it,
                )
        }
        val client = client()
        return Reviewer(id, client, login(client, "$id@example.test"))
    }

    protected fun administrator(): UUID =
        database()
            .queryForObject(
                "select id from accounts where email='admin@example.test'",
                UUID::class.java,
            )!!

    protected fun pending(f: Fixture): UUID {
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "5").statusCode())
        val id = UUID.randomUUID()
        val result = submit(f, id, listOf("2026-10-05" to "FULL"))
        assertEquals(200, result.statusCode(), result.body())
        return id
    }

    protected fun approvalId(company: UUID, leave: UUID): UUID =
        database()
            .queryForObject(
                "select approval_id from leave_requests where company_id=? and id=?",
                UUID::class.java,
                company,
                leave,
            )!!

    protected fun delegationBody(
        f: Fixture,
        to: UUID,
        version: Long? = null,
        active: Boolean = true,
        changes: Map<String, Any?> = emptyMap(),
    ): String =
        json.writeValueAsString(
            mapOf(
                "kind" to "LEAVE",
                "fromAccount" to f.managerAccount,
                "toAccount" to to,
                "validFrom" to clock.instant().minusSeconds(60).toString(),
                "validUntil" to clock.instant().plusSeconds(3600).toString(),
                "active" to active,
                "expectedVersion" to version,
                "reason" to "Approval coverage",
            ) + changes
        )

    protected fun saveDelegation(
        f: Fixture,
        id: UUID,
        body: String,
        key: UUID = UUID.randomUUID(),
    ): HttpResponse<String> =
        command(
            f.supervisor,
            "/api/v1/companies/${f.company}/approvals/delegations/$id",
            body,
            f.supervisorCsrf,
            key,
            "PUT",
        )

    protected fun decideAs(
        f: Fixture,
        id: UUID,
        reviewer: Reviewer,
        key: UUID = UUID.randomUUID(),
    ): HttpResponse<String> =
        command(
            reviewer.client,
            "/api/v1/companies/${f.company}/leave/requests/$id/decisions",
            """{"version":0,"decision":"APPROVE","reason":"Reviewed"}""",
            reviewer.csrf,
            key,
        )

    protected fun templateBody(
        kind: String = "EXPENSE",
        version: Long? = null,
        active: Boolean = true,
        changes: Map<String, Any?> = emptyMap(),
    ): String =
        json.writeValueAsString(
            mapOf(
                "name" to "Approval policy",
                "kind" to kind,
                "effectiveFrom" to "2025-01-01",
                "expectedVersion" to version,
                "active" to active,
                "stages" to listOf(mapOf("assignment" to "MANAGER")),
                "reason" to "Approval configuration",
            ) + changes
        )

    protected fun saveTemplate(
        f: Fixture,
        id: UUID,
        body: String = templateBody(),
        key: UUID = UUID.randomUUID(),
    ): HttpResponse<String> =
        command(
            f.admin,
            "/api/v1/companies/${f.company}/approvals/templates/$id",
            body,
            f.adminCsrf,
            key,
            "PUT",
        )

    protected fun reassign(
        f: Fixture,
        approval: UUID,
        assignees: Set<UUID>,
        version: Long = 0,
        key: UUID = UUID.randomUUID(),
    ): HttpResponse<String> =
        command(
            f.admin,
            "/api/v1/companies/${f.company}/approvals/$approval/reassign",
            json.writeValueAsString(
                mapOf(
                    "version" to version,
                    "assignees" to assignees,
                    "reason" to "Reviewer changed",
                )
            ),
            f.adminCsrf,
            key,
        )

    protected fun seedTemplates(
        f: Fixture,
        count: Int,
        kind: String = "EXPENSE",
        active: Boolean = true,
    ) {
        database()
            .update(
                """with headers as (insert into approval_templates(company_id,id,name,kind,active) select ?,gen_random_uuid(),'Capacity policy',?,? from generate_series(1,?) returning id)
            insert into approval_template_revisions(company_id,template_id,revision,effective_from,category,stages,actor_id,reason)
            select ?,id,0,date '2025-01-01','CAPACITY','[{"assignment":"MANAGER","accountIds":[],"permission":null}]'::jsonb,?,'Fixture policy' from headers""",
                f.company,
                kind,
                active,
                count,
                f.company,
                administrator(),
            )
    }

    protected fun seedDelegations(f: Fixture, to: UUID, count: Int, active: Boolean = true) {
        database()
            .update(
                """insert into approval_delegations(company_id,id,kind,from_account,to_account,valid_from,valid_until,active)
            select ?,gen_random_uuid(),'LEAVE',?,?,?::timestamptz,?::timestamptz,? from generate_series(1,?)""",
                f.company,
                f.managerAccount,
                to,
                clock.instant().minusSeconds(60).toString(),
                clock.instant().plusSeconds(3600).toString(),
                active,
                count,
            )
    }

    protected fun assertCode(result: HttpResponse<String>, status: Int, code: String) {
        assertEquals(status, result.statusCode(), result.body())
        assertEquals(code, json.readTree(result.body()).get("code").asString())
    }
}
