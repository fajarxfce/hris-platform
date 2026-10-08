package dev.fajar.hris.identity.data.di

import dev.fajar.hris.core.domain.ChangeJournalRepository
import dev.fajar.hris.core.domain.TransactionRunner
import dev.fajar.hris.identity.data.datasources.*
import dev.fajar.hris.identity.data.repositories.StoredAccountAdministrationRepository
import dev.fajar.hris.identity.data.repositories.StoredIdentityRepository
import dev.fajar.hris.identity.data.repositories.StoredOidcIdentityRepository
import dev.fajar.hris.identity.data.repositories.StoredRoleTemplateRepository
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.repositories.AccountAdministrationRepository
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import dev.fajar.hris.identity.domain.repositories.OidcIdentityRepository
import dev.fajar.hris.identity.domain.repositories.RoleTemplateRepository
import dev.fajar.hris.identity.domain.usecases.*
import java.time.Clock
import java.util.UUID
import org.jooq.DSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder

@Configuration(proxyBeanMethods = false)
class IdentityConfiguration {
    @Bean fun assignablePermissions() = ListAssignablePermissions()

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
        roles: RoleTemplateRepository,
    ) =
        SaveCompanyMembership(
            members,
            identities,
            operations,
            journal,
            transactions,
            clock,
            security,
            roles,
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
    fun authenticationAttemptSource(sql: DSLContext): AuthenticationAttemptDataSource =
        PostgresAuthenticationAttemptDataSource(sql)

    @Bean
    fun authenticationLimits(
        source: AuthenticationAttemptDataSource
    ): dev.fajar.hris.identity.domain.repositories.AuthenticationRateLimitRepository =
        dev.fajar.hris.identity.data.repositories.PostgresAuthenticationRateLimitRepository(source)

    @Bean
    fun signInWithPassword(
        identities: IdentityRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
        limits: dev.fajar.hris.identity.domain.repositories.AuthenticationRateLimitRepository,
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
    fun identityTokens(
        keyring: dev.fajar.hris.identity.data.crypto.IdentityKeyring
    ): IdentityTokenDataSource = JceIdentityTokenDataSource(keyring)

    @Bean
    fun nativeSessions(
        store: NativeSessionDataSource,
        crypto: IdentityTokenDataSource,
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

    @Bean
    fun roleTemplateSource(sql: DSLContext): RoleTemplateDataSource =
        PostgresRoleTemplateDataSource(sql)

    @Bean
    fun roleTemplates(
        source: RoleTemplateDataSource,
        json: tools.jackson.databind.ObjectMapper,
    ): RoleTemplateRepository = StoredRoleTemplateRepository(source, json)

    @Bean
    fun listRoleTemplates(roles: RoleTemplateRepository, transactions: TransactionRunner) =
        ListRoleTemplates(roles, transactions)

    @Bean
    fun saveRoleTemplate(
        roles: RoleTemplateRepository,
        operations: dev.fajar.hris.core.domain.OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = SaveRoleTemplate(roles, operations, journal, transactions, security, clock)

    @Bean
    fun getCompanyMemberGrant(
        members: dev.fajar.hris.identity.domain.repositories.MembershipRepository,
        roles: RoleTemplateRepository,
        transactions: TransactionRunner,
    ) = GetCompanyMemberGrant(members, roles, transactions)

    @Bean
    fun accountAdministrationSource(sql: DSLContext): AccountAdministrationDataSource =
        PostgresAccountAdministrationDataSource(sql)

    @Bean
    fun accountAdministration(
        source: AccountAdministrationDataSource
    ): AccountAdministrationRepository = StoredAccountAdministrationRepository(source)

    @Bean
    fun listManagedAccounts(
        accounts: AccountAdministrationRepository,
        transactions: TransactionRunner,
    ) = ListManagedAccounts(accounts, transactions)

    @Bean
    fun saveAccountAccess(
        accounts: AccountAdministrationRepository,
        operations: dev.fajar.hris.core.domain.OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        clock: Clock,
    ) = SaveAccountAccess(accounts, operations, journal, transactions, security, clock)

    @Bean
    fun credentialPolicy(
        environment: org.springframework.core.env.Environment
    ): CredentialChallengePolicy {
        val enabled = environment.getProperty("HRIS_MAIL_ENABLED", Boolean::class.java, false)
        val origin =
            if (enabled)
                credentialPublicOrigin(
                    environment.getRequiredProperty("HRIS_PUBLIC_URL"),
                    environment.getProperty(
                        "HRIS_AUTH_LINKS_ALLOW_LOOPBACK_HTTP",
                        Boolean::class.java,
                        false,
                    ),
                )
            else ""
        return CredentialChallengePolicy(enabled, origin)
    }

    @Bean
    fun credentialSource(sql: DSLContext): CredentialStoreDataSource =
        PostgresCredentialStoreDataSource(sql)

    @Bean
    fun credentialChallenges(
        source: CredentialStoreDataSource,
        passwords: PasswordDataSource,
        tokens: IdentityTokenDataSource,
    ): dev.fajar.hris.identity.domain.repositories.CredentialChallengeRepository =
        dev.fajar.hris.identity.data.repositories.StoredCredentialChallengeRepository(
            source,
            passwords,
            tokens,
        )

    @Bean
    fun identityMailSource(sql: DSLContext): IdentityMailDataSource =
        PostgresIdentityMailDataSource(sql)

    @Bean
    fun identityMail(
        source: IdentityMailDataSource,
        tokens: IdentityTokenDataSource,
    ): dev.fajar.hris.identity.domain.repositories.IdentityMailRepository =
        dev.fajar.hris.identity.data.repositories.StoredIdentityMailRepository(source, tokens)

    @Bean
    fun inviteAccount(
        accounts: AccountAdministrationRepository,
        credentials: dev.fajar.hris.identity.domain.repositories.CredentialChallengeRepository,
        mail: dev.fajar.hris.identity.domain.repositories.IdentityMailRepository,
        operations: dev.fajar.hris.core.domain.OperationRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        security: IdentitySecurityPolicy,
        policy: CredentialChallengePolicy,
        clock: Clock,
    ) =
        InviteAccount(
            accounts,
            credentials,
            mail,
            operations,
            journal,
            transactions,
            security,
            policy,
            clock,
        )

    @Bean
    fun requestPasswordRecovery(
        credentials: dev.fajar.hris.identity.domain.repositories.CredentialChallengeRepository,
        mail: dev.fajar.hris.identity.domain.repositories.IdentityMailRepository,
        limits: dev.fajar.hris.identity.domain.repositories.AuthenticationRateLimitRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        policy: CredentialChallengePolicy,
        clock: Clock,
    ) = RequestPasswordRecovery(credentials, mail, limits, journal, transactions, policy, clock)

    @Bean
    fun confirmAccountCredential(
        credentials: dev.fajar.hris.identity.domain.repositories.CredentialChallengeRepository,
        mail: dev.fajar.hris.identity.domain.repositories.IdentityMailRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) = ConfirmAccountCredential(credentials, mail, journal, transactions, clock)

    @Bean
    fun leaseIdentityMail(
        mail: dev.fajar.hris.identity.domain.repositories.IdentityMailRepository,
        credentials: dev.fajar.hris.identity.domain.repositories.CredentialChallengeRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        policy: CredentialChallengePolicy,
        clock: Clock,
    ) = LeaseIdentityMail(mail, credentials, journal, transactions, policy, clock)

    @Bean
    fun deliverIdentityMail(
        deliveries: dev.fajar.hris.identity.domain.repositories.IdentityMailRepository,
        credentials: dev.fajar.hris.identity.domain.repositories.CredentialChallengeRepository,
        mail: dev.fajar.hris.mail.domain.repositories.MailRepository,
        journal: ChangeJournalRepository,
        transactions: TransactionRunner,
        policy: CredentialChallengePolicy,
        clock: Clock,
    ) = DeliverIdentityMail(deliveries, credentials, mail, journal, transactions, policy, clock)
}
