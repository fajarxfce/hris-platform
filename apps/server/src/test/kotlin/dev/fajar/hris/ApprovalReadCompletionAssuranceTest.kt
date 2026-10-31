package dev.fajar.hris

import dev.fajar.hris.approvals.domain.entities.*
import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.approvals.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class ApprovalReadCompletionAssuranceTest : ApprovalApiFixture() {
    @Autowired private lateinit var approvals: ApprovalRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "request",
                "templateCurrent",
                "templateRevision",
                "templates",
                "delegation",
                "delegations",
                "inbox",
                "assignees",
            ]
    )
    fun expiryDuringSuccessfulAcquisitionCannotDeliverAnAuthorizedProjection(operation: String) {
        val f = leaveFixture()
        val request = approvalId(f.company, pending(f))
        val permissions = setOf("approvals.manage", "approvals.read", "leave.approve")
        val reviewer = reviewer(f.company, permissions.toList())
        val template = UUID.randomUUID()
        if (operation.startsWith("template"))
            assertEquals(200, saveTemplate(f, template).statusCode())
        val delegation = UUID.randomUUID()
        if (operation.startsWith("delegation"))
            assertEquals(
                200,
                saveDelegation(f, delegation, delegationBody(f, reviewer.account)).statusCode(),
            )
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                reviewer.account,
            )
        val original = clock.instant()
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val actor =
            Actor(
                reviewer.account,
                f.company,
                permissions,
                original,
                UUID.randomUUID(),
                credentialVersion = 0,
                mfaVerifiedAt = original.minus(security.maximumMfaAge).plusSeconds(1),
            )
        var acquisitions = 0
        fun <T> expire(result: Result<T>): Result<T> {
            assertTrue(result is Result.Success, "Fixture acquisition must succeed before expiry")
            acquisitions++
            clock.set(original.plusSeconds(2))
            return result
        }
        val source =
            object : ApprovalRepository by approvals {
                override fun find(companyId: UUID, id: UUID) = expire(approvals.find(companyId, id))

                override fun findTemplate(companyId: UUID, id: UUID) =
                    expire(approvals.findTemplate(companyId, id))

                override fun findTemplateRevision(companyId: UUID, id: UUID, revision: Long) =
                    expire(approvals.findTemplateRevision(companyId, id, revision))

                override fun findDelegation(companyId: UUID, id: UUID) =
                    expire(approvals.findDelegation(companyId, id))

                override fun templatePage(
                    companyId: UUID,
                    kind: ApprovalKind,
                    asOf: LocalDate,
                    after: UUID?,
                    limit: Int,
                ) = expire(approvals.templatePage(companyId, kind, asOf, after, limit))

                override fun delegationPage(
                    companyId: UUID,
                    accountId: UUID,
                    at: Instant,
                    after: UUID?,
                    limit: Int,
                ) = expire(approvals.delegationPage(companyId, accountId, at, after, limit))

                override fun inbox(
                    companyId: UUID,
                    accountId: UUID,
                    includeBlocked: Boolean,
                    kinds: Set<ApprovalKind>,
                    at: Instant,
                    after: UUID?,
                    limit: Int,
                ) =
                    expire(
                        approvals.inbox(
                            companyId,
                            accountId,
                            includeBlocked,
                            kinds,
                            at,
                            after,
                            limit,
                        )
                    )
            }
        val references =
            object : MembershipRepository by members {
                override fun activeReferences(
                    companyId: UUID,
                    permissions: Set<String>,
                    query: String,
                    after: UUID?,
                    limit: Int,
                    excludedAccountId: UUID?,
                ) =
                    expire(
                        members.activeReferences(
                            companyId,
                            permissions,
                            query,
                            after,
                            limit,
                            excludedAccountId,
                        )
                    )
            }
        val result: Result<*> =
            when (operation) {
                "request" ->
                    GetApprovalRequest(source, members, identities, transactions, clock, security)
                        .execute(actor, request)
                "templateCurrent" ->
                    GetApprovalTemplate(source, identities, members, transactions, clock, security)
                        .execute(actor, template, null)
                "templateRevision" ->
                    GetApprovalTemplate(source, identities, members, transactions, clock, security)
                        .execute(actor, template, 0)
                "templates" ->
                    ListApprovalTemplates(
                            source,
                            identities,
                            members,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor, ApprovalKind.LEAVE, LocalDate.of(2026, 10, 1), null, 50)
                "delegation" ->
                    GetApprovalDelegation(
                            source,
                            identities,
                            members,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor, delegation)
                "delegations" ->
                    ListMyDelegations(source, identities, members, transactions, clock, security)
                        .execute(actor, null, 50)
                "inbox" ->
                    ListApprovalInbox(source, identities, members, transactions, clock, security)
                        .execute(actor, null, 50)
                else ->
                    ListApprovalAssignees(identities, references, transactions, clock, security)
                        .execute(actor, ApprovalKind.LEAVE, "", null, 50)
            }
        assertEquals(1, acquisitions)
        assertTrue(result is Result.Failed, "An expired proof must not return acquired data")
        assertEquals("mfa_required", (result as Result.Failed).failure.code)
    }

    @ParameterizedTest
    @ValueSource(strings = ["request", "inbox"])
    fun aDelegationExpiringDuringAcquisitionCannotKeepAnApproverReadAdmitted(operation: String) {
        val f = leaveFixture()
        val id = approvalId(f.company, pending(f))
        val delegate = reviewer(f.company)
        assertEquals(
            200,
            saveDelegation(f, UUID.randomUUID(), delegationBody(f, delegate.account)).statusCode(),
        )
        val original = clock.instant()
        val actor =
            Actor(
                delegate.account,
                f.company,
                setOf("approvals.read", "leave.approve"),
                original,
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val source =
            object : ApprovalRepository by approvals {
                override fun inbox(
                    companyId: UUID,
                    accountId: UUID,
                    includeBlocked: Boolean,
                    kinds: Set<ApprovalKind>,
                    at: Instant,
                    after: UUID?,
                    limit: Int,
                ) =
                    approvals
                        .inbox(companyId, accountId, includeBlocked, kinds, at, after, limit)
                        .also {
                            assertEquals(id, (it as Result.Success).value.items.single().id)
                            clock.set(original.plusSeconds(3601))
                        }
            }
        val references =
            object : MembershipRepository by members {
                override fun candidates(
                    companyId: UUID,
                    accountIds: Set<UUID>,
                    permissions: Set<String>,
                    limit: Int,
                ) =
                    members.candidates(companyId, accountIds, permissions, limit).also {
                        clock.set(original.plusSeconds(3601))
                    }
            }
        val security = IdentitySecurityPolicy(enforceMfa = false)
        val result =
            if (operation == "request")
                GetApprovalRequest(source, references, identities, transactions, clock, security)
                    .execute(actor, id)
            else
                ListApprovalInbox(source, identities, references, transactions, clock, security)
                    .execute(actor, null, 50)
        assertTrue(
            result is Result.Failed,
            "An expired delegation must not return the acquired request",
        )
        assertEquals(
            if (operation == "request") "approval_not_found" else "approval_changed",
            (result as Result.Failed).failure.code,
        )
    }
}
