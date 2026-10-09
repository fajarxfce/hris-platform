package dev.fajar.hris.leave.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.leave.data.datasources.*
import dev.fajar.hris.leave.data.mappers.*
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.LeaveRequestRepository
import dev.fajar.hris.schema.tables.records.LeaveRequestChangesRecord
import java.time.*
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredLeaveRequestRepository(
    private val source: LeaveRequestDataSource,
    private val allocations: LeaveAllocationDataSource,
    private val attachments: LeaveAttachmentDataSource,
    private val json: ObjectMapper,
) : LeaveRequestRepository {
    override fun find(companyId: UUID, id: UUID): Result<LeaveRequest?> = safeDatabaseCall {
        source.find(companyId, id)?.let { row ->
            row.toRequest(json, attachments.list(companyId, id).map { it.toAttachment() })
        }
    }

    override fun list(
        companyId: UUID,
        employeeId: UUID?,
        status: LeaveStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<LeaveRequestSummary>> = safeDatabaseCall {
        val rows = source.list(companyId, employeeId, status?.name, after, limit + 1)
        Page(
            rows.take(limit).map { it.toSummary() },
            if (rows.size > limit) rows[limit - 1].id.toString() else null,
        )
    }

    override fun history(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<LeaveRequestChange>> = safeDatabaseCall {
        val rows = source.history(companyId, id, after, limit + 1)
        Page(
            rows.take(limit).map { it.toChange() },
            if (rows.size > limit) rows[limit - 1].revision.toString() else null,
        )
    }

    override fun occupancy(
        companyId: UUID,
        employeeId: UUID,
        from: LocalDate,
        until: LocalDate,
    ): Result<List<LeaveOccupancy>> = safeDatabaseCall {
        allocations.occupancy(companyId, employeeId, from, until).map {
            LeaveOccupancy(it.workDate, it.mask)
        }
    }

    override fun create(companyId: UUID, request: LeaveRequest): Result<MutationReceipt> =
        safeDatabaseCall {
            source.insert(request.toRow(companyId, json))
            attachments.insert(
                request.attachments.mapIndexed { index, item ->
                    item.toRow(companyId, request.id, index + 1)
                }
            )
            allocations.insert(request.toAllocations(companyId))
            source.append(
                LeaveRequestChangesRecord().also {
                    it.companyId = companyId
                    it.requestId = request.id
                    it.kind = LeaveChangeKind.SUBMITTED.name
                    it.revision = 0
                    it.status = request.status.name
                    it.cancellationApprovalId = request.cancellationApprovalId
                    it.actorId = request.authorId
                    it.recordedAt = request.submittedAt.atOffset(ZoneOffset.UTC)
                    it.reason = request.reason
                }
            )
            MutationReceipt(request.id, 0)
        }

    override fun update(
        actor: Actor,
        id: UUID,
        expectedVersion: Long,
        status: LeaveStatus,
        cancellationApprovalId: UUID?,
        kind: LeaveChangeKind,
        reason: String,
        at: Instant,
    ): Result<MutationReceipt> {
        val company = requireNotNull(actor.companyId)
        return safeDatabaseCall {
                source.update(company, id, expectedVersion, status.name, cancellationApprovalId)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.append(
                        LeaveRequestChangesRecord().also {
                            it.companyId = company
                            it.requestId = id
                            it.kind = kind.name
                            it.revision = version
                            it.status = status.name
                            it.cancellationApprovalId = cancellationApprovalId
                            it.actorId = actor.accountId
                            it.recordedAt = at.atOffset(ZoneOffset.UTC)
                            it.reason = reason
                        }
                    )
                    MutationReceipt(id, version)
                }
            }
    }

    override fun releaseOccupancy(companyId: UUID, id: UUID, expectedHalfDays: Int): Result<Unit> =
        safeDatabaseCall { allocations.remove(companyId, id) }
            .flatMap {
                if (it == expectedHalfDays) Result.Success(Unit)
                else Result.Failed(Failure(FailureKind.CONFLICT, "leave_occupancy_conflict"))
            }
}
