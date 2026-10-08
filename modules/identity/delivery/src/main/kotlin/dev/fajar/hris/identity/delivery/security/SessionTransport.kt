package dev.fajar.hris.identity.delivery.security

enum class SessionKind {
    COOKIE,
    NATIVE,
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SessionTransport(val value: SessionKind)
