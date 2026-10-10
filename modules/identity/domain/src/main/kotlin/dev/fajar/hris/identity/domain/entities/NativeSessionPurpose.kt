package dev.fajar.hris.identity.domain.entities

/** Selected by a trusted authentication adapter, never by a client request. */
enum class NativeSessionPurpose {
    VERIFIED_EXCHANGE,
    PASSWORD_SIGN_IN,
}
