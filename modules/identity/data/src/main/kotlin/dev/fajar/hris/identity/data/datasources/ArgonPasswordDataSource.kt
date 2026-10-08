package dev.fajar.hris.identity.data.datasources

import org.springframework.security.crypto.password.PasswordEncoder

class ArgonPasswordDataSource(private val encoder: PasswordEncoder) : PasswordDataSource {
    override fun hash(password: String): String? = encoder.encode(password)

    override fun matches(password: String, hash: String): Boolean = encoder.matches(password, hash)
}
