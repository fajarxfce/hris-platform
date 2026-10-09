package dev.fajar.hris.identity.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.validatePlatformCommandActor
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlatformCommandAccessPolicyTest {
    private val account =
        Account(UUID.randomUUID(), "admin@example.invalid", "Administrator", true, true, 0, 2)
    private val actor =
        Actor(
            account.id,
            null,
            setOf("companies.create", "identity.manage"),
            Instant.EPOCH,
            UUID.randomUUID(),
            credentialVersion = 2,
        )
    private val access =
        AccountAccess(account, setOf("companies.create", "new.permission"), false, false)

    @Test
    fun platformRevalidationOnlyRemovesCapabilitiesFromTheOriginalRequest() {
        val result = validatePlatformCommandActor(actor, access) as Result.Success
        assertEquals(setOf("companies.create"), result.value.permissions)
        assertEquals(actor.accountId, result.value.accountId)
        assertNull(result.value.companyId)
    }

    @Test
    fun foreignInactiveAndObsoleteCredentialsAreRejected() {
        for (current in
            listOf(
                null,
                access.copy(account = account.copy(id = UUID.randomUUID())),
                access.copy(account = account.copy(active = false)),
                access.copy(account = account.copy(securityVersion = 3)),
            )) {
            val failed = validatePlatformCommandActor(actor, current) as Result.Failed
            assertEquals("session_revoked", failed.failure.code)
        }
        val scoped =
            validatePlatformCommandActor(actor.copy(companyId = UUID.randomUUID()), access)
                as Result.Failed
        assertEquals("platform_scope_required", scoped.failure.code)
    }
}
