package dev.fajar.hris.core.domain

/** Command receipts are coordinated by the use case inside its business transaction. */
interface OperationRepository {
    fun lockAndReplay(actor: Actor, key: OperationKey): Result<MutationReceipt?>

    fun record(actor: Actor, key: OperationKey, receipt: MutationReceipt): Result<Unit>
}
