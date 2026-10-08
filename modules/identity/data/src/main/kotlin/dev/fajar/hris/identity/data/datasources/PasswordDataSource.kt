package dev.fajar.hris.identity.data.datasources

interface PasswordDataSource {
    fun hash(password: String): String?

    fun matches(password: String, hash: String): Boolean
}
