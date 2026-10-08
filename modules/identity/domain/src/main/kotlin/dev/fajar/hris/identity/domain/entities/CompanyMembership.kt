package dev.fajar.hris.identity.domain.entities

import java.util.UUID

data class CompanyMembership(
    val companyId: UUID,
    val companyName: String,
    val companyCode: String,
    val timezone: String,
    val memberActive: Boolean,
    val companyActive: Boolean,
)
