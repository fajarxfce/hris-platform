package dev.fajar.hris.jobs.data.mappers

import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.schema.tables.records.BackgroundJobsRecord
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

fun decodeJobValues(value: JSONB, json: ObjectMapper): Map<String, String> {
    val node = json.readTree(value.data())
    require(node.isObject && node.size() <= 100)
    return node.properties().associate {
        require(it.value.isString)
        it.key to it.value.asString()
    }
}

fun BackgroundJobsRecord.toJob(json: ObjectMapper): BackgroundJob =
    BackgroundJob(
        JobRequest(
            id,
            companyId,
            actorId,
            JobKind.valueOf(kind),
            operationId,
            decodeJobValues(request, json),
            authenticatedAt.toInstant(),
            credentialVersion,
            correlationId,
            createdAt.toInstant(),
            totalItems,
            JobProgressMode.valueOf(progressMode),
        ),
        JobStatus.valueOf(status),
        attempts,
        cancellationRequested,
        completedItems,
        decodeJobValues(checkpoint, json),
        failureCode,
        finishedAt?.toInstant(),
        version,
    )

fun JobRequest.toRecord(json: ObjectMapper): BackgroundJobsRecord =
    BackgroundJobsRecord().also {
        it.id = id
        it.companyId = companyId
        it.actorId = actorId
        it.kind = kind.name
        it.operationId = operationId
        it.request = JSONB.valueOf(json.writeValueAsString(values))
        it.authenticatedAt = OffsetDateTime.ofInstant(authenticatedAt, ZoneOffset.UTC)
        it.credentialVersion = credentialVersion
        it.correlationId = correlationId
        it.createdAt = OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC)
        it.totalItems = totalItems
        it.progressMode = progressMode.name
    }
