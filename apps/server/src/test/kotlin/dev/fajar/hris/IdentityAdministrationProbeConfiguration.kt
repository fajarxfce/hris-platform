package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.data.datasources.AccountAdministrationDataSource
import dev.fajar.hris.identity.data.datasources.AccountAdministrationRow
import dev.fajar.hris.identity.domain.entities.Account
import dev.fajar.hris.identity.domain.entities.ManagedAccount
import dev.fajar.hris.identity.domain.repositories.AccountAdministrationRepository
import dev.fajar.hris.identity.domain.repositories.OidcIdentityRepository
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary

class AccountDirectoryProbe {
    data class Barrier(
        val query: String,
        val entered: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    val current = AtomicReference<Barrier?>()
}

private fun awaitAccountGuard(probe: AccountLockProbe, id: UUID) {
    probe.current
        .get()
        ?.takeIf { it.account == id }
        ?.let {
            it.entered.countDown()
            check(it.release.await(5, TimeUnit.SECONDS))
        }
}

@TestConfiguration(proxyBeanMethods = false)
@Import(AccountLockProbeConfiguration::class)
class IdentityAdministrationProbeConfiguration {
    @Bean fun accountDirectoryProbe() = AccountDirectoryProbe()

    @Bean
    @Primary
    fun probedAccountAdministration(
        @Qualifier("accountAdministration") delegate: AccountAdministrationRepository,
        probe: AccountLockProbe,
    ): AccountAdministrationRepository =
        object : AccountAdministrationRepository by delegate {
            override fun lockAccount(id: UUID): Result<ManagedAccount?> {
                awaitAccountGuard(probe, id)
                return delegate.lockAccount(id)
            }
        }

    @Bean
    @Primary
    fun probedOidcAdministration(
        @Qualifier("oidcIdentities") delegate: OidcIdentityRepository,
        probe: AccountLockProbe,
    ): OidcIdentityRepository =
        object : OidcIdentityRepository by delegate {
            override fun lockAccount(accountId: UUID): Result<Account?> {
                awaitAccountGuard(probe, accountId)
                return delegate.lockAccount(accountId)
            }
        }

    @Bean
    @Primary
    fun probedAccountDirectory(
        @Qualifier("accountAdministrationSource") delegate: AccountAdministrationDataSource,
        probe: AccountDirectoryProbe,
    ): AccountAdministrationDataSource =
        object : AccountAdministrationDataSource by delegate {
            override fun list(
                query: String,
                after: UUID?,
                limit: Int,
            ): List<AccountAdministrationRow> {
                val rows = delegate.list(query, after, limit)
                probe.current
                    .get()
                    ?.takeIf { it.query == query }
                    ?.let {
                        if (probe.current.compareAndSet(it, null)) {
                            it.entered.countDown()
                            check(it.release.await(5, TimeUnit.SECONDS))
                        }
                    }
                return rows
            }
        }
}
