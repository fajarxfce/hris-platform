package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.entities.ScheduleOffboardingCommand
import dev.fajar.hris.people.domain.repositories.*
import dev.fajar.hris.people.domain.usecases.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class OffboardingScheduleAssuranceHttpTest : OffboardingScheduleApiFixture() {
    @Autowired private lateinit var schedules: OffboardingScheduleRepository
    @Autowired private lateinit var lifecycle: LifecycleRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var jobs: JobRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    private val security = IdentitySecurityPolicy(enforceMfa = true)

    @ParameterizedTest
    @ValueSource(strings = ["review", "list", "detail"])
    fun mfaExpiryAfterAcquisitionDoesNotReleasePrivateScheduleData(operation: String) {
        val f = fixture(future = true, operator = true)
        val prepared = scheduled(f)
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.actor.accountId,
            )
        val actor =
            f.actor.copy(
                mfaVerifiedAt = clock.instant().minus(security.maximumMfaAge).plusSeconds(1)
            )
        val invoke: (Actor) -> Result<*> =
            when (operation) {
                "review" -> { current ->
                        GetOffboardingScheduleReview(
                                schedules,
                                lifecycle,
                                people,
                                companies,
                                members,
                                identities,
                                transactions,
                                security,
                                clock,
                            )
                            .execute(current, f.caseId)
                    }
                "list" -> { current ->
                        ListOffboardingSchedules(
                                schedules,
                                lifecycle,
                                people,
                                companies,
                                members,
                                identities,
                                transactions,
                                security,
                                clock,
                            )
                            .execute(current, f.caseId)
                    }
                else -> { current ->
                        GetOffboardingSchedule(
                                schedules,
                                lifecycle,
                                people,
                                companies,
                                members,
                                identities,
                                transactions,
                                security,
                                clock,
                            )
                            .execute(current, f.caseId, prepared.id)
                    }
            }
        val hook =
            OffboardingScheduleProbe.Hook(f.company) { clock.set(clock.instant().plusSeconds(2)) }
        if (operation == "detail") scheduleProbe.afterFind.set(hook)
        else scheduleProbe.afterList.set(hook)
        val before = count(f, "operation_receipts")
        val failed = invoke(actor) as Result.Failed
        assertEquals("mfa_required", failed.failure.code)
        assertEquals(before, count(f, "operation_receipts"))
        assertTrue(invoke(actor.copy(mfaVerifiedAt = clock.instant())) is Result.Success)
    }

    @ParameterizedTest
    @CsvSource(
        "schedule,queue",
        "schedule,create",
        "schedule,journal",
        "cancel,journal",
        "recover,journal",
    )
    fun recentMfaMustSurviveQueueWaitsAndWritesIncludingOriginalReceiptRecovery(
        operation: String,
        boundary: String,
    ) {
        val f = fixture(operator = true)
        val prepared = prepare(f)
        if (operation != "schedule") ok(submit(f, prepared))
        if (operation == "recover") {
            val lease = claim().single()
            database()
                .update(
                    "update background_jobs set status='FAILED',failure_code='job_attempts_exhausted',finished_at=now(),lease_owner=null,lease_token=null,lease_until=null,version=version+1 where id=?",
                    lease.job.request.id,
                )
        }
        val view = if (operation == "schedule") null else details(f, prepared.id)
        val body = json.readTree(prepared.body)
        val key = UUID.randomUUID()
        val command =
            ScheduleOffboardingCommand(
                prepared.id,
                f.caseId,
                body["expectedVersion"].asLong(),
                body["employmentVersion"].asLong(),
                body["basisRevision"].asString(),
                body["reason"].asString(),
            )
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.actor.accountId,
            )
        val actor =
            f.actor.copy(
                mfaVerifiedAt =
                    clock.instant().minus(security.recentAuthenticationAge).plusSeconds(1)
            )
        val invoke: (Actor) -> Result<MutationReceipt> =
            when (operation) {
                "schedule" -> { current ->
                        ScheduleOffboarding(
                                schedules,
                                lifecycle,
                                people,
                                companies,
                                members,
                                identities,
                                jobs,
                                operations,
                                journal,
                                transactions,
                                security,
                                clock,
                            )
                            .execute(current, key, command)
                    }
                "cancel" -> { current ->
                        CancelOffboardingSchedule(
                                schedules,
                                lifecycle,
                                people,
                                companies,
                                members,
                                identities,
                                jobs,
                                operations,
                                journal,
                                transactions,
                                security,
                                clock,
                            )
                            .execute(
                                current,
                                key,
                                f.caseId,
                                prepared.id,
                                requireNotNull(view)["version"].asLong(),
                                view["job"]["version"].asLong(),
                                "Reviewed cancellation",
                            )
                    }
                else -> { current ->
                        RecoverOffboardingSchedule(
                                schedules,
                                lifecycle,
                                people,
                                companies,
                                members,
                                identities,
                                jobs,
                                operations,
                                journal,
                                transactions,
                                security,
                                clock,
                            )
                            .execute(
                                current,
                                key,
                                f.caseId,
                                prepared.id,
                                requireNotNull(view)["version"].asLong(),
                                view["job"]["version"].asLong(),
                                "Reviewed recovery",
                            )
                    }
            }
        val hook =
            OffboardingScheduleProbe.Hook(f.company) { clock.set(clock.instant().plusSeconds(2)) }
        when (boundary) {
            "queue" -> scheduleProbe.afterQueueLock.set(hook)
            "create" -> scheduleProbe.afterCreate.set(hook)
            else ->
                scheduleProbe.afterJournal.set(
                    OffboardingScheduleProbe.JournalHook(
                        f.company,
                        when (operation) {
                            "schedule" -> "people.offboarding_scheduled"
                            "cancel" -> "people.offboarding_cancellation_requested"
                            else -> "people.offboarding_schedule_recovered"
                        },
                        hook.action,
                    )
                )
        }
        val tables =
            listOf(
                "background_jobs",
                "offboarding_schedules",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )
        val before = tables.associateWith { count(f, it) }
        assertEquals(
            "recent_authentication_required",
            (invoke(actor) as Result.Failed).failure.code,
        )
        assertEquals(before, tables.associateWith { count(f, it) })
        if (view != null) {
            assertEquals(
                "SCHEDULED",
                database()
                    .queryForObject(
                        "select status from offboarding_schedules where company_id=? and id=?",
                        String::class.java,
                        f.company,
                        prepared.id,
                    ),
            )
            if (operation == "cancel")
                assertFalse(
                    database()
                        .queryForObject(
                            "select cancellation_requested from background_jobs where id=?",
                            Boolean::class.java,
                            UUID.fromString(view["job"]["id"].asString()),
                        )!!
                )
        }
        val fresh = actor.copy(mfaVerifiedAt = clock.instant())
        val accepted = invoke(fresh)
        assertTrue(accepted is Result.Success, accepted.toString())
        val receipt = (accepted as Result.Success).value
        assertEquals(Result.Success(receipt.copy(replayed = true)), invoke(fresh))
        assertEquals(
            "recent_authentication_required",
            (invoke(actor) as Result.Failed).failure.code,
        )
        assertEquals(before.getValue("operation_receipts") + 1, count(f, "operation_receipts"))
        assertEquals("ACTIVE", employmentStatus(f))
    }
}
