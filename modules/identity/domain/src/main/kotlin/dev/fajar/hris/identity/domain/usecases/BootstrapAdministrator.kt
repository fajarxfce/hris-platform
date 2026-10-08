package dev.fajar.hris.identity.domain.usecases

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.identity.domain.policies.validateNewPassword
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.Clock
import java.util.UUID

class BootstrapAdministrator(
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val journal: ChangeJournalRepository,
    private val clock: Clock,
) {
    fun execute(email: String, password: String, displayName: String): Result<Boolean> {
        val normalized = email.trim().lowercase()
        if (!normalized.contains("@") || normalized.length > 254 || displayName.isBlank()) {
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_bootstrap_identity"))
        }
        val passwordResult = validateNewPassword(password)
        if (passwordResult is Result.Failed) return passwordResult
        val id = UUID.randomUUID()
        val actor = Actor(id, null, emptySet(), clock.instant(), UUID.randomUUID())
        return transactions.run(actor) {
            identities.lockBootstrap().flatMap {
                identities.hasAccounts().flatMap { exists ->
                    if (exists) Result.Success(false)
                    else
                        identities
                            .createAccount(id, normalized, displayName.trim(), password)
                            .flatMap {
                                identities
                                    .grantPlatformPermissions(
                                        id,
                                        PermissionCatalog.platformAdministrator,
                                    )
                                    .flatMap {
                                        journal
                                            .record(
                                                actor,
                                                ChangeRecord("account", id, "identity.bootstrapped"),
                                            )
                                            .map { true }
                                    }
                            }
                }
            }
        }
    }
}
