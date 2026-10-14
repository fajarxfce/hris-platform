package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.payroll.domain.usecases.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.util.UUID
import org.springframework.beans.factory.annotation.Autowired

abstract class PayrollAmendmentDraftAssuranceApiFixture : PayrollAmendmentDraftApiFixture() {
    @Autowired private lateinit var policies: PayrollPolicyRepository
    @Autowired private lateinit var sources: PayrollAmendmentSourceRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    protected val security = IdentitySecurityPolicy()

    protected data class AssuranceScene(
        val draft: DraftFixture,
        val actor: Actor,
        val terms: PayrollAmendmentTerms,
        val operation: UUID,
        val createdId: UUID,
        val calculationId: UUID?,
    )

    protected fun assuranceScene(calculated: Boolean = false): AssuranceScene {
        val draft = draftFixture()
        if (calculated) payrollBody(draftCommand(draft, "calculate", 0))
        val terms = draftTerms(draft)
        val calculationId =
            if (calculated) UUID.fromString(draftView(draft)["latestCalculation"]["id"].asString())
            else null
        val actor =
            payrollActor(draft.source.payroll, draft.source.maker)
                .copy(mfaVerifiedAt = clock.instant())
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                actor.accountId,
            )
        return AssuranceScene(
            draft,
            actor,
            terms,
            UUID.randomUUID(),
            UUID.randomUUID(),
            calculationId,
        )
    }

    protected fun protectedCommand(
        scene: AssuranceScene,
        action: String,
        actor: Actor = scene.actor,
    ): Result<MutationReceipt> =
        when (action) {
            "create",
            "save" ->
                SavePayrollAmendment(
                        amendments,
                        sources,
                        policies,
                        people,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(
                        actor,
                        scene.operation,
                        if (action == "create") scene.createdId else scene.draft.id,
                        scene.draft.source.assessment,
                        if (action == "create") null else 0,
                        scene.terms,
                        "Reviewed remuneration",
                    )
            "calculate" ->
                CalculatePayrollAmendment(
                        amendments,
                        sources,
                        policies,
                        people,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.operation, scene.draft.id, 0, "Reviewed remuneration")
            "cancel" ->
                CancelPayrollAmendment(
                        amendments,
                        policies,
                        people,
                        companies,
                        members,
                        identities,
                        operations,
                        journal,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.operation, scene.draft.id, 0, "Reviewed remuneration")
            else -> error("Unknown draft command")
        }

    protected fun protectedRead(
        scene: AssuranceScene,
        action: String,
        actor: Actor = scene.actor,
    ): Result<*> =
        when (action) {
            "details" ->
                GetPayrollAmendment(
                        amendments,
                        policies,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.draft.id)
            "history" ->
                GetPayrollAmendmentHistory(
                        amendments,
                        policies,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.draft.id, null, 25)
            "revision" ->
                GetPayrollAmendmentRevision(
                        amendments,
                        policies,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.draft.id, 0)
            "calculation" ->
                GetPayrollAmendmentCalculation(
                        amendments,
                        policies,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, scene.draft.id, requireNotNull(scene.calculationId))
            "list" ->
                ListPayrollAmendments(
                        amendments,
                        policies,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, 2026, null, null, "", null, 25)
            else -> error("Unknown draft read")
        }
}
