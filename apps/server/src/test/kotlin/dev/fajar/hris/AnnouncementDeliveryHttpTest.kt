package dev.fajar.hris

import dev.fajar.hris.communications.domain.entities.AnnouncementDeliveryCounts
import dev.fajar.hris.communications.domain.repositories.*
import dev.fajar.hris.communications.domain.usecases.GetAnnouncementDelivery
import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AnnouncementDeliveryHttpTest : AnnouncementPublicationApiFixture() {
    @Autowired private lateinit var delivery: GetAnnouncementDelivery
    @Autowired private lateinit var announcements: AnnouncementRepository
    @Autowired private lateinit var counts: AnnouncementDeliveryRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var members: MembershipRepository
    @Autowired private lateinit var identities: IdentityRepository

    private fun read(f: Fixture, id: UUID) = get(f.browser, "${f.path}/$id/delivery")

    private fun published(f: Fixture): UUID {
        member(f)
        val id = draft(f)
        ok(publish(f, id))
        assertTrue(advance.execute(f.actor, claim().single()) is Result.Success)
        return id
    }

    @Test
    fun draftAndPublishedCountsAreReadOnlyAndDiscloseNoRecipientOrSessionMetadata() {
        val f = fixture()
        val draft = draft(f)
        val empty = ok(read(f, draft))
        assertEquals(0, empty["recipientCount"].asInt())
        assertEquals(0, empty["unavailableRecipients"].asInt())
        val id = published(f)
        val before = count(f, "audit_entries")
        val response = ok(read(f, id))
        assertEquals(
            setOf(
                "announcementId",
                "recipientCount",
                "pending",
                "processing",
                "completed",
                "failed",
                "superseded",
                "unavailableRecipients",
                "processedDevices",
                "acceptedDevices",
                "rejectedDevices",
                "observedAt",
            ),
            response.properties().map { it.key }.toSet(),
        )
        assertEquals(id.toString(), response["announcementId"].asString())
        assertEquals(1, response["recipientCount"].asInt())
        assertEquals(1, response["pending"].asInt())
        assertEquals(0, response["processedDevices"].asInt())
        assertEquals(before, count(f, "audit_entries"))
        assertEquals(
            Result.Success(0),
            transactions.run(f.actor) {
                safeDatabaseCall {
                    runtimeDatabase.queryForObject(
                        "select count(*) from inbox_push_dispatches where company_id=?",
                        Int::class.java,
                        f.company,
                    )!!
                }
            },
        )
    }

    @Test
    fun terminalFailureAndPurgedHistoryRemainDistinctFromCompletedDelivery() {
        val f = fixture()
        val id = published(f)
        database()
            .update(
                "update inbox_push_dispatches set state='FAILED',failure_code='push_attempts_exhausted',finished_at=clock_timestamp() where company_id=?",
                f.company,
            )
        val failed = ok(read(f, id))
        assertEquals(1, failed["failed"].asInt())
        assertEquals(0, failed["pending"].asInt())
        assertEquals(0, failed["completed"].asInt())
        database().update("delete from inbox_push_dispatches where company_id=?", f.company)
        val unavailable = ok(read(f, id))
        assertEquals(1, unavailable["recipientCount"].asInt())
        assertEquals(1, unavailable["unavailableRecipients"].asInt())
        assertEquals(0, unavailable["failed"].asInt())
        assertEquals(0, unavailable["completed"].asInt())
    }

    @Test
    fun currentCompanyManagementIsIndependentOfRecipientOrGeneralJobAuthority() {
        val f = fixture()
        val id = published(f)
        val manager = member(f, listOf("announcements.manage"))
        val result = ok(get(manager.browser, "${f.path}/$id/delivery"))
        assertEquals(1, result["pending"].asInt())
        val reader = member(f)
        error(get(reader.browser, "${f.path}/$id/delivery"), 403, "access_denied")
        error(read(fixture(), id), 404, "announcement_not_found")
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='announcements.manage'",
                f.company,
                manager.account,
            )
        error(get(manager.browser, "${f.path}/$id/delivery"), 403, "access_denied")
    }

    @Test
    fun runtimeCannotForgeProjectionWritesOrInvokeItsFixedTrigger() {
        val f = fixture()
        val id = published(f)
        assertEquals(
            Result.Success(0),
            transactions.run(f.actor) {
                safeDatabaseCall {
                    runtimeDatabase.update(
                        "update announcement_push_delivery set state='COMPLETE' where company_id=?",
                        f.company,
                    )
                }
            },
        )
        assertEquals(
            Result.Success(0),
            transactions.run(f.actor) {
                safeDatabaseCall {
                    runtimeDatabase.update(
                        "delete from announcement_push_delivery where company_id=?",
                        f.company,
                    )
                }
            },
        )
        assertTrue(
            transactions.run(f.actor) {
                safeDatabaseCall {
                    runtimeDatabase.update(
                        "insert into announcement_push_delivery select company_id,announcement_id,inbox_id,'COMPLETE',0,0,0 from announcement_push_delivery where company_id=?",
                        f.company,
                    )
                }
            } is Result.Failed
        )
        assertEquals(
            Result.Success(false),
            transactions.run(f.actor) {
                safeDatabaseCall {
                    runtimeDatabase.queryForObject(
                        "select has_function_privilege(current_user,'capture_announcement_push_delivery()','EXECUTE')",
                        Boolean::class.java,
                    )!!
                }
            },
        )
        assertEquals(1, ok(read(f, id))["pending"].asInt())
        assertEquals(
            7,
            database()
                .queryForObject(
                    "select count(*) from information_schema.columns where table_schema='public' and table_name='announcement_push_delivery'",
                    Int::class.java,
                ),
        )
        assertEquals(
            true,
            database()
                .queryForObject(
                    "select relforcerowsecurity from pg_class where oid='announcement_push_delivery'::regclass",
                    Boolean::class.java,
                ),
        )
    }

    @Test
    fun credentialChangesWhileWaitingCannotDiscloseRetainedCounts() {
        val f = fixture()
        val id = published(f)
        val barrier = AccountLockProbe.Barrier(f.actor.accountId)
        accountProbe.current.set(barrier)
        Executors.newSingleThreadExecutor().use { executor ->
            val pending = executor.submit<Result<*>> { delivery.execute(f.actor, id) }
            try {
                assertTrue(barrier.entered.await(5, TimeUnit.SECONDS))
                database()
                    .update(
                        "update accounts set security_version=security_version+1 where id=?",
                        f.actor.accountId,
                    )
            } finally {
                barrier.release.countDown()
            }
            failure(pending.get(15, TimeUnit.SECONDS), "session_revoked")
        }
    }

    @Test
    fun originalGrantAndMfaAfterAcquisitionRemainRequired() {
        val f = fixture()
        val id = published(f)
        failure(delivery.execute(f.actor.copy(permissions = emptySet()), id), "access_denied")
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.actor.accountId,
            )
        val security =
            IdentitySecurityPolicy(
                maximumMfaAge = Duration.ofSeconds(4),
                recentAuthenticationAge = Duration.ofSeconds(2),
            )
        val delayed =
            object : AnnouncementDeliveryRepository by counts {
                override fun counts(
                    companyId: UUID,
                    announcementId: UUID,
                ): Result<AnnouncementDeliveryCounts> {
                    val result = counts.counts(companyId, announcementId)
                    clock.set(clock.instant().plusSeconds(5))
                    return result
                }
            }
        val guarded =
            GetAnnouncementDelivery(
                announcements,
                delayed,
                companies,
                members,
                identities,
                transactions,
                clock,
                security,
            )
        failure(guarded.execute(f.actor.copy(mfaVerifiedAt = clock.instant()), id), "mfa_required")
    }
}
