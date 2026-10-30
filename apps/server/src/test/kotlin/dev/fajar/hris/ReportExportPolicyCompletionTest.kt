package dev.fajar.hris

import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.usecases.ManageReportExports
import dev.fajar.hris.reporting.domain.usecases.ReadReportExports
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import

@Import(ReportExportReadProbeConfiguration::class)
class ReportExportPolicyCompletionTest : ReportExportApiFixture() {
    @Autowired private lateinit var probe: ReportExportReadProbe

    @AfterEach
    fun clearProvider() {
        probe.afterRead = {}
        probe.modify = { it }
        probe.reads = 0
        probe.objects.clear()
    }

    @ParameterizedTest
    @ValueSource(strings = ["start", "start_replay", "resume", "resume_replay"])
    fun latePolicyAcquisitionCannotCommitOrReplayAnExportCommand(mode: String) {
        val f = exportFixture()
        val original = clock.instant()
        val strict = security.copy(enforceMfa = true)
        val proof = original.minus(strict.maximumMfaAge).plusSeconds(1)
        val secured = actor(f).copy(mfaVerifiedAt = proof)
        val operation = UUID.randomUUID()
        val isResume = mode.startsWith("resume")
        val isReplay = mode.endsWith("replay")
        val id = if (isResume) start(f) else null
        if (id != null)
            value(abort.execute(lease(f, id), Failure(FailureKind.UNAVAILABLE, "test_stopped")))
        val version = id?.let { find(f, it).version }
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.account,
            )
        var calls = 0
        var expire = false
        val target = if (isReplay) 1 else if (isResume) 4 else 3
        val observed =
            object : CompanyClientPolicyRepository by policies {
                override fun effective(companyId: UUID, at: Instant) =
                    policies.effective(companyId, at).also {
                        assertTrue(it is Result.Success, it.toString())
                        calls++
                        if (expire && calls == target) clock.set(original.plusSeconds(2))
                    }
            }
        val commands =
            ManageReportExports(
                exports,
                jobs,
                observed,
                companies,
                members,
                identities,
                operations,
                journal,
                transactions,
                sources,
                strict,
                clock,
            )
        fun invoke(current: Actor) =
            if (id == null)
                commands.start(
                    current,
                    operation,
                    selection(),
                    ReportExportDataset.SUMMARY,
                    browserClient,
                )
            else commands.resume(current, operation, id, requireNotNull(version), browserClient)
        fun facts() =
            database()
                .queryForList(
                    "select e.id,e.version,e.job_id,e.status,j.status as job_status,j.cancellation_requested from report_exports e join background_jobs j on j.id=e.job_id where e.company_id=? order by e.id",
                    f.company,
                )
        try {
            val originalReceipt = if (isReplay) value(invoke(secured)) else null
            val before = facts()
            calls = 0
            expire = true
            rejected(invoke(secured), "mfa_required")
            assertEquals(target, calls)
            assertEquals(before, facts())
            expire = false
            val renewed = secured.copy(mfaVerifiedAt = clock.instant())
            val recovered = value(invoke(renewed))
            if (originalReceipt != null)
                assertEquals(originalReceipt.copy(replayed = true), recovered)
            else assertFalse(recovered.replayed)
            assertEquals(recovered.copy(replayed = true), value(invoke(renewed)))
        } finally {
            database().update("update accounts set mfa_secret_encrypted=null where id=?", f.account)
            clock.set(original)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["download", "before_storage", "after_storage"])
    fun contentPolicyCompletionRejectsExpiredProofBeforePreparingOrReleasingBytes(mode: String) {
        val f = exportFixture()
        val id = start(f)
        finish(f, id, worker(), lease(f, id))
        val before = find(f, id)
        val original = clock.instant()
        val strict = security.copy(enforceMfa = true)
        val secured =
            actor(f).copy(mfaVerifiedAt = original.minus(strict.maximumMfaAge).plusSeconds(1))
        database()
            .update(
                "update accounts set mfa_secret_encrypted='fixture-enrolled' where id=?",
                f.account,
            )
        var calls = 0
        var expire = false
        val target = if (mode == "after_storage") 4 else 2
        val observed =
            object : CompanyClientPolicyRepository by policies {
                override fun effective(companyId: UUID, at: Instant) =
                    policies.effective(companyId, at).also {
                        assertTrue(it is Result.Success, it.toString())
                        calls++
                        if (expire && calls == target) clock.set(original.plusSeconds(2))
                    }
            }
        val reader =
            ReadReportExports(
                exports,
                snapshots,
                jobs,
                observed,
                companies,
                members,
                identities,
                probe,
                transactions,
                sources,
                strict,
                clock,
            )
        try {
            val download = value(reader.download(secured, id, browserClient))
            val expected = value(reader.read(secured, id, download.tag, 0, 1, browserClient))
            calls = 0
            probe.reads = 0
            expire = true
            val rejected =
                if (mode == "download") reader.download(secured, id, browserClient)
                else reader.read(secured, id, download.tag, 0, 1, browserClient)
            rejected(rejected, "mfa_required")
            assertEquals(target, calls)
            assertEquals(if (mode == "after_storage") 1 else 0, probe.reads)
            assertEquals(before, find(f, id))
            expire = false
            val renewed = secured.copy(mfaVerifiedAt = clock.instant())
            assertEquals(download, value(reader.download(renewed, id, browserClient)))
            assertArrayEquals(
                expected,
                value(reader.read(renewed, id, download.tag, 0, 1, browserClient)),
            )
        } finally {
            database().update("update accounts set mfa_secret_encrypted=null where id=?", f.account)
            clock.set(original)
        }
    }
}
