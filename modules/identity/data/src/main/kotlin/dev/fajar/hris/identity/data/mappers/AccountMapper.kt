package dev.fajar.hris.identity.data.mappers

import dev.fajar.hris.identity.domain.entities.Account
import dev.fajar.hris.schema.tables.records.AccountsRecord

fun AccountsRecord.toAccount(): Account =
    Account(id, email, displayName, active, mfaSecretEncrypted != null, version)
