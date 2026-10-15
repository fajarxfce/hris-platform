package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.LifecycleRepository
import dev.fajar.hris.people.domain.repositories.OnboardingScheduleRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class OnboardingScheduleProbe {
    data class Hook(val company: UUID, val action: () -> Unit)

    data class JournalHook(val company: UUID, val event: String, val action: () -> Unit)

    data class Transform(val company: UUID, val apply: (OnboardingSchedule) -> OnboardingSchedule)

    data class CaseTransform(val company: UUID, val apply: (LifecycleCase) -> LifecycleCase)

    val transformCaseCreate = AtomicReference<CaseTransform?>()
    val afterPeopleGuard = AtomicReference<Hook?>()
    val afterList = AtomicReference<Hook?>()
    val afterFind = AtomicReference<Hook?>()
    val afterJournal = AtomicReference<JournalHook?>()
    val transformCreate = AtomicReference<Transform?>()
    val afterCreate = AtomicReference<Hook?>()
    val beforeForJob = AtomicReference<Hook?>()
    val afterQueueLock = AtomicReference<Hook?>()
    val afterCheckpoint = AtomicReference<Hook?>()
    val failCheckpoint = AtomicReference<UUID?>()

    fun hit(reference: AtomicReference<Hook?>, company: UUID) {
        reference
            .get()
            ?.takeIf { it.company == company && reference.compareAndSet(it, null) }
            ?.action
            ?.invoke()
    }

    fun clear() {
        transformCaseCreate.set(null)
        afterPeopleGuard.set(null)
        afterList.set(null)
        afterFind.set(null)
        afterJournal.set(null)
        transformCreate.set(null)
        afterCreate.set(null)
        beforeForJob.set(null)
        afterQueueLock.set(null)
        afterCheckpoint.set(null)
        failCheckpoint.set(null)
    }
}

@TestConfiguration(proxyBeanMethods = false)
class OnboardingScheduleProbeConfiguration {
    @Bean fun onboardingScheduleProbe() = OnboardingScheduleProbe()

    @Bean
    @Primary
    fun probedOnboardingLifecycle(
        @Qualifier("lifecycle") delegate: LifecycleRepository,
        probe: OnboardingScheduleProbe,
    ): LifecycleRepository =
        object : LifecycleRepository by delegate {
            override fun create(
                actor: Actor,
                case: LifecycleCase,
                reason: String,
            ): Result<MutationReceipt> {
                val transform =
                    probe.transformCaseCreate.get()?.takeIf {
                        it.company == actor.companyId &&
                            probe.transformCaseCreate.compareAndSet(it, null)
                    }
                return delegate.create(actor, transform?.apply?.invoke(case) ?: case, reason)
            }
        }

    @Bean
    @Primary
    fun probedOnboardingPeople(
        @Qualifier("people") delegate: PeopleRepository,
        probe: OnboardingScheduleProbe,
    ): PeopleRepository =
        object : PeopleRepository by delegate {
            override fun lockReportingLines(companyId: UUID, shared: Boolean): Result<Unit> =
                delegate.lockReportingLines(companyId, shared).also {
                    if (it is Result.Success) probe.hit(probe.afterPeopleGuard, companyId)
                }
        }

    @Bean
    @Primary
    fun probedOnboardingSchedules(
        @Qualifier("onboardingSchedules") delegate: OnboardingScheduleRepository,
        probe: OnboardingScheduleProbe,
    ): OnboardingScheduleRepository =
        object : OnboardingScheduleRepository by delegate {
            override fun forEmployee(
                companyId: UUID,
                employmentId: UUID,
            ): Result<List<OnboardingScheduleDetails>> =
                delegate.forEmployee(companyId, employmentId).also {
                    if (it is Result.Success) probe.hit(probe.afterList, companyId)
                }

            override fun find(companyId: UUID, id: UUID): Result<OnboardingScheduleDetails?> =
                delegate.find(companyId, id).also {
                    if (it is Result.Success) probe.hit(probe.afterFind, companyId)
                }

            override fun create(
                actor: Actor,
                schedule: OnboardingSchedule,
            ): Result<MutationReceipt> {
                val transform =
                    probe.transformCreate.get()?.takeIf {
                        it.company == actor.companyId &&
                            probe.transformCreate.compareAndSet(it, null)
                    }
                return delegate.create(actor, transform?.apply?.invoke(schedule) ?: schedule).also {
                    if (it is Result.Success)
                        probe.hit(probe.afterCreate, requireNotNull(actor.companyId))
                }
            }

            override fun forJob(companyId: UUID, jobId: UUID): Result<OnboardingSchedule?> {
                probe.hit(probe.beforeForJob, companyId)
                return delegate.forJob(companyId, jobId)
            }
        }

    @Bean
    @Primary
    fun probedOnboardingJobs(
        @Qualifier("jobs") delegate: JobRepository,
        probe: OnboardingScheduleProbe,
    ): JobRepository =
        object : JobRepository by delegate {
            override fun lockQueue(companyId: UUID): Result<Unit> =
                delegate.lockQueue(companyId).also {
                    if (it is Result.Success) probe.hit(probe.afterQueueLock, companyId)
                }

            override fun checkpoint(lease: JobLease, progress: JobProgress): Result<Boolean> {
                val result = delegate.checkpoint(lease, progress)
                if (result is Result.Failed) return result
                probe.hit(probe.afterCheckpoint, lease.job.request.companyId)
                val failed =
                    probe.failCheckpoint.get()?.takeIf {
                        it == lease.job.request.companyId &&
                            probe.failCheckpoint.compareAndSet(it, null)
                    }
                return if (failed != null)
                    Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_checkpoint_failure"))
                else result
            }
        }

    @Bean
    @Primary
    fun probedOnboardingJournal(
        @Qualifier("changeJournal") delegate: ChangeJournalRepository,
        probe: OnboardingScheduleProbe,
    ): ChangeJournalRepository =
        object : ChangeJournalRepository by delegate {
            override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                delegate.record(actor, change).also { result ->
                    if (result is Result.Success)
                        probe.afterJournal
                            .get()
                            ?.takeIf {
                                it.company == actor.companyId &&
                                    it.event == change.action &&
                                    probe.afterJournal.compareAndSet(it, null)
                            }
                            ?.action
                            ?.invoke()
                }
        }
}
