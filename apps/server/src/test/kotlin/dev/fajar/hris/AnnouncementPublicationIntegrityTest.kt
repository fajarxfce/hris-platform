package dev.fajar.hris

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.JobStep
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessException

class AnnouncementPublicationIntegrityTest : AnnouncementPublicationApiFixture() {
    @Test
    fun leaseExpiryAfterInboxInsertionRollsBackEverythingAndTheOriginalLeaseCanRetry() {
        val f = fixture()
        val recipient = member(f)
        val id = draft(f)
        ok(publish(f, id))
        val lease = claim().single()
        val audits = count(f, "audit_entries")
        publicationProbe.afterInbox = {
            runtimeDatabase.update(
                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                lease.job.request.id,
            )
        }
        failure(advance.execute(f.actor, lease), "job_lease_lost")
        assertEquals(0, count(f, "announcement_publications"))
        assertEquals(0, count(f, "inbox_items"))
        assertEquals(audits, count(f, "audit_entries"))
        assertEquals("QUEUED", view(f, id)["status"].asString())
        assertEquals(0, job(f, lease.job.request.id)["completedItems"].asInt())
        publicationProbe.clear()
        assertEquals(Result.Success(JobStep(1, true)), advance.execute(f.actor, lease))
        assertEquals(1, inbox(f, recipient).size())
    }

    @Test
    fun omittedRecipientWritesCannotCommitAPublication() {
        val f = fixture()
        member(f)
        val id = draft(f)
        ok(publish(f, id))
        val lease = claim().single()
        publicationProbe.omitInbox = true
        assertTrue(advance.execute(f.actor, lease) is Result.Failed)
        assertEquals(0, count(f, "announcement_publications"))
        assertEquals(0, count(f, "inbox_items"))
        assertEquals(2, count(f, "announcement_revisions"))
        publicationProbe.clear()
        assertEquals(Result.Success(JobStep(1, true)), advance.execute(f.actor, lease))
    }

    @Test
    fun deferredDatabaseConstraintRejectsAnOtherwiseValidIncompletePublication() {
        val f = fixture()
        val recipient = member(f)
        val id = draft(f)
        ok(publish(f, id))
        val lease = claim().single()
        val result =
            transactions.run(f.actor) {
                safeDatabaseCall {
                    runtimeDatabase.update(
                        """insert into announcement_publications(company_id,id,announcement_id,content_version,published_at,actor_id,recipients,recipient_count,audience_versions)
                values(?,?,?,1,?,?,?::jsonb,?, '{}'::jsonb)""",
                        f.company,
                        lease.job.request.id,
                        id,
                        java.sql.Timestamp.from(clock.instant()),
                        f.actor.accountId,
                        json.writeValueAsString(
                            mapOf(recipient.account.toString() to recipient.employment.toString())
                        ),
                        1,
                    )
                }
            }
        assertTrue(result is Result.Failed, result.toString())
        assertEquals(0, count(f, "announcement_publications"))
        assertEquals(0, count(f, "inbox_items"))
        assertEquals("RUNNING", job(f, lease.job.request.id)["status"].asString())
    }

    @Test
    fun lateAuditFailureRollsBackFrozenContentInboxAndJobCompletion() {
        val f = fixture()
        member(f)
        val id = draft(f)
        ok(publish(f, id))
        val lease = claim().single()
        val constraint = "publication_fixture_" + id.toString().replace("-", "")
        database()
            .execute(
                "alter table audit_entries add constraint $constraint check(not(company_id='${f.company}'::uuid and action='communications.announcement_published'))"
            )
        try {
            assertTrue(advance.execute(f.actor, lease) is Result.Failed)
            assertEquals(0, count(f, "announcement_publications"))
            assertEquals(0, count(f, "inbox_items"))
            assertEquals("RUNNING", job(f, lease.job.request.id)["status"].asString())
            assertEquals("QUEUED", view(f, id)["status"].asString())
        } finally {
            database().execute("alter table audit_entries drop constraint $constraint")
        }
        assertEquals(Result.Success(JobStep(1, true)), advance.execute(f.actor, lease))
    }

    @Test
    fun databaseRejectsIdentityChangesReadReversalAndWithdrawalWithoutArchive() {
        val f = fixture()
        val recipient = member(f)
        val other = member(f)
        published(f, draft(f))
        val id = UUID.fromString(inbox(f, recipient).single()["id"].asString())
        ok(inboxAction(f, recipient, id, "acknowledge", 0))
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update inbox_items set owner_account_id=?,version=version+1 where company_id=? and id=?",
                    other.account,
                    f.company,
                    id,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update inbox_items set read_at=null,acknowledged_at=null,version=version+1 where company_id=? and id=?",
                    f.company,
                    id,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database()
                .update(
                    "update inbox_items set withdrawn=true,version=version+1 where company_id=? and id=?",
                    f.company,
                    id,
                )
        }
        assertThrows(DataAccessException::class.java) {
            database().update("delete from inbox_items where company_id=? and id=?", f.company, id)
        }
        val foreign = fixture()
        val result =
            transactions.run(foreign.actor) {
                safeDatabaseCall {
                    runtimeDatabase.queryForObject(
                        "select count(*) from inbox_items where company_id=?",
                        Int::class.java,
                        f.company,
                    )
                }
            }
        assertEquals(Result.Success(0), result)
    }

    @Test
    fun publicationCannotBindARecipientToAnotherAccountsEmployment() {
        val f = fixture()
        val recipient = member(f)
        val other = member(f)
        val id = draft(f)
        ok(publish(f, id))
        val lease = claim().single()
        val result =
            transactions.run(f.actor) {
                val insert = safeDatabaseCall {
                    runtimeDatabase.update(
                        """insert into announcement_publications(company_id,id,announcement_id,content_version,published_at,actor_id,recipients,recipient_count,audience_versions)
                    values(?,?,?,1,?,?,?::jsonb,1,'{}'::jsonb)""",
                        f.company,
                        lease.job.request.id,
                        id,
                        java.sql.Timestamp.from(clock.instant()),
                        f.actor.accountId,
                        json.writeValueAsString(
                            mapOf(recipient.account.toString() to other.employment.toString())
                        ),
                    )
                }
                assertTrue(
                    insert is Result.Failed,
                    "Invalid recipient must fail at insertion before the deferred completeness check",
                )
                insert
            }
        assertTrue(result is Result.Failed)
        assertEquals(0, count(f, "announcement_publications"))
    }
}
