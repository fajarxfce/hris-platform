package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.payroll.domain.usecases.*
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(PayrollFinalizationProbeConfiguration::class)
abstract class PayrollFinalizationApiFixture : PayrollReviewApiFixture() {
    @Autowired protected lateinit var finalizationProbe: PayrollFinalizationProbe
    @Autowired protected lateinit var advanceFinalization: AdvancePayrollFinalization
    @Autowired protected lateinit var abortFinalization: AbortPayrollFinalization
    @Autowired protected lateinit var startFinalizationUseCase: StartPayrollFinalization

    protected data class PublicationFixture(
        val calculation: CalculationFixture,
        val run: UUID,
        val review: UUID,
        val finalizer: PayrollMember,
    )

    @AfterEach
    fun resetFinalizationProbe() {
        finalizationProbe.clear()
    }

    protected fun approved(f: CalculationFixture = calculationFixture()): PublicationFixture {
        val run = calculated(f)
        val review = pendingReview(f, run)
        payrollBody(decideReview(f, review))
        return PublicationFixture(
            f,
            run,
            review,
            payrollMember(f.people.payroll.company, setOf("company.read", "payroll.finalize")),
        )
    }

    protected fun finalizationsPath(f: PublicationFixture) =
        "/api/v1/companies/${f.calculation.people.payroll.company}/payroll/finalizations"

    protected fun finalizationBody(
        f: PublicationFixture,
        id: UUID = UUID.randomUUID(),
        changes: Map<String, Any?> = emptyMap(),
    ) =
        json.writeValueAsString(
            mapOf(
                "id" to id,
                "reviewId" to f.review,
                "expectedRunVersion" to 1,
                "expectedReviewVersion" to 1,
                "expectedApprovalVersion" to 1,
                "reason" to "Publish approved September payroll",
            ) + changes
        )

    protected fun startFinalization(
        f: PublicationFixture,
        body: String = finalizationBody(f),
        key: UUID = UUID.randomUUID(),
        member: PayrollMember = f.finalizer,
    ) =
        command(
            member.client,
            runPath(f.calculation, f.run) + "/finalizations",
            body,
            member.csrf,
            key,
        )

    protected fun finalizationView(
        f: PublicationFixture,
        id: UUID,
        member: PayrollMember = f.finalizer,
    ) = payrollBody(get(member.client, finalizationsPath(f) + "/$id"))

    protected fun beginFinalization(f: PublicationFixture): JobLease {
        val id = UUID.fromString(payrollBody(startFinalization(f))["id"].asString())
        return claimPayrollJobs(JobKind.PAYROLL_FINALIZE).single {
            it.job.request.values["finalizationId"] == id.toString()
        }
    }

    protected fun finalizationId(lease: JobLease) =
        UUID.fromString(lease.job.request.values.getValue("finalizationId"))

    protected fun stepFinalization(
        f: PublicationFixture,
        lease: JobLease,
        actor: Actor = payrollActor(f.calculation.people.payroll, f.finalizer),
    ) = advanceFinalization.execute(actor, lease)

    protected fun cancelFinalization(f: PublicationFixture, lease: JobLease) {
        val job = finalizationView(f, finalizationId(lease))["job"]
        payrollBody(
            command(
                f.finalizer.client,
                "/api/v1/companies/${f.calculation.people.payroll.company}/jobs/${lease.job.request.id}/cancel",
                json.writeValueAsString(mapOf("expectedVersion" to job["version"].asLong())),
                f.finalizer.csrf,
                UUID.randomUUID(),
            )
        )
    }
}
