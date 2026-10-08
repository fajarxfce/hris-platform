package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.schema.tables.Accounts.ACCOUNTS
import dev.fajar.hris.schema.tables.Companies.COMPANIES
import dev.fajar.hris.schema.tables.CompanyMemberships.COMPANY_MEMBERSHIPS
import dev.fajar.hris.schema.tables.MembershipPermissions.MEMBERSHIP_PERMISSIONS
import dev.fajar.hris.schema.tables.PlatformPermissions.PLATFORM_PERMISSIONS
import dev.fajar.hris.schema.tables.records.AccountsRecord
import java.util.UUID
import org.jooq.DSLContext

class PostgresIdentityDataSource(private val sql: DSLContext) : IdentityDataSource {
    override fun lockBootstrap() {
        sql.execute("select pg_advisory_xact_lock(70419321)")
    }

    override fun countAccounts(): Int = sql.fetchCount(ACCOUNTS)

    override fun findByEmail(email: String): AccountsRecord? =
        sql.selectFrom(ACCOUNTS).where(ACCOUNTS.EMAIL.eq(email)).fetchOne()

    override fun findById(id: UUID): AccountsRecord? =
        sql.selectFrom(ACCOUNTS).where(ACCOUNTS.ID.eq(id)).fetchOne()

    override fun insertAccount(
        id: UUID,
        email: String,
        name: String,
        passwordHash: String,
    ): AccountsRecord =
        sql.insertInto(ACCOUNTS)
            .set(ACCOUNTS.ID, id)
            .set(ACCOUNTS.EMAIL, email)
            .set(ACCOUNTS.DISPLAY_NAME, name)
            .set(ACCOUNTS.PASSWORD_HASH, passwordHash)
            .returning()
            .fetchSingle()

    override fun activeMembershipPermissions(accountId: UUID): Set<String> =
        sql.selectDistinct(MEMBERSHIP_PERMISSIONS.PERMISSION)
            .from(MEMBERSHIP_PERMISSIONS)
            .join(COMPANY_MEMBERSHIPS)
            .on(
                COMPANY_MEMBERSHIPS.COMPANY_ID.eq(MEMBERSHIP_PERMISSIONS.COMPANY_ID)
                    .and(COMPANY_MEMBERSHIPS.ACCOUNT_ID.eq(MEMBERSHIP_PERMISSIONS.ACCOUNT_ID))
            )
            .join(COMPANIES)
            .on(COMPANIES.ID.eq(COMPANY_MEMBERSHIPS.COMPANY_ID))
            .where(MEMBERSHIP_PERMISSIONS.ACCOUNT_ID.eq(accountId))
            .and(COMPANY_MEMBERSHIPS.ACTIVE.isTrue)
            .and(COMPANIES.ACTIVE.isTrue)
            .fetch(MEMBERSHIP_PERMISSIONS.PERMISSION)
            .toSet()

    override fun platformPermissions(accountId: UUID): Set<String> =
        sql.select(PLATFORM_PERMISSIONS.PERMISSION)
            .from(PLATFORM_PERMISSIONS)
            .where(PLATFORM_PERMISSIONS.ACCOUNT_ID.eq(accountId))
            .fetch(PLATFORM_PERMISSIONS.PERMISSION)
            .toSet()

    override fun companyPermissions(accountId: UUID, companyId: UUID): CompanyPermissionRow? {
        val member =
            sql.select(COMPANY_MEMBERSHIPS.ACTIVE, COMPANIES.ACTIVE)
                .from(COMPANY_MEMBERSHIPS)
                .join(COMPANIES)
                .on(COMPANIES.ID.eq(COMPANY_MEMBERSHIPS.COMPANY_ID))
                .where(
                    COMPANY_MEMBERSHIPS.ACCOUNT_ID.eq(accountId)
                        .and(COMPANY_MEMBERSHIPS.COMPANY_ID.eq(companyId))
                )
                .fetchOne()
        if (member == null) return null
        val permissions =
            sql.select(MEMBERSHIP_PERMISSIONS.PERMISSION)
                .from(MEMBERSHIP_PERMISSIONS)
                .where(
                    MEMBERSHIP_PERMISSIONS.ACCOUNT_ID.eq(accountId)
                        .and(MEMBERSHIP_PERMISSIONS.COMPANY_ID.eq(companyId))
                )
                .fetch(MEMBERSHIP_PERMISSIONS.PERMISSION)
                .toSet()
        return CompanyPermissionRow(member.value1(), member.value2(), permissions)
    }

    override fun memberships(accountId: UUID): List<MembershipRow> =
        sql.select(
                COMPANIES.ID,
                COMPANIES.NAME,
                COMPANIES.CODE,
                COMPANIES.TIMEZONE,
                COMPANY_MEMBERSHIPS.ACTIVE,
                COMPANIES.ACTIVE,
            )
            .from(COMPANY_MEMBERSHIPS)
            .join(COMPANIES)
            .on(COMPANIES.ID.eq(COMPANY_MEMBERSHIPS.COMPANY_ID))
            .where(COMPANY_MEMBERSHIPS.ACCOUNT_ID.eq(accountId))
            .orderBy(COMPANIES.NAME, COMPANIES.ID)
            .fetch {
                MembershipRow(
                    it.value1(),
                    it.value2(),
                    it.value3(),
                    it.value4(),
                    it.value5(),
                    it.value6(),
                )
            }

    override fun insertPlatformPermissions(accountId: UUID, permissions: Set<String>) {
        permissions.forEach {
            sql.insertInto(PLATFORM_PERMISSIONS)
                .set(PLATFORM_PERMISSIONS.ACCOUNT_ID, accountId)
                .set(PLATFORM_PERMISSIONS.PERMISSION, it)
                .onConflictDoNothing()
                .execute()
        }
    }

    override fun insertMembership(accountId: UUID, companyId: UUID, permissions: Set<String>) {
        sql.insertInto(COMPANY_MEMBERSHIPS)
            .set(COMPANY_MEMBERSHIPS.COMPANY_ID, companyId)
            .set(COMPANY_MEMBERSHIPS.ACCOUNT_ID, accountId)
            .execute()
        permissions.forEach {
            sql.insertInto(MEMBERSHIP_PERMISSIONS)
                .set(MEMBERSHIP_PERMISSIONS.COMPANY_ID, companyId)
                .set(MEMBERSHIP_PERMISSIONS.ACCOUNT_ID, accountId)
                .set(MEMBERSHIP_PERMISSIONS.PERMISSION, it)
                .execute()
        }
    }
}
