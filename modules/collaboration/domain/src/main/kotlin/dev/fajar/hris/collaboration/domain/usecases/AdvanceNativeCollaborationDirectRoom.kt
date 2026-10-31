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
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Finite reconciliation, no SQL locks during provider I/O, no destructive compensation. */
class AdvanceNativeCollaborationDirectRoom(
    private val conversations: CollaborationDirectRepository,
    private val rooms: CollaborationDirectRoomRepository,
    private val registry: CollaborationRegistryRepository,
    private val credentials: CollaborationProviderSessionRepository,
    private val matrix: CollaborationMatrixRepository,
    private val providerRooms: CollaborationMatrixRoomRepository,
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
        if (!service.enabled || !provider.enabled)
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        val decoded = nativeCollaborationDirectRoomRequest(lease.job.request)
        if (decoded is Result.Failed) return decoded
        val input = (decoded as Result.Success).value
        val job = lease.job.request
        val scope =
            Actor(
                job.actorId,
                job.companyId,
                setOf(COLLABORATION_CHAT_PERMISSION),
                job.authenticatedAt,
                job.correlationId,
                credentialVersion = job.credentialVersion,
            )
        val acquired = transactions.run(scope) { observe(scope, lease, input) }
        if (acquired is Result.Failed) return acquired
        val prepared = (acquired as Result.Success).value
        fun current(): Result<Observation> =
            transactions.run(scope) {
                observe(scope, lease, input).flatMap { observation ->
                    if (
                        observation.pointer != prepared.pointer ||
                            observation.request != prepared.request
                    )
                        return@flatMap changed()
                    val valid =
                        validateCollaborationExchange(
                            prepared.admission,
                            observation.admission,
                            clock.instant(),
                        )
                    if (valid is Result.Failed) changed() else Result.Success(observation)
                }
            }
        // Each exchange repeats current authority before and after I/O, including
        // failures. Provider credentials never enter a client response or a job.
        fun <T> exchange(action: () -> Result<T>): Result<T> {
            val before = current()
            if (before is Result.Failed) return before
            val value = action()
            return current().flatMap { value }
        }
        val previous = prepared.credential
        val login =
            if (
                previous != null &&
                    collaborationProviderCredentialIsCurrent(previous, clock.instant()) &&
                    requireNotNull(previous.expiresAt) > clock.instant().plusSeconds(10)
            ) {
                Result.Success(
                    CollaborationMatrixCredential(
                        previous.matrixUserId,
                        previous.matrixDeviceId,
                        previous.accessToken,
                        requireNotNull(previous.expiresAt),
                    )
                )
            } else
                exchange {
                    matrix.login(
                        CollaborationMatrixLogin(
                            job.companyId,
                            job.actorId,
                            input.deviceId,
                            prepared.admission.admittedAt,
                            prepared.admission.expiresAt,
                        )
                    )
                }
        if (login is Result.Failed) return login
        val credential = (login as Result.Success).value
        val expectedUser =
            "@${collaborationMatrixLocalpart(job.companyId,job.actorId)}:${provider.serverName}"
        if (
            credential.userId != expectedUser ||
                credential.deviceId != collaborationMatrixDeviceId(input.deviceId) ||
                credential.expiresAt > clock.instant().plusSeconds(600)
        )
            return Result.Failed(
                Failure(FailureKind.UNAVAILABLE, "collaboration_provider_invalid_response")
            )
        val deadline = minOf(prepared.admission.expiresAt, credential.expiresAt)
        fun <T> call(action: (Instant) -> Result<T>): Result<T> = exchange {
            if (clock.instant() >= deadline) changed()
            else {
                val response = action(deadline)
                if (clock.instant() >= deadline) changed() else response
            }
        }
        val resolved = call { providerRooms.resolve(prepared.request, credential.accessToken, it) }
        if (resolved is Result.Failed) return resolved
        var roomId = (resolved as Result.Success).value
        if (roomId == null) {
            val created = call {
                providerRooms.create(prepared.request, credential.accessToken, it)
            }
            if (created is Result.Failed) return created
            when (val creation = (created as Result.Success).value) {
                is CollaborationMatrixRoomCreation.Created -> roomId = creation.roomId
                CollaborationMatrixRoomCreation.AliasInUse -> {
                    val recovered = call {
                        providerRooms.resolve(prepared.request, credential.accessToken, it)
                    }
                    if (recovered is Result.Failed) return recovered
                    roomId = (recovered as Result.Success).value ?: return changed()
                }
            }
        }
        val id = roomId
        val observed = call { providerRooms.observe(id, credential.accessToken, it) }
        if (observed is Result.Failed) return observed
        val assessed =
            assessCollaborationDirectRoom(
                prepared.request,
                (observed as Result.Success).value,
                provider.serverName,
            )
        if (assessed is Result.Failed) return assessed
        val repair = (assessed as Result.Success).value
        for (setting in repair.missingSettings) {
            val filled = call {
                providerRooms.putInitialSetting(id, setting, credential.accessToken, it)
            }
            if (filled is Result.Failed) return filled
        }
        if (repair.missingInvite) {
            val invited = call {
                providerRooms.invitePeer(id, prepared.request, credential.accessToken, it)
            }
            if (invited is Result.Failed) return invited
        }
        val finalState = call { providerRooms.observe(id, credential.accessToken, it) }
        if (finalState is Result.Failed) return finalState
        val verified =
            assessCollaborationDirectRoom(
                prepared.request,
                (finalState as Result.Success).value,
                provider.serverName,
            )
        if (verified is Result.Failed) return verified
        val remaining = (verified as Result.Success).value
        if (remaining.missingSettings.isNotEmpty() || remaining.missingInvite) return changed()
        return transactions.run(scope) {
            observe(scope, lease, input).flatMap { latest ->
                if (latest.pointer != prepared.pointer || latest.request != prepared.request)
                    return@flatMap changed()
                val valid =
                    validateCollaborationExchange(
                        prepared.admission,
                        latest.admission,
                        clock.instant(),
                    )
                if (valid is Result.Failed || clock.instant() >= deadline) return@flatMap changed()
                val at = clock.instant().truncatedTo(ChronoUnit.MICROS)
                rooms
                    .publish(
                        CollaborationDirectRoomBinding(
                            job.companyId,
                            input.conversationId,
                            job.actorId,
                            job.id,
                            latest.pointer.version,
                            provider.serverName,
                            id,
                            at,
                        )
                    )
                    .flatMap {
                        jobs.checkpoint(
                            lease,
                            JobProgress(
                                1,
                                mapOf("conversationId" to input.conversationId.toString()),
                            ),
                        )
                    }
                    .flatMap(::leaseChanged)
                    .flatMap { jobs.complete(lease, JobStatus.SUCCEEDED) }
                    .flatMap(::leaseChanged)
                    .flatMap {
                        journal.record(
                            latest.actor,
                            ChangeRecord(
                                "collaboration_direct_conversation",
                                input.conversationId,
                                "collaboration.direct_room_ready",
                                mapOf("version" to latest.pointer.version.toString()),
                            ),
                        )
                    }
                    .flatMap {
                        if (clock.instant() >= deadline) changed()
                        else {
                            val finalProof =
                                validateCollaborationExchange(
                                    prepared.admission,
                                    latest.admission,
                                    clock.instant(),
                                )
                            if (finalProof is Result.Failed) changed()
                            else Result.Success(JobStep(1, true))
                        }
                    }
            }
        }
    }

    private fun observe(
        scope: Actor,
        lease: JobLease,
        input: NativeCollaborationDirectRoomRequest,
    ): Result<Observation> {
        val company = requireNotNull(scope.companyId)
        val locked = jobs.lockLease(lease)
        if (locked is Result.Failed) return locked
        val job = (locked as Result.Success).value ?: return lostLease()
        if (job.request != lease.job.request || job.completedItems != 0) return changed()
        if (job.cancellationRequested)
            return Result.Failed(Failure(FailureKind.CONFLICT, "job_cancellation_requested"))
        val rg = rooms.lock(company, input.conversationId)
        if (rg is Result.Failed) return rg
        val found = conversations.find(company, input.conversationId)
        if (found is Result.Failed) return found
        val conversation = (found as Result.Success).value ?: return denied()
        if (
            conversation.companyId != company ||
                conversation.id != input.conversationId ||
                scope.accountId !in
                    listOf(conversation.firstAccountId, conversation.secondAccountId)
        )
            return denied()
        val pg = policies.lock(company, shared = true)
        if (pg is Result.Failed) return pg
        val cg = companies.lock(company, shared = true)
        if (cg is Result.Failed) return cg
        val mg = members.lock(company, shared = true)
        if (mg is Result.Failed) return mg
        for (account in
            listOf(conversation.firstAccountId, conversation.secondAccountId).sorted()) {
            val ag = identities.lockAccount(account, shared = true)
            if (ag is Result.Failed) return ag
        }
        val parentRead = sessions.lock(input.parentId, scope.accountId)
        if (parentRead is Result.Failed) return parentRead
        val parent = (parentRead as Result.Success).value ?: return denied()
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
        val peerId =
            if (scope.accountId == conversation.firstAccountId) conversation.secondAccountId
            else conversation.firstAccountId
        val peerRead = identities.access(peerId, company)
        if (peerRead is Result.Failed) return peerRead
        val peer = (peerRead as Result.Success).value
        if (
            peer == null ||
                peer.account.id != peerId ||
                !peer.account.active ||
                !peer.membershipActive ||
                !peer.companyActive ||
                COLLABORATION_CHAT_PERMISSION !in peer.permissions
        )
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_peer_unavailable"))
        val deviceRead = registry.findBound(company, scope.accountId, input.parentId)
        if (deviceRead is Result.Failed) return deviceRead
        val device = (deviceRead as Result.Success).value ?: return denied()
        if (
            device.id != input.deviceId ||
                device.companyId != company ||
                device.accountId != scope.accountId ||
                device.nativeSessionId != input.parentId ||
                device.platform != input.platform ||
                device.state != CollaborationDeviceState.ACTIVE ||
                device.revokedAt != null ||
                device.createdAt > clock.instant()
        )
            return denied()
        val pointerRead = rooms.provisioning(company, input.conversationId)
        if (pointerRead is Result.Failed) return pointerRead
        val pointer = (pointerRead as Result.Success).value ?: return changed()
        if (
            pointer.companyId != company ||
                pointer.conversationId != input.conversationId ||
                pointer.accountId != scope.accountId ||
                pointer.deviceId != device.id ||
                pointer.jobId != job.request.id ||
                pointer.updatedAt > clock.instant()
        )
            return changed()
        val binding = rooms.binding(company, input.conversationId)
        if (binding is Result.Failed) return binding
        if ((binding as Result.Success).value != null) return changed()
        val credentialRead = credentials.find(company, scope.accountId, device.id)
        if (credentialRead is Result.Failed) return credentialRead
        val credential = (credentialRead as Result.Success).value
        if (
            credential != null &&
                (credential.companyId != company ||
                    credential.accountId != scope.accountId ||
                    credential.deviceId != device.id ||
                    credential.matrixUserId !=
                        "@${collaborationMatrixLocalpart(company,scope.accountId)}:${provider.serverName}" ||
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
                    .flatMap { parentDeadline ->
                        val deadline = minOf(policyDeadline, parentDeadline, lease.expiresAt)
                        if (deadline <= now) lostLease()
                        else
                            Result.Success(
                                Observation(
                                    actor,
                                    pointer,
                                    credential,
                                    CollaborationMatrixDirectRoom(
                                        company,
                                        input.conversationId,
                                        scope.accountId,
                                        peerId,
                                    ),
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

    private fun leaseChanged(value: Boolean): Result<Unit> =
        if (value) Result.Success(Unit) else lostLease()

    private fun lostLease() = Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))

    private fun changed() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_admission_changed"))

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))

    private class Observation(
        val actor: Actor,
        val pointer: CollaborationDirectRoomProvisioning,
        val credential: CollaborationProviderSession?,
        val request: CollaborationMatrixDirectRoom,
        val admission: CollaborationAdmission,
    )
}
