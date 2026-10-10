package dev.fajar.hris.identity.data.mappers

import dev.fajar.hris.identity.data.datasources.MemberReferenceRow
import dev.fajar.hris.identity.domain.entities.MemberReference

fun MemberReferenceRow.toMemberReference(): MemberReference = MemberReference(id, displayName)
