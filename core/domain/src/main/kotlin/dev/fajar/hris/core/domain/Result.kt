package dev.fajar.hris.core.domain

sealed interface Result<out T> {
    data class Success<T>(val value: T) : Result<T>

    data class Failed(val failure: Failure) : Result<Nothing>
}

inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> =
    when (this) {
        is Result.Success -> Result.Success(transform(value))
        is Result.Failed -> this
    }

inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
    when (this) {
        is Result.Success -> transform(value)
        is Result.Failed -> this
    }
