package dev.fajar.hris.identity.data.firebase.datasources

import dev.fajar.hris.identity.data.firebase.dto.FirebaseTokenData
import java.security.interfaces.RSAPublicKey

interface FirebaseTokenDataSource {
    fun keyId(idToken: String): String

    fun verify(idToken: String, key: RSAPublicKey): FirebaseTokenData
}
