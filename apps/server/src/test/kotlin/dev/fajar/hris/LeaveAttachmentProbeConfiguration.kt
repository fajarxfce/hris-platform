package dev.fajar.hris

import dev.fajar.hris.core.database.datasources.*
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.documents.domain.entities.DocumentReferenceOrigin
import dev.fajar.hris.documents.domain.repositories.DocumentReferenceRepository
import dev.fajar.hris.leave.data.datasources.LeaveAttachmentDataSource
import dev.fajar.hris.schema.tables.records.LeaveRequestAttachmentsRecord
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class LeaveAttachmentProbe {
    @Volatile var omitRows = false
    @Volatile var omitReferences = false
    @Volatile var beforeJournal: ((JournalRow) -> Unit)? = null
    @Volatile var beforeInsert: ((List<LeaveRequestAttachmentsRecord>) -> Unit)? = null

    fun clear() {
        omitRows = false
        omitReferences = false
        beforeJournal = null
        beforeInsert = null
    }
}

@TestConfiguration(proxyBeanMethods = false)
class LeaveAttachmentProbeConfiguration {
    @Bean fun leaveAttachmentProbe() = LeaveAttachmentProbe()

    @Bean
    @Primary
    fun probedLeaveAttachments(
        @Qualifier("leaveAttachmentSource") source: LeaveAttachmentDataSource,
        probe: LeaveAttachmentProbe,
    ): LeaveAttachmentDataSource =
        object : LeaveAttachmentDataSource by source {
            override fun insert(rows: List<LeaveRequestAttachmentsRecord>) {
                probe.beforeInsert?.invoke(rows)
                if (!probe.omitRows) source.insert(rows)
            }
        }

    @Bean
    @Primary
    fun probedLeaveEvidence(
        @Qualifier("documentReferences") source: DocumentReferenceRepository,
        probe: LeaveAttachmentProbe,
    ): DocumentReferenceRepository =
        object : DocumentReferenceRepository by source {
            override fun retain(
                companyId: UUID,
                origin: DocumentReferenceOrigin,
                revisionIds: Set<UUID>,
                recordedBy: UUID,
                at: Instant,
            ): Result<Unit> =
                if (probe.omitReferences) Result.Success(Unit)
                else source.retain(companyId, origin, revisionIds, recordedBy, at)
        }

    @Bean
    @Primary
    fun probedLeaveJournal(
        @Qualifier("journalSource") source: ChangeJournalDataSource,
        probe: LeaveAttachmentProbe,
    ): ChangeJournalDataSource =
        object : ChangeJournalDataSource {
            override fun append(row: JournalRow) {
                probe.beforeJournal?.invoke(row)
                source.append(row)
            }
        }
}
