package dev.fajar.hris.identity.data.di

import dev.fajar.hris.core.domain.ChangeJournalRepository
import dev.fajar.hris.core.domain.TransactionRunner
import dev.fajar.hris.identity.data.datasources.*
import dev.fajar.hris.identity.data.repositories.StoredIdentityRepository
import dev.fajar.hris.identity.data.repositories.StoredOidcIdentityRepository
import dev.fajar.hris.identity.domain.entities.OidcPolicy
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.OidcIdentityRepository
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
        clock: Clock,
        security: dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy,
    ) =
        SaveCompanyMembership(
            members,
            identities,
            operations,
            journal,
            transactions,
            clock,
            security,
        )

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
    fun resolveActor(
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy,
    ) = ResolveActor(identities, transactions, clock, security)

    @Bean
    fun listMyCompanies(identities: IdentityRepository, transactions: TransactionRunner) =
        ListMyCompanies(identities, transactions)

    @Bean
    fun currentAccount(
        identities: IdentityRepository,
        mfa: dev.fajar.hris.identity.domain.repositories.MfaRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy,
    ) = GetCurrentAccount(identities, mfa, transactions, clock, security)

    @Bean
    fun identitySecurity(
        @org.springframework.beans.factory.annotation.Value("\${hris.security.enforce-mfa:true}")
        enforceMfa: Boolean
    ) = dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy(enforceMfa = enforceMfa)

    @Bean
    fun identityKeyring(
        @org.springframework.beans.factory.annotation.Value("\${HRIS_IDENTITY_ACTIVE_KEY:v1}")
        activeId: String,
        @org.springframework.beans.factory.annotation.Value("\${HRIS_IDENTITY_KEYS:}")
        encoded: String,
    ) = dev.fajar.hris.identity.data.crypto.parseIdentityKeyring(activeId, encoded)

    @Bean
    fun mfaCrypto(
        keyring: dev.fajar.hris.identity.data.crypto.IdentityKeyring
    ): MfaCryptoDataSource = JceMfaCryptoDataSource(keyring)

    @Bean fun mfaStore(sql: DSLContext): MfaStoreDataSource = PostgresMfaStoreDataSource(sql)

    @Bean
    fun mfaRepository(
        store: MfaStoreDataSource,
        crypto: MfaCryptoDataSource,
    ): dev.fajar.hris.identity.domain.repositories.MfaRepository =
        dev.fajar.hris.identity.data.repositories.StoredMfaRepository(store, crypto)

    @Bean
    fun beginMfa(
        mfa: dev.fajar.hris.identity.domain.repositories.MfaRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy,
    ) = BeginMfaEnrollment(mfa, transactions, clock, security)

    @Bean
    fun confirmMfa(
        mfa: dev.fajar.hris.identity.domain.repositories.MfaRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy,
    ) = ConfirmMfaEnrollment(mfa, journal, transactions, clock, security)

    @Bean
    fun verifyMfa(
        mfa: dev.fajar.hris.identity.domain.repositories.MfaRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy,
    ) = VerifyMfa(mfa, journal, transactions, clock, security)

    @Bean
    fun regenerateMfa(
        mfa: dev.fajar.hris.identity.domain.repositories.MfaRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        security: dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy,
    ) = RegenerateMfaRecoveryCodes(mfa, journal, transactions, clock, security)

    @Bean fun nativeSessionPolicy() = dev.fajar.hris.identity.domain.entities.NativeSessionPolicy()

    @Bean
    fun nativeStore(sql: DSLContext): NativeSessionDataSource = PostgresNativeSessionDataSource(sql)

    @Bean
    fun nativeCrypto(
        keyring: dev.fajar.hris.identity.data.crypto.IdentityKeyring
    ): NativeTokenDataSource = JceNativeTokenDataSource(keyring)

    @Bean
    fun nativeSessions(
        store: NativeSessionDataSource,
        crypto: NativeTokenDataSource,
    ): dev.fajar.hris.identity.domain.repositories.NativeSessionRepository =
        dev.fajar.hris.identity.data.repositories.StoredNativeSessionRepository(store, crypto)

    @Bean
    fun exchangeNative(
        sessions: dev.fajar.hris.identity.domain.repositories.NativeSessionRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        journal: ChangeJournalRepository,
        clock: Clock,
        policy: dev.fajar.hris.identity.domain.entities.NativeSessionPolicy,
        security: dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy,
    ) = ExchangeNativeSession(sessions, identities, transactions, journal, clock, policy, security)

    @Bean
    fun resolveNative(
        sessions: dev.fajar.hris.identity.domain.repositories.NativeSessionRepository,
        clock: Clock,
    ) = ResolveNativeAccess(sessions, clock)

    @Bean
    fun refreshNative(
        sessions: dev.fajar.hris.identity.domain.repositories.NativeSessionRepository,
        transactions: TransactionRunner,
        journal: ChangeJournalRepository,
        clock: Clock,
        policy: dev.fajar.hris.identity.domain.entities.NativeSessionPolicy,
    ) = RefreshNativeSession(sessions, transactions, journal, clock, policy)

    @Bean
    fun listNative(
        sessions: dev.fajar.hris.identity.domain.repositories.NativeSessionRepository,
        transactions: TransactionRunner,
        clock: Clock,
        policy: dev.fajar.hris.identity.domain.entities.NativeSessionPolicy,
    ) = ListNativeSessions(sessions, transactions, clock, policy)

    @Bean
    fun revokeNative(
        sessions: dev.fajar.hris.identity.domain.repositories.NativeSessionRepository,
        transactions: TransactionRunner,
        journal: ChangeJournalRepository,
        clock: Clock,
    ) = RevokeNativeSession(sessions, transactions, journal, clock)

    @Bean
    fun elevateNative(
        sessions: dev.fajar.hris.identity.domain.repositories.NativeSessionRepository,
        transactions: TransactionRunner,
        journal: ChangeJournalRepository,
        clock: Clock,
        policy: dev.fajar.hris.identity.domain.entities.NativeSessionPolicy,
    ) = ElevateNativeSession(sessions, transactions, journal, clock, policy)

    @Bean
    fun oidcPolicy(
        @org.springframework.beans.factory.annotation.Value("\${HRIS_OIDC_ENABLED:false}")
        enabled: Boolean,
        @org.springframework.beans.factory.annotation.Value("\${HRIS_OIDC_ISSUER:}") issuer: String,
    ) = OidcPolicy(enabled, issuer)

    @Bean
    fun oidcSource(sql: DSLContext): OidcIdentityDataSource = PostgresOidcIdentityDataSource(sql)

    @Bean
    fun oidcIdentities(source: OidcIdentityDataSource): OidcIdentityRepository =
        StoredOidcIdentityRepository(source)

    @Bean
    fun saveOidcIdentity(
        links: OidcIdentityRepository,
        operations: dev.fajar.hris.core.domain.OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy,
        oidc: OidcPolicy,
        clock: Clock,
    ) = SaveOidcIdentity(links, operations, journal, transactions, security, oidc, clock)

    @Bean
    fun listOidcIdentities(links: OidcIdentityRepository, transactions: TransactionRunner) =
        ListOidcIdentities(links, transactions)

    @Bean
    fun signInWithOidc(
        links: OidcIdentityRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        oidc: OidcPolicy,
        clock: Clock,
    ) = SignInWithOidc(links, journal, transactions, oidc, clock)
}
