package dev.fajar.hris.sync.data.di

import dev.fajar.hris.core.domain.TransactionRunner
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.domain.repositories.PeopleRepository
import dev.fajar.hris.sync.data.crypto.*
import dev.fajar.hris.sync.data.datasources.*
import dev.fajar.hris.sync.data.repositories.*
import dev.fajar.hris.sync.domain.repositories.*
import dev.fajar.hris.sync.domain.usecases.*
import java.time.Clock
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
class SyncConfiguration {
    @Bean fun syncSource(sql: DSLContext): SyncDataSource = PostgresSyncDataSource(sql)

    @Bean fun mobileSync(source: SyncDataSource): SyncRepository = StoredSyncRepository(source)

    @Bean
    fun syncKeyring(
        @Value("\${HRIS_SYNC_ACTIVE_KEY:v1}") active: String,
        @Value("\${HRIS_SYNC_KEYS:}") encoded: String,
    ) = parseSyncCursorKeyring(active, encoded)

    @Bean
    fun syncCursorSource(keys: SyncCursorKeyring): SyncCursorDataSource =
        JceSyncCursorDataSource(keys)

    @Bean
    fun syncCursors(source: SyncCursorDataSource, json: ObjectMapper): SyncCursorRepository =
        EncryptedSyncCursorRepository(source, json)

    @Bean
    fun getMobileSyncBootstrap(
        sync: SyncRepository,
        cursors: SyncCursorRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        GetMobileSyncBootstrap(
            sync,
            cursors,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
        )

    @Bean
    fun getMobileSyncChanges(
        sync: SyncRepository,
        cursors: SyncCursorRepository,
        people: PeopleRepository,
        companies: CompanyRepository,
        members: MembershipRepository,
        identities: IdentityRepository,
        transactions: TransactionRunner,
        clock: Clock,
    ) =
        GetMobileSyncChanges(
            sync,
            cursors,
            people,
            companies,
            members,
            identities,
            transactions,
            clock,
        )

    @Bean
    fun maintainMobileSync(sync: SyncRepository, transactions: TransactionRunner, clock: Clock) =
        MaintainMobileSync(sync, transactions, clock)
}
