package dev.fajar.hris

import dev.fajar.hris.documents.domain.entities.DocumentClassification
import dev.fajar.hris.documents.domain.repositories.DocumentRepository
import dev.fajar.hris.leave.domain.repositories.LeaveLedgerRepository
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

class LeavePreparationProbe {
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
class LeavePreparationProbeConfiguration {
    @Bean fun leavePreparationProbe() = LeavePreparationProbe()

    @Bean
    @Primary
    fun preparationPeople(
        @Qualifier("people") source: PeopleRepository,
        probe: LeavePreparationProbe,
    ): PeopleRepository =
        object : PeopleRepository by source {
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
                source
                    .list(companyId, accountId, visibility, asOf, accessAt, query, after, limit)
                    .also { probe.afterRead(companyId, "employees") }
        }

    @Bean
    @Primary
    fun preparationDocuments(
        @Qualifier("documents") source: DocumentRepository,
        probe: LeavePreparationProbe,
    ): DocumentRepository =
        object : DocumentRepository by source {
            override fun published(
                companyId: UUID,
                employmentId: UUID,
                classification: DocumentClassification,
                after: UUID?,
                limit: Int,
            ) =
                source.published(companyId, employmentId, classification, after, limit).also {
                    probe.afterRead(companyId, "evidence")
                }
        }

    @Bean
    @Primary
    fun preparationLedger(
        @Qualifier("leaveLedger") source: LeaveLedgerRepository,
        probe: LeavePreparationProbe,
    ): LeaveLedgerRepository =
        object : LeaveLedgerRepository by source {
            override fun balance(companyId: UUID, employeeId: UUID, typeId: UUID, year: Int) =
                source.balance(companyId, employeeId, typeId, year).also {
                    probe.afterRead(companyId, "preview")
                }
        }
}
