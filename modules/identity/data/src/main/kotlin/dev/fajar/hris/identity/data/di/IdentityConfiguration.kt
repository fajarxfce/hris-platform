package dev.fajar.hris.identity.data.di

import dev.fajar.hris.core.domain.ChangeJournalRepository
import dev.fajar.hris.core.domain.TransactionRunner
import dev.fajar.hris.identity.data.datasources.*
import dev.fajar.hris.identity.data.repositories.StoredIdentityRepository
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.usecases.*
import java.time.Clock
import java.util.UUID
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder

@Configuration(proxyBeanMethods = false)
class IdentityConfiguration {
    @Bean
    fun membershipSource(sql: DSLContext): MembershipDataSource = PostgresMembershipDataSource(sql)

    @Bean
    fun members(
        source: MembershipDataSource
    ): dev.fajar.hris.identity.domain.repositories.MembershipRepository =
        dev.fajar.hris.identity.data.repositories.StoredMembershipRepository(source)

    @Bean
    fun companyMembers(
        members: dev.fajar.hris.identity.domain.repositories.MembershipRepository,
        transactions: TransactionRunner,
    ) = ListCompanyMembers(members, transactions)

    @Bean
    fun saveMembership(
        members: dev.fajar.hris.identity.domain.repositories.MembershipRepository,
        identities: IdentityRepository,
        operations: dev.fajar.hris.core.domain.OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
    ) = SaveCompanyMembership(members, identities, operations, journal, transactions)

    @Bean fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun identityDataSource(sql: DSLContext): IdentityDataSource = PostgresIdentityDataSource(sql)

    @Bean
    fun passwordDataSource(): PasswordDataSource =
        ArgonPasswordDataSource(Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8())

    @Bean
    fun identities(source: IdentityDataSource, passwords: PasswordDataSource): IdentityRepository =
        StoredIdentityRepository(
            source,
            passwords,
            checkNotNull(passwords.hash(UUID.randomUUID().toString())),
        )

    @Bean
    fun bootstrapAdministrator(
        identities: IdentityRepository,
        transactions: TransactionRunner,
        journal: ChangeJournalRepository,
        clock: Clock,
    ) = BootstrapAdministrator(identities, transactions, journal, clock)

    @Bean
    fun signInAttemptSource(sql: DSLContext): SignInAttemptDataSource =
        PostgresSignInAttemptDataSource(sql)

    @Bean
    fun signInLimits(
        source: SignInAttemptDataSource
    ): dev.fajar.hris.identity.domain.repositories.SignInLimitRepository =
        dev.fajar.hris.identity.data.repositories.PostgresSignInLimitRepository(source)

    @Bean
    fun signInWithPassword(
        identities: IdentityRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        limits: dev.fajar.hris.identity.domain.repositories.SignInLimitRepository,
        @org.springframework.beans.factory.annotation.Value("\${hris.security.sign-in-limits:true}")
        enforceLimits: Boolean,
    ) = SignInWithPassword(identities, journal, transactions, clock, limits, enforceLimits)

    @Bean
    fun resolveActor(identities: IdentityRepository, transactions: TransactionRunner) =
        ResolveActor(identities, transactions)

    @Bean
    fun listMyCompanies(identities: IdentityRepository, transactions: TransactionRunner) =
        ListMyCompanies(identities, transactions)

    @Bean
    fun currentAccount(identities: IdentityRepository, transactions: TransactionRunner) =
        GetCurrentAccount(identities, transactions)
}
