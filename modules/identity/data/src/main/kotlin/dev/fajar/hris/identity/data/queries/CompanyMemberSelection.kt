package dev.fajar.hris.identity.data.queries

import dev.fajar.hris.identity.data.datasources.MemberRow
import dev.fajar.hris.schema.tables.Accounts.ACCOUNTS as A
import dev.fajar.hris.schema.tables.CompanyMemberships.COMPANY_MEMBERSHIPS as M
import dev.fajar.hris.schema.tables.MembershipPermissions.MEMBERSHIP_PERMISSIONS as P
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Record7
import org.jooq.SelectConditionStep
import org.jooq.impl.DSL

fun selectCompanyMembers(
    sql: DSLContext,
    companyId: UUID,
): SelectConditionStep<Record7<UUID, String, String, Boolean, Boolean, Long, Array<String>>> =
    sql.select(
            A.ID,
            A.EMAIL,
            A.DISPLAY_NAME,
            A.ACTIVE,
            M.ACTIVE,
            M.VERSION,
            DSL.array(
                sql.select(P.PERMISSION)
                    .from(P)
                    .where(P.COMPANY_ID.eq(M.COMPANY_ID))
                    .and(P.ACCOUNT_ID.eq(M.ACCOUNT_ID))
            ),
        )
        .from(M)
        .join(A)
        .on(A.ID.eq(M.ACCOUNT_ID))
        .where(M.COMPANY_ID.eq(companyId))

fun Record7<UUID, String, String, Boolean, Boolean, Long, Array<String>>.toMemberRow(): MemberRow =
    MemberRow(value1(), value2(), value3(), value4(), value5(), value7().toSet(), value6())
