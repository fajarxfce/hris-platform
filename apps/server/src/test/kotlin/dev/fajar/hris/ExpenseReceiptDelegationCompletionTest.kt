package dev.fajar.hris

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.expenses.domain.repositories.ExpenseClaimRepository
import dev.fajar.hris.expenses.domain.usecases.GetExpenseReceiptDownload
import dev.fajar.hris.expenses.domain.usecases.ReadExpenseReceiptContent
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
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
import org.springframework.context.annotation.Import

@Import(AccountLockProbeConfiguration::class)
class ExpenseReceiptDelegationCompletionTest : ExpenseReviewApiFixture() {
    @Autowired private lateinit var accountProbe: AccountLockProbe
    @Autowired private lateinit var claims: ExpenseClaimRepository
    @Autowired private lateinit var approvals: ApprovalRepository
    @Autowired private lateinit var documents: DocumentRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var storage: ObjectStorageRepository

    @ParameterizedTest
    @ValueSource(strings = ["metadata", "preparation", "completion"])
    fun delegatedEvidenceRechecksTheGrantAfterWaitingForTheDelegatorAccount(mode: String) {
        val f = expenseFixture()
        expenseTemplate(f)
        val revision = readyReceipt(f)
        val submission = pendingExpense(f, readyLines(f, revision))
        val permissions = setOf("company.read", "approvals.read", "expenses.approve")
        val recipient = expenseMember(f.company, permissions.toList())
        expenseDelegation(f, reviewer(f), recipient)
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
        val observed =
            object : ObjectStorageRepository by storage {
                override fun read(key: String, offset: Long, length: Int, etag: String) =
                    storage.read(key, offset, length, etag).also { objectReads++ }
            }
        fun invoke(): Result<*> =
            if (mode == "metadata")
                GetExpenseReceiptDownload(
                        claims,
                        documents,
                        approvals,
                        companies,
                        people,
                        identities,
                        members,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, submission, revision)
            else
                ReadExpenseReceiptContent(
                        claims,
                        documents,
                        approvals,
                        companies,
                        people,
                        identities,
                        members,
                        transactions,
                        clock,
                        observed,
                        security,
                    )
                    .execute(actor, submission, revision, 0, pdf.size)
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
                    assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                    database()
                        .update("update accounts set active=false where id=?", f.managerAccount)
                    barrier.release.countDown()
                    val denied = pending.get(10, TimeUnit.SECONDS)
                    assertTrue(denied is Result.Failed, denied.toString())
                    assertEquals(
                        "expense_receipt_not_found",
                        (denied as Result.Failed).failure.code,
                    )
                    assertEquals(if (mode == "completion") 1 else 0, objectReads)
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
        if (mode != "metadata")
            assertArrayEquals(pdf, (renewed as Result.Success).value as ByteArray)
    }

    @ParameterizedTest
    @CsvSource(
        "metadata,false",
        "preparation,false",
        "completion,false",
        "metadata,true",
        "preparation,true",
        "completion,true",
    )
    fun delegationExpiryCannotReleaseEvidenceUnlessOrdinaryClaimScopeStillPermitsIt(
        mode: String,
        ordinaryScope: Boolean,
    ) {
        val f = expenseFixture()
        expenseTemplate(f)
        val revision = readyReceipt(f)
        val submission = pendingExpense(f, readyLines(f, revision))
        val permissions =
            setOf("company.read", "approvals.read", "expenses.approve") +
                if (ordinaryScope) setOf("expenses.read") else emptySet()
        val recipient = expenseMember(f.company, permissions.toList())
        val until = clock.instant().plusSeconds(1)
        val delegation = expenseDelegation(f, reviewer(f), recipient, until = until)
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
        var revisionReads = 0
        var storageReads = 0
        var expire = false
        val buffers = mutableListOf<ByteArray>()
        val observedDocuments =
            object : DocumentRepository by documents {
                override fun revision(companyId: UUID, id: UUID) =
                    documents.revision(companyId, id).also {
                        revisionReads++
                        if (
                            expire &&
                                ((mode == "metadata" && revisionReads == 1) ||
                                    (mode == "completion" && revisionReads == 2))
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
                        storageReads++
                        if (it is Result.Success) buffers += it.value
                    }
            }
        fun invoke(): Result<*> =
            if (mode == "metadata")
                GetExpenseReceiptDownload(
                        claims,
                        observedDocuments,
                        approvals,
                        companies,
                        people,
                        identities,
                        members,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, submission, revision)
            else
                ReadExpenseReceiptContent(
                        claims,
                        observedDocuments,
                        approvals,
                        companies,
                        people,
                        identities,
                        members,
                        transactions,
                        clock,
                        observedStorage,
                        security,
                    )
                    .execute(actor, submission, revision, 0, pdf.size)
        fun counts() =
            listOf("audit_entries", "outbox_events", "operation_receipts").associateWith {
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
            revisionReads = 0
            storageReads = 0
            expire = true
            val result = invoke()
            if (ordinaryScope) {
                assertTrue(result is Result.Success, result.toString())
                if (mode != "metadata")
                    assertArrayEquals(pdf, (result as Result.Success).value as ByteArray)
            } else {
                assertEquals(
                    if (mode == "completion") 1 else 0,
                    storageReads,
                    "Expired preparation must not start object I/O",
                )
                assertTrue(result is Result.Failed, "Expired delegation returned receipt evidence")
                assertEquals("expense_receipt_not_found", (result as Result.Failed).failure.code)
            }
            assertEquals(before, counts())
            assertTrue(buffers.all { bytes -> bytes.all { it == 0.toByte() } })
            expire = false
            expenseDelegation(
                f,
                reviewer(f),
                recipient,
                id = delegation,
                version = 0,
                until = clock.instant().plusSeconds(60),
            )
            val renewed = invoke()
            assertTrue(renewed is Result.Success, renewed.toString())
            if (mode != "metadata")
                assertArrayEquals(pdf, (renewed as Result.Success).value as ByteArray)
        } finally {
            clock.set(Instant.now())
        }
    }
}
