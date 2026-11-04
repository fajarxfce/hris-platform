package dev.fajar.hris.collaboration.data.repositories

import dev.fajar.hris.collaboration.data.datasources.CollaborationSpacePushBindingDataSource
import dev.fajar.hris.collaboration.data.mappers.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpacePushBindingRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.safeIdentityCall
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import dev.fajar.hris.schema.tables.records.CollaborationSpacePushBindingsRecord
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.util.UUID

class EncryptedCollaborationSpacePushBindingRepository(
    private val source: CollaborationSpacePushBindingDataSource,
    private val crypto: IdentityTokenDataSource,
) : CollaborationSpacePushBindingRepository {
    override fun lock(spaceId: UUID, deviceId: UUID): Result<Unit> = safeIdentityCall {
        require(spaceId != UUID(0, 0) && deviceId != UUID(0, 0))
        source.lock(spaceId, deviceId)
    }

    override fun find(
        spaceId: UUID,
        accountId: UUID,
        deviceId: UUID,
    ): Result<CollaborationSpacePushBinding?> = safeIdentityCall {
        require(listOf(spaceId, accountId, deviceId).none { it == UUID(0, 0) })
        source.find(spaceId, accountId, deviceId)?.toSpacePushBinding(spaceId, accountId, deviceId)
    }

    override fun findKey(key: CollaborationSpacePushKey): Result<CollaborationSpacePushBinding?> =
        safeIdentityCall {
            require(validCollaborationSpacePushKey(key.value))
            val hash = crypto.hash(key.value)
            require(hash.matches(Regex("[a-f0-9]{64}")))
            source.byKey(hash)?.let {
                require(it.keyHash == hash)
                it.toSpacePushBinding(
                    requireNotNull(it.spaceId),
                    requireNotNull(it.accountId),
                    requireNotNull(it.deviceId),
                )
            }
        }

    override fun reserve(binding: CollaborationSpacePushBinding): Result<Unit> = safeIdentityCall {
        require(binding.version == 0L)
        val record = binding.toSpacePushRecord()
        val key = "hrisgp1_" + crypto.hash(crypto.generate())
        require(validCollaborationSpacePushKey(key))
        record.keyHash = crypto.hash(key)
        require(requireNotNull(record.keyHash).matches(Regex("[a-f0-9]{64}")))
        record.keyEncrypted = crypto.encrypt(purpose(record), key)
        require(
            record.toSpacePushBinding(binding.spaceId, binding.accountId, binding.deviceId) ==
                binding
        )
        source.insert(record)
        val found =
            requireNotNull(source.find(binding.spaceId, binding.accountId, binding.deviceId))
        require(
            found.toSpacePushBinding(binding.spaceId, binding.accountId, binding.deviceId) ==
                binding
        )
        require(found.keyHash == record.keyHash && found.keyEncrypted == record.keyEncrypted)
    }

    override fun key(binding: CollaborationSpacePushBinding): Result<CollaborationSpacePushKey?> =
        safeIdentityCall {
            binding.toSpacePushRecord()
            val found = source.find(binding.spaceId, binding.accountId, binding.deviceId)
            if (
                found == null ||
                    found.toSpacePushBinding(
                        binding.spaceId,
                        binding.accountId,
                        binding.deviceId,
                    ) != binding
            )
                null
            else {
                val key = crypto.decrypt(purpose(found), requireNotNull(found.keyEncrypted))
                require(validCollaborationSpacePushKey(key) && crypto.hash(key) == found.keyHash)
                CollaborationSpacePushKey(key)
            }
        }

    override fun publish(expected: CollaborationSpacePushBinding, at: Instant): Result<Unit> =
        safeIdentityCall {
                require(expected.version == 0L)
                expected.toSpacePushRecord()
                val next = expected.copy(version = 1L, publishedAt = at)
                next.toSpacePushRecord()
                val found = source.find(expected.spaceId, expected.accountId, expected.deviceId)
                if (
                    found == null ||
                        found.toSpacePushBinding(
                            expected.spaceId,
                            expected.accountId,
                            expected.deviceId,
                        ) != expected
                )
                    false
                else {
                    val changed = source.publish(found, at.atOffset(UTC))
                    if (changed) {
                        val readback =
                            requireNotNull(
                                source.find(expected.spaceId, expected.accountId, expected.deviceId)
                            )
                        require(
                            readback.toSpacePushBinding(
                                expected.spaceId,
                                expected.accountId,
                                expected.deviceId,
                            ) == next
                        )
                        require(
                            readback.keyHash == found.keyHash &&
                                readback.keyEncrypted == found.keyEncrypted
                        )
                    }
                    changed
                }
            }
            .flatMap {
                if (it) Result.Success(Unit)
                else
                    Result.Failed(Failure(FailureKind.CONFLICT, "collaboration_space_push_changed"))
            }

    private fun purpose(row: CollaborationSpacePushBindingsRecord): String {
        val at = requireNotNull(row.reservedAt).toInstant()
        val canonical =
            listOf(
                    row.spaceId,
                    row.accountId,
                    row.deviceId,
                    row.nativeSessionId,
                    row.matrixUserId,
                    row.matrixDeviceId,
                    Math.addExact(Math.multiplyExact(at.epochSecond, 1000000L), at.nano / 1000L),
                    row.keyHash,
                )
                .joinToString("\n")
        return "hris:matrix-space-push:v1:" + crypto.hash(canonical)
    }
}
