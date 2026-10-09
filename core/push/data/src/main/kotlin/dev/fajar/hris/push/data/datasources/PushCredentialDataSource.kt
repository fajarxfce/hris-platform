package dev.fajar.hris.push.data.datasources

fun interface PushCredentialDataSource {
    fun accessToken(): String
}
