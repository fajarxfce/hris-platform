package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationPushBindingDataSource
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationPushBindingRepository
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.identity.data.crypto.safeIdentityCall
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import dev.fajar.hris.schema.tables.records.CollaborationPushBindingsRecord
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class EncryptedCollaborationPushBindingRepository(
    private val source: CollaborationPushBindingDataSource,
    private val crypto: IdentityTokenDataSource,
) : CollaborationPushBindingRepository {
    override fun lock(companyId: UUID, deviceId: UUID): Result<Unit> = safeIdentityCall {
        source.lock(companyId, deviceId)
    }

    override fun find(
        companyId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): Result<CollaborationPushBinding?> = safeIdentityCall {
        source.find(companyId, accountId, deviceId)?.let(::binding)
    }

    override fun findKey(key: CollaborationPushKey): Result<CollaborationPushBinding?> =
        safeIdentityCall {
            require(validKey(key.value))
            source.byKey(crypto.hash(key.value))?.let(::binding)
        }

    override fun reserve(binding: CollaborationPushBinding): Result<Unit> = safeIdentityCall {
        require(binding.reservedAt.nano % 1000 == 0)
        val key = "hriscp1_" + crypto.hash(crypto.generate())
        require(validKey(key))
        val record =
            CollaborationPushBindingsRecord().apply {
                companyId = binding.companyId
                accountId = binding.accountId
                deviceId = binding.deviceId
                nativeSessionId = binding.nativeSessionId
                version = binding.version
                reservedAt = binding.reservedAt.atOffset(ZoneOffset.UTC)
                publishedAt = binding.publishedAt?.atOffset(ZoneOffset.UTC)
                keyHash = crypto.hash(key)
            }
        record.keyEncrypted = crypto.encrypt(purpose(record), key)
        source.insert(record)
    }

    override fun key(binding: CollaborationPushBinding): Result<CollaborationPushKey?> =
        safeIdentityCall {
            val found = source.find(binding.companyId, binding.accountId, binding.deviceId)
            if (found == null || binding(found) != binding) null
            else {
                val key = crypto.decrypt(purpose(found), found.keyEncrypted)
                require(validKey(key) && crypto.hash(key) == found.keyHash)
                CollaborationPushKey(key)
            }
        }

    override fun publish(binding: CollaborationPushBinding, at: Instant): Result<Boolean> =
        safeIdentityCall {
            if (binding.version != 0L || binding.publishedAt != null) false
            else
                source.publish(
                    binding.companyId,
                    binding.accountId,
                    binding.deviceId,
                    binding.nativeSessionId,
                    binding.reservedAt.atOffset(ZoneOffset.UTC),
                    at.atOffset(ZoneOffset.UTC),
                )
        }

    private fun binding(row: CollaborationPushBindingsRecord) =
        CollaborationPushBinding(
            row.companyId,
            row.accountId,
            row.deviceId,
            row.nativeSessionId,
            row.version,
            row.reservedAt.toInstant(),
            row.publishedAt?.toInstant(),
        )

    private fun purpose(row: CollaborationPushBindingsRecord): String {
        val canonical =
            listOf(
                    row.companyId,
                    row.accountId,
                    row.deviceId,
                    row.nativeSessionId,
                    Math.addExact(
                        Math.multiplyExact(row.reservedAt.toInstant().epochSecond, 1000000L),
                        row.reservedAt.nano / 1000L,
                    ),
                    row.keyHash,
                )
                .joinToString(":")
        return "hris:matrix-push:v1:" + crypto.hash(canonical)
    }

    private fun validKey(value: String) = value.matches(Regex("hriscp1_[a-f0-9]{64}"))
}
