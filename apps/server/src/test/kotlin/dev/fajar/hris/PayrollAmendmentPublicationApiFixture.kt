package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.payroll.domain.usecases.*
import java.math.BigDecimal
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(PayrollAmendmentPublicationProbeConfiguration::class)
abstract class PayrollAmendmentPublicationApiFixture : PayrollAmendmentReviewApiFixture() {
    @Autowired
    protected lateinit var publicationJobs: dev.fajar.hris.jobs.domain.repositories.JobRepository
    @Autowired protected lateinit var publishAmendment: AdvancePayrollAmendmentPublication
    @Autowired protected lateinit var abortAmendment: AbortPayrollAmendmentPublication
    @Autowired protected lateinit var startAmendmentPublication: StartPayrollAmendmentPublication
    @Autowired
    protected lateinit var recoveryAmendmentPublication: RecoverPayrollAmendmentPublication
    @Autowired protected lateinit var publicationProbe: PayrollAmendmentPublicationProbe

    @AfterEach fun clearPublicationProbe() = publicationProbe.clear()

    protected data class AmendmentPublicationScene(
        val review: AmendmentReviewScene,
        val publisher: PayrollMember,
        val id: UUID = UUID.randomUUID(),
        val operation: UUID = UUID.randomUUID(),
    ) {
        val source
            get() = review.draft.source
    }

    protected fun publicationScene(
        source: AmendmentFixture = amendmentFixture(),
        difference: String = "1000000",
        previousReview: AmendmentReviewScene? = null,
        corrections: List<Map<String, Any?>>? = null,
    ): AmendmentPublicationScene {
        val preparation = amendmentPreparation(source)
        val draft =
            DraftFixture(
                source,
                UUID.randomUUID(),
                UUID.randomUUID(),
                draftBody(
                    source,
                    preparation,
                    changes =
                        corrections ?: listOf(remuneration(preparation, difference = difference)),
                ),
            )
        payrollBody(saveDraft(source, draft.id, draft.body, draft.operation))
        val scene =
            if (previousReview == null) amendmentReviewScene(draft)
            else {
                payrollBody(draftCommand(draft, "calculate", 0))
                previousReview
                    .copy(
                        draft = draft,
                        id = UUID.randomUUID(),
                        operation = UUID.randomUUID(),
                        calculationId =
                            UUID.fromString(draftView(draft)["latestCalculation"]["id"].asString()),
                    )
                    .also { payrollBody(submitAmendmentReview(it)) }
            }
        payrollBody(decideAmendmentReview(scene))
        return AmendmentPublicationScene(
            scene,
            payrollMember(source.payroll.company, setOf("company.read", "payroll.amend.publish")),
        )
    }

    protected fun publicationPath(scene: AmendmentPublicationScene) =
        draftsPath(scene.source) + "/${scene.review.draft.id}/publications"

    protected fun publicationBody(scene: AmendmentPublicationScene, version: Long = 3) =
        json.writeValueAsString(
            mapOf(
                "expectedVersion" to version,
                "reviewId" to scene.review.id,
                "expectedReviewVersion" to 1,
                "expectedApprovalVersion" to 1,
                "calculationId" to scene.review.calculationId,
                "reason" to "Publish reviewed correction",
            )
        )

    protected fun admitPublication(
        scene: AmendmentPublicationScene,
        version: Long = 3,
        member: PayrollMember = scene.publisher,
    ) =
        command(
            member.client,
            publicationPath(scene) + "/${scene.id}",
            publicationBody(scene, version),
            member.csrf,
            scene.operation,
            "PUT",
        )

    protected fun publicationView(scene: AmendmentPublicationScene) =
        payrollBody(get(scene.publisher.client, publicationPath(scene)))

    protected fun publicationLease(scene: AmendmentPublicationScene): JobLease {
        payrollBody(admitPublication(scene))
        return claimPayrollJobs(JobKind.PAYROLL_AMENDMENT_PUBLISH).single {
            it.job.request.values["publicationId"] == scene.id.toString()
        }
    }

    protected fun stepPublication(scene: AmendmentPublicationScene, lease: JobLease) =
        publishAmendment.execute(
            payrollActor(scene.source.payroll, scene.publisher)
                .copy(authenticatedAt = lease.job.request.authenticatedAt),
            lease,
        )

    protected fun publish(scene: AmendmentPublicationScene) {
        assertEquals(
            Result.Success(JobStep(1, true)),
            stepPublication(scene, publicationLease(scene)),
        )
    }

    protected fun currentPayslip(scene: AmendmentPublicationScene, revision: Long? = null) =
        payrollBody(
            get(
                scene.source.payroll.owner.client,
                amendmentPath(scene.source) + (revision?.let { "?revision=$it" } ?: ""),
            )
        )

    protected fun currentProgress(scene: AmendmentPublicationScene) =
        payrollBody(
            get(scene.source.payroll.owner.client, amendmentPath(scene.source) + "/payments")
        )

    protected fun publicationCounts(scene: AmendmentPublicationScene) =
        amendmentReviewCounts(scene.review) +
            listOf(
                    "payroll_amendment_publications",
                    "payroll_assessment_revisions",
                    "payroll_assessment_heads",
                    "payroll_assessment_holidays",
                    "payroll_payment_heads",
                    "payroll_payment_recoveries",
                    "mobile_sync_changes",
                    "background_jobs",
                )
                .associateWith { count(scene.source.payroll.company, it) }

    protected data class BankInstruction(
        val id: UUID,
        val item: UUID,
        val maker: PayrollMember,
        val checker: PayrollMember,
        val operation: UUID,
        val body: String,
    )

    protected data class BankRecovery(
        val id: UUID,
        val operation: UUID,
        val body: String,
        val member: PayrollMember,
    )

    protected fun bankRecovery(
        scene: AmendmentPublicationScene,
        member: PayrollMember,
        amount: BigDecimal? = null,
        reference: String = "RETURN:${UUID.randomUUID()}",
    ): BankRecovery {
        val progress = currentProgress(scene)
        return BankRecovery(
            UUID.randomUUID(),
            UUID.randomUUID(),
            json.writeValueAsString(
                mapOf(
                    "assessmentRevision" to progress["settlement"]["assessmentRevision"].asLong(),
                    "paymentVersion" to progress["version"].asLong(),
                    "amount" to
                        (amount?.toPlainString()
                            ?: progress["settlement"]["recoverable"].asString()),
                    "transactionReference" to reference,
                    "occurredAt" to clock.instant(),
                    "reason" to "Confirmed bank return",
                )
            ),
            member,
        )
    }

    protected fun recordBankRecovery(scene: AmendmentPublicationScene, recovery: BankRecovery) =
        command(
            recovery.member.client,
            amendmentPath(scene.source) + "/recoveries/${recovery.id}",
            recovery.body,
            recovery.member.csrf,
            recovery.operation,
            "PUT",
        )

    protected fun bankInstruction(
        scene: AmendmentPublicationScene,
        observed: Boolean = false,
    ): BankInstruction {
        val company = scene.source.payroll.company
        val maker = payrollMember(company, setOf("company.read", "payroll.pay"))
        val checker = payrollMember(company, setOf("company.read", "payroll.pay"))
        val item = UUID.randomUUID()
        val progress = currentProgress(scene)
        val observation =
            if (observed)
                mapOf(
                    "assessmentRevision" to progress["settlement"]["assessmentRevision"].asLong(),
                    "paymentVersion" to progress["version"].asLong(),
                    "amount" to progress["settlement"]["payable"].asString(),
                )
            else emptyMap()
        return BankInstruction(
            UUID.randomUUID(),
            item,
            maker,
            checker,
            UUID.randomUUID(),
            json.writeValueAsString(
                mapOf(
                    "title" to "Reviewed balance",
                    "reason" to "Verified destination",
                    "items" to
                        listOf(
                            mapOf(
                                "id" to item,
                                "assessmentId" to scene.source.assessment,
                                "destination" to
                                    mapOf(
                                        "bankCode" to "014",
                                        "accountNumber" to "001234567890",
                                        "accountName" to "Test beneficiary",
                                    ),
                            ) + observation
                        ),
                )
            ),
        )
    }

    protected fun paymentPath(scene: AmendmentPublicationScene, bank: BankInstruction) =
        "/api/v1/companies/${scene.source.payroll.company}/payroll/payments/${bank.id}"

    protected fun prepareBank(scene: AmendmentPublicationScene, bank: BankInstruction) =
        command(
            bank.maker.client,
            paymentPath(scene, bank),
            bank.body,
            bank.maker.csrf,
            bank.operation,
            "PUT",
        )

    protected fun releaseBank(scene: AmendmentPublicationScene, bank: BankInstruction) =
        command(
            bank.checker.client,
            paymentPath(scene, bank) + "/release",
            """{"expectedVersion":0,"reason":"Independent release"}""",
            bank.checker.csrf,
            UUID.randomUUID(),
        )

    protected fun payBank(scene: AmendmentPublicationScene, bank: BankInstruction) {
        payrollBody(prepareBank(scene, bank))
        payrollBody(releaseBank(scene, bank))
        clock.set(clock.instant().plusMillis(1))
        payrollBody(
            command(
                bank.checker.client,
                paymentPath(scene, bank) + "/reconcile",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to 1,
                        "reason" to "Confirmed bank result",
                        "results" to
                            listOf(
                                mapOf(
                                    "itemId" to bank.item,
                                    "status" to "SUCCEEDED",
                                    "transactionReference" to "BANK:${UUID.randomUUID()}",
                                    "occurredAt" to clock.instant(),
                                    "reason" to "Bank statement",
                                    "confirmedNoTransfer" to false,
                                )
                            ),
                    )
                ),
                bank.checker.csrf,
                UUID.randomUUID(),
            )
        )
    }
}
