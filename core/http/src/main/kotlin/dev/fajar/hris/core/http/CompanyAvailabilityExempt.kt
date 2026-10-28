package dev.fajar.hris.core.http

/** Owned status and cleanup only. Does not exempt authentication, company scope or assurance. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CompanyAvailabilityExempt
