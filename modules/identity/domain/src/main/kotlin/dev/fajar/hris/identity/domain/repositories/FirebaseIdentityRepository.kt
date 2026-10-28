package dev.fajar.hris.identity.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.domain.entities.FirebaseIdentity

/** Verify signature with trusted Google keys; unverified payloads must never be returned. */
fun interface FirebaseIdentityRepository {
    fun verify(idToken: String): Result<FirebaseIdentity>
}
