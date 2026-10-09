package dev.fajar.hris.communications.domain.usecases

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.policies.*
import dev.fajar.hris.communications.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.push.domain.entities.*
import dev.fajar.hris.push.domain.repositories.PushRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Owns preparation and acknowledgement transactions; provider I/O occurs between them. */
class DeliverInboxPush(
    private val dispatches: InboxPushRepository,
    private val inbox: InboxRepository,
    private val announcements: AnnouncementRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val sessions: NativeSessionRepository,
    private val registrations: NativePushRegistrationRepository,
    private val push: PushRepository,
    private val transactions: TransactionRunner,
    private val journal: ChangeJournalRepository,
    private val policy: InboxPushPolicy,
    private val clock: Clock,
) {
    fun execute(lease: InboxPushLease): Result<Unit> {
        if (!policy.enabled)
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "push_not_configured"))
        val actor =
            Actor(
                UUID(0, 0),
                lease.dispatch.companyId,
                emptySet(),
                clock.instant(),
                UUID.randomUUID(),
            )
        val prepared = prepare(actor, lease)
        if (prepared is Result.Failed) return prepared
        val value = (prepared as Result.Success).value
        if (value == PreparedInboxPush.Handled) return Result.Success(Unit)
        val ready = value as PreparedInboxPush.Ready
        val outcome = push.send(ready.message)
        return acknowledge(actor, lease, ready, outcome)
    }

    private fun prepare(actor: Actor, lease: InboxPushLease): Result<PreparedInboxPush> =
        transactions.run(actor) {
            val company = lease.dispatch.companyId
            val account = lease.dispatch.accountId
            val announcementGuard = announcements.lock(company, shared = true)
            if (announcementGuard is Result.Failed) return@run announcementGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(account, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val locked = dispatches.lock(lease)
            if (locked is Result.Failed) return@run locked
            val dispatch =
                (locked as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.CONFLICT, "push_lease_lost"))
            val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
            if (!dispatch.enqueuedAt.plus(policy.deliveryLifetime).isAfter(now.plusSeconds(1)))
                return@run recordTerminalOutcome(
                        actor,
                        lease,
                        dispatch,
                        InboxPushState.FAILED,
                        "push_delivery_expired",
                        now,
                    )
                    .map { PreparedInboxPush.Handled }
            val access = identities.access(account, company)
            if (access is Result.Failed) return@run access
            val found = inbox.find(company, account, dispatch.inboxId)
            if (found is Result.Failed) return@run found
            if (
                !inboxPushAccess((access as Result.Success).value) ||
                    (found as Result.Success).value == null
            )
                return@run recordTerminalOutcome(
                        actor,
                        lease,
                        dispatch,
                        InboxPushState.SUPERSEDED,
                        "inbox_item_unavailable",
                        now,
                    )
                    .map { PreparedInboxPush.Handled }
            val target =
                if (dispatch.targetSessionId != null)
                    registrations.find(account, dispatch.targetSessionId)
                else
                    registrations
                        .listEnabled(account, dispatch.publishedAt, dispatch.cursorSessionId, 1)
                        .map { it.firstOrNull() }
            if (target is Result.Failed) return@run target
            val registration = (target as Result.Success).value
            if (registration == null && dispatch.targetSessionId == null)
                return@run recordTerminalOutcome(
                        actor,
                        lease,
                        dispatch,
                        InboxPushState.COMPLETE,
                        null,
                        now,
                    )
                    .map { PreparedInboxPush.Handled }
            if (dispatch.processedCount >= policy.maximumTargets)
                return@run recordTerminalOutcome(
                        actor,
                        lease,
                        dispatch,
                        InboxPushState.FAILED,
                        "push_target_limit",
                        now,
                    )
                    .map { PreparedInboxPush.Handled }
            val sessionId = registration?.sessionId ?: requireNotNull(dispatch.targetSessionId)
            if (registration == null)
                return@run dispatches
                    .advance(
                        lease,
                        sessionId,
                        InboxPushTargetOutcome.SKIPPED,
                        "push_target_unavailable",
                        now,
                    )
                    .requirePushLease()
                    .map { PreparedInboxPush.Handled }
            // Account-first ordering matches native rotation, revocation, and token updates.
            val session = sessions.lock(sessionId, account)
            if (session is Result.Failed) return@run session
            val pinned = dispatches.pin(lease, sessionId).requirePushLease()
            if (pinned is Result.Failed) return@run pinned
            if (
                !inboxPushTargetActive(
                    registration,
                    (session as Result.Success).value,
                    requireNotNull(access.value).account,
                    dispatch.publishedAt,
                    now,
                )
            )
                return@run dispatches
                    .advance(
                        lease,
                        sessionId,
                        InboxPushTargetOutcome.SKIPPED,
                        "push_target_unavailable",
                        now,
                    )
                    .requirePushLease()
                    .map { PreparedInboxPush.Handled }
            val loadedToken = registrations.token(account, sessionId, registration.version)
            if (loadedToken is Result.Failed) return@run loadedToken
            val token =
                (loadedToken as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.CONFLICT, "push_registration_changed")
                    )
            // Recheck the fence after token acquisition before exposing a prepared external
            // operation.
            dispatches.pin(lease, sessionId).requirePushLease().map {
                PreparedInboxPush.Ready(
                    registration,
                    PushMessage(
                        token,
                        mapOf(
                            "schemaVersion" to "1",
                            "eventCode" to "communications.inbox_available",
                            "eventId" to dispatch.inboxId.toString(),
                            "accountId" to account.toString(),
                            "companyId" to company.toString(),
                            "inboxId" to dispatch.inboxId.toString(),
                        ),
                        minOf(
                            now.plusSeconds(300),
                            dispatch.enqueuedAt.plus(policy.deliveryLifetime),
                        ),
                    ),
                )
            }
        }

    private fun acknowledge(
        actor: Actor,
        lease: InboxPushLease,
        ready: PreparedInboxPush.Ready,
        outcome: Result<PushOutcome>,
    ): Result<Unit> =
        transactions.run(actor) {
            val account = lease.dispatch.accountId
            val guarded = identities.lockAccount(account)
            if (guarded is Result.Failed) return@run guarded
            val locked = dispatches.lock(lease)
            if (locked is Result.Failed) return@run locked
            val dispatch =
                (locked as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.CONFLICT, "push_lease_lost"))
            if (dispatch.targetSessionId != ready.registration.sessionId)
                return@run Result.Failed(Failure(FailureKind.CONFLICT, "push_lease_lost"))
            val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
            if (outcome is Result.Success && outcome.value == PushOutcome.UNREGISTERED) {
                val loaded = registrations.find(account, ready.registration.sessionId)
                if (loaded is Result.Failed) return@run loaded
                val current = (loaded as Result.Success).value
                if (
                    current != null &&
                        current.enabled &&
                        current.version == ready.registration.version &&
                        current.version < 10000
                ) {
                    val retired =
                        registrations.save(
                            current.copy(
                                enabled = false,
                                version = current.version + 1,
                                updatedAt = maxOf(now, current.updatedAt),
                            ),
                            null,
                            current.version,
                        )
                    if (retired is Result.Failed) return@run retired
                    if (!(retired as Result.Success).value)
                        return@run Result.Failed(
                            Failure(FailureKind.CONFLICT, "push_registration_changed")
                        )
                }
            }
            if (outcome is Result.Failed) {
                val retry =
                    inboxPushRetryAt(
                        outcome.failure,
                        dispatch.attempts,
                        now,
                        dispatch.enqueuedAt.plus(policy.deliveryLifetime),
                        policy.maximumAttempts,
                    )
                if (retry != null)
                    return@run dispatches
                        .retry(lease, retry, outcome.failure.code)
                        .requirePushLease()
            }
            val accepted = outcome is Result.Success && outcome.value == PushOutcome.ACCEPTED
            val code =
                when (outcome) {
                    is Result.Failed -> outcome.failure.code
                    is Result.Success ->
                        if (outcome.value == PushOutcome.UNREGISTERED) "push_token_unregistered"
                        else null
                }
            dispatches
                .advance(
                    lease,
                    ready.registration.sessionId,
                    if (accepted) InboxPushTargetOutcome.ACCEPTED
                    else InboxPushTargetOutcome.REJECTED,
                    code,
                    now,
                )
                .requirePushLease()
        }

    /** Called inside the owning transaction; terminal queue state and audit commit together. */
    private fun recordTerminalOutcome(
        actor: Actor,
        lease: InboxPushLease,
        dispatch: InboxPushDispatch,
        state: InboxPushState,
        code: String?,
        now: Instant,
    ): Result<Unit> =
        dispatches.finish(lease, state, code, now).requirePushLease().flatMap {
            journal.record(actor, inboxPushFinishedRecord(dispatch, state, code))
        }
}
