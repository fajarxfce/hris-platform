package dev.fajar.hris.identity.data.datasources

import java.util.UUID

data class MembershipRow(
    val companyId: UUID,
    val name: String,
    val code: String,
    val timezone: String,
    val memberActive: Boolean,
    val companyActive: Boolean,
)
