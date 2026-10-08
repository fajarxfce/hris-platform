package dev.fajar.hris.identity.delivery.security

/** Only self-service authentication metadata/challenges may bypass step-up routing. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PendingMfaAllowed
