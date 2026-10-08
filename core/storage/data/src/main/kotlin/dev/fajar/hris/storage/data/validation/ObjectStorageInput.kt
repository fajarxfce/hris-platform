package dev.fajar.hris.storage.data.validation

const val MAXIMUM_OBJECT_BYTES = 5 * 1024 * 1024

fun requireObjectKey(key: String) {
    require(
        key.length in 1..300 &&
            key.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9/_-]*")) &&
            !key.contains("//")
    )
}
