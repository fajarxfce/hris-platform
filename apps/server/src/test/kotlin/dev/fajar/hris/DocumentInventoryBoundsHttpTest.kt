package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.JobStep
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class DocumentInventoryBoundsHttpTest : DocumentInventoryApiFixture() {
    @Autowired private lateinit var scope: TransactionRunner
    @Autowired private lateinit var sql: DSLContext

    @Test
    fun aGrowingBucketStopsAtThePageCeilingWithAnExplicitIncompleteOutcome() {
        val f = fixture()
        val lease = beginInventory(f)
        val id = inventoryId(lease)
        // Seed valid historical pages close to the ceiling without 9,999 network calls.
        database()
            .execute(
                """do ${'$'}${'$'} declare n integer; k bytea; begin
            for n in 1..9999 loop
                k:=convert_to('${f.company}/entry-'||lpad(n::text,5,'0'),'UTF8');
                insert into document_inventory_pages(company_id,run_id,page_no,job_id,created_at,actor_id,has_more,last_key,retained,unknown,anomalous,queued,recovery_exhausted,scheduled)
                    values('${f.company}','$id',n,'${lease.job.request.id}',clock_timestamp(),'${f.actor.accountId}',true,k,0,1,0,0,0,0);
                update document_inventory_runs set version=version+1,pages=n,last_key=k,unknown=n where id='$id';
            end loop;
            update background_jobs set completed_items=9999,checkpoint=jsonb_build_object('pages','9999'),version=version+1 where id='${lease.job.request.id}';
        end ${'$'}${'$'}"""
            )
        for (index in 10000..10100) storageProbe.objects["${f.company}/entry-$index"] =
            DocumentStorageProbe.Value(byteArrayOf(1), "unknown")
        assertEquals(Result.Success(JobStep(10000, true)), runInventory(f, lease))
        val result = inventory(f, id)
        assertEquals("LIMIT_REACHED", result.get("status").asString())
        assertEquals(10000, result.get("pages").asInt())
        assertEquals(10099, result.get("scanned").asInt())
        assertEquals(101, storageProbe.objects.size)
        assertEquals(1, storageProbe.listings.get())
        assertEquals("SUCCEEDED", jobStatus(lease))
        assertEquals(409, resumeInventory(f, id, 10000).statusCode())
    }

    @Test
    fun inventoryTablesAndInvokerViewsRemainCompanyScopedForRuntimeCredentials() {
        val f = fixture()
        orphan(f)
        val lease = beginInventory(f)
        assertEquals(Result.Success(JobStep(1, true)), runInventory(f, lease))
        val other = fixture()
        val tables =
            listOf(
                "document_inventory_runs",
                "document_inventory_attempts",
                "document_inventory_pages",
                "document_inventory_recoveries",
                "document_inventory_views",
                "document_inventory_attempt_views",
            )
        val result =
            scope.run(other.actor) {
                Result.Success(tables.map { sql.fetchCount(DSL.table(DSL.name(it))) })
            }
        assertEquals(Result.Success(List(tables.size) { 0 }), result)
    }
}
