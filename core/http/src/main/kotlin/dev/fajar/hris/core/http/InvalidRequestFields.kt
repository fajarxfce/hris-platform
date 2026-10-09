package dev.fajar.hris.core.http

import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/** Maps only framework field metadata, never rejected values or exception messages. */
fun invalidRequestFields(error: Exception): Map<String, String> {
    val names =
        when (error) {
            is MethodArgumentTypeMismatchException -> sequenceOf(error.name)
            is MethodArgumentNotValidException ->
                error.bindingResult.fieldErrors.asSequence().map { it.field }
            else -> emptySequence()
        }
    return names
        .take(50)
        .filter {
            it.length <= 128 &&
                it.matches(
                    Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*|\\[[0-9]{1,8}\\])*")
                )
        }
        .associateWith { "invalid_value" }
}
