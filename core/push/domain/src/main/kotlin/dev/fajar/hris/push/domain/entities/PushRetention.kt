package dev.fajar.hris.push.domain.entities

/** Provider store-and-forward ceiling, independent of the current dispatch operation deadline. */
enum class PushRetention(val maximumSeconds: Long) {
    SHORT_LIVED(300),
    RECOVERABLE(86400),
}
