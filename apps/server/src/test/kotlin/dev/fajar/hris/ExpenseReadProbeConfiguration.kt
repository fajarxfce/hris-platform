package dev.fajar.hris

import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.expenses.domain.entities.ExpenseClaimStatus
import dev.fajar.hris.expenses.domain.repositories.*
import dev.fajar.hris.people.domain.entities.EmployeeVisibility
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class ExpenseReadProbe {
    data class Barrier(
        val company: UUID,
        val kind: String,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    val current = AtomicReference<Barrier?>()

    fun afterRead(company: UUID, kind: String) {
        val barrier = current.get()
        if (
            barrier != null &&
                barrier.company == company &&
                barrier.kind == kind &&
                current.compareAndSet(barrier, null)
        ) {
            barrier.entered.countDown()
            check(barrier.release.await(5, TimeUnit.SECONDS))
        }
    }
}

@TestConfiguration(proxyBeanMethods = false)
class ExpenseReadProbeConfiguration {
    @Bean fun expenseReadProbe() = ExpenseReadProbe()

    @Bean
    @Primary
    fun probedExpenseClaims(
        @Qualifier("expenseClaims") delegate: ExpenseClaimRepository,
        probe: ExpenseReadProbe,
    ): ExpenseClaimRepository =
        object : ExpenseClaimRepository by delegate {
            override fun find(companyId: UUID, id: UUID) =
                delegate.find(companyId, id).also { probe.afterRead(companyId, "claim-head") }

            override fun list(
                companyId: UUID,
                employmentId: UUID?,
                status: ExpenseClaimStatus?,
                from: Instant,
                until: Instant,
                after: UUID?,
                limit: Int,
            ) =
                delegate.list(companyId, employmentId, status, from, until, after, limit).also {
                    probe.afterRead(companyId, "list")
                }

            override fun draft(companyId: UUID, id: UUID, revision: Int) =
                delegate.draft(companyId, id, revision).also {
                    probe.afterRead(companyId, "details")
                }

            override fun drafts(companyId: UUID, id: UUID, after: Int?, limit: Int) =
                delegate.drafts(companyId, id, after, limit).also {
                    probe.afterRead(companyId, "drafts")
                }

            override fun history(companyId: UUID, id: UUID, after: Long?, limit: Int) =
                delegate.history(companyId, id, after, limit).also {
                    probe.afterRead(companyId, "history")
                }

            override fun submissions(companyId: UUID, claimId: UUID, after: Int?, limit: Int) =
                delegate.submissions(companyId, claimId, after, limit).also {
                    probe.afterRead(companyId, "submissions")
                }

            override fun duplicateReceiptDigests(
                companyId: UUID,
                exceptClaimId: UUID,
                digests: Set<String>,
            ) =
                delegate.duplicateReceiptDigests(companyId, exceptClaimId, digests).also {
                    probe.afterRead(companyId, "submission")
                }
        }

    @Bean
    @Primary
    fun probedExpensePayments(
        @Qualifier("expensePaymentRepository") delegate: ExpensePaymentRepository,
        probe: ExpenseReadProbe,
    ): ExpensePaymentRepository =
        object : ExpensePaymentRepository by delegate {
            override fun progress(companyId: UUID, claimId: UUID) =
                delegate.progress(companyId, claimId).also {
                    probe.afterRead(companyId, "payments")
                }
        }

    @Bean
    @Primary
    fun probedExpensePolicies(
        @Qualifier("expensePolicies") delegate: ExpensePolicyRepository,
        probe: ExpenseReadProbe,
    ): ExpensePolicyRepository =
        object : ExpensePolicyRepository by delegate {
            override fun find(companyId: UUID, id: UUID) =
                delegate.find(companyId, id).also { probe.afterRead(companyId, "policy-head") }

            override fun catalog(companyId: UUID, active: Boolean?, after: String?, limit: Int) =
                delegate.catalog(companyId, active, after, limit).also {
                    probe.afterRead(companyId, "policy-catalog")
                }

            override fun list(companyId: UUID, asOf: LocalDate, after: String?, limit: Int) =
                delegate.list(companyId, asOf, after, limit).also {
                    probe.afterRead(companyId, "categories")
                }

            override fun history(companyId: UUID, id: UUID, after: Long?, limit: Int) =
                delegate.history(companyId, id, after, limit).also {
                    probe.afterRead(companyId, "category-history")
                }
        }

    @Bean
    @Primary
    fun probedExpensePeople(
        @Qualifier("people") delegate: PeopleRepository,
        probe: ExpenseReadProbe,
    ): PeopleRepository =
        object : PeopleRepository by delegate {
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
                delegate
                    .list(companyId, accountId, visibility, asOf, accessAt, query, after, limit)
                    .also { probe.afterRead(companyId, "directory") }
        }

    @Bean
    @Primary
    fun probedExpenseDocuments(
        @Qualifier("documents") delegate: DocumentRepository,
        probe: ExpenseReadProbe,
    ): DocumentRepository =
        object : DocumentRepository by delegate {
            override fun revision(companyId: UUID, id: UUID) =
                delegate.revision(companyId, id).also {
                    probe.afterRead(companyId, "receipt-metadata")
                }
        }
}
