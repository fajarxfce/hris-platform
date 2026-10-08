package dev.fajar.hris.storage.data.mappers

import dev.fajar.hris.schema.tables.records.ObjectCleanupQueueRecord
import dev.fajar.hris.storage.domain.entities.*
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun ObjectCleanupRequest.toRecord() =
    ObjectCleanupQueueRecord().also {
        it.id = id
        it.companyId = companyId
        it.resourceId = resourceId
        it.objectKey = key
        it.objectBytes = bytes
        it.createdBy = createdBy
        it.eligibleAt = OffsetDateTime.ofInstant(deleteAfter, ZoneOffset.UTC)
    }

fun ObjectCleanupQueueRecord.toEntry() =
    ObjectCleanupEntry(
        ObjectCleanupRequest(
            id,
            companyId,
            resourceId,
            objectKey,
            objectBytes,
            createdBy,
            eligibleAt.toInstant(),
        ),
        ObjectCleanupStatus.valueOf(status),
        attempts,
        failureCode,
        version,
    )

fun ObjectCleanupQueueRecord.toLease() = ObjectCleanupLease(toEntry(), requireNotNull(leaseToken))
