package dev.fajar.hris.identity.data.mappers

import dev.fajar.hris.identity.data.datasources.MemberRow
import dev.fajar.hris.identity.domain.entities.MemberAccount

fun MemberRow.toMember(): MemberAccount =
    MemberAccount(id, email, displayName, accountActive, membershipActive, permissions, version)
