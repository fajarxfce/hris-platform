package dev.fajar.hris

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MobileSyncRetentionHttpTest : MobileSyncApiFixture() {
    @Test
    fun publicationAndRetentionAreBoundedAndPendingChangesNeverExpireBeforePublication() {
        val f = expenseFixture()
        val initial = token(f)
        // Owned infrastructure fixture to exercise batch limits without 501 unrelated HTTP
        // commands.
        database()
            .update(
                "insert into mobile_sync_changes(company_id,collection,resource_id,employment_id,resource_version,operation) select ?,'EXPENSE_CLAIMS',?,?,n,'UPSERT' from generate_series(1,501) n",
                f.company,
                f.claim,
                f.employee,
            )
        assertEquals(200, success(publisher.publish()))
        assertEquals(200, success(publisher.publish()))
        assertEquals(101, success(publisher.publish()))
        assertEquals(0, success(publisher.publish()))
        val pending = UUID.randomUUID()
        database()
            .update(
                "insert into mobile_sync_changes(id,company_id,collection,resource_id,employment_id,resource_version,operation,recorded_at) values(?,?,'EXPENSE_CLAIMS',?,?,502,'UPSERT',clock_timestamp()-interval '20 days')",
                pending,
                f.company,
                f.claim,
                f.employee,
            )
        // Only the test migration role can age immutable publication metadata.
        database()
            .execute("alter table mobile_sync_changes disable trigger mobile_sync_change_immutable")
        try {
            database()
                .update(
                    "update mobile_sync_changes set published_at=clock_timestamp()-interval '9 days' where company_id=? and sequence is not null",
                    f.company,
                )
        } finally {
            database()
                .execute(
                    "alter table mobile_sync_changes enable trigger mobile_sync_change_immutable"
                )
        }
        assertEquals(500, success(publisher.prune()))
        assertEquals(
            500,
            database()
                .queryForObject(
                    "select pruned_through from mobile_sync_heads where company_id=?",
                    Long::class.java,
                    f.company,
                ),
        )
        error(changes(f, initial), 409, "sync_cursor_out_of_range")
        assertEquals(1, success(publisher.prune()))
        assertEquals(0, success(publisher.prune()))
        assertEquals(1, queueCount(f))
        assertEquals(
            pending,
            database()
                .queryForObject(
                    "select id from mobile_sync_changes where company_id=?",
                    UUID::class.java,
                    f.company,
                ),
        )
        assertEquals(1, success(publisher.publish()))
        assertEquals(0, success(publisher.prune()))
        assertEquals(
            502,
            database()
                .queryForObject(
                    "select head_position from mobile_sync_heads where company_id=?",
                    Long::class.java,
                    f.company,
                ),
        )
        assertTrue(items(body(bootstrap(f))).isEmpty())
    }

    @Test
    fun runtimeCannotPublishRewriteOrReadAnotherCompanyAndWorkerCannotPruneRecentData() {
        val f = expenseFixture()
        val other = expenseFixture()
        body(saveExpense(f))
        body(saveExpense(other))
        assertEquals(
            0,
            success(
                transactions.run(actor(f)) {
                    safeDatabaseCall {
                        runtimeJdbc.queryForObject(
                            "select count(*) from mobile_sync_changes where company_id=?",
                            Int::class.java,
                            other.company,
                        )!!
                    }
                }
            ),
        )
        assertTrue(transactions.run(actor(f)) { sync.publish(1) } is Result.Failed)
        assertTrue(transactions.run(actor(f)) { sync.prune(1) } is Result.Failed)
        assertTrue(
            transactions.run(actor(f)) {
                safeDatabaseCall {
                    runtimeJdbc.update(
                        "update mobile_sync_changes set resource_version=99 where company_id=?",
                        f.company,
                    )
                }
            } is Result.Failed
        )
        assertTrue(
            transactions.run(actor(f)) {
                safeDatabaseCall {
                    runtimeJdbc.update(
                        "update mobile_sync_heads set head_position=head_position+1,published_at=clock_timestamp() where company_id=?",
                        f.company,
                    )
                }
            } is Result.Failed
        )
        assertTrue(publisher.publish(201) is Result.Failed)
        assertTrue(publisher.prune(501) is Result.Failed)
        assertEquals(2, success(publisher.publish()))
        assertEquals(0, success(publisher.prune()))
        assertEquals(1, queueCount(f))
        assertEquals(1, queueCount(other))
    }
}
