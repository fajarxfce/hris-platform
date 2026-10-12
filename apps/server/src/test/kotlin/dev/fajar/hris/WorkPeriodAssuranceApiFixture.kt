package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.MembershipRepository
import dev.fajar.hris.jobs.domain.entities.JobStep
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.workforce.domain.repositories.*
import dev.fajar.hris.workforce.domain.usecases.*
import java.time.YearMonth
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired

/** Fixtures use the HTTP test policy; the exercised use cases require current MFA. */
abstract class WorkPeriodAssuranceApiFixture : WorkforceAccessApiFixture() {
    @Autowired private lateinit var periods: WorkPeriodRepository
    @Autowired private lateinit var jobs: JobRepository
    @Autowired private lateinit var people: PeopleRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var schedules: ScheduleRepository
    @Autowired private lateinit var operations: OperationRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var overtime: OvertimeRepository
    @Autowired private lateinit var identities: IdentityRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var advance: AdvanceWorkPeriodClose
    protected val security = IdentitySecurityPolicy()

    protected data class Scenario(
        val fixture: Fixture,
        val action: String,
        val operation: UUID,
        val version: Long,
        val actor: Actor,
    )

    protected fun prepare(action: String): Scenario {
        val f = isolatedFixture()
        if (action in setOf("recover", "snapshot")) {
            val lease = begin(f)
            if (action == "snapshot") {
                assertEquals(Result.Success(JobStep(1, false)), advance.execute(f.actor, lease))
                assertEquals(Result.Success(JobStep(2, true)), advance.execute(f.actor, lease))
            } else {
                database()
                    .update(
                        "update background_jobs set attempts=8,lease_until=now()-interval '1 second' where id=?",
                        lease.job.request.id,
                    )
                assertTrue(claim().isEmpty())
            }
        }
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.actor.accountId,
            )
        return Scenario(
            f,
            action,
            UUID.randomUUID(),
            period(f)["version"].asLong(),
            f.actor.copy(mfaVerifiedAt = clock.instant()),
        )
    }

    protected fun invoke(scene: Scenario, actor: Actor = scene.actor): Result<*> {
        val month = YearMonth.parse("2026-09")
        return when (scene.action) {
            "close" ->
                StartWorkPeriodClose(
                        periods,
                        jobs,
                        people,
                        companies,
                        schedules,
                        operations,
                        journal,
                        transactions,
                        clock,
                        overtime,
                        identities,
                        members,
                        security,
                    )
                    .execute(actor, scene.operation, month, scene.version, "Monthly review")
            "recover" ->
                RecoverWorkPeriod(
                        periods,
                        jobs,
                        operations,
                        journal,
                        companies,
                        members,
                        identities,
                        transactions,
                        security,
                        clock,
                    )
                    .execute(
                        actor,
                        scene.operation,
                        month,
                        scene.version,
                        "Recover stopped attempt",
                    )
            "list" ->
                ListWorkPeriods(
                        periods,
                        companies,
                        members,
                        identities,
                        transactions,
                        security,
                        clock,
                    )
                    .execute(actor, month, month)
            "snapshot" ->
                GetWorkPeriodSnapshot(
                        periods,
                        people,
                        companies,
                        members,
                        identities,
                        transactions,
                        clock,
                        security,
                    )
                    .execute(actor, month, scene.fixture.employee)
            else -> error("Unknown closing fixture action")
        }
    }

    protected fun stored(scene: Scenario): Map<String, Any?> {
        val tables =
            listOf(
                "work_periods",
                "work_period_targets",
                "work_period_snapshots",
                "background_jobs",
                "operation_receipts",
                "audit_entries",
                "outbox_events",
            )
        return tables.associateWith { table ->
            database()
                .queryForObject(
                    "select count(*) from $table where company_id=?",
                    Int::class.java,
                    scene.fixture.company,
                )
        } + ("period" to period(scene.fixture))
    }
}
