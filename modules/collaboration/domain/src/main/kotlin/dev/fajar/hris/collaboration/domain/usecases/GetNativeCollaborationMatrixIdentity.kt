package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** A fixed provider operation; all network I/O occurs between scoped admission transactions. */
class GetNativeCollaborationMatrixIdentity(
    private val gateway: CollaborationGatewaySessionRepository,
    private val registry: CollaborationRegistryRepository,
    private val credentials: CollaborationProviderSessionRepository,
    private val matrix: CollaborationMatrixRepository,
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
    fun execute(token: String, correlationId: UUID): Result<CollaborationMatrixIdentity> {
        if (!service.enabled || !provider.enabled)
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        if (!validCollaborationGatewayAccess(token))
            return collaborationGatewayAuthenticationRequired()
        val lookup = Actor(UUID(0, 0), null, emptySet(), Instant.EPOCH, correlationId)
        val resolved = transactions.run(lookup) { gateway.findAccess(token) }
        if (resolved is Result.Failed) return resolved
        val initial =
            (resolved as Result.Success).value
                ?: return collaborationGatewayAuthenticationRequired()
        val scope = lookup.copy(accountId = initial.accountId, companyId = initial.companyId)
        val captured = transactions.run(scope) { observe(scope, initial) }
        if (captured is Result.Failed) return captured
        val prepared = (captured as Result.Success).value
        val credential =
            if (usable(prepared.credential)) Result.Success(requireNotNull(prepared.credential))
            else renew(scope, initial, prepared)
        if (credential is Result.Failed) return credential
        val active = (credential as Result.Success).value
        val deadline =
            minOf(
                prepared.admission.expiresAt,
                initial.accessExpiresAt,
                requireNotNull(active.expiresAt),
            )
        if (clock.instant() >= deadline) return changed()
        val response = matrix.whoAmI(active.accessToken, deadline)
        // Even failure responses are withheld if HRIS authority was removed during the exchange.
        return transactions.run(scope) {
            observe(scope, initial).flatMap { current ->
                validateObservation(prepared.admission, current.admission).flatMap {
                    if (clock.instant() >= deadline) return@flatMap changed()
                    response.flatMap { identity ->
                        if (
                            identity.userId != expectedUser(initial) ||
                                identity.deviceId !=
                                    collaborationMatrixDeviceId(initial.deviceId) ||
                                identity.isGuest
                        )
                            invalidProvider()
                        else Result.Success(identity)
                    }
                }
            }
        }
    }

    private fun renew(
        scope: Actor,
        initial: CollaborationGatewaySession,
        prepared: Observation,
    ): Result<CollaborationProviderSession> {
        val issued =
            matrix.login(
                CollaborationMatrixLogin(
                    initial.companyId,
                    initial.accountId,
                    initial.deviceId,
                    prepared.admission.admittedAt,
                    prepared.admission.expiresAt,
                )
            )
        // Losing/late candidates expire privately. Synapse logout would erase the winning keys.
        return transactions.run(scope) {
            observe(scope, initial).flatMap { current ->
                validateObservation(prepared.admission, current.admission).flatMap publication@{
                    if (issued is Result.Failed) return@publication issued
                    val candidate = (issued as Result.Success).value
                    if (
                        candidate.userId != expectedUser(initial) ||
                            candidate.deviceId != collaborationMatrixDeviceId(initial.deviceId)
                    )
                        return@publication invalidProvider()
                    val prior = current.credential
                    if (prior?.generation != prepared.credential?.generation)
                        return@publication if (usable(prior)) Result.Success(requireNotNull(prior))
                        else changed()
                    val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
                    if (
                        candidate.expiresAt <= now.plusSeconds(10) ||
                            candidate.expiresAt > now.plusSeconds(600)
                    )
                        return@publication invalidProvider()
                    if ((prior?.generation ?: -1) >= 999999)
                        return@publication Result.Failed(
                            Failure(
                                FailureKind.UNAUTHENTICATED,
                                "collaboration_provider_renewal_required",
                            )
                        )
                    val next =
                        CollaborationProviderSession(
                            initial.companyId,
                            initial.accountId,
                            initial.deviceId,
                            candidate.userId,
                            candidate.deviceId,
                            (prior?.generation ?: -1) + 1,
                            candidate.accessToken,
                            prior?.createdAt ?: now,
                            now,
                            candidate.expiresAt,
                        )
                    val stored =
                        if (prior == null) credentials.create(next)
                        else credentials.rotate(next, prior.generation)
                    stored
                        .flatMap {
                            journal.record(
                                current.actor,
                                ChangeRecord(
                                    "collaboration_device",
                                    initial.deviceId,
                                    "collaboration.provider_renewed",
                                    mapOf("generation" to next.generation.toString()),
                                ),
                            )
                        }
                        .flatMap { validateObservation(prepared.admission, current.admission) }
                        .flatMap {
                            if (initial.accessExpiresAt <= clock.instant() || !usable(next))
                                changed()
                            else Result.Success(next)
                        }
                }
            }
        }
    }

    /** The token lookup identifies locks only; this method reacquires every authority input. */
    private fun observe(scope: Actor, initial: CollaborationGatewaySession): Result<Observation> {
        val company = initial.companyId
        val pg = policies.lock(company, shared = true)
        if (pg is Result.Failed) return pg
        val cg = companies.lock(company, shared = true)
        if (cg is Result.Failed) return cg
        val mg = members.lock(company, shared = true)
        if (mg is Result.Failed) return mg
        val ag = identities.lockAccount(initial.accountId, shared = true)
        if (ag is Result.Failed) return ag
        val locked = sessions.lock(initial.nativeSessionId, initial.accountId)
        if (locked is Result.Failed) return locked
        val parent =
            (locked as Result.Success).value ?: return collaborationGatewayAuthenticationRequired()
        if (parent.id != initial.nativeSessionId || parent.accountId != initial.accountId)
            return collaborationGatewayAuthenticationRequired()
        val actor =
            scope.copy(
                permissions = setOf(COLLABORATION_CHAT_PERMISSION),
                authenticatedAt = parent.authenticatedAt,
                mfaVerifiedAt = parent.mfaVerifiedAt,
                credentialVersion = parent.credentialVersion,
            )
        val acquired = identities.access(initial.accountId, company)
        if (acquired is Result.Failed) return acquired
        val access = (acquired as Result.Success).value
        val bound = registry.findBound(company, initial.accountId, parent.id)
        if (bound is Result.Failed) return bound
        val device = (bound as Result.Success).value
        if (device == null || device.id != initial.deviceId || device.platform != initial.platform)
            return collaborationGatewayAuthenticationRequired()
        val read = gateway.find(company, initial.accountId, initial.deviceId, lock = true)
        if (read is Result.Failed) return read
        val head =
            (read as Result.Success).value ?: return collaborationGatewayAuthenticationRequired()
        if (head != initial || head.accessExpiresAt <= clock.instant())
            return collaborationGatewayAuthenticationRequired()
        val stored = credentials.find(company, initial.accountId, device.id)
        if (stored is Result.Failed) return stored
        val credential = (stored as Result.Success).value
        if (
            credential != null &&
                (credential.companyId != company ||
                    credential.accountId != initial.accountId ||
                    credential.deviceId != device.id ||
                    credential.matrixUserId != expectedUser(initial) ||
                    credential.matrixDeviceId != collaborationMatrixDeviceId(device.id))
        )
            return invalidProvider()
        val effective = policies.effective(company, clock.instant())
        if (effective is Result.Failed) return effective
        val at = clock.instant()
        val client =
            ClientVersion(
                if (head.platform == CollaborationPlatform.ANDROID) ClientPlatform.ANDROID
                else ClientPlatform.IOS,
                head.clientBuild,
            )
        return nativeCollaborationClientDeadline((effective as Result.Success).value, client, at)
            .flatMap { deadline ->
                admitCollaborationDevice(
                        actor,
                        access,
                        parent,
                        parent.version,
                        device,
                        at,
                        security,
                    )
                    .flatMap {
                        val admission =
                            it.copy(expiresAt = minOf(it.expiresAt, deadline, head.accessExpiresAt))
                        validateCollaborationGatewaySession(head, admission, device.platform, at)
                            .map { Observation(actor, admission, credential) }
                    }
            }
    }

    // Live denial is returned by observe. A stale captured observation only asks for reacquisition;
    // it must not look like permanent permission removal after an ordinary native refresh.
    private fun validateObservation(
        prepared: CollaborationAdmission,
        current: CollaborationAdmission,
    ): Result<Unit> =
        if (validateCollaborationExchange(prepared, current, clock.instant()) is Result.Failed)
            changed()
        else Result.Success(Unit)

    private fun usable(credential: CollaborationProviderSession?): Boolean =
        credential != null &&
            collaborationProviderCredentialIsCurrent(credential, clock.instant()) &&
            requireNotNull(credential.expiresAt) > clock.instant().plusSeconds(10)

    private fun expectedUser(session: CollaborationGatewaySession) =
        "@${collaborationMatrixLocalpart(session.companyId, session.accountId)}:${provider.serverName}"

    private fun invalidProvider() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_provider_invalid_response"))

    private fun changed() =
        Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_admission_changed"))

    private class Observation(
        val actor: Actor,
        val admission: CollaborationAdmission,
        val credential: CollaborationProviderSession?,
    )
}
