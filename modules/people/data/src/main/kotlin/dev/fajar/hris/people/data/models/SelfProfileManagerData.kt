package dev.fajar.hris.people.data.models

import java.util.UUID

data class SelfProfileManagerData(
    val id: UUID,
    val employeeNumber: String,
    val legalName: String,
    val working: Boolean,
)
