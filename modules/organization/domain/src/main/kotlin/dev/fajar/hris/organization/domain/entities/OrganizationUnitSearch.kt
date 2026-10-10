package dev.fajar.hris.organization.domain.entities

data class OrganizationUnitSearch(
    val kind: UnitKind? = null,
    val query: String = "",
    val active: Boolean? = null,
    val after: String? = null,
    val limit: Int = 50,
)
