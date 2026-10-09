package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.requireRecentAuthentication
import dev.fajar.hris.identity.domain.policies.requireRecentMfa
import dev.fajar.hris.payroll.domain.entities.*
import java.time.Instant

fun validatePayrollPaymentAccess(
    actor: Actor,
    now: Instant,
    security: IdentitySecurityPolicy,
): Result<Unit> =
    actor.requirePermission("payroll.pay").flatMap {
        if (security.enforceMfa) requireRecentMfa(actor, now, security.recentAuthenticationAge)
        else requireRecentAuthentication(actor, now, security.recentAuthenticationAge)
    }

fun validatePayrollPaymentInstructions(
    title: String,
    items: List<PayrollPaymentInstruction>,
    reason: String,
): Result<Unit> {
    if (
        title.isBlank() ||
            title.length > 160 ||
            title.any { it.isISOControl() } ||
            reason.isBlank() ||
            reason.length > 1000 ||
            items.size !in 1..100 ||
            items.map { it.id }.toSet().size != items.size ||
            items.map { it.assessmentId }.toSet().size != items.size
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_payment_batch"))
    if (
        items.any {
            !it.destination.bankCode.matches(Regex("[A-Z0-9]{2,12}")) ||
                !it.destination.accountNumber.matches(Regex("[0-9]{6,34}")) ||
                it.destination.accountName.isBlank() ||
                it.destination.accountName.length > 120 ||
                it.destination.accountName.any { c -> c.isISOControl() }
        }
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_payment_destination"))
    return Result.Success(Unit)
}

fun validatePayrollPaymentResults(
    batch: PayrollPaymentBatch,
    results: List<PayrollPaymentResult>,
    now: Instant,
): Result<Unit> {
    if (batch.status != PayrollPaymentBatchStatus.RELEASED || batch.releasedAt == null)
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_payment_not_released"))
    if (results.size !in 1..100 || results.map { it.itemId }.toSet().size != results.size)
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_payment_results"))
    val pending =
        batch.items.filter { it.status == PayrollPaymentItemStatus.PENDING }.map { it.id }.toSet()
    if (results.any { it.itemId !in pending })
        return Result.Failed(Failure(FailureKind.CONFLICT, "payroll_payment_item_not_pending"))
    if (
        results.any {
            it.reason.isBlank() ||
                it.reason.length > 1000 ||
                it.occurredAt.isBefore(batch.releasedAt) ||
                it.occurredAt.isAfter(now) ||
                when (it.status) {
                    PayrollPaymentItemStatus.SUCCEEDED ->
                        it.transactionReference == null ||
                            !it.transactionReference.matches(
                                Regex("[A-Za-z0-9][A-Za-z0-9._:/ -]{0,99}")
                            ) ||
                            it.confirmedNoTransfer
                    PayrollPaymentItemStatus.FAILED ->
                        it.transactionReference != null || !it.confirmedNoTransfer
                    else -> true
                }
        }
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_payroll_payment_result"))
    val references = results.mapNotNull { it.transactionReference }
    if (references.toSet().size != references.size)
        return Result.Failed(
            Failure(FailureKind.VALIDATION, "duplicate_payment_transaction_reference")
        )
    return Result.Success(Unit)
}
