package dev.fajar.hris.push.data.datasources

import com.google.auth.oauth2.ServiceAccountCredentials

class GooglePushCredentialDataSource(private val credentials: ServiceAccountCredentials) :
    PushCredentialDataSource {
    override fun accessToken(): String {
        credentials.refreshIfExpired()
        return requireNotNull(credentials.accessToken).tokenValue
    }
}
