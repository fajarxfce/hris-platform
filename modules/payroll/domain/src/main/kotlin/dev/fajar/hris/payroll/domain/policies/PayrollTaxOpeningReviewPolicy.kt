package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.payroll.domain.entities.*
import java.util.UUID

fun availablePayrollTaxOpeningActions(
    actor: Actor,
    beneficiary: UUID?,
    current: PayrollTaxOpening?,
    preparers: Set<UUID>,
    frozen: Boolean,
): Set<PayrollTaxOpeningAvailableAction> {
    if (frozen || actor.accountId == beneficiary || current?.version == 999L) return emptySet()
    return buildSet {
        if ("payroll.calculate" in actor.permissions) add(PayrollTaxOpeningAvailableAction.PREPARE)
        if (
            "payroll.review" in actor.permissions &&
                actor.accountId !in preparers &&
                current?.status == PayrollTaxOpeningStatus.DRAFT
        )
            add(PayrollTaxOpeningAvailableAction.VERIFY)
    }
}
