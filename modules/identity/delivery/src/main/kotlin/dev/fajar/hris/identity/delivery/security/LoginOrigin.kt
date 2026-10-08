package dev.fajar.hris.identity.delivery.security

/** Server-observed address; forwarded headers are not accepted as a client claim. */
data class LoginOrigin(val address: String)
