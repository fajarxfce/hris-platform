package dev.fajar.hris.identity.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.identity.domain.policies.validateCompanyCommandActor
import dev.fajar.hris.identity.domain.policies.validateCompanySessionActor
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CompanyCommandAccessPolicyTest {
    private val account =
        Account(UUID.randomUUID(), "admin@example.invalid", "Administrator", true, false, 0, 2)
    private val actor =
        Actor(
            account.id,
            UUID.randomUUID(),
            setOf("identity.manage"),
            Instant.EPOCH,
            UUID.randomUUID(),
            credentialVersion = 2,
        )

    @Test
    fun companyAndSecurityGrantsCannotBecomePlatformAuthority() {
        val access =
            AccountAccess(
                account,
                setOf("identity.manage", "people.read"),
                true,
                true,
                securityPermissions = setOf("identity.manage", "payroll.read"),
                platformPermissions = setOf("identity.manage"),
            )
        val result = validateCompanyCommandActor(actor, access) as Result.Success
        assertEquals(setOf("identity.manage"), result.value.permissions)
        assertTrue(result.value.platformPermissions.isEmpty())
    }

    @Test
    fun revalidationIndependentlyNarrowsBothPermissionScopes() {
        val access =
            AccountAccess(
                account,
                setOf("company.read"),
                true,
                true,
                platformPermissions = setOf("companies.create"),
            )
        val original =
            actor.copy(platformPermissions = setOf("identity.manage", "companies.create"))
        val result = validateCompanyCommandActor(original, access) as Result.Success
        assertTrue(result.value.permissions.isEmpty())
        assertEquals(setOf("companies.create"), result.value.platformPermissions)
    }

    @Test
    fun interactiveCompanyAccessUsesLiveSecurityRequirementsAndAnUnexpiredProof() {
        val security = IdentitySecurityPolicy()
        val access =
            AccountAccess(
                account.copy(mfaConfigured = true),
                setOf("identity.manage", "jobs.read"),
                true,
                true,
            )
        val verified = actor.copy(mfaVerifiedAt = Instant.EPOCH)
        val boundary = Instant.EPOCH.plus(security.maximumMfaAge)
        val current =
            validateCompanySessionActor(verified, access, boundary.minusNanos(1), security)
                as Result.Success
        assertEquals(setOf("identity.manage"), current.value.permissions)
        assertEquals(
            "mfa_required",
            (validateCompanySessionActor(verified, access, boundary, security) as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            "session_revoked",
            (validateCompanySessionActor(verified, null, boundary, security) as Result.Failed)
                .failure
                .code,
        )
        val enrolledLater =
            access.copy(
                account = account,
                permissions = emptySet(),
                securityPermissions = setOf("jobs.manage"),
            )
        assertEquals(
            "mfa_setup_required",
            (validateCompanySessionActor(verified, enrolledLater, boundary, security)
                    as Result.Failed)
                .failure
                .code,
        )
        assertTrue(
            validateCompanySessionActor(
                verified,
                access,
                boundary,
                security.copy(enforceMfa = false),
            )
                is Result.Success
        )
    }
}
