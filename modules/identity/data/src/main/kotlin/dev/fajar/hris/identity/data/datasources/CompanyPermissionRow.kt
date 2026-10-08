package dev.fajar.hris.identity.data.datasources

data class CompanyPermissionRow(
    val memberActive: Boolean,
    val companyActive: Boolean,
    val permissions: Set<String>,
)
