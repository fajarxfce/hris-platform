package dev.fajar.hris.identity.domain.entities

data class ManagedAccountReview(val account: ManagedAccount, val assignablePermissions: Set<String>)
