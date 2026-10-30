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
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.storage.domain.repositories.ObjectStorageRepository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class LeaveDelegationCompletionTest : LeaveAttachmentApiFixture() {
    @Autowired private lateinit var requests: LeaveRequestRepository
    @Autowired private lateinit var ledger: LeaveLedgerRepository
    @Autowired private lateinit var approvals: ApprovalRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var documents: DocumentRepository
    @Autowired private lateinit var cutoffs: PayrollCutoffRepository
    @Autowired private lateinit var storage: ObjectStorageRepository

    @ParameterizedTest
    @CsvSource(
        "details,false",
        "download,false",
        "preparation,false",
        "completion,false",
        "details,true",
        "download,true",
        "preparation,true",
        "completion,true",
    )
    fun delegationMustRemainCurrentThroughDataAndActionCompletion(mode: String, ordinary: Boolean) {
        val f = preparedLeave()
        val revision = evidence(f)
        val id = UUID.randomUUID()
        body(submitEvidence(f, id, listOf(revision)))
        val permissions =
            setOf("company.read", "approvals.read", "leave.approve") +
                if (ordinary) setOf("leave.read") else emptySet()
        val recipient = member(f, permissions.toList()).first
        val until = clock.instant().plusSeconds(1)
        val delegation = UUID.randomUUID()
        fun delegate(version: Long?, deadline: Instant) {
            body(
                command(
                    f.supervisor,
                    "/api/v1/companies/${f.company}/approvals/delegations/$delegation",
                    json.writeValueAsString(
                        mapOf(
                            "kind" to "LEAVE",
                            "fromAccount" to f.managerAccount,
                            "toAccount" to recipient,
                            "validFrom" to clock.instant().minusSeconds(60).toString(),
                            "validUntil" to deadline.toString(),
                            "active" to true,
                            "expectedVersion" to version,
                            "reason" to "Reviewed coverage",
                        )
                    ),
                    f.supervisorCsrf,
                    UUID.randomUUID(),
                    "PUT",
                )
            )
        }
        delegate(null, until)
        val actor =
            Actor(
                recipient,
                f.company,
                permissions,
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val security = IdentitySecurityPolicy(enforceMfa = false)
        var expire = false
        var revisions = 0
        var reads = 0
        val buffers = mutableListOf<ByteArray>()
        val observedRequests =
            object : LeaveRequestRepository by requests {
                override fun history(companyId: UUID, id: UUID, after: Long?, limit: Int) =
                    requests.history(companyId, id, after, limit).also {
                        if (expire && mode == "details") clock.set(until.plusSeconds(1))
                    }
            }
        val observedDocuments =
            object : DocumentRepository by documents {
                override fun revision(companyId: UUID, id: UUID) =
                    documents.revision(companyId, id).also {
                        revisions++
                        if (
                            expire &&
                                (mode == "download" || (mode == "completion" && revisions == 2))
                        )
                            clock.set(until.plusSeconds(1))
                    }

                override fun chunkAt(companyId: UUID, revisionId: UUID, ordinal: Int) =
                    documents.chunkAt(companyId, revisionId, ordinal).also {
                        if (expire && mode == "preparation") clock.set(until.plusSeconds(1))
                    }
            }
        val observedStorage =
            object : ObjectStorageRepository by storage {
                override fun read(key: String, offset: Long, length: Int, etag: String) =
                    storage.read(key, offset, length, etag).also {
                        reads++
                        if (it is Result.Success) buffers += it.value
                    }
            }
        fun invoke(): Result<*> =
            when (mode) {
                "details" ->
                    GetLeaveRequest(
                            observedRequests,
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
                        .execute(actor, id, null, 20)
                "download" ->
                    GetLeaveAttachmentDownload(
                            requests,
                            observedDocuments,
                            people,
                            approvals,
                            companies,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor, id, revision)
                else ->
                    ReadLeaveAttachmentContent(
                            requests,
                            observedDocuments,
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
                        .execute(actor, id, revision, 0, pdf.size)
            }
        fun counts() =
            listOf("leave_ledger", "audit_entries", "outbox_events", "operation_receipts")
                .associateWith {
                    database()
                        .queryForObject(
                            "select count(*) from $it where company_id=?",
                            Long::class.java,
                            f.company,
                        )
                }
        val before = counts()
        try {
            val original = invoke()
            assertTrue(original is Result.Success, original.toString())
            if (mode == "details")
                assertTrue(
                    LeaveAction.DECIDE in
                        ((original as Result.Success).value as LeaveRequestDetails).availableActions
                )
            revisions = 0
            reads = 0
            expire = true
            val result = invoke()
            if (ordinary) {
                assertTrue(result is Result.Success, result.toString())
                if (mode == "details")
                    assertFalse(
                        LeaveAction.DECIDE in
                            ((result as Result.Success).value as LeaveRequestDetails)
                                .availableActions,
                        "Ordinary read access must not retain an expired decision action",
                    )
                if (mode in setOf("preparation", "completion"))
                    assertArrayEquals(pdf, (result as Result.Success).value as ByteArray)
            } else {
                assertEquals(
                    if (mode == "completion") 1 else 0,
                    reads,
                    "Expired preparation must not start storage",
                )
                assertEquals(
                    if (mode == "details") "leave_request_not_found"
                    else "leave_attachment_not_found",
                    (result as? Result.Failed)?.failure?.code,
                )
            }
            assertEquals(before, counts())
            assertTrue(buffers.all { bytes -> bytes.all { it == 0.toByte() } })
            expire = false
            delegate(0, clock.instant().plusSeconds(60))
            val renewed = invoke()
            assertTrue(renewed is Result.Success, renewed.toString())
            if (mode == "details")
                assertTrue(
                    LeaveAction.DECIDE in
                        ((renewed as Result.Success).value as LeaveRequestDetails).availableActions
                )
        } finally {
            clock.set(Instant.now())
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["details", "download", "preparation", "completion"])
    fun delegatedReadsAcquireTheDelegatorAccountBeforeReadingItsCurrentGrant(mode: String) {
        val f = preparedLeave()
        val revision = evidence(f)
        val id = UUID.randomUUID()
        body(submitEvidence(f, id, listOf(revision)))
        val permissions = setOf("company.read", "approvals.read", "leave.approve")
        val recipient = member(f, permissions.toList()).first
        body(
            command(
                f.supervisor,
                "/api/v1/companies/${f.company}/approvals/delegations/${UUID.randomUUID()}",
                json.writeValueAsString(
                    mapOf(
                        "kind" to "LEAVE",
                        "fromAccount" to f.managerAccount,
                        "toAccount" to recipient,
                        "validFrom" to clock.instant().minusSeconds(60).toString(),
                        "validUntil" to clock.instant().plusSeconds(3600).toString(),
                        "active" to true,
                        "reason" to "Current account grant",
                    )
                ),
                f.supervisorCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        val actor =
            Actor(
                recipient,
                f.company,
                permissions,
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val security = IdentitySecurityPolicy(enforceMfa = false)
        var objectReads = 0
        val buffers = mutableListOf<ByteArray>()
        val observedStorage =
            object : ObjectStorageRepository by storage {
                override fun read(key: String, offset: Long, length: Int, etag: String) =
                    storage.read(key, offset, length, etag).also {
                        objectReads++
                        if (it is Result.Success) buffers += it.value
                    }
            }
        fun invoke(): Result<*> =
            when (mode) {
                "details" ->
                    GetLeaveRequest(
                            requests,
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
                        .execute(actor, id, null, 20)
                "download" ->
                    GetLeaveAttachmentDownload(
                            requests,
                            documents,
                            people,
                            approvals,
                            companies,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor, id, revision)
                else ->
                    ReadLeaveAttachmentContent(
                            requests,
                            documents,
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
                        .execute(actor, id, revision, 0, pdf.size)
            }
        val barrier =
            AccountLockProbe.Barrier(
                f.managerAccount,
                occurrence = if (mode == "completion") 2 else 1,
            )
        accountProbe.current.set(barrier)
        try {
            Executors.newSingleThreadExecutor().use { pool ->
                val pending = pool.submit<Result<*>> { invoke() }
                try {
                    assertTrue(
                        barrier.entered.await(5, TimeUnit.SECONDS),
                        "The relevant delegator account guard must be acquired",
                    )
                    database()
                        .update("update accounts set active=false where id=?", f.managerAccount)
                    barrier.release.countDown()
                    val result = pending.get(10, TimeUnit.SECONDS)
                    assertEquals(
                        if (mode == "details") "leave_request_not_found"
                        else "leave_attachment_not_found",
                        (result as? Result.Failed)?.failure?.code,
                    )
                    assertEquals(if (mode == "completion") 1 else 0, objectReads)
                    assertTrue(buffers.all { bytes -> bytes.all { it == 0.toByte() } })
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
        } finally {
            barrier.release.countDown()
            accountProbe.current.set(null)
            database().update("update accounts set active=true where id=?", f.managerAccount)
        }
        val renewed = invoke()
        assertTrue(renewed is Result.Success, renewed.toString())
        if (mode in setOf("preparation", "completion"))
            assertArrayEquals(pdf, (renewed as Result.Success).value as ByteArray)
    }

    @ParameterizedTest
    @CsvSource(
        "details,ordinary",
        "details,direct",
        "details,indirect",
        "download,direct",
        "preparation,direct",
        "completion,direct",
    )
    fun excessDelegationsPreserveIndependentReadScope(mode: String, scope: String) {
        val f = preparedLeave()
        val revision = evidence(f)
        val id = UUID.randomUUID()
        body(submitEvidence(f, id, listOf(revision)))
        val permissions =
            setOf("company.read", "approvals.read", "leave.approve") +
                if (scope == "ordinary") setOf("leave.read") else emptySet()
        val recipient = member(f, permissions.toList()).first
        if (scope == "direct") {
            val approval =
                database()
                    .queryForObject(
                        "select approval_id from leave_requests where company_id=? and id=?",
                        UUID::class.java,
                        f.company,
                        id,
                    )!!
            body(
                command(
                    f.admin,
                    "/api/v1/companies/${f.company}/approvals/$approval/reassign",
                    json.writeValueAsString(
                        mapOf(
                            "version" to 0,
                            "assignees" to listOf(recipient),
                            "reason" to "Direct review",
                        )
                    ),
                    f.adminCsrf,
                    UUID.randomUUID(),
                )
            )
        }
        fun overflow() {
            database()
                .update(
                    """insert into approval_delegations(company_id,id,kind,from_account,to_account,valid_from,valid_until,active)
                select ?,gen_random_uuid(),'LEAVE',?,?,?::timestamptz,?::timestamptz,true from generate_series(1,201)""",
                    f.company,
                    f.managerAccount,
                    recipient,
                    clock.instant().minusSeconds(60).toString(),
                    clock.instant().plusSeconds(3600).toString(),
                )
        }
        if (mode != "completion") overflow()
        val actor =
            Actor(
                recipient,
                f.company,
                permissions,
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val security = IdentitySecurityPolicy(enforceMfa = false)
        val buffers = mutableListOf<ByteArray>()
        var reads = 0
        val observedStorage =
            object : ObjectStorageRepository by storage {
                override fun read(key: String, offset: Long, length: Int, etag: String) =
                    storage.read(key, offset, length, etag).also {
                        reads++
                        if (it is Result.Success) buffers += it.value
                        if (mode == "completion") overflow()
                    }
            }
        val result =
            when (mode) {
                "details" ->
                    GetLeaveRequest(
                            requests,
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
                        .execute(actor, id, null, 20)
                "download" ->
                    GetLeaveAttachmentDownload(
                            requests,
                            documents,
                            people,
                            approvals,
                            companies,
                            members,
                            identities,
                            transactions,
                            clock,
                            security,
                        )
                        .execute(actor, id, revision)
                else ->
                    ReadLeaveAttachmentContent(
                            requests,
                            documents,
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
                        .execute(actor, id, revision, 0, pdf.size)
            }
        if (scope == "indirect") {
            assertEquals("leave_request_not_found", (result as? Result.Failed)?.failure?.code)
        } else {
            assertTrue(result is Result.Success, result.toString())
            val value = (result as Result.Success).value
            if (mode == "details")
                assertFalse(LeaveAction.DECIDE in (value as LeaveRequestDetails).availableActions)
            if (mode in setOf("preparation", "completion")) {
                assertArrayEquals(pdf, value as ByteArray)
                assertEquals(1, reads)
                assertTrue(buffers.all { bytes -> bytes.all { it == 0.toByte() } })
            }
        }
    }
}
