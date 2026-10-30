package dev.fajar.hris.communications.data.datasources

import dev.fajar.hris.schema.tables.records.InboxEmailDispatchesRecord
import java.time.Instant
import java.util.UUID

interface InboxEmailDataSource {
    fun claim(
        owner: UUID,
        limit: Int,
        leaseSeconds: Int,
        maximumAttempts: Int,
    ): List<InboxEmailDispatchesRecord>

    fun lock(companyId: UUID, inboxId: UUID, owner: UUID, token: UUID): InboxEmailDispatchesRecord?

    fun retry(
        companyId: UUID,
        inboxId: UUID,
        owner: UUID,
        token: UUID,
        availableAt: Instant,
        code: String,
    ): Boolean

    fun finish(
        companyId: UUID,
        inboxId: UUID,
        owner: UUID,
        token: UUID,
        state: String,
        code: String?,
        now: Instant,
    ): Boolean

    fun exhausted(
        olderThan: Instant,
        maximumAttempts: Int,
        limit: Int,
    ): List<InboxEmailDispatchesRecord>

    fun failUnleased(
        companyId: UUID,
        inboxId: UUID,
        expectedAttempts: Int,
        code: String,
        now: Instant,
    ): Boolean

    fun purgeFinished(before: Instant, limit: Int): Int
}
