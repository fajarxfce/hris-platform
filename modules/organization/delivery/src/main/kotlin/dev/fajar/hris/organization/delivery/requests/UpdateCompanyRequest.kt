package dev.fajar.hris.organization.delivery.requests

data class UpdateCompanyRequest(
    val code: String,
    val name: String,
    val timezone: String,
    val version: Long,
)
