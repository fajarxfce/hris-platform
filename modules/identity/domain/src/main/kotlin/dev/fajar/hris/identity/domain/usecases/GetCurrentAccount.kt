package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.requiresMfa
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock

class GetCurrentAccount(
    private val identities: IdentityRepository,
    private val mfa: MfaRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val policy: IdentitySecurityPolicy,
) {
    fun execute(actor: Actor): Result<CurrentAccount> =
        transactions.run(actor.copy(companyId = null)) {
            identities.access(actor.accountId, null).flatMap { access ->
                if (
                    access == null ||
                        !access.account.active ||
                        (actor.credentialVersion != null &&
                            access.account.securityVersion != actor.credentialVersion)
                )
                    return@flatMap Result.Failed(
                        Failure(FailureKind.UNAUTHENTICATED, "session_revoked")
                    )
                val now = clock.instant()
                val proof = actor.mfaVerifiedAt
                val verified =
                    access.account.mfaConfigured &&
                        proof != null &&
                        !proof.isAfter(now) &&
                        proof.plus(policy.maximumMfaAge).isAfter(now)
                val required =
                    policy.enforceMfa &&
                        (requiresMfa(access.securityPermissions) || access.account.mfaConfigured)
                val assurance =
                    SessionAssurance(
                        required,
                        verified,
                        mfa.available(),
                        if (verified) proof.plus(policy.maximumMfaAge) else null,
                        if (verified) proof.plus(policy.recentAuthenticationAge) else null,
                    )
                if (required && !verified)
                    Result.Success(
                        CurrentAccount(access.account, emptySet(), emptyList(), assurance)
                    )
                else
                    identities.memberships(actor.accountId).map { companies ->
                        CurrentAccount(
                            access.account,
                            access.permissions,
                            companies.filter { it.memberActive && it.companyActive },
                            assurance,
                        )
                    }
            }
        }
}
