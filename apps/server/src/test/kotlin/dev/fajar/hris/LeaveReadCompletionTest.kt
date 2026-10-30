package dev.fajar.hris

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.repositories.PayrollCutoffRepository
import dev.fajar.hris.people.domain.entities.EmployeeVisibility
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class LeaveReadCompletionTest : LeaveAttachmentApiFixture() {
    @Autowired private lateinit var requests: LeaveRequestRepository
    @Autowired private lateinit var policies: LeavePolicyRepository
    @Autowired private lateinit var ledger: LeaveLedgerRepository
    @Autowired private lateinit var approvals: ApprovalRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var documents: DocumentRepository
    @Autowired private lateinit var cutoffs: PayrollCutoffRepository
    @Autowired
    private lateinit var storage: dev.fajar.hris.storage.domain.repositories.ObjectStorageRepository

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "catalog",
                "policy",
                "types",
                "employments",
                "requests",
                "details",
                "self_details",
                "download",
                "content_preparation",
                "content_completion",
            ]
    )
    fun acquiredLeaveDataRequiresCurrentAssuranceAtCompletion(mode: String) {
        val f = leaveFixture(Instant.now())
        val attachment = mode == "download" || mode.startsWith("content_")
        val needsRequest = attachment || mode in setOf("requests", "details", "self_details")
        val requestId = UUID.randomUUID()
        var revision = UUID.randomUUID()
        if (needsRequest) {
            configureWorkAndApprovals(f)
            body(adjust(f, "5"))
            val evidence =
                if (attachment) listOf(evidence(f).also { revision = it }) else emptyList()
            body(submitEvidence(f, requestId, evidence))
        }
        val permissions = setOf("company.read", "leave.self.manage", "leave.manage")
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'leave.manage')",
                f.company,
                f.account,
            )
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.account,
            )
        val security = IdentitySecurityPolicy(enforceMfa = true)
        val original = clock.instant()
        val actor =
            Actor(
                f.account,
                f.company,
                permissions,
                original,
                UUID.randomUUID(),
                credentialVersion = 0,
                mfaVerifiedAt = original.minus(security.maximumMfaAge).plusSeconds(1),
            )
        var acquisitions = 0
        fun <T> acquired(value: Result<T>): Result<T> {
            assertTrue(value is Result.Success, value.toString())
            acquisitions++
            clock.set(original.plusSeconds(2))
            return value
        }
        val policySource =
            object : LeavePolicyRepository by policies {
                override fun catalog(
                    companyId: UUID,
                    active: Boolean?,
                    after: String?,
                    limit: Int,
                ) = acquired(policies.catalog(companyId, active, after, limit))

                override fun history(companyId: UUID, id: UUID, after: Long?, limit: Int) =
                    acquired(policies.history(companyId, id, after, limit))

                override fun list(companyId: UUID, asOf: LocalDate, after: String?, limit: Int) =
                    acquired(policies.list(companyId, asOf, after, limit))
            }
        val peopleSource =
            object : PeopleRepository by people {
                override fun list(
                    companyId: UUID,
                    accountId: UUID,
                    visibility: EmployeeVisibility,
                    asOf: LocalDate,
                    accessAt: Instant,
                    query: String,
                    after: String?,
                    limit: Int,
                ) =
                    acquired(
                        people.list(
                            companyId,
                            accountId,
                            visibility,
                            asOf,
                            accessAt,
                            query,
                            after,
                            limit,
                        )
                    )
            }
        val requestSource =
            object : LeaveRequestRepository by requests {
                override fun history(companyId: UUID, id: UUID, after: Long?, limit: Int) =
                    acquired(requests.history(companyId, id, after, limit))

                override fun list(
                    companyId: UUID,
                    employeeId: UUID?,
                    status: LeaveStatus?,
                    after: UUID?,
                    limit: Int,
                ) = acquired(requests.list(companyId, employeeId, status, after, limit))
            }
        var revisionReads = 0
        val documentSource =
            object : DocumentRepository by documents {
                override fun revision(companyId: UUID, id: UUID) =
                    documents.revision(companyId, id).let {
                        revisionReads++
                        if (
                            mode == "download" ||
                                (mode == "content_completion" && revisionReads % 2 == 0)
                        )
                            acquired(it)
                        else it
                    }

                override fun chunkAt(companyId: UUID, revisionId: UUID, ordinal: Int) =
                    documents.chunkAt(companyId, revisionId, ordinal).let {
                        if (mode == "content_preparation") acquired(it) else it
                    }
            }
        var objectReads = 0
        val buffers = mutableListOf<ByteArray>()
        val observedStorage =
            object : dev.fajar.hris.storage.domain.repositories.ObjectStorageRepository by storage {
                override fun read(key: String, offset: Long, length: Int, etag: String) =
                    storage.read(key, offset, length, etag).also {
                        objectReads++
                        if (it is Result.Success) buffers += it.value
                    }
            }
        fun read(selected: Actor): Result<*> =
            when (mode) {
                "catalog" ->
                    ListLeavePolicies(
                            policySource,
                            identities,
                            members,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(selected, null, null, 20)
                "policy" ->
                    GetLeavePolicy(policySource, identities, members, transactions, clock, security)
                        .execute(selected, f.type, null, 20)
                "types" ->
                    ListLeaveTypes(policySource, transactions, identities, members, clock, security)
                        .execute(selected, LocalDate.parse("2026-10-05"), null, 20)
                "employments" ->
                    ListSelfLeaveEmployments(
                            peopleSource,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(selected, null, 20)
                "requests" ->
                    ListLeaveRequests(
                            requestSource,
                            people,
                            transactions,
                            clock,
                            identities,
                            members,
                            security,
                        )
                        .execute(selected, f.employee, null, null, 20)
                "details",
                "self_details" ->
                    GetLeaveRequest(
                            requestSource,
                            ledger,
                            people,
                            approvals,
                            members,
                            identities,
                            transactions,
                            clock,
                            cutoffs,
                            security,
                        )
                        .execute(
                            selected,
                            requestId,
                            null,
                            20,
                            if (mode == "self_details") LeaveRequestReadScope.SELF
                            else LeaveRequestReadScope.AUTHORIZED,
                        )
                "download" ->
                    GetLeaveAttachmentDownload(
                            requests,
                            documentSource,
                            people,
                            approvals,
                            companies,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(selected, requestId, revision)
                else ->
                    ReadLeaveAttachmentContent(
                            requests,
                            documentSource,
                            people,
                            approvals,
                            companies,
                            members,
                            identities,
                            transactions,
                            clock,
                            observedStorage,
                            security,
                        )
                        .execute(selected, requestId, revision, 0, pdf.size)
            }
        fun state() =
            listOf(
                    "leave_requests",
                    "leave_ledger",
                    "audit_entries",
                    "outbox_events",
                    "operation_receipts",
                )
                .associateWith {
                    database()
                        .queryForObject(
                            "select count(*) from $it where company_id=?",
                            Long::class.java,
                            f.company,
                        )
                }
        val before = state()
        try {
            val first = read(actor.copy(mfaVerifiedAt = original))
            assertTrue(first is Result.Success, first.toString())
            assertEquals(1, acquisitions)
            if (mode.startsWith("content_"))
                assertArrayEquals(pdf, (first as Result.Success).value as ByteArray)
            objectReads = 0
            clock.set(original)
            val expired = read(actor)
            assertEquals(2, acquisitions)
            assertEquals(
                if (mode == "content_completion") 1 else 0,
                objectReads,
                "Expired preparation must not start object I/O",
            )
            assertEquals(
                "mfa_required",
                (expired as? Result.Failed)?.failure?.code,
                "Expired assurance released Leave data",
            )
            val renewed = read(actor.copy(mfaVerifiedAt = clock.instant()))
            assertTrue(renewed is Result.Success, renewed.toString())
            assertEquals(3, acquisitions)
            if (mode.startsWith("content_"))
                assertArrayEquals(pdf, (renewed as Result.Success).value as ByteArray)
            assertEquals(before, state())
            assertTrue(
                buffers.all { bytes -> bytes.all { it == 0.toByte() } },
                "Owned storage buffers must be cleared",
            )
        } finally {
            clock.set(Instant.now())
        }
    }
}
