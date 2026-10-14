package dev.fajar.hris.people.domain.entities

import java.util.UUID

data class SelfProfileRecord(val id: UUID, val version: Long, val snapshot: SelfProfileSnapshot?) {
    override fun toString() = "SelfProfileRecord(version=$version)"
}
