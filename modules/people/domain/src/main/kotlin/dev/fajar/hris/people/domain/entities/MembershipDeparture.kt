package dev.fajar.hris.people.domain.entities

import dev.fajar.hris.identity.domain.entities.MemberAccount
import dev.fajar.hris.identity.domain.entities.MembershipGrant

data class MembershipDeparture(val member: MemberAccount, val grant: MembershipGrant)
