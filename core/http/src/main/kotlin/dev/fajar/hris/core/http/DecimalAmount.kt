package dev.fajar.hris.core.http

import dev.fajar.hris.core.domain.Failure
import dev.fajar.hris.core.domain.FailureKind
import java.math.BigDecimal

/** Parse bounded decimal transport values without exponent expansion. */
fun decimalAmount(value: String): BigDecimal {
    if (value.length > 32 || !value.matches(Regex("-?[0-9]+(\\.[0-9]+)?")))
        throw DomainFailureException(Failure(FailureKind.VALIDATION, "invalid_decimal_amount"))
    return value.toBigDecimal()
}
