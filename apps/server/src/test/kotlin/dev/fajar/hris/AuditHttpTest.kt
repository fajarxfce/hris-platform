package dev.fajar.hris

import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AuditHttpTest : AuditApiFixture() {
    @Test
    fun metadataPagesUseTimestampAndUuidOrderWithoutReadingReasonsPayloadsOrGlobalEvents() {
        val f = fixture()
        val until = clock.instant()
        val from = until.minus(Duration.ofDays(30))
        val tied = List(3) { seed(f, until.minusSeconds(60)) }.sortedByDescending { it.toString() }
        val lower = seed(f, from)
        seed(f, from.minusSeconds(1))
        seed(f, until)
        seed(f, company = null)
        val other = fixture()
        seed(other)
        val query =
            mapOf("from" to from, "until" to until, "limit" to 2, "action" to "fixture.created")
        val first = page(f, query)
        val firstIds =
            first["items"]
                .iterator()
                .asSequence()
                .map { UUID.fromString(it["id"].asString()) }
                .toList()
        assertEquals(tied.take(2), firstIds)
        assertEquals(tied[1].toString(), first["nextCursor"].asString())
        assertEquals(from.toString(), first["from"].asString())
        assertEquals(until.toString(), first["until"].asString())
        assertEquals(
            setOf(
                "id",
                "companyId",
                "actorId",
                "resourceType",
                "resourceId",
                "action",
                "correlationId",
                "recordedAt",
            ),
            first["items"][0].properties().map { it.key }.toSet(),
        )
        // A newly committed earlier transaction may appear ahead of an existing page cursor.
        val newest = seed(f, until.minusSeconds(1))
        val next = page(f, query + ("cursor" to first["nextCursor"].asString()))
        assertEquals(
            listOf(tied.last(), lower),
            next["items"]
                .iterator()
                .asSequence()
                .map { UUID.fromString(it["id"].asString()) }
                .toList(),
        )
        assertTrue(next["nextCursor"].isNull)
        assertEquals(newest.toString(), page(f, query)["items"][0]["id"].asString())
        failure(get(f.browser, other.path), 403, "company_access_denied")
    }

    @Test
    fun exactFiltersAndCursorValidationCannotCrossCompanyOrWindowBoundaries() {
        val f = fixture()
        val resource = UUID.randomUUID()
        val actor = UUID.randomUUID()
        val wanted =
            seed(
                f,
                actor = actor,
                resource = resource,
                action = "people.created",
                resourceType = "employment",
            )
        seed(f, resource = resource, action = "people.created", resourceType = "employment")
        seed(f, actor = actor, action = "people.created", resourceType = "employment")
        seed(
            f,
            actor = actor,
            resource = resource,
            action = "people.changed",
            resourceType = "employment",
        )
        seed(
            f,
            actor = actor,
            resource = resource,
            action = "people.created",
            resourceType = "profile",
        )
        val query =
            mapOf(
                "actorId" to actor,
                "resourceId" to resource,
                "resourceType" to "employment",
                "action" to "people.created",
            )
        val selected = page(f, query)
        assertEquals(1, selected["items"].size())
        assertEquals(wanted.toString(), selected["items"][0]["id"].asString())
        assertTrue(page(f, query + ("cursor" to wanted))["items"].isEmpty)
        val other = fixture()
        for (cursor in
            listOf(
                UUID.randomUUID(),
                seed(other),
                seed(f, company = null),
                seed(f, clock.instant().minus(Duration.ofDays(31))),
            )) {
            failure(read(f, mapOf("cursor" to cursor)), 422, "invalid_audit_cursor")
        }
        failure(
            read(f, mapOf("cursor" to wanted, "action" to "people.changed")),
            422,
            "invalid_audit_cursor",
        )
        failure(read(f, mapOf("cursor" to "not-a-uuid")), 400, "invalid_request")
    }

    @Test
    fun currentAuditAuthorityIsRequiredAndInvalidQueriesCannotReachTheSource() {
        val f = fixture()
        val marker = IllegalStateException("audit query must not run")
        auditProbe.failure.set(marker)
        for (query in
            listOf(
                mapOf("limit" to 0),
                mapOf("limit" to 201),
                mapOf("action" to "bad filter"),
                mapOf("from" to clock.instant().minus(Duration.ofDays(91))),
                mapOf("until" to clock.instant().plusSeconds(1)),
            )) {
            val response = read(f, query)
            assertEquals(422, response.statusCode(), response.body())
        }
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='audit.read'",
                f.company,
                f.account,
            )
        failure(read(f), 403, "access_denied")
        assertSame(marker, auditProbe.failure.get())
        assertEquals(0, auditProbe.reads.get())
    }

    @Test
    fun technicalFailuresRemainSanitizedAndAuditRecoveryRemainsAvailableDuringMaintenance() {
        val f = fixture()
        seed(f)
        val settings =
            command(
                f.browser,
                "/api/v1/companies/${f.company}/settings/client-policy",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to null,
                        "activateAt" to null,
                        "disabledModules" to emptyList<String>(),
                        "minimumBuilds" to mapOf("android" to 10, "ios" to 10, "web" to 10),
                        "maintenance" to
                            mapOf(
                                "startsAt" to clock.instant(),
                                "endsAt" to clock.instant().plusSeconds(120),
                            ),
                        "reason" to "Audit recovery fixture",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, settings.statusCode(), settings.body())
        assertEquals(1, page(f, mapOf("action" to "fixture.created"))["items"].size())
        auditProbe.failure.set(IllegalStateException("PRIVATE_AUDIT_DIAGNOSTIC"))
        val failed = read(f)
        failure(failed, 500, "database_failure")
        assertFalse(failed.body().contains("PRIVATE_AUDIT_DIAGNOSTIC"))
        assertEquals(1, page(f, mapOf("action" to "fixture.created"))["items"].size())
    }
}
