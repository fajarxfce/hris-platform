package dev.fajar.hris.payroll.domain.policies

import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.payroll.domain.entities.*
import java.util.UUID

fun availablePayrollInputActions(
    actor: Actor,
    employee: PayrollEmployee,
    beneficiary: UUID?,
    source: PayrollWorkSource?,
    current: PayrollInput?,
    preparers: Set<UUID>,
    frozen: Boolean,
): Set<PayrollInputAvailableAction> {
    if (
        frozen ||
            actor.accountId == beneficiary ||
            current?.version == 999L ||
            source == null ||
            !source.closed ||
            !source.includesEmployee ||
            source.jobId == null
    )
        return emptySet()
    return buildSet {
        if ("payroll.calculate" in actor.permissions) add(PayrollInputAvailableAction.PREPARE)
        if (
            "payroll.review" in actor.permissions &&
                actor.accountId !in preparers &&
                current?.status == PayrollInputStatus.DRAFT &&
                current.employmentVersion == employee.employmentVersion &&
                current.workJobId == source.jobId &&
                current.workPeriodVersion == source.version
        )
            add(PayrollInputAvailableAction.VERIFY)
    }
}
