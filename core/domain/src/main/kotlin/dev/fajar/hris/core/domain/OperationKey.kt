package dev.fajar.hris.core.domain

import java.util.UUID

data class OperationKey(val name: String, val id: UUID, val payload: List<String?>)
