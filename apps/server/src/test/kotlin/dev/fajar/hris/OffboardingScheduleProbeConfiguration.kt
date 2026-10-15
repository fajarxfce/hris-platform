package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.OffboardingScheduleRepository
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class OffboardingScheduleProbe {
    data class Hook(val company: UUID, val action: () -> Unit)

    data class JournalHook(val company: UUID, val event: String, val action: () -> Unit)

    data class Transform(val company: UUID, val apply: (OffboardingSchedule) -> OffboardingSchedule)

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
class OffboardingScheduleProbeConfiguration {
    @Bean fun offboardingScheduleProbe() = OffboardingScheduleProbe()

    @Bean
    @Primary
    fun probedOffboardingSchedules(
        @Qualifier("offboardingSchedules") delegate: OffboardingScheduleRepository,
        probe: OffboardingScheduleProbe,
    ): OffboardingScheduleRepository =
        object : OffboardingScheduleRepository by delegate {
            override fun forCase(
                companyId: UUID,
                caseId: UUID,
            ): Result<List<OffboardingScheduleDetails>> =
                delegate.forCase(companyId, caseId).also {
                    if (it is Result.Success) probe.hit(probe.afterList, companyId)
                }

            override fun find(companyId: UUID, id: UUID): Result<OffboardingScheduleDetails?> =
                delegate.find(companyId, id).also {
                    if (it is Result.Success) probe.hit(probe.afterFind, companyId)
                }

            override fun create(
                actor: Actor,
                schedule: OffboardingSchedule,
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

            override fun forJob(companyId: UUID, jobId: UUID): Result<OffboardingSchedule?> {
                probe.hit(probe.beforeForJob, companyId)
                return delegate.forJob(companyId, jobId)
            }
        }

    @Bean
    @Primary
    fun probedOffboardingJobs(
        @Qualifier("jobs") delegate: JobRepository,
        probe: OffboardingScheduleProbe,
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
    fun probedOffboardingJournal(
        @Qualifier("changeJournal") delegate: ChangeJournalRepository,
        probe: OffboardingScheduleProbe,
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
