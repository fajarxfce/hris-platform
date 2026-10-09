package dev.fajar.hris.administration.domain

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.policies.*
import dev.fajar.hris.core.domain.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClientAvailabilityPolicyTest {
    private val now = Instant.parse("2026-10-01T00:00:00Z")

    private fun status(policy: ClientPolicy, at: Instant = now, next: Instant? = null) =
        clientPolicyStatus(
            EffectiveClientPolicy(
                CompanyClientPolicyRevision(
                    3,
                    now,
                    policy,
                    now,
                    UUID.randomUUID(),
                    "Configuration",
                ),
                next,
            ),
            at,
        )

    @Test
    fun maintenanceUsesAnExclusiveEndAndCacheFreshnessStopsAtTheNextBoundary() {
        val policy =
            ClientPolicy(maintenance = MaintenanceWindow(now.plusSeconds(10), now.plusSeconds(40)))
        assertFalse(status(policy).maintenanceActive)
        assertEquals(now.plusSeconds(10), status(policy).validUntil)
        assertTrue(status(policy, now.plusSeconds(10)).maintenanceActive)
        assertEquals(now.plusSeconds(40), status(policy, now.plusSeconds(10)).validUntil)
        assertFalse(status(policy, now.plusSeconds(40)).maintenanceActive)
        assertEquals(now.plusSeconds(5), status(policy, next = now.plusSeconds(5)).validUntil)
        val denied =
            validateClientAvailability(status(policy, now.plusSeconds(20)), emptySet(), null, false)
                as Result.Failed
        assertEquals("company_maintenance", denied.failure.code)
        assertEquals(
            mapOf("endsAt" to now.plusSeconds(40).toString(), "retryAfterSeconds" to "20"),
            denied.failure.parameters,
        )
        val fractional =
            validateClientAvailability(
                status(policy, now.plusSeconds(20).minusNanos(1)),
                emptySet(),
                null,
                false,
            )
                as Result.Failed
        assertEquals("21", fractional.failure.parameters["retryAfterSeconds"])
    }

    @Test
    fun VersionRequirementsAreTransportSpecificAndRemainSeparateFromModuleAvailability() {
        val policy = ClientPolicy(setOf(CompanyModule.EXPENSES), MinimumClientBuilds(100, 25, 0))
        assertEquals(
            "client_version_required",
            (validateClientAvailability(status(policy), emptySet(), null, true) as Result.Failed)
                .failure
                .code,
        )
        val old =
            validateClientAvailability(
                status(policy),
                emptySet(),
                ClientVersion(ClientPlatform.ANDROID, 99),
                true,
            )
                as Result.Failed
        assertEquals(
            mapOf("platform" to "ANDROID", "minimumBuild" to "100"),
            old.failure.parameters,
        )
        assertEquals(
            Result.Success(Unit),
            validateClientAvailability(
                status(policy),
                emptySet(),
                ClientVersion(ClientPlatform.IOS, 25),
                true,
            ),
        )
        assertEquals(
            "invalid_client_version",
            (validateClientAvailability(
                    status(policy),
                    emptySet(),
                    ClientVersion(ClientPlatform.WEB, 100),
                    true,
                )
                    as Result.Failed)
                .failure
                .code,
        )
        assertEquals(
            Result.Success(Unit),
            validateClientAvailability(status(policy), emptySet(), null, false),
        )
        val disabled =
            validateClientAvailability(
                status(policy),
                setOf(CompanyModule.EXPENSES),
                ClientVersion(ClientPlatform.ANDROID, 100),
                true,
            )
                as Result.Failed
        assertEquals("company_module_disabled", disabled.failure.code)
        assertEquals(mapOf("module" to "EXPENSES"), disabled.failure.parameters)
    }

    @Test
    fun defaultPolicyIsFiniteAndTheDisabledSetCannotBeChangedAfterCapture() {
        val defaults = clientPolicyStatus(EffectiveClientPolicy(null, null), now)
        assertNull(defaults.version)
        assertEquals(now.plusSeconds(60), defaults.validUntil)
        assertEquals(
            Result.Success(Unit),
            validateClientAvailability(defaults, CompanyModule.entries.toSet(), null, true),
        )
        val source = mutableSetOf(CompanyModule.LEAVE)
        val policy = ClientPolicy(source)
        source.clear()
        assertEquals(setOf(CompanyModule.LEAVE), policy.disabledModules)
        assertThrows(UnsupportedOperationException::class.java) {
            (policy.disabledModules as MutableSet).clear()
        }
    }

    @Test
    fun configurationRejectsUnboundedVersionsWindowsAndTimestamps() {
        val invalid =
            SaveCompanyClientPolicyCommand(
                10000,
                Instant.MAX,
                ClientPolicy(
                    minimumBuilds = MinimumClientBuilds(-1, 0, 1000000000),
                    maintenance = MaintenanceWindow(now, now.plusSeconds(8 * 86400)),
                ),
                "Reason",
            )
        val failure = validateClientPolicyCommand(invalid) as Result.Failed
        assertEquals(
            setOf(
                "expectedVersion",
                "activateAt",
                "minimumBuilds.android",
                "minimumBuilds.web",
                "maintenance",
            ),
            failure.failure.fields.keys,
        )
        assertEquals(
            "client_policy_activation_expired",
            (validateClientPolicyActivation(now.minusSeconds(1), now) as Result.Failed).failure.code,
        )
        assertEquals(
            "client_policy_activation_too_late",
            (validateClientPolicyActivation(now.plusSeconds(366 * 86400L), now) as Result.Failed)
                .failure
                .code,
        )
        assertEquals(Result.Success(Unit), validateClientPolicyActivation(null, now))
        assertEquals(
            Result.Success(Unit),
            validateClientPolicyActivation(now.plusSeconds(365 * 86400L), now),
        )
    }
}
