package dev.fajar.hris.identity.data.firebase.datasources

import java.security.interfaces.RSAPublicKey

fun interface FirebaseKeyDataSource {
    fun keys(): Map<String, RSAPublicKey>
}
