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

/** Standard Matrix refresh has no operation UUID; only the latest bounded response may replay. */
class RefreshNativeCollaborationGateway(
    private val gateway: CollaborationGatewaySessionRepository,
    private val registry: CollaborationRegistryRepository,
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
    fun execute(token: String, correlationId: UUID): Result<CollaborationGatewayTokens> {
        if (!service.enabled || !provider.enabled)
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        if (!validCollaborationGatewayRefresh(token))
            return collaborationGatewayAuthenticationRequired()
        val lookupScope = Actor(UUID(0, 0), null, emptySet(), Instant.EPOCH, correlationId)
        val resolved = transactions.run(lookupScope) { gateway.findRefresh(token) }
        if (resolved is Result.Failed) return resolved
        val lookup =
            (resolved as Result.Success).value
                ?: return collaborationGatewayAuthenticationRequired()
        val initial = lookup.session
        val scope = lookupScope.copy(accountId = initial.accountId, companyId = initial.companyId)
        return transactions.run(scope) {
            val company = initial.companyId
            val pg = policies.lock(company, shared = true)
            if (pg is Result.Failed) return@run pg
            val cg = companies.lock(company, shared = true)
            if (cg is Result.Failed) return@run cg
            val mg = members.lock(company, shared = true)
            if (mg is Result.Failed) return@run mg
            val ag = identities.lockAccount(initial.accountId, shared = true)
            if (ag is Result.Failed) return@run ag
            val locked = sessions.lock(initial.nativeSessionId, initial.accountId)
            if (locked is Result.Failed) return@run locked
            val parent =
                (locked as Result.Success).value
                    ?: return@run collaborationGatewayAuthenticationRequired()
            if (parent.id != initial.nativeSessionId || parent.accountId != initial.accountId)
                return@run collaborationGatewayAuthenticationRequired()
            val actor =
                scope.copy(
                    permissions = setOf(COLLABORATION_CHAT_PERMISSION),
                    authenticatedAt = parent.authenticatedAt,
                    mfaVerifiedAt = parent.mfaVerifiedAt,
                    credentialVersion = parent.credentialVersion,
                )
            val acquired = identities.access(initial.accountId, company)
            if (acquired is Result.Failed) return@run acquired
            val access = (acquired as Result.Success).value
            val bound = registry.findBound(company, initial.accountId, parent.id)
            if (bound is Result.Failed) return@run bound
            val device = (bound as Result.Success).value
            if (
                device == null ||
                    device.id != initial.deviceId ||
                    device.platform != initial.platform
            )
                return@run collaborationGatewayAuthenticationRequired()
            val read = gateway.find(company, initial.accountId, initial.deviceId, lock = true)
            if (read is Result.Failed) return@run read
            val head =
                (read as Result.Success).value
                    ?: return@run collaborationGatewayAuthenticationRequired()
            if (head.id != initial.id || head.nativeSessionId != initial.nativeSessionId)
                return@run collaborationGatewayAuthenticationRequired()
            val client =
                ClientVersion(
                    if (head.platform == CollaborationPlatform.ANDROID) ClientPlatform.ANDROID
                    else ClientPlatform.IOS,
                    head.clientBuild,
                )
            fun admission(): Result<CollaborationAdmission> {
                val effective = policies.effective(company, clock.instant())
                if (effective is Result.Failed) return effective
                val at = clock.instant()
                return nativeCollaborationClientDeadline(
                        (effective as Result.Success).value,
                        client,
                        at,
                    )
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
                            .map { it.copy(expiresAt = minOf(it.expiresAt, deadline)) }
                    }
            }
            val admitted = admission()
            if (admitted is Result.Failed) return@run admitted
            val valid =
                validateCollaborationGatewaySession(
                    head,
                    (admitted as Result.Success).value,
                    device.platform,
                    clock.instant(),
                )
            if (valid is Result.Failed) return@run valid
            // Re-read the replay under the locked head: another request may have rotated since
            // the isolated lookup. It may recover only this winner, never overwrite/revoke it.
            val recovered = gateway.replayRefresh(head, token)
            if (recovered is Result.Failed) return@run recovered
            val replay = (recovered as Result.Success).value
            if (replay != null)
                return@run admission().flatMap {
                    validateCollaborationGatewayResponse(
                            head,
                            replay,
                            it,
                            device.platform,
                            clock.instant(),
                        )
                        .map { replay }
                }
            if (lookup.previous || head.version != initial.version)
                return@run collaborationGatewayAuthenticationRequired()
            val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
            val allowed = collaborationGatewayRotationAllowed(head, now)
            if (allowed is Result.Failed) return@run allowed
            val next =
                head.copy(
                    version = head.version + 1,
                    rotatedAt = now,
                    accessExpiresAt =
                        minOf(now.plusSeconds(COLLABORATION_GATEWAY_ACCESS_SECONDS), head.expiresAt),
                )
            val until =
                minOf(now.plusSeconds(COLLABORATION_GATEWAY_REPLAY_SECONDS), next.accessExpiresAt)
            gateway.rotateRefresh(next, token, until).flatMap { tokens ->
                journal
                    .record(
                        actor,
                        ChangeRecord(
                            "collaboration_device",
                            device.id,
                            "collaboration.gateway_refreshed",
                            mapOf("gatewayVersion" to next.version.toString()),
                        ),
                    )
                    .flatMap { admission() }
                    .flatMap {
                        validateCollaborationGatewayResponse(
                            next,
                            tokens,
                            it,
                            device.platform,
                            clock.instant(),
                        )
                    }
                    .map { tokens }
            }
        }
    }
}
