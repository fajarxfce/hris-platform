package dev.fajar.hris.identity.data.datasources

interface NativeTokenDataSource {
    fun generate(): String

    fun hash(token: String): String

    fun encrypt(purpose: String, value: String): String

    fun decrypt(purpose: String, value: String): String
}
