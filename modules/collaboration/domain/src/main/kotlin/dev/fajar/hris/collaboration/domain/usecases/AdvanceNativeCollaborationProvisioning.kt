package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.time.temporal.ChronoUnit

/**
 * The provider call holds no SQL locks; publication repeats authority and lease/generation checks.
 */
class AdvanceNativeCollaborationProvisioning(
    private val registry: CollaborationRegistryRepository,
    private val provisioning: CollaborationProvisioningRepository,
    private val credentials: CollaborationProviderSessionRepository,
    private val matrix: CollaborationMatrixRepository,
    private val jobs: JobRepository,
    private val sessions: NativeSessionRepository,
    private val policies: CompanyClientPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val service: CollaborationServicePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val clock: Clock,
) {
    fun execute(lease: JobLease): Result<JobStep> {
        if (!service.enabled || !provider.enabled) return unavailable()
        val decoded = nativeCollaborationProvisioningRequest(lease.job.request)
        if (decoded is Result.Failed) return decoded
        val input = (decoded as Result.Success).value
        val request = lease.job.request
        val scope =
            Actor(
                request.actorId,
                request.companyId,
                setOf(COLLABORATION_CHAT_PERMISSION),
                request.authenticatedAt,
                request.correlationId,
                credentialVersion = request.credentialVersion,
            )
        val preparation = transactions.run(scope) { observe(scope, lease, input) }
        if (preparation is Result.Failed) return retryObservation(preparation)
        val prepared = (preparation as Result.Success).value
        val existing = prepared.credential
        val candidate =
            if (
                existing != null &&
                    collaborationProviderCredentialIsCurrent(existing, clock.instant())
            ) {
                Result.Success(
                    CollaborationMatrixCredential(
                        existing.matrixUserId,
                        existing.matrixDeviceId,
                        existing.accessToken,
                        requireNotNull(existing.expiresAt),
                    )
                )
            } else
                matrix.login(
                    CollaborationMatrixLogin(
                        request.companyId,
                        request.actorId,
                        input.deviceId,
                        prepared.admission.admittedAt,
                        prepared.admission.expiresAt,
                    )
                )
        if (candidate is Result.Failed)
            return if (candidate.failure.code == "collaboration_admission_changed") changed()
            else candidate
        val value = (candidate as Result.Success).value
        if (
            value.userId != expectedUser(scope) ||
                value.deviceId != collaborationMatrixDeviceId(input.deviceId)
        )
            return Result.Failed(
                Failure(FailureKind.UNAVAILABLE, "collaboration_provider_invalid_response")
            )
        // A losing token is never published. Its bounded private-provider lifetime retires it;
        // Matrix logout would destroy the winning device and must not be used here.
        val publication =
            transactions.run(scope) {
                observe(scope, lease, input).flatMap { current ->
                    if (
                        current.pointer != prepared.pointer ||
                            current.credential?.generation != prepared.credential?.generation
                    )
                        return@flatMap changed()
                    val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
                    val valid =
                        validateCollaborationExchange(
                            prepared.admission,
                            current.admission,
                            clock.instant(),
                        )
                    if (valid is Result.Failed) return@flatMap changed()
                    if (
                        value.expiresAt <= clock.instant() || value.expiresAt > now.plusSeconds(600)
                    )
                        return@flatMap changed()
                    val prior = current.credential
                    val stored =
                        if (
                            prior != null &&
                                prior.accessToken == value.accessToken &&
                                prior.expiresAt == value.expiresAt
                        ) {
                            Result.Success(Unit)
                        } else {
                            val next =
                                CollaborationProviderSession(
                                    request.companyId,
                                    request.actorId,
                                    input.deviceId,
                                    value.userId,
                                    value.deviceId,
                                    (prior?.generation ?: -1) + 1,
                                    value.accessToken,
                                    prior?.createdAt ?: now,
                                    now,
                                    value.expiresAt,
                                )
                            if (prior == null) credentials.create(next)
                            else credentials.rotate(next, prior.generation)
                        }
                    stored
                        .flatMap {
                            registry.activate(
                                request.companyId,
                                request.actorId,
                                input.deviceId,
                                current.device.version,
                                now,
                            )
                        }
                        .flatMap { if (it) Result.Success(Unit) else changed() }
                        .flatMap {
                            jobs.checkpoint(
                                lease,
                                JobProgress(1, mapOf("deviceId" to input.deviceId.toString())),
                            )
                        }
                        .flatMap(::leaseChanged)
                        .flatMap { jobs.complete(lease, JobStatus.SUCCEEDED) }
                        .flatMap(::leaseChanged)
                        .flatMap {
                            journal.record(
                                current.actor,
                                ChangeRecord(
                                    "collaboration_device",
                                    input.deviceId,
                                    "collaboration.device_activated",
                                    mapOf("version" to (current.device.version + 1).toString()),
                                ),
                            )
                        }
                        .flatMap {
                            val finalProof =
                                validateCollaborationExchange(
                                    prepared.admission,
                                    current.admission,
                                    clock.instant(),
                                )
                            if (finalProof is Result.Failed) changed() else finalProof
                        }
                        .flatMap {
                            if (value.expiresAt <= clock.instant()) changed()
                            else Result.Success(JobStep(1, true))
                        }
                }
            }
        return retryObservation(publication)
    }

    /**
     * Only an obsolete observation is retryable; actual access/MFA/maintenance denials remain
     * final.
     */
    private fun <T> retryObservation(result: Result<T>): Result<T> =
        if (result is Result.Failed && result.failure.code == "collaboration_admission_changed")
            changed()
        else result

    /**
     * Caller owns a transaction. Lock order starts with the lease, then the ordinary authority
     * guards.
     */
    private fun observe(
        scope: Actor,
        lease: JobLease,
        input: NativeCollaborationProvisioningRequest,
    ): Result<Observation> {
        val company = requireNotNull(scope.companyId)
        val locked = jobs.lockLease(lease)
        if (locked is Result.Failed) return locked
        val job = (locked as Result.Success).value ?: return lostLease()
        if (job.request != lease.job.request || job.completedItems != 0) return changed()
        if (job.cancellationRequested)
            return Result.Failed(Failure(FailureKind.CONFLICT, "job_cancellation_requested"))
        val policyGuard = policies.lock(company, shared = true)
        if (policyGuard is Result.Failed) return policyGuard
        val companyGuard = companies.lock(company, shared = true)
        if (companyGuard is Result.Failed) return companyGuard
        val memberGuard = members.lock(company, shared = true)
        if (memberGuard is Result.Failed) return memberGuard
        val accountGuard = identities.lockAccount(scope.accountId)
        if (accountGuard is Result.Failed) return accountGuard
        val lockedParent = sessions.lock(input.parentId, scope.accountId)
        if (lockedParent is Result.Failed) return lockedParent
        val parent = (lockedParent as Result.Success).value ?: return denied()
        if (
            parent.id != input.parentId ||
                parent.accountId != scope.accountId ||
                parent.credentialVersion != scope.credentialVersion
        )
            return denied()
        val actor =
            scope.copy(
                authenticatedAt = parent.authenticatedAt,
                mfaVerifiedAt = parent.mfaVerifiedAt,
            )
        val acquired = identities.access(scope.accountId, company)
        if (acquired is Result.Failed) return acquired
        val access = (acquired as Result.Success).value
        val allowed =
            collaborationParentDeadline(
                actor,
                access,
                parent,
                parent.version,
                clock.instant(),
                security,
            )
        if (allowed is Result.Failed) return allowed
        val found = registry.findBound(company, scope.accountId, input.parentId)
        if (found is Result.Failed) return found
        val device = (found as Result.Success).value ?: return denied()
        if (
            device.id != input.deviceId ||
                device.companyId != company ||
                device.accountId != scope.accountId ||
                device.nativeSessionId != input.parentId ||
                device.platform != input.platform ||
                device.state != CollaborationDeviceState.PROVISIONING ||
                device.revokedAt != null ||
                device.createdAt > clock.instant()
        )
            return denied()
        val pointed = provisioning.find(company, scope.accountId, device.id)
        if (pointed is Result.Failed) return pointed
        val pointer = (pointed as Result.Success).value ?: return changed()
        if (
            pointer.jobId != job.request.id ||
                pointer.companyId != company ||
                pointer.accountId != scope.accountId ||
                pointer.deviceId != device.id ||
                pointer.version < 0 ||
                pointer.updatedAt < pointer.createdAt ||
                pointer.updatedAt > clock.instant()
        )
            return changed()
        val stored = credentials.find(company, scope.accountId, device.id)
        if (stored is Result.Failed) return stored
        val credential = (stored as Result.Success).value
        if (
            credential != null &&
                (credential.companyId != company ||
                    credential.accountId != scope.accountId ||
                    credential.deviceId != device.id ||
                    credential.matrixUserId != expectedUser(scope) ||
                    credential.matrixDeviceId != collaborationMatrixDeviceId(device.id))
        )
            return Result.Failed(
                Failure(FailureKind.UNEXPECTED, "collaboration_provider_identity_changed")
            )
        val effective = policies.effective(company, clock.instant())
        if (effective is Result.Failed) return effective
        val now = clock.instant()
        return nativeCollaborationClientDeadline(
                (effective as Result.Success).value,
                input.client,
                now,
            )
            .flatMap { policyDeadline ->
                collaborationParentDeadline(actor, access, parent, parent.version, now, security)
                    .flatMap parent@{ parentDeadline ->
                        val deadline = minOf(policyDeadline, parentDeadline, lease.expiresAt)
                        if (deadline <= now) return@parent lostLease()
                        Result.Success(
                            Observation(
                                actor,
                                device,
                                pointer,
                                credential,
                                CollaborationAdmission(
                                    company,
                                    scope.accountId,
                                    device.id,
                                    device.version,
                                    parent.id,
                                    parent.version,
                                    parent.credentialVersion,
                                    now,
                                    deadline,
                                ),
                            )
                        )
                    }
            }
    }

    private fun expectedUser(actor: Actor) =
        "@${collaborationMatrixLocalpart(requireNotNull(actor.companyId), actor.accountId)}:${provider.serverName}"

    private fun leaseChanged(value: Boolean): Result<Unit> =
        if (value) Result.Success(Unit) else lostLease()

    private fun lostLease() = Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))

    private fun changed() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_admission_changed"))

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))

    private fun unavailable() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))

    private class Observation(
        val actor: Actor,
        val device: CollaborationDevice,
        val pointer: CollaborationProvisioning,
        val credential: CollaborationProviderSession?,
        val admission: CollaborationAdmission,
    )
}
