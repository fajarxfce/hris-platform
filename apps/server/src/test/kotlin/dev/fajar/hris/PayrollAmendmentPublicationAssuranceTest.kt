package dev.fajar.hris

import dev.fajar.hris.approvals.domain.repositories.ApprovalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.payroll.domain.repositories.*
import dev.fajar.hris.payroll.domain.usecases.*
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class PayrollAmendmentPublicationAssuranceTest : PayrollAmendmentPublicationApiFixture() {
    @Autowired private lateinit var publications: PayrollAmendmentPublicationRepository
    @Autowired private lateinit var policies: PayrollPolicyRepository
    @Autowired private lateinit var sources: PayrollAmendmentSourceRepository
    @Autowired private lateinit var payments: PayrollPaymentRepository
    @Autowired private lateinit var approvals: ApprovalRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var operations: OperationRepository
    private val security = IdentitySecurityPolicy()

    @Test
    fun lateMfaExpiryCannotCommitBankRecoveryOrAdvanceItsEmployeeProjection() {
        val scene = publicationScene(difference = "-1000000")
        val bank = bankInstruction(scene)
        payBank(scene, bank)
        publish(scene)
        val progress = currentProgress(scene)
        val before = publicationCounts(scene)
        val command =
            ConfirmPayrollPaymentRecovery(
                payments,
                people,
                companies,
                members,
                identities,
                transactions,
                security,
                clock,
                operations,
                journal,
            )
        val actor =
            payrollActor(scene.source.payroll, bank.checker)
                .copy(
                    mfaVerifiedAt =
                        clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
                )
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                actor.accountId,
            )
        val operation = UUID.randomUUID()
        val id = UUID.randomUUID()
        val occurredAt = clock.instant()
        val amount = progress["settlement"]["recoverable"].asString().toBigDecimal()
        payrollProbe.afterJournal = {
            if (it.action == "payroll.payment_recovered") clock.set(clock.instant().plusSeconds(2))
        }
        val failed =
            command.execute(
                actor,
                operation,
                scene.source.assessment,
                id,
                1,
                progress["version"].asLong(),
                amount,
                "RETURN:$id",
                occurredAt,
                "Confirmed bank return",
            )
        payrollProbe.clear()
        assertEquals("recent_authentication_required", (failed as Result.Failed).failure.code)
        assertEquals(before, publicationCounts(scene))
        assertEquals(progress, currentProgress(scene))
        val recovered =
            command.execute(
                actor.copy(mfaVerifiedAt = clock.instant()),
                operation,
                scene.source.assessment,
                id,
                1,
                progress["version"].asLong(),
                amount,
                "RETURN:$id",
                occurredAt,
                "Confirmed bank return",
            )
        assertTrue(recovered is Result.Success, recovered.toString())
    }

    private data class ProofScene(
        val publication: AmendmentPublicationScene,
        val actor: Actor,
        val jobVersion: Long = 0,
        val operation: UUID = UUID.randomUUID(),
    )

    private fun proofScene(recover: Boolean): ProofScene {
        val scene = publicationScene()
        var jobVersion = 0L
        if (recover) {
            val lease = publicationLease(scene)
            database()
                .update(
                    "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                    lease.job.request.id,
                )
            assertEquals(
                Result.Success(true),
                transactions.run(payrollActor(scene.source.payroll, scene.publisher)) {
                    publicationJobs.failExpired(lease, "job_attempt_limit")
                },
            )
            jobVersion = publicationView(scene)[0]["job"]["version"].asLong()
        }
        val actor =
            payrollActor(scene.source.payroll, scene.publisher)
                .copy(mfaVerifiedAt = clock.instant())
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                actor.accountId,
            )
        return ProofScene(scene, actor, jobVersion)
    }

    private fun command(
        scene: ProofScene,
        recover: Boolean,
        actor: Actor,
    ): Result<MutationReceipt> =
        if (recover)
            RecoverPayrollAmendmentPublication(
                    publications,
                    amendments,
                    amendmentReviews,
                    policies,
                    approvals,
                    people,
                    companies,
                    members,
                    identities,
                    publicationJobs,
                    transactions,
                    security,
                    clock,
                    operations,
                    journal,
                )
                .execute(
                    actor,
                    scene.operation,
                    scene.publication.review.draft.id,
                    scene.publication.id,
                    4,
                    scene.jobVersion,
                    "Recover exhausted publication",
                )
        else
            StartPayrollAmendmentPublication(
                    publications,
                    amendments,
                    amendmentReviews,
                    sources,
                    policies,
                    payments,
                    approvals,
                    people,
                    companies,
                    members,
                    identities,
                    publicationJobs,
                    journal,
                    transactions,
                    security,
                    clock,
                    operations,
                )
                .execute(
                    actor,
                    scene.operation,
                    scene.publication.review.draft.id,
                    scene.publication.id,
                    3,
                    scene.publication.review.id,
                    1,
                    1,
                    scene.publication.review.calculationId,
                    "Publish reviewed correction",
                )

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun mfaExpiryAfterJournalingRollsBackAdmissionOrRecovery(recover: Boolean) {
        val scene = proofScene(recover)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt =
                    clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
            )
        val before = publicationCounts(scene.publication)
        payrollProbe.afterJournal = {
            if (it.action.startsWith("payroll.amendment_publication_"))
                clock.set(clock.instant().plusSeconds(2))
        }
        val result = command(scene, recover, actor)
        payrollProbe.clear()
        assertEquals("recent_authentication_required", (result as Result.Failed).failure.code)
        assertEquals(before, publicationCounts(scene.publication))
        val retried = command(scene, recover, actor.copy(mfaVerifiedAt = clock.instant()))
        assertTrue(retried is Result.Success, retried.toString())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun committedPublicationCommandsStillRequireFreshProofAfterAcquisition(recover: Boolean) {
        val scene = proofScene(recover)
        val committed = command(scene, recover, scene.actor)
        assertTrue(committed is Result.Success, committed.toString())
        val before = publicationCounts(scene.publication)
        val actor =
            scene.actor.copy(
                mfaVerifiedAt =
                    clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
            )
        draftProbe.afterRead = {
            if (it == actor.companyId) clock.set(clock.instant().plusSeconds(2))
        }
        val failed = command(scene, recover, actor)
        draftProbe.clear()
        assertEquals("recent_authentication_required", (failed as Result.Failed).failure.code)
        assertEquals(before, publicationCounts(scene.publication))
        val recovered =
            command(scene, recover, actor.copy(mfaVerifiedAt = clock.instant())) as Result.Success
        assertEquals((committed as Result.Success).value.copy(replayed = true), recovered.value)
    }

    @Test
    fun privatePublicationDetailsAreDiscardedWhenMfaExpiresDuringAcquisition() {
        val scene = proofScene(recover = true)
        val reader =
            ListPayrollAmendmentPublications(
                publications,
                amendments,
                amendmentReviews,
                policies,
                approvals,
                people,
                companies,
                members,
                identities,
                publicationJobs,
                transactions,
                security,
                clock,
            )
        val actor =
            scene.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        publicationProbe.afterRead = { clock.set(clock.instant().plusSeconds(2)) }
        val failed = reader.execute(actor, scene.publication.review.draft.id)
        publicationProbe.clear()
        assertEquals("mfa_required", (failed as Result.Failed).failure.code)
        val recovered =
            reader.execute(
                actor.copy(mfaVerifiedAt = clock.instant()),
                scene.publication.review.draft.id,
            )
        assertTrue(recovered is Result.Success, recovered.toString())
    }
}
