package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.repositories.*
import dev.fajar.hris.workforce.domain.usecases.*
import java.time.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class AttendanceAdjustmentCompletionTest : AttendanceAdjustmentApiFixture() {
    @Autowired private lateinit var periods: WorkPeriodRepository
    @Autowired private lateinit var adjustments: AttendanceAdjustmentRepository
    @Autowired private lateinit var attendance: AttendanceRepository
    @Autowired private lateinit var corrections: AttendanceCorrectionRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner
    private val security = IdentitySecurityPolicy()

    private fun writer(
        revisions: AttendanceAdjustmentRepository = adjustments,
        events: AttendanceRepository = attendance,
        correctionStore: AttendanceCorrectionRepository = corrections,
        changes: ChangeJournalRepository = journal,
        receipts: OperationRepository = operations,
        policy: IdentitySecurityPolicy = security.copy(enforceMfa = false),
    ) =
        AdjustClosedAttendance(
            periods,
            revisions,
            events,
            correctionStore,
            people,
            companies,
            members,
            identities,
            receipts,
            changes,
            transactions,
            policy,
            clock,
        )

    private fun invoke(
        f: Fixture,
        revision: String,
        events: List<AttendanceEvidenceDecision>,
        key: UUID,
        command: AdjustClosedAttendance = writer(),
        actor: Actor = f.actor,
    ) =
        command.execute(
            actor,
            key,
            f.employee,
            LocalDate.parse(adjustmentDate),
            revision,
            Instant.parse("2026-09-30T01:00:00Z"),
            Instant.parse("2026-09-30T09:00:00Z"),
            60,
            events,
            "Reviewed late attendance",
        )

    private fun enrolled(f: Fixture): Actor {
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.actor.accountId,
            )
        return f.actor.copy(
            mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["event", "correction", "snapshot", "journal", "receipt"])
    fun anyLateFailureRollsBackReviewsCorrectionSnapshotJournalReceiptAndSync(phase: String) {
        val f = closedFixture()
        val capture = capture(f)
        val revision = adjustmentReview(f)["revision"].asString()
        val evidence =
            listOf(AttendanceEvidenceDecision(capture, 0, AttendanceReviewDecision.ACCEPT))
        val before = adjustmentState(f)
        val original = unchangedFacts(f)
        val key = UUID.randomUUID()
        val failure = Result.Failed(Failure(FailureKind.CONFLICT, "fixture_late_failure"))
        val events =
            object : AttendanceRepository by attendance {
                override fun review(companyId: UUID, id: UUID, review: AttendanceReview) =
                    attendance.review(companyId, id, review).flatMap {
                        if (phase == "event") failure else Result.Success(it)
                    }
            }
        val correctionStore =
            object : AttendanceCorrectionRepository by corrections {
                override fun save(
                    companyId: UUID,
                    correction: AttendanceCorrection,
                    expectedVersion: Long?,
                ) =
                    corrections.save(companyId, correction, expectedVersion).flatMap {
                        if (phase == "correction") failure else Result.Success(it)
                    }
            }
        val revisions =
            object : AttendanceAdjustmentRepository by adjustments {
                override fun append(companyId: UUID, adjustment: AttendanceAdjustment) =
                    adjustments.append(companyId, adjustment).flatMap {
                        if (phase == "snapshot") failure else Result.Success(it)
                    }
            }
        val changes =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord) =
                    journal.record(actor, change).flatMap {
                        if (phase == "journal" && change.resourceType == "attendance_adjustment")
                            failure
                        else Result.Success(Unit)
                    }
            }
        val receipts =
            object : OperationRepository by operations {
                override fun record(actor: Actor, key: OperationKey, receipt: MutationReceipt) =
                    operations.record(actor, key, receipt).flatMap {
                        if (phase == "receipt") failure else Result.Success(Unit)
                    }
            }
        assertEquals(
            failure,
            invoke(
                f,
                revision,
                evidence,
                key,
                writer(revisions, events, correctionStore, changes, receipts),
            ),
        )
        assertEquals(before, adjustmentState(f))
        assertEquals(original, unchangedFacts(f))
        assertTrue(invoke(f, revision, evidence, key) is Result.Success)
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["guard", "acquire", "event", "correction", "snapshot", "journal", "receipt"]
    )
    fun mfaMustRemainCurrentAfterWaitsAndThroughTheLastWrite(phase: String) {
        val f = closedFixture()
        val capture = capture(f)
        val revision = adjustmentReview(f)["revision"].asString()
        val evidence =
            listOf(AttendanceEvidenceDecision(capture, 0, AttendanceReviewDecision.ACCEPT))
        val actor = enrolled(f)
        val before = adjustmentState(f)
        val key = UUID.randomUUID()
        val events =
            object : AttendanceRepository by attendance {
                override fun review(companyId: UUID, id: UUID, review: AttendanceReview) =
                    attendance.review(companyId, id, review).also {
                        if (phase == "event") clock.set(clock.instant().plusSeconds(2))
                    }
            }
        val correctionStore =
            object : AttendanceCorrectionRepository by corrections {
                override fun save(
                    companyId: UUID,
                    correction: AttendanceCorrection,
                    expectedVersion: Long?,
                ) =
                    corrections.save(companyId, correction, expectedVersion).also {
                        if (phase == "correction") clock.set(clock.instant().plusSeconds(2))
                    }
            }
        val revisions =
            object : AttendanceAdjustmentRepository by adjustments {
                override fun latest(companyId: UUID, jobId: UUID, employeeId: UUID) =
                    adjustments.latest(companyId, jobId, employeeId).also {
                        if (phase == "acquire") clock.set(clock.instant().plusSeconds(2))
                    }

                override fun append(companyId: UUID, adjustment: AttendanceAdjustment) =
                    adjustments.append(companyId, adjustment).also {
                        if (phase == "snapshot") clock.set(clock.instant().plusSeconds(2))
                    }
            }
        val changes =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord) =
                    journal.record(actor, change).also {
                        if (phase == "journal" && change.resourceType == "attendance_adjustment")
                            clock.set(clock.instant().plusSeconds(2))
                    }
            }
        val receipts =
            object : OperationRepository by operations {
                override fun record(actor: Actor, key: OperationKey, receipt: MutationReceipt) =
                    operations.record(actor, key, receipt).also {
                        if (phase == "receipt") clock.set(clock.instant().plusSeconds(2))
                    }
            }
        val barrier = AccountLockProbe.Barrier(actor.accountId)
        if (phase == "guard") accountProbe.current.set(barrier)
        val result =
            Executors.newSingleThreadExecutor().use { pool ->
                val pending =
                    pool.submit<Result<*>> {
                        invoke(
                            f,
                            revision,
                            evidence,
                            key,
                            writer(revisions, events, correctionStore, changes, receipts, security),
                            actor,
                        )
                    }
                try {
                    if (phase == "guard") {
                        assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                        clock.set(clock.instant().plusSeconds(2))
                        barrier.release.countDown()
                    }
                    pending.get(15, TimeUnit.SECONDS)
                } finally {
                    barrier.release.countDown()
                    accountProbe.current.set(null)
                }
            }
        assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code)
        assertEquals(before, adjustmentState(f))
        val renewed = actor.copy(mfaVerifiedAt = clock.instant())
        val accepted = invoke(f, revision, evidence, key, writer(policy = security), renewed)
        assertTrue(accepted is Result.Success, accepted.toString())
        val committed = adjustmentState(f)
        assertEquals(
            "mfa_required",
            (invoke(f, revision, evidence, key, writer(policy = security), actor) as Result.Failed)
                .failure
                .code,
        )
        assertEquals(committed, adjustmentState(f))
        assertEquals(
            Result.Success((accepted as Result.Success).value.copy(replayed = true)),
            invoke(f, revision, evidence, key, writer(policy = security), renewed),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["permission", "membership", "account", "credential"])
    fun revocationDuringAdmissionRejectsTheWholeCommand(mode: String) {
        val f = closedFixture()
        capture(f)
        val body = adjustmentBody(adjustmentReview(f))
        val before = adjustmentState(f)
        val response =
            waiting(
                f.actor.accountId,
                {
                    when (mode) {
                        "permission" ->
                            database()
                                .update(
                                    "delete from membership_permissions where company_id=? and account_id=? and permission='attendance.correct'",
                                    f.company,
                                    f.actor.accountId,
                                )
                        "membership" ->
                            database()
                                .update(
                                    "update company_memberships set active=false where company_id=? and account_id=?",
                                    f.company,
                                    f.actor.accountId,
                                )
                        "account" ->
                            database()
                                .update(
                                    "update accounts set active=false where id=?",
                                    f.actor.accountId,
                                )
                        else ->
                            database()
                                .update(
                                    "update accounts set security_version=security_version+1 where id=?",
                                    f.actor.accountId,
                                )
                    }
                },
            ) {
                adjust(f, body)
            }
        assertTrue(response.statusCode() in setOf(401, 403), response.body())
        assertEquals(before, adjustmentState(f))
    }

    @Test
    fun cancellingPendingWorkReleasesAllGuardsAndAllowsAnExplicitRetry() {
        val f = closedFixture()
        val capture = capture(f)
        val revision = adjustmentReview(f)["revision"].asString()
        val evidence =
            listOf(AttendanceEvidenceDecision(capture, 0, AttendanceReviewDecision.ACCEPT))
        val before = adjustmentState(f)
        val key = UUID.randomUUID()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val revisions =
            object : AttendanceAdjustmentRepository by adjustments {
                override fun append(companyId: UUID, adjustment: AttendanceAdjustment) =
                    adjustments.append(companyId, adjustment).also {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                    }
            }
        Executors.newSingleThreadExecutor().use { pool ->
            val pending =
                pool.submit<Result<*>> {
                    try {
                        invoke(f, revision, evidence, key, writer(revisions))
                    } finally {
                        finished.countDown()
                    }
                }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertTrue(pending.cancel(true))
                assertTrue(finished.await(5, TimeUnit.SECONDS))
            } finally {
                release.countDown()
            }
        }
        assertEquals(before, adjustmentState(f))
        assertTrue(invoke(f, revision, evidence, key) is Result.Success)
    }

    @Test
    fun callerMutationAfterAdmissionCannotReplaceTheReviewedEvidence() {
        val f = closedFixture()
        val capture = capture(f)
        val revision = adjustmentReview(f)["revision"].asString()
        val evidence =
            mutableListOf(AttendanceEvidenceDecision(capture, 0, AttendanceReviewDecision.ACCEPT))
        val revisions =
            object : AttendanceAdjustmentRepository by adjustments {
                override fun latest(companyId: UUID, jobId: UUID, employeeId: UUID) =
                    adjustments.latest(companyId, jobId, employeeId).also { evidence.clear() }
            }
        val saved = invoke(f, revision, evidence, UUID.randomUUID(), writer(revisions))
        assertTrue(saved is Result.Success, saved.toString())
        assertEquals(0, adjustmentReview(f)["attendance"]["pendingCount"].asInt())
    }

    @ParameterizedTest
    @ValueSource(strings = ["review", "history", "details"])
    fun privateReadsCannotCompleteAfterAssuranceExpiresDuringAcquisition(kind: String) {
        val f = closedFixture()
        val saved = adjusted(f, adjustmentBody(adjustmentReview(f)))
        val id = UUID.fromString(saved["id"].asString())
        val actor = enrolled(f)
        val before = adjustmentState(f)
        val source =
            object : AttendanceAdjustmentRepository by adjustments {
                override fun latest(companyId: UUID, jobId: UUID, employeeId: UUID) =
                    adjustments.latest(companyId, jobId, employeeId).also {
                        clock.set(clock.instant().plusSeconds(2))
                    }

                override fun history(
                    companyId: UUID,
                    jobId: UUID,
                    employeeId: UUID,
                    before: Long?,
                    limit: Int,
                ) =
                    adjustments.history(companyId, jobId, employeeId, before, limit).also {
                        clock.set(clock.instant().plusSeconds(2))
                    }

                override fun find(companyId: UUID, id: UUID) =
                    adjustments.find(companyId, id).also {
                        clock.set(clock.instant().plusSeconds(2))
                    }
            }
        val result =
            when (kind) {
                "review" ->
                    GetAttendanceAdjustmentReview(
                            periods,
                            source,
                            attendance,
                            corrections,
                            people,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(actor, f.employee, LocalDate.parse(adjustmentDate))
                "history" ->
                    ListAttendanceAdjustments(
                            periods,
                            source,
                            people,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(actor, f.employee, YearMonth.of(2026, 9), null, 25)
                else ->
                    GetAttendanceAdjustment(
                            periods,
                            source,
                            people,
                            companies,
                            members,
                            identities,
                            transactions,
                            security,
                            clock,
                        )
                        .execute(actor, f.employee, id)
            }
        assertEquals("mfa_required", (result as? Result.Failed)?.failure?.code)
        assertEquals(before, adjustmentState(f))
    }
}
