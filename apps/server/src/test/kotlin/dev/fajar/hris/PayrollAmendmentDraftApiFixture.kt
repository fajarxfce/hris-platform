package dev.fajar.hris

import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.PayrollAmendmentRepository
import dev.fajar.hris.payroll.domain.usecases.*
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import tools.jackson.databind.JsonNode

@Import(PayrollAmendmentDraftProbeConfiguration::class)
abstract class PayrollAmendmentDraftApiFixture : PayrollAmendmentApiFixture() {
    @Autowired protected lateinit var draftProbe: PayrollAmendmentDraftProbe
    @Autowired protected lateinit var amendments: PayrollAmendmentRepository
    @Autowired protected lateinit var saveAmendment: SavePayrollAmendment
    @Autowired protected lateinit var calculateAmendment: CalculatePayrollAmendment
    @Autowired protected lateinit var cancelAmendment: CancelPayrollAmendment

    protected data class DraftFixture(
        val source: AmendmentFixture,
        val id: UUID,
        val operation: UUID,
        val body: String,
    )

    @AfterEach fun clearDraftProbe() = draftProbe.clear()

    protected fun draftsPath(f: AmendmentFixture) =
        "/api/v1/companies/${f.payroll.company}/payroll/amendments"

    protected fun draftBody(
        f: AmendmentFixture,
        preparation: JsonNode,
        version: Long? = null,
        changes: List<Map<String, Any?>> = listOf(remuneration(preparation)),
        reason: String = "Reviewed remuneration",
    ): String =
        json.writeValueAsString(
            mapOf(
                "firstAssessmentId" to f.assessment,
                "expectedVersion" to version,
                "sourceVersions" to preparation["sourceVersions"],
                "corrections" to changes,
                "reason" to reason,
            )
        )

    protected fun saveDraft(
        f: AmendmentFixture,
        id: UUID,
        body: String,
        key: UUID = UUID.randomUUID(),
        member: PayrollMember = f.maker,
    ) = command(member.client, draftsPath(f) + "/$id", body, member.csrf, key, "PUT")

    protected fun draftFixture(f: AmendmentFixture = amendmentFixture()): DraftFixture {
        val draft =
            DraftFixture(
                f,
                UUID.randomUUID(),
                UUID.randomUUID(),
                draftBody(f, amendmentPreparation(f)),
            )
        payrollBody(saveDraft(f, draft.id, draft.body, draft.operation))
        return draft
    }

    protected fun draftCommand(
        draft: DraftFixture,
        action: String,
        version: Long,
        key: UUID = UUID.randomUUID(),
        member: PayrollMember = draft.source.maker,
        reason: String = "Reviewed remuneration",
    ) =
        command(
            member.client,
            draftsPath(draft.source) + "/${draft.id}/$action",
            json.writeValueAsString(mapOf("expectedVersion" to version, "reason" to reason)),
            member.csrf,
            key,
        )

    protected fun draftView(draft: DraftFixture) =
        payrollBody(get(draft.source.maker.client, draftsPath(draft.source) + "/${draft.id}"))

    protected fun draftTerms(draft: DraftFixture): PayrollAmendmentTerms =
        (transactions.run(payrollActor(draft.source.payroll, draft.source.maker)) {
                amendments.find(draft.source.payroll.company, draft.id)
            } as dev.fajar.hris.core.domain.Result.Success)
            .value!!
            .terms

    protected fun draftCounts(f: AmendmentFixture) =
        listOf(
                "payroll_amendments",
                "payroll_amendment_revisions",
                "payroll_amendment_calculations",
                "payroll_amendment_results",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )
            .associateWith { count(f.payroll.company, it) }
}
