package dev.fajar.hris.organization.domain.policies

import dev.fajar.hris.core.domain.*
import java.time.ZoneId

fun validateCompany(code: String, name: String, timezone: String): Result<Unit> {
    val fields = buildMap {
        if (!code.matches(Regex("[A-Z0-9][A-Z0-9_-]{1,31}"))) put("code", "invalid_code")
        if (name.isBlank() || name.length > 200) put("name", "invalid_name")
        if (timezone !in ZoneId.getAvailableZoneIds()) put("timezone", "invalid_timezone")
    }
    return if (fields.isEmpty()) Result.Success(Unit)
    else Result.Failed(Failure(FailureKind.VALIDATION, "invalid_company", fields))
}
