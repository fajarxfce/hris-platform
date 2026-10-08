package dev.fajar.hris.identity.domain.entities

data class CurrentAccount(
    val account: Account,
    val permissions: Set<String>,
    val companies: List<CompanyMembership>,
)
