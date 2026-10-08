package dev.fajar.hris.core.database.repositories

import dev.fajar.hris.core.database.*
import dev.fajar.hris.core.database.datasources.OperationReceiptDataSource
import dev.fajar.hris.core.domain.*
import tools.jackson.databind.ObjectMapper

class PostgresOperationRepository(
    private val source: OperationReceiptDataSource,
    private val json: ObjectMapper,
) : OperationRepository {
    override fun lockAndReplay(actor: Actor, key: OperationKey): Result<MutationReceipt?> =
        safeDatabaseCall {
                val company = requireNotNull(actor.companyId)
                source.lock(company, actor.accountId, key.name, key.id)
                source.find(company, actor.accountId, key.name, key.id)
            }
            .flatMap { row ->
                if (row == null) Result.Success(null)
                else if (row.payloadHash != commandFingerprint(key.payload))
                    Result.Failed(Failure(FailureKind.CONFLICT, "operation_payload_mismatch"))
                else
                    safeDatabaseCall {
                        MutationReceipt(
                            row.resourceId,
                            json.readTree(row.response.data()).get("version").asLong(),
                            true,
                        )
                    }
            }

    override fun record(actor: Actor, key: OperationKey, receipt: MutationReceipt): Result<Unit> =
        safeDatabaseCall {
            source.insert(
                requireNotNull(actor.companyId),
                actor.accountId,
                key.name,
                key.id,
                commandFingerprint(key.payload),
                receipt.id,
                receipt.version,
            )
        }
}
