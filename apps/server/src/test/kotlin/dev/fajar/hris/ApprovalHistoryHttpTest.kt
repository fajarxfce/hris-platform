package dev.fajar.hris

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.repositories.*
import dev.fajar.hris.approvals.domain.usecases.GetApprovalHistory
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ApprovalHistoryHttpTest : ApprovalApiFixture() {
    @Autowired private lateinit var approvals: ApprovalRepository
    @Autowired private lateinit var history: ApprovalHistoryRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner

    @Test
    fun historyExposesRetainedFactsWithoutTurningCurrentAssignmentsIntoPastDecisions() {
        val f = leaveFixture()
        val leave = pending(f)
        val approval = approvalId(f.company, leave)
        val response = get(f.admin, "/api/v1/companies/${f.company}/approvals/$approval/history")
        assertEquals(200, response.statusCode(), response.body())
        val history = json.readTree(response.body())
        assertEquals(approval.toString(), history["request"]["id"].asString())
        assertEquals(0, history["decisions"].size())
        assertEquals(0, history["reassignments"]["items"].size())
        assertTrue(history["reassignments"]["nextCursor"].isNull)
    }

    @Test
    fun maximumHistoryPageDoesNotTruncateOrRepeatTheLastReassignment() {
        val f = leaveFixture()
        val id = approvalId(f.company, pending(f))
        val alternate = reviewer(f.company)
        for (version in 0L..200L) {
            val assigned = if (version % 2 == 0L) alternate.account else f.managerAccount
            val response = reassign(f, id, setOf(assigned), version = version)
            assertEquals(200, response.statusCode(), response.body())
        }
        val path = "/api/v1/companies/${f.company}/approvals/$id/history"
        val first = get(f.admin, "$path?limit=200")
        assertEquals(200, first.statusCode(), first.body())
        val page = json.readTree(first.body())
        assertEquals(201, page["request"]["version"].asInt())
        assertEquals(
            (1..200).toList(),
            page["reassignments"]["items"]
                .iterator()
                .asSequence()
                .map { it["revision"].asInt() }
                .toList(),
        )
        assertEquals("200", page["reassignments"]["nextCursor"].asString())
        val next = get(f.admin, "$path?after=200&expectedVersion=201&limit=200")
        assertEquals(200, next.statusCode(), next.body())
        val last = json.readTree(next.body())
        assertEquals(201, last["reassignments"]["items"].single()["revision"].asInt())
        assertTrue(last["reassignments"]["nextCursor"].isNull)
    }

    @Test
    fun originalReassignmentsAndDelegatedDecisionRemainReadableAndCannotBeChanged() {
        val f = leaveFixture()
        val leave = pending(f)
        val id = approvalId(f.company, leave)
        val first = reviewer(f.company)
        val delegated = reviewer(f.company)
        val key = UUID.randomUUID()
        val original = reassign(f, id, setOf(first.account), key = key)
        assertEquals(200, original.statusCode(), original.body())
        assertEquals(200, reassign(f, id, setOf(f.managerAccount), version = 1).statusCode())
        assertEquals(original.body(), reassign(f, id, setOf(first.account), key = key).body())
        assertEquals(
            200,
            saveDelegation(f, UUID.randomUUID(), delegationBody(f, delegated.account)).statusCode(),
        )
        val decided = decideAs(f, leave, delegated)
        assertEquals(200, decided.statusCode(), decided.body())
        val path = "/api/v1/companies/${f.company}/approvals/$id/history"
        val before =
            database()
                .queryForObject(
                    "select count(*) from audit_entries where company_id=?",
                    Int::class.java,
                    f.company,
                )
        val response = get(f.admin, "$path?limit=1")
        assertEquals(200, response.statusCode(), response.body())
        val page = json.readTree(response.body())
        assertEquals("APPROVED", page["request"]["status"].asString())
        assertEquals(3, page["request"]["version"].asInt())
        val decision = page["decisions"].single()
        assertEquals(
            setOf("step", "actorId", "decidingFor", "decision", "reason", "decidedAt"),
            decision.propertyNames().toSet(),
        )
        assertEquals(delegated.account.toString(), decision["actorId"].asString())
        assertEquals(f.managerAccount.toString(), decision["decidingFor"].asString())
        assertEquals("APPROVE", decision["decision"].asString())
        assertEquals("Reviewed", decision["reason"].asString())
        assertEquals(
            first.account.toString(),
            page["reassignments"]["items"].single()["assignees"].single().asString(),
        )
        assertEquals("1", page["reassignments"]["nextCursor"].asString())
        val next = get(f.admin, "$path?after=1&expectedVersion=3&limit=1")
        assertEquals(200, next.statusCode(), next.body())
        val continued = json.readTree(next.body())
        assertEquals(page["decisions"], continued["decisions"])
        assertEquals(
            f.managerAccount.toString(),
            continued["reassignments"]["items"].single()["assignees"].single().asString(),
        )
        assertTrue(continued["reassignments"]["nextCursor"].isNull)
        assertEquals(
            before,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where company_id=?",
                    Int::class.java,
                    f.company,
                ),
        )
        assertCode(get(first.client, path), 404, "approval_not_found")
        assertThrows(Exception::class.java) {
            database()
                .update(
                    "update approval_decisions set reason='changed' where company_id=? and request_id=?",
                    f.company,
                    id,
                )
        }
        assertThrows(Exception::class.java) {
            database()
                .update(
                    "delete from approval_assignment_overrides where company_id=? and request_id=?",
                    f.company,
                    id,
                )
        }
    }

    @Test
    fun historyContinuationRejectsAnUpdatedSnapshotAndAllUnboundedOrForeignQueries() {
        val f = leaveFixture()
        val id = approvalId(f.company, pending(f))
        val next = reviewer(f.company)
        assertEquals(200, reassign(f, id, setOf(next.account)).statusCode())
        val path = "/api/v1/companies/${f.company}/approvals/$id/history"
        assertEquals(200, get(f.admin, path).statusCode())
        assertEquals(200, reassign(f, id, setOf(f.managerAccount), version = 1).statusCode())
        assertCode(get(f.admin, "$path?after=1&expectedVersion=1"), 409, "approval_changed")
        for (query in
            listOf(
                "limit=0",
                "limit=201",
                "after=-1&expectedVersion=2",
                "after=1",
                "expectedVersion=-1",
                "after=9007199254740992&expectedVersion=2",
            )) assertCode(get(f.admin, "$path?$query"), 422, "invalid_approval_history_query")
        val unrelated = reviewer(f.company)
        assertCode(get(unrelated.client, "$path?expectedVersion=0"), 404, "approval_not_found")
        val other = leaveFixture()
        assertCode(
            get(f.admin, "/api/v1/companies/${other.company}/approvals/$id/history"),
            404,
            "approval_not_found",
        )
    }

    @Test
    fun liveRevocationOrDelegationExpiryCannotExposeAnotherHistoryPage() {
        val f = leaveFixture()
        val id = approvalId(f.company, pending(f))
        val delegated = reviewer(f.company)
        val delegation = UUID.randomUUID()
        assertEquals(
            200,
            saveDelegation(f, delegation, delegationBody(f, delegated.account)).statusCode(),
        )
        val path = "/api/v1/companies/${f.company}/approvals/$id/history"
        assertEquals(200, get(delegated.client, path).statusCode())
        assertEquals(
            200,
            saveDelegation(
                    f,
                    delegation,
                    delegationBody(f, delegated.account, version = 0, active = false),
                )
                .statusCode(),
        )
        assertCode(get(delegated.client, path), 404, "approval_not_found")
        assertEquals(200, get(f.supervisor, path).statusCode())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission in ('leave.approve','leave.team.approve')",
                f.company,
                f.managerAccount,
            )
        assertCode(get(f.supervisor, path), 404, "approval_not_found")
        assertEquals(200, get(f.worker, path).statusCode())
    }

    @Test
    fun assuranceAndDelegationAreCheckedAgainAfterHistoryAcquisition() {
        val f = leaveFixture()
        val id = approvalId(f.company, pending(f))
        val delegated = reviewer(f.company)
        assertEquals(
            200,
            saveDelegation(f, UUID.randomUUID(), delegationBody(f, delegated.account)).statusCode(),
        )
        val original = clock.instant()
        val probe =
            object : ApprovalHistoryRepository by history {
                override fun decisions(
                    companyId: UUID,
                    id: UUID,
                    limit: Int,
                ): Result<List<ApprovalDecisionEntry>> =
                    history.decisions(companyId, id, limit).also {
                        clock.set(original.plusSeconds(3601))
                    }
            }
        val actor =
            Actor(
                delegated.account,
                f.company,
                setOf("approvals.read", "leave.approve"),
                original,
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val expired =
            GetApprovalHistory(
                    approvals,
                    probe,
                    members,
                    identities,
                    transactions,
                    clock,
                    IdentitySecurityPolicy(enforceMfa = false),
                )
                .execute(actor, id, null, 50, null)
        assertEquals("approval_not_found", (expired as Result.Failed).failure.code)
        clock.set(original)
        val admin = reviewer(f.company, listOf("approvals.manage"))
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                admin.account,
            )
        val policy = IdentitySecurityPolicy(enforceMfa = true)
        val assured =
            Actor(
                admin.account,
                f.company,
                setOf("approvals.manage"),
                original,
                UUID.randomUUID(),
                credentialVersion = 0,
                mfaVerifiedAt = original.minus(policy.maximumMfaAge).plusSeconds(1),
            )
        val stale =
            GetApprovalHistory(approvals, probe, members, identities, transactions, clock, policy)
                .execute(assured, id, null, 50, null)
        assertEquals("mfa_required", (stale as Result.Failed).failure.code)
    }

    @Test
    fun cancellationReleasesHistoryGuardsAndNeverBecomesAnEmptySuccessfulPage() {
        val f = leaveFixture()
        val id = approvalId(f.company, pending(f))
        val cancelled = CancellationException("Owned cancelled history read")
        val source =
            object : ApprovalHistoryRepository by history {
                override fun decisions(
                    companyId: UUID,
                    id: UUID,
                    limit: Int,
                ): Result<List<ApprovalDecisionEntry>> = throw cancelled
            }
        val actor =
            Actor(
                administrator(),
                f.company,
                setOf("approvals.manage"),
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val read =
            GetApprovalHistory(
                approvals,
                source,
                members,
                identities,
                transactions,
                clock,
                IdentitySecurityPolicy(enforceMfa = false),
            )
        assertSame(
            cancelled,
            assertThrows(CancellationException::class.java) {
                read.execute(actor, id, null, 50, null)
            },
        )
        assertEquals(200, reassign(f, id, setOf(reviewer(f.company).account)).statusCode())
        assertEquals(
            200,
            get(f.admin, "/api/v1/companies/${f.company}/approvals/$id/history").statusCode(),
        )
    }
}
