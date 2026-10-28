package dev.fajar.hris.identity.data.firebase.repositories

import dev.fajar.hris.identity.data.firebase.datasources.*
import dev.fajar.hris.identity.data.firebase.errors.safeFirebaseCall
import dev.fajar.hris.identity.domain.entities.FirebaseIdentity
import dev.fajar.hris.identity.domain.repositories.FirebaseIdentityRepository
import java.time.Instant

class VerifiedFirebaseIdentityRepository(
    private val keys: FirebaseKeyDataSource,
    private val tokens: FirebaseTokenDataSource,
) : FirebaseIdentityRepository {
    override fun verify(idToken: String) = safeFirebaseCall {
        val keyId = tokens.keyId(idToken)
        val key = requireNotNull(keys.keys()[keyId])
        val value = tokens.verify(idToken, key)
        FirebaseIdentity(
            value.issuer,
            value.subject,
            value.audience,
            Instant.ofEpochSecond(value.issuedAt),
            Instant.ofEpochSecond(value.expiresAt),
            Instant.ofEpochSecond(value.authenticatedAt),
            value.signInProvider,
            value.tenantId,
        )
    }
}
