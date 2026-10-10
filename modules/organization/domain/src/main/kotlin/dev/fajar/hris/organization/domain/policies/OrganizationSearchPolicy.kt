package dev.fajar.hris.organization.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.organization.domain.entities.OrganizationUnitSearch

private val unitCursor = Regex("(BRANCH|DEPARTMENT|POSITION|COST_CENTER):[A-Z0-9][A-Z0-9_-]{1,31}")

fun validateOrganizationUnitSearch(search: OrganizationUnitSearch): Result<OrganizationUnitSearch> {
    if (search.query.length > 120)
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_organization_search"))
    val cursor = search.after?.let(unitCursor::matchEntire)
    if (
        search.limit !in 1..200 ||
            (search.after != null && cursor == null) ||
            (cursor != null && search.kind != null && cursor.groupValues[1] != search.kind.name)
    )
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_page"))
    return Result.Success(search.copy(query = search.query.trim()))
}
