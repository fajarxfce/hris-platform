package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.entities.CollaborationSecretStorageDefaultKey
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSecretStorageRepository
import dev.fajar.hris.core.domain.Result
import java.time.Instant

class InternalCollaborationSecretStorageRepository(
    private val source: MatrixSecretStorageDataSource
) : CollaborationSecretStorageRepository {
    override fun defaultKey(
        userId: String,
        bearer: String,
        deadline: Instant,
    ): Result<CollaborationSecretStorageDefaultKey?> = safeMatrixProviderCall {
        val content =
            source.defaultKey(userId, bearer, deadline) ?: return@safeMatrixProviderCall null
        if (!content.isObject || content.propertyNames().any { it != "key" })
            throw MatrixResponseException()
        if (content.isEmpty)
            return@safeMatrixProviderCall CollaborationSecretStorageDefaultKey(null)
        val key = content.path("key")
        if (
            !key.isString ||
                key.asString().length !in 1..255 ||
                key.asString().any { it.code !in 33..126 }
        )
            throw MatrixResponseException()
        CollaborationSecretStorageDefaultKey(key.asString())
    }
}
