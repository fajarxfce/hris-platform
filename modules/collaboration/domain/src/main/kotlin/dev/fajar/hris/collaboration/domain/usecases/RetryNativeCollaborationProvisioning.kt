package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.nativeAuthenticationRequired
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Explicit retry creates one new bounded job, never a replacement Matrix device. */
class RetryNativeCollaborationProvisioning(
    private val registry: CollaborationRegistryRepository,
    private val provisioning: CollaborationProvisioningRepository,
    private val jobs: JobRepository,
    private val sessions: NativeSessionRepository,
    private val policies: CompanyClientPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val operations: OperationRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
    private val service: CollaborationServicePolicy,
) {
    fun execute(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        operationId: UUID,
        deviceId: UUID,
        expectedDeviceVersion: Long,
        expectedProvisioningVersion: Long,
        client: ClientVersion?,
    ): Result<MutationReceipt> {
        val permission = actor.requirePermission(COLLABORATION_CHAT_PERMISSION)
        if (permission is Result.Failed) return permission
        if (!service.enabled)
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        if (expectedDeviceVersion !in 0..999999 || expectedProvisioningVersion !in 0..999998)
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_provisioning_version")
            )
        val key =
            OperationKey(
                "collaboration.native_provisioning_retry",
                operationId,
                listOf(
                    sessionId.toString(),
                    deviceId.toString(),
                    expectedDeviceVersion.toString(),
                    expectedProvisioningVersion.toString(),
                ),
            )
        return transactions.run(actor) {
            val replay = operations.lockAndReplay(actor, key)
            if (replay is Result.Failed) return@run replay
            val policyGuard = policies.lock(company, shared = true)
            if (policyGuard is Result.Failed) return@run policyGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId)
            if (accountGuard is Result.Failed) return@run accountGuard
            val locked = sessions.lock(sessionId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val session = (locked as Result.Success).value
            if (session?.id != sessionId) return@run nativeAuthenticationRequired()
            val acquired = identities.access(actor.accountId, company)
            if (acquired is Result.Failed) return@run acquired
            val access = (acquired as Result.Success).value
            fun authorized(): Result<Unit> {
                val effective = policies.effective(company, clock.instant())
                if (effective is Result.Failed) return effective
                val at = clock.instant()
                return nativeCollaborationClientDeadline(
                        (effective as Result.Success).value,
                        client,
                        at,
                    )
                    .flatMap {
                        collaborationParentDeadline(
                            actor,
                            access,
                            session,
                            sessionVersion,
                            at,
                            security,
                        )
                    }
                    .map { Unit }
            }
            val allowed = authorized()
            if (allowed is Result.Failed) return@run allowed
            val found = registry.findBound(company, actor.accountId, sessionId)
            if (found is Result.Failed) return@run found
            val device = (found as Result.Success).value
            if (
                device == null ||
                    device.id != deviceId ||
                    device.companyId != company ||
                    device.accountId != actor.accountId ||
                    device.nativeSessionId != sessionId ||
                    device.createdAt > clock.instant() ||
                    device.state == CollaborationDeviceState.REVOKED ||
                    device.revokedAt != null
            )
                return@run Result.Failed(
                    Failure(FailureKind.FORBIDDEN, "collaboration_access_denied")
                )
            if (client != null && client.platform.name != device.platform.name)
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "invalid_collaboration_device")
                )
            val previous = (replay as Result.Success).value
            if (previous != null) return@run authorized().map { previous }
            if (
                device.state != CollaborationDeviceState.PROVISIONING ||
                    device.version != expectedDeviceVersion
            )
                return@run changed()
            val read = provisioning.find(company, actor.accountId, deviceId)
            if (read is Result.Failed) return@run read
            val pointer = (read as Result.Success).value ?: return@run changed()
            if (
                pointer.version != expectedProvisioningVersion ||
                    pointer.updatedAt > clock.instant()
            )
                return@run changed()
            // Do not lock a job after the account/device. Terminal jobs are immutable; this
            // nonlocking read and the database replacement constraint protect the old pointer.
            val old = jobs.find(company, pointer.jobId)
            if (old is Result.Failed) return@run old
            val job = (old as Result.Success).value ?: return@run changed()
            if (!collaborationProvisioningMatches(device, pointer, job))
                return@run Result.Failed(
                    Failure(FailureKind.UNEXPECTED, "invalid_collaboration_provisioning")
                )
            if (job.status !in setOf(JobStatus.FAILED, JobStatus.CANCELLED)) return@run changed()
            val earliest = pointer.updatedAt.plusSeconds(COLLABORATION_PROVISIONING_RETRY_SECONDS)
            val at = clock.instant()
            if (at < earliest)
                return@run Result.Failed(
                    Failure(
                        FailureKind.RATE_LIMITED,
                        "collaboration_provisioning_retry_too_soon",
                        parameters =
                            mapOf(
                                "retryAfterSeconds" to
                                    ((Duration.between(at, earliest).toMillis() + 999) / 1000)
                                        .toString()
                            ),
                    )
                )
            val queue = jobs.lockQueue(company)
            if (queue is Result.Failed) return@run queue
            val count = jobs.pendingCount(company)
            if (count is Result.Failed) return@run count
            if ((count as Result.Success).value >= 100)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "job_queue_full"))
            val createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS)
            val request =
                JobRequest(
                    UUID.randomUUID(),
                    company,
                    actor.accountId,
                    JobKind.COLLABORATION_PROVISION,
                    operationId,
                    buildMap {
                        put("deviceId", deviceId.toString())
                        put("nativeSessionId", sessionId.toString())
                        put("platform", device.platform.name)
                        if (client != null) put("clientBuild", client.build.toString())
                    },
                    session.authenticatedAt,
                    session.credentialVersion,
                    actor.correlationId,
                    createdAt,
                    1,
                )
            val receipt = MutationReceipt(request.id, 0)
            jobs
                .create(request)
                .flatMap {
                    provisioning.replace(
                        pointer.copy(
                            jobId = request.id,
                            version = pointer.version + 1,
                            updatedAt = createdAt,
                        ),
                        pointer.version,
                    )
                }
                .flatMap { operations.record(actor, key, receipt) }
                .flatMap {
                    journal.record(
                        actor,
                        ChangeRecord(
                            "collaboration_device",
                            deviceId,
                            "collaboration.device_provisioning_retried",
                            mapOf("provisioningVersion" to (pointer.version + 1).toString()),
                        ),
                    )
                }
                .flatMap { authorized().map { receipt } }
        }
    }

    private fun changed() =
        Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_provisioning_changed"))
}
