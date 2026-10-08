package dev.fajar.hris.core.domain

fun <T> Result<T?>.requireCurrentVersion(): Result<T> = flatMap {
    if (it == null) Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
    else Result.Success(it)
}
