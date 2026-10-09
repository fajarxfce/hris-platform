package dev.fajar.hris.identity.data.queries

import dev.fajar.hris.identity.data.datasources.AccountAdministrationRow
import dev.fajar.hris.schema.Tables.ACCOUNTS as A
import dev.fajar.hris.schema.Tables.PLATFORM_PERMISSIONS as P
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Record9
import org.jooq.Result
import org.jooq.SelectJoinStep
import org.jooq.impl.DSL

fun selectManagedAccounts(
    sql: DSLContext
): SelectJoinStep<
    Record9<UUID, String, String, Boolean, Boolean, Long, Long, Boolean, Result<Record1<String>>>
> =
    sql.select(
            A.ID,
            A.EMAIL,
            A.DISPLAY_NAME,
            A.ACTIVE,
            A.MFA_SECRET_ENCRYPTED.isNotNull,
            A.VERSION,
            A.SECURITY_VERSION,
            A.INVITATION_PENDING,
            DSL.multiset(
                DSL.select(P.PERMISSION).from(P).where(P.ACCOUNT_ID.eq(A.ID)).orderBy(P.PERMISSION)
            ),
        )
        .from(A)

fun Record9<UUID, String, String, Boolean, Boolean, Long, Long, Boolean, Result<Record1<String>>>
    .toAccountAdministrationRow() =
    AccountAdministrationRow(
        value1(),
        value2(),
        value3(),
        value4(),
        value5(),
        value6(),
        value7(),
        value8(),
        value9().map { it.value1() }.toSet(),
    )
