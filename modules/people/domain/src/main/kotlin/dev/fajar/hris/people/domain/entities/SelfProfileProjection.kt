package dev.fajar.hris.people.domain.entities

import java.time.Instant

data class SelfProfileProjection(val changed: SelfProfileRecord?, val nextRefresh: Instant?)
