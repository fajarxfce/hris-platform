package dev.fajar.hris.identity.data.datasources

import dev.fajar.hris.schema.tables.records.IdentityMailDeliveriesRecord
import java.time.Instant
import java.util.UUID

interface IdentityMailDataSource {
    fun insert(record: IdentityMailDeliveriesRecord)

    fun supersedePending(accountId: UUID, at: Instant)

    fun find(id: UUID): IdentityMailDeliveriesRecord?

    fun exhausted(
        now: Instant,
        maximumAttempts: Int,
        limit: Int,
    ): List<IdentityMailDeliveriesRecord>

    fun failUnleased(id: UUID, now: Instant, code: String): Boolean

    fun claim(
        owner: UUID,
        limit: Int,
        leaseSeconds: Int,
        maximumAttempts: Int,
    ): List<IdentityMailDeliveriesRecord>

    fun lock(id: UUID, owner: UUID, token: UUID): IdentityMailDeliveriesRecord?

    fun token(id: UUID, owner: UUID, token: UUID): String?

    fun finish(
        id: UUID,
        owner: UUID,
        token: UUID,
        state: String,
        availableAt: Instant,
        code: String?,
        discardToken: Boolean,
        at: Instant,
    ): Boolean
}
