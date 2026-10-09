package dev.fajar.hris.sync.data

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.sync.data.crypto.*
import dev.fajar.hris.sync.data.datasources.*
import dev.fajar.hris.sync.data.repositories.EncryptedSyncCursorRepository
import dev.fajar.hris.sync.domain.entities.*
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

class SyncCursorCodecTest {
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
    private val secondKey = Base64.getEncoder().encodeToString(ByteArray(32) { 2 })
    private val source = JceSyncCursorDataSource(parseSyncCursorKeyring("one", "one:$key"))
    private val codec = EncryptedSyncCursorRepository(source, jacksonObjectMapper())
    private val at = Instant.parse("2026-10-09T00:00:00Z")
    private val cursor =
        SyncCursor(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "a".repeat(64),
            SyncCursorPhase.CHANGES,
            123,
            at,
            at.plusSeconds(604800),
        )

    private fun <T> value(result: Result<T>): T {
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    private fun failure(result: Result<*>, code: String) {
        assertTrue(result is Result.Failed, result.toString())
        assertEquals(code, (result as Result.Failed).failure.code)
    }

    @Test
    fun encryptionRoundTripsWithIndependentNoncesAndNoPublicScopeOrPosition() {
        val first = value(codec.encode(cursor))
        val second = value(codec.encode(cursor))
        assertNotEquals(first, second)
        assertTrue(first.length <= 2048)
        assertFalse(first.contains(cursor.accountId.toString()))
        assertFalse(first.contains(cursor.companyId.toString()))
        assertEquals(cursor, value(codec.decode(first)))
        assertEquals(cursor, value(codec.decode(second)))
    }

    @Test
    fun tamperedOversizedAndMalformedPayloadsStayAtTheFailureBoundary() {
        val token = value(codec.encode(cursor))
        val parts = token.split('.')
        val bytes = Base64.getUrlDecoder().decode(parts[2])
        bytes[20] = (bytes[20].toInt() xor 1).toByte()
        failure(
            codec.decode(
                "${parts[0]}.${parts[1]}." +
                    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            ),
            "invalid_sync_cursor",
        )
        for (bad in
            listOf("", "x".repeat(2049), "1.one.invalid!", "1.one.AA", "1.one.a.b")) failure(
            codec.decode(bad),
            "invalid_sync_cursor",
        )
        val malformed = source.encrypt("{private-token}".toByteArray())
        val failed = codec.decode(malformed)
        failure(failed, "invalid_sync_cursor")
        assertFalse(failed.toString().contains("private-token"))
        assertThrows(IllegalArgumentException::class.java) { source.encrypt(ByteArray(1025)) }
    }

    @Test
    fun keyRotationRetainsOldCursorsUntilTheirKeyIsExplicitlyRetired() {
        val token = value(codec.encode(cursor))
        val rotated =
            EncryptedSyncCursorRepository(
                JceSyncCursorDataSource(parseSyncCursorKeyring("two", "one:$key,two:$secondKey")),
                jacksonObjectMapper(),
            )
        assertEquals(cursor, value(rotated.decode(token)))
        assertTrue(value(rotated.encode(cursor)).startsWith("1.two."))
        val retired =
            EncryptedSyncCursorRepository(
                JceSyncCursorDataSource(parseSyncCursorKeyring("two", "two:$secondKey")),
                jacksonObjectMapper(),
            )
        failure(retired.decode(token), "sync_cursor_invalidated")
        val missing =
            EncryptedSyncCursorRepository(
                JceSyncCursorDataSource(parseSyncCursorKeyring("one", "")),
                jacksonObjectMapper(),
            )
        failure(missing.encode(cursor), "sync_unavailable")
        failure(missing.decode(token), "sync_unavailable")
        assertFalse(parseSyncCursorKeyring("one", "one:$key").toString().contains(key))
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                parseSyncCursorKeyring("one", "one:private-configuration")
            }
        assertFalse(error.message.orEmpty().contains("private-configuration"))
    }

    @Test
    fun fingerprintUsesCompleteStableScopeWithoutDependingOnCollectionOrder() {
        val first =
            SyncScope(
                cursor.accountId,
                cursor.companyId,
                1,
                2,
                3,
                linkedSetOf("a", "b"),
                linkedSetOf(UUID.randomUUID(), UUID.randomUUID()),
                SyncCollection.entries.toSet(),
            )
        val digest = value(codec.fingerprint(first))
        assertEquals(
            digest,
            value(
                codec.fingerprint(
                    first.copy(
                        permissions = first.permissions.reversed().toSet(),
                        employmentIds = first.employmentIds.reversed().toSet(),
                        collections = first.collections.reversed().toSet(),
                    )
                )
            ),
        )
        for (changed in
            listOf(
                first.copy(accountId = UUID.randomUUID()),
                first.copy(companyId = UUID.randomUUID()),
                first.copy(credentialVersion = 2),
                first.copy(membershipVersion = 3),
                first.copy(companyVersion = 4),
                first.copy(permissions = setOf("a")),
                first.copy(employmentIds = emptySet()),
                first.copy(collections = emptySet()),
            )) assertNotEquals(digest, value(codec.fingerprint(changed)))
    }

    @Test
    fun directWrappedAndLateCancellationPropagateWithoutLeakingProviderMessages() {
        val cancelled = CancellationException("private-cancellation")
        assertSame(
            cancelled,
            assertThrows(CancellationException::class.java) {
                safeSyncCursorCall<Unit> { throw IllegalStateException("private", cancelled) }
            },
        )
        try {
            assertThrows(InterruptedException::class.java) {
                safeSyncCursorCall {
                    Thread.currentThread().interrupt()
                    "late"
                }
            }
        } finally {
            Thread.interrupted()
        }
        val unexpected =
            safeSyncCursorCall<Unit> {
                throw java.security.GeneralSecurityException("private-provider-message")
            }
        failure(unexpected, "sync_cursor_failure")
        assertFalse(unexpected.toString().contains("private-provider-message"))
    }
}
