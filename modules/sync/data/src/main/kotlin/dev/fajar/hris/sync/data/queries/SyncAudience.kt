package dev.fajar.hris.sync.data.queries

import dev.fajar.hris.schema.tables.MobileSyncChanges.MOBILE_SYNC_CHANGES as C
import dev.fajar.hris.sync.data.models.SyncSelection
import org.jooq.Condition

/** Applies the explicit audience selection supplied by the application use case. */
fun syncAudience(selection: SyncSelection): Condition =
    C.COMPANY_ID.eq(selection.companyId)
        .and(C.COLLECTION.`in`(selection.collections))
        .and(
            C.EMPLOYMENT_ID.`in`(selection.employmentIds)
                .or(C.OWNER_ACCOUNT_ID.eq(selection.accountId))
        )
