package dev.fajar.hris.communications.domain.policies

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.organization.domain.entities.*

fun validateAnnouncementInput(input: SaveAnnouncementCommand): Result<Unit> {
    val fields = linkedMapOf<String, String>()
    if (input.expectedVersion != null && input.expectedVersion < 0)
        fields["expectedVersion"] = "out_of_range"
    if (
        input.title.isBlank() ||
            input.title.length > 200 ||
            input.title.any { Character.isISOControl(it) }
    )
        fields["title"] = "invalid_text"
    if (
        input.body.isBlank() ||
            input.body.length > 16000 ||
            input.body.any { Character.isISOControl(it) && it != '\n' && it != '\t' }
    )
        fields["body"] = "invalid_text"
    if (input.reason.isBlank() || input.reason.length > 1000) fields["reason"] = "invalid_text"
    val ids = input.audience.targetIds
    if (input.audience.kind == AudienceKind.COMPANY) {
        if (ids.isNotEmpty()) fields["audience.targetIds"] = "must_be_empty"
    } else if (ids.size !in 1..32 || ids.distinct().size != ids.size) {
        fields["audience.targetIds"] = "invalid_selection"
    }
    return if (fields.isEmpty()) Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.VALIDATION, "invalid_announcement", fields))
}

fun validateAnnouncementUnit(kind: AudienceKind, unit: OrganizationUnit?): Result<Unit> {
    val expected =
        when (kind) {
            AudienceKind.BRANCH -> UnitKind.BRANCH
            AudienceKind.DEPARTMENT -> UnitKind.DEPARTMENT
            else ->
                return Result.Failed(
                    Failure(FailureKind.VALIDATION, "announcement_audience_unavailable")
                )
        }
    return if (unit != null && unit.active && unit.kind == expected) Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.VALIDATION, "announcement_audience_unavailable"))
}

fun validateAnnouncementGroups(
    audience: AnnouncementAudience,
    groups: List<AudienceGroupSummary>,
): Result<Unit> =
    if (
        audience.kind == AudienceKind.GROUP &&
            groups.map { it.id }.toSet() == audience.targetIds.toSet() &&
            groups.all { it.active }
    )
        Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.VALIDATION, "announcement_audience_unavailable"))
