package dev.fajar.hris.communications.domain.policies

import dev.fajar.hris.communications.domain.entities.SaveAudienceGroupCommand
import dev.fajar.hris.core.domain.*

fun validateAudienceGroupInput(input: SaveAudienceGroupCommand): Result<Unit> {
    val fields = linkedMapOf<String, String>()
    if (input.expectedVersion != null && input.expectedVersion < 0)
        fields["expectedVersion"] = "out_of_range"
    if (
        input.name.isBlank() ||
            input.name.length > 120 ||
            input.name.any { Character.isISOControl(it) }
    )
        fields["name"] = "invalid_text"
    if (input.reason.isBlank() || input.reason.length > 1000) fields["reason"] = "invalid_text"
    if (
        input.employmentIds.size > 5000 ||
            input.employmentIds.distinct().size != input.employmentIds.size
    )
        fields["employmentIds"] = "invalid_selection"
    return if (fields.isEmpty()) Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.VALIDATION, "invalid_audience_group", fields))
}
