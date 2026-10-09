package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(
    ApprovalFenceProbeConfiguration::class,
    AccountLockProbeConfiguration::class,
    CompanyLockObservationConfiguration::class,
)
class ApprovalAccessRaceHttpTest : ApprovalApiFixture() {
    @Autowired private lateinit var probe: ApprovalFenceProbe
    @Autowired private lateinit var accounts: AccountLockProbe
    @Autowired private lateinit var companyLocks: CompanyLockObservation
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var transactions: TransactionRunner

    @Test
    fun revocationCommittedBeforeTheDecisionFencePreventsDelegatedApproval() {
        val f = leaveFixture()
        val request = pending(f)
        val to = reviewer(f.company)
        val delegation = UUID.randomUUID()
        assertEquals(200, saveDelegation(f, delegation, delegationBody(f, to.account)).statusCode())
        val key = UUID.randomUUID()
        val barrier = ApprovalFenceProbe.Barrier(f.company)
        probe.beforeLock.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val deciding = pool.submit<HttpResponse<String>> { decideAs(f, request, to, key) }
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val revoked = saveDelegation(f, delegation, delegationBody(f, to.account, 0, false))
                assertEquals(200, revoked.statusCode(), revoked.body())
                barrier.release.countDown()
                assertCode(deciding.get(15, TimeUnit.SECONDS), 403, "not_assigned_approver")
            }
        } finally {
            barrier.release.countDown()
            probe.beforeLock.set(null)
        }
        assertEquals("PENDING", details(f, request).get("status").asString())
        assertEquals("1", balance(f, f.admin).get("reservedDays").asString())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from approval_decisions where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertEquals(
            200,
            saveDelegation(f, delegation, delegationBody(f, to.account, 1)).statusCode(),
        )
        val retried = decideAs(f, request, to, key)
        assertEquals(200, retried.statusCode(), retried.body())
        assertEquals(retried.body(), decideAs(f, request, to, key).body())
    }

    @Test
    fun aDecisionInsideItsFenceCompletesBeforeACompetingRevocation() {
        val f = leaveFixture()
        val request = pending(f)
        val to = reviewer(f.company)
        val delegation = UUID.randomUUID()
        assertEquals(200, saveDelegation(f, delegation, delegationBody(f, to.account)).statusCode())
        val barrier = ApprovalFenceProbe.Barrier(f.company)
        probe.beforeDecision.set(barrier)
        try {
            Executors.newFixedThreadPool(2).use { pool ->
                val deciding = pool.submit<HttpResponse<String>> { decideAs(f, request, to) }
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                // Admission now waits for the decision's company guard before its business fence.
                val waiting = CompanyLockObservation.Attempt(f.company)
                companyLocks.current.set(waiting)
                val revoking =
                    pool.submit<HttpResponse<String>> {
                        saveDelegation(f, delegation, delegationBody(f, to.account, 0, false))
                    }
                assertTrue(waiting.entered.await(5, TimeUnit.SECONDS))
                assertFalse(revoking.isDone)
                barrier.release.countDown()
                for (response in
                    listOf(
                        deciding.get(15, TimeUnit.SECONDS),
                        revoking.get(15, TimeUnit.SECONDS),
                    )) assertEquals(200, response.statusCode(), response.body())
            }
        } finally {
            barrier.release.countDown()
            probe.beforeDecision.set(null)
            companyLocks.current.set(null)
        }
        assertEquals("APPROVED", details(f, request).get("status").asString())
        assertEquals("1", balance(f, f.admin).get("consumedDays").asString())
        assertEquals(
            false,
            database()
                .queryForObject(
                    "select active from approval_delegations where company_id=? and id=?",
                    Boolean::class.java,
                    f.company,
                    delegation,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from approval_decisions where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun reassignmentWhileWaitingCannotBeBypassedByTheFormerAssignee() {
        val f = leaveFixture()
        val request = pending(f)
        val next = reviewer(f.company)
        val barrier = ApprovalFenceProbe.Barrier(f.company)
        probe.beforeLock.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val deciding = pool.submit<HttpResponse<String>> { decide(f, request, 0) }
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                val reassigned = reassign(f, approvalId(f.company, request), setOf(next.account))
                assertEquals(200, reassigned.statusCode(), reassigned.body())
                barrier.release.countDown()
                assertCode(deciding.get(15, TimeUnit.SECONDS), 403, "not_assigned_approver")
            }
        } finally {
            barrier.release.countDown()
            probe.beforeLock.set(null)
        }
        assertEquals("PENDING", details(f, request).get("status").asString())
        assertEquals(200, decideAs(f, request, next).statusCode())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from approval_decisions where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun aWaitingSubmissionUsesThePolicyThatSurvivedArchival() {
        val f = leaveFixture()
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "5").statusCode())
        val template =
            database()
                .queryForObject(
                    "select id from approval_templates where company_id=? and kind='LEAVE'",
                    UUID::class.java,
                    f.company,
                )!!
        val request = UUID.randomUUID()
        val key = UUID.randomUUID()
        val days = listOf("2026-10-05" to "FULL")
        val barrier = ApprovalFenceProbe.Barrier(f.company)
        probe.beforeLock.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val submitting = pool.submit<HttpResponse<String>> { submit(f, request, days, key) }
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                assertEquals(
                    200,
                    saveTemplate(f, template, templateBody("LEAVE", 0, false)).statusCode(),
                )
                barrier.release.countDown()
                assertCode(submitting.get(15, TimeUnit.SECONDS), 422, "approval_policy_missing")
            }
        } finally {
            barrier.release.countDown()
            probe.beforeLock.set(null)
        }
        assertEquals("0", balance(f, f.admin).get("reservedDays").asString())
        assertEquals(200, saveTemplate(f, template, templateBody("LEAVE", 1)).statusCode())
        assertEquals(200, submit(f, request, days, key).statusCode())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select template_revision from approval_requests where company_id=? and id=?",
                    Int::class.java,
                    f.company,
                    approvalId(f.company, request),
                ),
        )
    }

    @Test
    fun approvalAdministrationReplaysRecheckCredentialsAfterWaiting() {
        for (kind in listOf("template", "delegation", "reassignment")) {
            val f = leaveFixture()
            val id =
                if (kind == "reassignment") approvalId(f.company, pending(f)) else UUID.randomUUID()
            val to = reviewer(f.company)
            val key = UUID.randomUUID()
            val action: () -> HttpResponse<String> =
                when (kind) {
                    "template" -> {
                        { saveTemplate(f, id, key = key) }
                    }
                    "delegation" -> {
                        { saveDelegation(f, id, delegationBody(f, to.account), key) }
                    }
                    else -> {
                        { reassign(f, id, setOf(to.account), key = key) }
                    }
                }
            val first = action()
            assertEquals(200, first.statusCode(), "$kind: ${first.body()}")
            val account = if (kind == "delegation") f.managerAccount else administrator()
            val barrier = AccountLockProbe.Barrier(account)
            accounts.current.set(barrier)
            try {
                Executors.newSingleThreadExecutor().use { pool ->
                    val replay = pool.submit<HttpResponse<String>> { action() }
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS), kind)
                    database()
                        .update(
                            "update accounts set security_version=security_version+1 where id=?",
                            account,
                        )
                    barrier.release.countDown()
                    assertCode(replay.get(15, TimeUnit.SECONDS), 401, "session_revoked")
                }
            } finally {
                barrier.release.countDown()
                accounts.current.set(null)
            }
            val table =
                when (kind) {
                    "template" -> "approval_templates"
                    "delegation" -> "approval_delegations"
                    else -> "approval_requests"
                }
            assertEquals(
                if (kind == "reassignment") 1 else 0,
                database()
                    .queryForObject(
                        "select version from $table where company_id=? and id=?",
                        Int::class.java,
                        f.company,
                        id,
                    ),
            )
        }
    }

    @Test
    fun pendingLeaveCommandsCannotUseRemovedPermissions() {
        for (kind in listOf("submit", "withdraw", "cancellation", "decide")) {
            val f = leaveFixture()
            val request =
                if (kind == "submit") {
                    configureWorkAndApprovals(f)
                    assertEquals(200, adjust(f, "5").statusCode())
                    UUID.randomUUID()
                } else pending(f)
            if (kind == "cancellation") assertEquals(200, decide(f, request, 0).statusCode())
            val before = balance(f, f.admin)
            val account = if (kind == "decide") f.managerAccount else f.account
            val permission = if (kind == "decide") "leave.team.approve" else "leave.self.manage"
            val barrier = AccountLockProbe.Barrier(account)
            accounts.current.set(barrier)
            try {
                Executors.newSingleThreadExecutor().use { pool ->
                    val command =
                        pool.submit<HttpResponse<String>> {
                            when (kind) {
                                "submit" -> submit(f, request, listOf("2026-10-05" to "FULL"))
                                "withdraw" -> action(f, request, "withdraw", 0)
                                "cancellation" -> action(f, request, "cancellation", 1)
                                else -> decide(f, request, 0)
                            }
                        }
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS), kind)
                    database()
                        .update(
                            "delete from membership_permissions where company_id=? and account_id=? and permission=?",
                            f.company,
                            account,
                            permission,
                        )
                    barrier.release.countDown()
                    assertCode(command.get(15, TimeUnit.SECONDS), 403, "access_denied")
                }
            } finally {
                barrier.release.countDown()
                accounts.current.set(null)
            }
            assertEquals(before, balance(f, f.admin), kind)
            if (kind == "submit")
                assertEquals(
                    0,
                    database()
                        .queryForObject(
                            "select count(*) from leave_requests where company_id=? and id=?",
                            Int::class.java,
                            f.company,
                            request,
                        ),
                )
            else
                assertEquals(
                    if (kind == "cancellation") "APPROVED" else "PENDING",
                    details(f, request, f.admin).get("status").asString(),
                )
        }
    }

    @Test
    fun aBeneficiaryLinkedAfterSubmissionCannotApproveDirectlyOrThroughDelegation() {
        val original = leaveFixture()
        val to = reviewer(original.company)
        val employee = UUID.randomUUID()
        val created =
            command(
                original.admin,
                "/api/v1/companies/${original.company}/employees",
                json.writeValueAsString(
                    mapOf(
                        "id" to employee,
                        "employeeNumber" to "E${employee.toString().take(8)}",
                        "person" to
                            mapOf(
                                "id" to UUID.randomUUID(),
                                "legalName" to "Unlinked employee",
                                "nationality" to "ID",
                            ),
                        "terms" to
                            mapOf(
                                "effectiveFrom" to "2025-01-01",
                                "startDate" to "2025-01-01",
                                "status" to "ACTIVE",
                                "contract" to "PERMANENT",
                                "managerId" to original.manager,
                            ),
                        "reason" to "Employee onboarding",
                    )
                ),
                original.adminCsrf,
                UUID.randomUUID(),
            )
        assertEquals(200, created.statusCode(), created.body())
        val f = original.copy(employee = employee)
        configureWorkAndApprovals(f)
        assertEquals(200, adjust(f, "5").statusCode())
        val template =
            database()
                .queryForObject(
                    "select id from approval_templates where company_id=? and kind='LEAVE'",
                    UUID::class.java,
                    f.company,
                )!!
        assertEquals(
            200,
            saveTemplate(
                    f,
                    template,
                    templateBody(
                        "LEAVE",
                        0,
                        changes =
                            mapOf(
                                "stages" to
                                    listOf(
                                        mapOf(
                                            "assignment" to "NAMED",
                                            "accountIds" to setOf(to.account),
                                        )
                                    )
                            ),
                    ),
                )
                .statusCode(),
        )
        val request = UUID.randomUUID()
        val submitted =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/leave/requests",
                json.writeValueAsString(
                    mapOf(
                        "id" to request,
                        "employeeId" to employee,
                        "typeId" to f.type,
                        "days" to listOf(mapOf("workDate" to "2026-10-05", "portion" to "FULL")),
                        "reason" to "Personal leave",
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
            )
        assertEquals(200, submitted.statusCode(), submitted.body())
        val bound =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/employees/$employee/account-link",
                json.writeValueAsString(
                    mapOf(
                        "accountId" to to.account,
                        "expectedVersion" to 0,
                        "reason" to "Identity verified independently",
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
            )
        assertEquals(200, bound.statusCode(), bound.body())
        assertCode(decideAs(f, request, to), 403, "self_approval_denied")
        assertFalse(
            details(f, request, to.client).get("availableActions").toString().contains("DECIDE")
        )
        val other = reviewer(f.company)
        val owner =
            f.copy(managerAccount = to.account, supervisor = to.client, supervisorCsrf = to.csrf)
        assertEquals(
            200,
            saveDelegation(owner, UUID.randomUUID(), delegationBody(owner, other.account))
                .statusCode(),
        )
        assertCode(decideAs(f, request, other), 403, "self_approval_denied")
        assertFalse(
            details(f, request, other.client).get("availableActions").toString().contains("DECIDE")
        )
        assertEquals("1", balance(f, f.admin).get("reservedDays").asString())
    }

    @Test
    fun failedAdministrationAuditRollsBackEveryChangeAndLeavesTheOriginalKeyRetryable() {
        for (kind in listOf("template", "delegation", "reassignment")) {
            val f = leaveFixture()
            val id =
                if (kind == "reassignment") approvalId(f.company, pending(f)) else UUID.randomUUID()
            val to = reviewer(f.company)
            val key = UUID.randomUUID()
            val action: () -> HttpResponse<String> =
                when (kind) {
                    "template" -> {
                        { saveTemplate(f, id, key = key) }
                    }
                    "delegation" -> {
                        { saveDelegation(f, id, delegationBody(f, to.account), key) }
                    }
                    else -> {
                        { reassign(f, id, setOf(to.account), key = key) }
                    }
                }
            database()
                .execute(
                    """create function fail_approval_audit() returns trigger language plpgsql as ${'$'}${'$'} begin if new.resource_id='$id'::uuid then raise exception 'Fixture failure' using errcode='23514';end if;return new;end ${'$'}${'$'}"""
                )
            database()
                .execute(
                    "create trigger approval_audit_probe before insert on audit_entries for each row execute function fail_approval_audit()"
                )
            try {
                val failed = action()
                assertEquals(409, failed.statusCode(), "$kind: ${failed.body()}")
                when (kind) {
                    "template" -> {
                        assertEquals(
                            0,
                            database()
                                .queryForObject(
                                    "select count(*) from approval_templates where company_id=? and id=?",
                                    Int::class.java,
                                    f.company,
                                    id,
                                ),
                        )
                        assertEquals(
                            0,
                            database()
                                .queryForObject(
                                    "select count(*) from approval_template_revisions where company_id=? and template_id=?",
                                    Int::class.java,
                                    f.company,
                                    id,
                                ),
                        )
                    }
                    "delegation" ->
                        assertEquals(
                            0,
                            database()
                                .queryForObject(
                                    "select count(*) from approval_delegations where company_id=? and id=?",
                                    Int::class.java,
                                    f.company,
                                    id,
                                ),
                        )
                    else -> {
                        assertEquals(
                            0,
                            database()
                                .queryForObject(
                                    "select version from approval_requests where company_id=? and id=?",
                                    Int::class.java,
                                    f.company,
                                    id,
                                ),
                        )
                        assertEquals(
                            0,
                            database()
                                .queryForObject(
                                    "select count(*) from approval_assignment_overrides where company_id=? and request_id=?",
                                    Int::class.java,
                                    f.company,
                                    id,
                                ),
                        )
                    }
                }
            } finally {
                database().execute("drop trigger approval_audit_probe on audit_entries")
                database().execute("drop function fail_approval_audit()")
            }
            val retried = action()
            assertEquals(200, retried.statusCode(), "$kind: ${retried.body()}")
            assertEquals(retried.body(), action().body())
        }
    }

    @Test
    fun delegationExpiryWhileARequestWaitsIsEvaluatedAtDecisionTime() {
        val f = leaveFixture()
        val request = pending(f)
        val to = reviewer(f.company)
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val body = delegationBody(f, to.account)
        val created = saveDelegation(f, id, body, key)
        assertEquals(200, created.statusCode(), created.body())
        val barrier = ApprovalFenceProbe.Barrier(f.company)
        probe.beforeLock.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val deciding = pool.submit<HttpResponse<String>> { decideAs(f, request, to) }
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                clock.set(clock.instant().plusSeconds(3600))
                barrier.release.countDown()
                assertCode(deciding.get(15, TimeUnit.SECONDS), 403, "not_assigned_approver")
            }
        } finally {
            barrier.release.countDown()
            probe.beforeLock.set(null)
        }
        assertEquals("PENDING", details(f, request).get("status").asString())
        val replay = saveDelegation(f, id, body, key)
        assertEquals(200, replay.statusCode(), replay.body())
        assertEquals(created.body(), replay.body())
    }

    @Test
    fun beneficiaryIdentityIsAvailableBeforeEmploymentStarts() {
        val f = leaveFixture()
        val to = reviewer(f.company)
        val id = UUID.randomUUID()
        val created =
            command(
                f.admin,
                "/api/v1/companies/${f.company}/employees",
                json.writeValueAsString(
                    mapOf(
                        "id" to id,
                        "employeeNumber" to "F${id.toString().take(8)}",
                        "person" to
                            mapOf(
                                "id" to UUID.randomUUID(),
                                "accountId" to to.account,
                                "legalName" to "Future employee",
                                "nationality" to "ID",
                            ),
                        "terms" to
                            mapOf(
                                "effectiveFrom" to "2027-01-01",
                                "startDate" to "2027-01-01",
                                "status" to "ACTIVE",
                                "contract" to "PERMANENT",
                                "managerId" to f.manager,
                            ),
                        "reason" to "Future employment recorded",
                    )
                ),
                f.adminCsrf,
                UUID.randomUUID(),
            )
        assertEquals(200, created.statusCode(), created.body())
        val actor =
            Actor(
                administrator(),
                f.company,
                setOf("people.read"),
                clock.instant(),
                UUID.randomUUID(),
            )
        val account =
            transactions.run(actor) {
                people.find(f.company, id, java.time.LocalDate.of(2026, 10, 1)).flatMap {
                    assertNull(it)
                    people.accountForEmployee(f.company, id)
                }
            }
        assertEquals(to.account, (account as Result.Success).value)
        val foreign =
            transactions.run(actor.copy(companyId = UUID.randomUUID())) {
                people.accountForEmployee(f.company, id)
            }
        assertNull((foreign as Result.Success).value)
    }
}
