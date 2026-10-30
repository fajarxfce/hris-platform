package dev.fajar.hris.communications.domain.usecases

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.policies.*
import dev.fajar.hris.communications.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.mail.domain.repositories.MailRepository
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * SMTP runs outside database transactions; a lost acknowledgement can repeat the stable Message-ID.
 */
class DeliverInboxEmail(
    private val dispatches: InboxEmailRepository,
    private val inbox: InboxRepository,
    private val announcements: AnnouncementRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val mail: MailRepository,
    private val transactions: TransactionRunner,
    private val journal: ChangeJournalRepository,
    private val policy: InboxEmailPolicy,
    private val clock: Clock,
) {
    fun execute(lease: InboxEmailLease): Result<Unit> {
        if (!policy.enabled)
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "mail_not_configured"))
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
        if (value == PreparedInboxEmail.Handled) return Result.Success(Unit)
        val outcome = mail.send((value as PreparedInboxEmail.Ready).message)
        return transactions.run(actor) {
            val locked = dispatches.lock(lease)
            if (locked is Result.Failed) return@run locked
            val dispatch =
                (locked as Result.Success).value
                    ?: return@run Result.Failed(Failure(FailureKind.CONFLICT, "email_lease_lost"))
            val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
            if (outcome is Result.Failed) {
                val retry =
                    inboxEmailRetryAt(
                        outcome.failure,
                        dispatch.attempts,
                        now,
                        dispatch.enqueuedAt.plus(policy.deliveryLifetime),
                        policy.maximumAttempts,
                    )
                if (retry != null)
                    return@run dispatches
                        .retry(lease, retry, outcome.failure.code)
                        .requireEmailLease()
            }
            recordTerminalOutcome(
                actor,
                lease,
                dispatch,
                if (outcome is Result.Success) InboxEmailState.ACCEPTED else InboxEmailState.FAILED,
                (outcome as? Result.Failed)?.failure?.code,
                now,
            )
        }
    }

    private fun prepare(actor: Actor, lease: InboxEmailLease): Result<PreparedInboxEmail> =
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
                    ?: return@run Result.Failed(Failure(FailureKind.CONFLICT, "email_lease_lost"))
            val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
            if (!dispatch.enqueuedAt.plus(policy.deliveryLifetime).isAfter(now.plusSeconds(1)))
                return@run recordTerminalOutcome(
                        actor,
                        lease,
                        dispatch,
                        InboxEmailState.FAILED,
                        "email_delivery_expired",
                        now,
                    )
                    .map { PreparedInboxEmail.Handled }
            val access = identities.access(account, company)
            if (access is Result.Failed) return@run access
            val found = inbox.find(company, account, dispatch.inboxId)
            if (found is Result.Failed) return@run found
            if (
                !inboxEmailAccess((access as Result.Success).value) ||
                    (found as Result.Success).value == null
            )
                return@run recordTerminalOutcome(
                        actor,
                        lease,
                        dispatch,
                        InboxEmailState.SUPERSEDED,
                        "inbox_item_unavailable",
                        now,
                    )
                    .map { PreparedInboxEmail.Handled }
            // Recheck the live fence after reading current access and recipient address.
            dispatches.lock(lease).flatMap {
                if (it == null) Result.Failed(Failure(FailureKind.CONFLICT, "email_lease_lost"))
                else
                    Result.Success(
                        PreparedInboxEmail.Ready(
                            inboxEmailMessage(
                                dispatch,
                                requireNotNull(access.value).account.email,
                                policy,
                            )
                        )
                    )
            }
        }

    private fun recordTerminalOutcome(
        actor: Actor,
        lease: InboxEmailLease,
        dispatch: InboxEmailDispatch,
        state: InboxEmailState,
        code: String?,
        now: Instant,
    ): Result<Unit> =
        dispatches.finish(lease, state, code, now).requireEmailLease().flatMap {
            journal.record(actor, inboxEmailFinishedRecord(dispatch, state, code))
        }
}
