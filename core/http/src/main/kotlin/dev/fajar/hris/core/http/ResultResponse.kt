package dev.fajar.hris.core.http

import dev.fajar.hris.core.domain.Result

fun <T> Result<T>.response(): T =
    when (this) {
        is Result.Success -> value
        is Result.Failed -> throw DomainFailureException(failure)
    }
