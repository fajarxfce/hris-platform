package dev.fajar.hris.leave.data.models

import dev.fajar.hris.schema.tables.records.LeaveCarryExpiriesRecord
import java.util.UUID

data class LeaveCarryExpiryOverviewRow(
    val expiry: LeaveCarryExpiriesRecord,
    val jobId: UUID,
    val jobCompanyId: UUID,
    val jobKind: String,
    val typeCode: String,
    val typeName: String,
    val status: String,
    val completedEmployees: Int,
    val failureCode: String?,
)
