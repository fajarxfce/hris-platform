package dev.fajar.hris.organization.delivery.requests

data class CreateCompanyRequest(val code: String, val name: String, val timezone: String)
