package dev.fajar.hris.core.database

import dev.fajar.hris.core.database.datasources.OperationReceiptDataSource
import dev.fajar.hris.core.domain.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import tools.jackson.databind.ObjectMapper

/** Length-prefixing distinguishes null, empty values, and embedded separators. */
fun commandFingerprint(fields: List<String?>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    fields.forEach { value ->
        val bytes = value?.toByteArray(Charsets.UTF_8)
        digest.update(ByteBuffer.allocate(4).putInt(bytes?.size ?: -1).array())
        bytes?.let(digest::update)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** The calling use case owns the transaction and permission/policy checks. No retry. */
fun idempotentWrite(
    receipts: OperationReceiptDataSource,
    json: ObjectMapper,
    actor: Actor,
    operation: String,
    operationId: UUID,
    fields: List<String?>,
    write: () -> Result<MutationReceipt>,
): Result<MutationReceipt> {
    val companyId = requireNotNull(actor.companyId)
    val hash = commandFingerprint(fields)
    return safeDatabaseCall {
            receipts.lock(companyId, actor.accountId, operation, operationId)
            receipts.find(companyId, actor.accountId, operation, operationId)
        }
        .flatMap { existing ->
            when {
                existing == null ->
                    write().flatMap { outcome ->
                        safeDatabaseCall {
                            receipts.insert(
                                companyId,
                                actor.accountId,
                                operation,
                                operationId,
                                hash,
                                outcome.id,
                                outcome.version,
                            )
                            outcome
                        }
                    }
                existing.payloadHash != hash ->
                    Result.Failed(Failure(FailureKind.CONFLICT, "operation_payload_mismatch"))
                else ->
                    safeDatabaseCall {
                        MutationReceipt(
                            existing.resourceId,
                            json.readTree(existing.response.data()).get("version").asLong(),
                            true,
                        )
                    }
            }
        }
}
