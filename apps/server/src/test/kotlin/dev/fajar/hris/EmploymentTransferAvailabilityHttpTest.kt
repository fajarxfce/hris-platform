package dev.fajar.hris

import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.core.domain.CrossCompanyTransactionRunner
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.core.domain.TransactionRunner
import dev.fajar.hris.core.domain.flatMap
import dev.fajar.hris.core.domain.map
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class EmploymentTransferAvailabilityHttpTest : ClientPolicyApiFixture() {
    @Autowired private lateinit var policies: CompanyClientPolicyRepository
    @Autowired private lateinit var transfers: CrossCompanyTransactionRunner
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var runtime: JdbcTemplate

    @Test
    fun destinationMaintenanceAndMinimumBuildsUseTheAuthenticatedClientMetadata() {
        val source = fixture()
        val target = source.copy(company = company(source.browser, source.csrf))
        val employee = employee(source.browser, source.csrf, source.company)
        val path = "${source.root}/employees/$employee/transfer"
        val payload = transferPayload(target.company, UUID.randomUUID())
        val key = UUID.randomUUID()
        assertEquals(
            200,
            save(
                    target,
                    changes =
                        mapOf(
                            "maintenance" to
                                mapOf(
                                    "startsAt" to clock.instant(),
                                    "endsAt" to clock.instant().plusSeconds(120),
                                )
                        ),
                )
                .statusCode(),
        )
        failure(
            command(source.browser, path, payload, source.csrf, key),
            503,
            "company_maintenance",
        )
        assertEquals(
            200,
            save(target, 0, mapOf("minimumBuilds" to mapOf("android" to 0, "ios" to 0, "web" to 2)))
                .statusCode(),
        )
        failure(
            command(source.browser, path, payload, source.csrf, key),
            403,
            "client_version_required",
        )
        for (build in listOf(1, 2)) {
            val response =
                source.browser.send(
                    HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .header("X-CSRF-TOKEN", source.csrf)
                        .header("Idempotency-Key", key.toString())
                        .header("X-HRIS-Client-Platform", "WEB")
                        .header("X-HRIS-Client-Build", build.toString())
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            if (build == 1) failure(response, 403, "client_update_required")
            else assertEquals(200, response.statusCode(), response.body())
        }
    }

    @Test
    fun destinationPolicyReadScopeDoesNotPermitThirdCompanyReadsOrPolicyWrites() {
        val source = fixture()
        val target = source.copy(company = company(source.browser, source.csrf))
        val third = source.copy(company = company(source.browser, source.csrf))
        for (company in listOf(source, target, third)) assertEquals(200, save(company).statusCode())
        val observed =
            transfers.run(source.actor, target.company) {
                policies.find(source.company).flatMap { first ->
                    policies.find(target.company).flatMap { second ->
                        policies.find(third.company).map { last ->
                            listOf(first?.version, second?.version, last?.version)
                        }
                    }
                }
            }
        assertEquals(Result.Success(listOf(0L, 0L, null)), observed)
        val changed =
            transfers.run(source.actor, target.company) {
                Result.Success(
                    runtime.update(
                        "update company_client_policy_heads set version=version+1 where company_id=?",
                        target.company,
                    )
                )
            }
        assertEquals(Result.Success(0), changed)
        assertEquals(
            Result.Success(null),
            transactions.run(source.actor) { policies.find(target.company) },
        )
        assertEquals(
            0L,
            database()
                .queryForObject(
                    "select version from company_client_policy_heads where company_id=?",
                    Long::class.java,
                    target.company,
                ),
        )
    }

    @Test
    fun destinationModuleAdmissionAppliesBeforeMutationAndReceiptReplay() {
        val source = fixture()
        val target = source.copy(company = company(source.browser, source.csrf))
        val employee = employee(source.browser, source.csrf, source.company)
        val destination = UUID.randomUUID()
        val key = UUID.randomUUID()
        val path = "${source.root}/employees/$employee/transfer"
        val payload = transferPayload(target.company, destination)
        assertEquals(
            200,
            save(target, changes = mapOf("disabledModules" to listOf("PEOPLE"))).statusCode(),
        )

        val blocked = command(source.browser, path, payload, source.csrf, key)
        failure(blocked, 403, "company_module_disabled")
        assertEquals(
            target.company.toString(),
            json.readTree(blocked.body())["parameters"]["companyId"].asString(),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from employment_transfers where id=?",
                    Int::class.java,
                    key,
                ),
        )
        assertEquals(200, save(target, 0).statusCode())
        val accepted = command(source.browser, path, payload, source.csrf, key)
        assertEquals(200, accepted.statusCode(), accepted.body())
        assertEquals(
            200,
            save(target, 1, mapOf("disabledModules" to listOf("PEOPLE"))).statusCode(),
        )
        failure(
            command(source.browser, path, payload, source.csrf, key),
            403,
            "company_module_disabled",
        )
        assertEquals(200, save(target, 2).statusCode())
        val replay = command(source.browser, path, payload, source.csrf, key)
        assertEquals(200, replay.statusCode(), replay.body())
        assertEquals(json.readTree(accepted.body()), json.readTree(replay.body()))
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from employment_transfers where id=?",
                    Int::class.java,
                    key,
                ),
        )
    }

    private fun transferPayload(target: UUID, destination: UUID) =
        json.writeValueAsString(
            mapOf(
                "targetCompanyId" to target,
                "targetEmploymentId" to destination,
                "expectedVersion" to 0,
                "employeeNumber" to "TRANSFER-001",
                "terms" to terms("2026-10-01", start = "2026-10-01"),
                "reason" to "Approved company transfer",
            )
        )
}
