package dev.fajar.hris.identity.data.queries

import dev.fajar.hris.identity.data.datasources.AccountAdministrationRow
import dev.fajar.hris.schema.Tables.ACCOUNTS as A
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Record8
import org.jooq.SelectJoinStep

fun selectManagedAccounts(
    sql: DSLContext
): SelectJoinStep<Record8<UUID, String, String, Boolean, Boolean, Long, Long, Boolean>> =
    sql.select(
            A.ID,
            A.EMAIL,
            A.DISPLAY_NAME,
            A.ACTIVE,
            A.MFA_SECRET_ENCRYPTED.isNotNull,
            A.VERSION,
            A.SECURITY_VERSION,
            A.INVITATION_PENDING,
        )
        .from(A)

fun Record8<UUID, String, String, Boolean, Boolean, Long, Long, Boolean>
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
    )
