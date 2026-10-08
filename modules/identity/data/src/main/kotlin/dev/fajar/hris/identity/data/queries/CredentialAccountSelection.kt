package dev.fajar.hris.identity.data.queries

import dev.fajar.hris.identity.data.datasources.CredentialAccountRow
import dev.fajar.hris.schema.Tables.ACCOUNTS as A
import java.util.UUID
import org.jooq.*

fun selectCredentialAccounts(
    sql: DSLContext
): SelectJoinStep<Record8<UUID, String, String, Boolean, Boolean, Boolean, Long, Long>> =
    sql.select(
            A.ID,
            A.EMAIL,
            A.DISPLAY_NAME,
            A.ACTIVE,
            A.INVITATION_PENDING,
            A.PASSWORD_HASH.isNotNull,
            A.VERSION,
            A.SECURITY_VERSION,
        )
        .from(A)

fun Record8<UUID, String, String, Boolean, Boolean, Boolean, Long, Long>.toCredentialAccountRow() =
    CredentialAccountRow(
        value1(),
        value2(),
        value3(),
        value4(),
        value5(),
        value6(),
        value7(),
        value8(),
    )
