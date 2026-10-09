package dev.fajar.hris.communications.data.datasources

import dev.fajar.hris.schema.tables.records.InboxPushDispatchesRecord
import java.time.Instant
import java.util.UUID

interface InboxPushDataSource {
    fun claim(
        owner: UUID,
        limit: Int,
        leaseSeconds: Int,
        maximumAttempts: Int,
    ): List<InboxPushDispatchesRecord>

    fun lock(companyId: UUID, inboxId: UUID, owner: UUID, token: UUID): InboxPushDispatchesRecord?

    fun pin(companyId: UUID, inboxId: UUID, owner: UUID, token: UUID, sessionId: UUID): Boolean

    fun advance(
        companyId: UUID,
        inboxId: UUID,
        owner: UUID,
        token: UUID,
        sessionId: UUID,
        accepted: Boolean,
        rejected: Boolean,
        code: String?,
        now: Instant,
    ): Boolean

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
    ): List<InboxPushDispatchesRecord>

    fun failUnleased(
        companyId: UUID,
        inboxId: UUID,
        expectedProcessedCount: Int,
        expectedAttempts: Int,
        code: String,
        now: Instant,
    ): Boolean

    fun purgeFinished(before: Instant, limit: Int): Int
}
