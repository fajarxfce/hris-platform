package dev.fajar.hris.organization.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.organization.domain.entities.*
import java.time.ZoneId

fun validateUnit(
    change: UnitChange,
    parent: OrganizationUnit?,
    ancestors: List<OrganizationUnit>,
): Result<Unit> {
    if (
        !change.code.matches(Regex("[A-Z0-9][A-Z0-9_-]{1,31}")) ||
            change.name.isBlank() ||
            change.name.length > 200 ||
            (change.expectedVersion ?: 0) < 0
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_organization_unit"))
    if (
        (change.kind == UnitKind.BRANCH && change.timezone !in ZoneId.getAvailableZoneIds()) ||
            (change.kind != UnitKind.BRANCH && change.timezone != null)
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_unit_timezone"))
    if (
        change.id == change.parentId || ancestors.any { it.id == change.id } || ancestors.size >= 32
    )
        return Result.Failed(Failure(FailureKind.CONFLICT, "organization_cycle_or_depth"))
    if (change.parentId == null) return Result.Success(Unit)
    if (parent == null || (change.active && !parent.active))
        return Result.Failed(Failure(FailureKind.VALIDATION, "parent_unavailable"))
    val allowed =
        when (change.kind) {
            UnitKind.BRANCH -> setOf(UnitKind.BRANCH)
            UnitKind.DEPARTMENT -> setOf(UnitKind.BRANCH, UnitKind.DEPARTMENT)
            UnitKind.POSITION -> setOf(UnitKind.DEPARTMENT)
            UnitKind.COST_CENTER -> setOf(UnitKind.COST_CENTER)
        }
    return if (parent.kind in allowed) Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.VALIDATION, "invalid_parent_kind"))
}
