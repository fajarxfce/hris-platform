package dev.fajar.hris.leave.data.models

import dev.fajar.hris.schema.tables.records.LeaveAccountsRecord
import dev.fajar.hris.schema.tables.records.LeaveCarryBucketsRecord

data class LeaveBalanceSummaryRow(
    val account: LeaveAccountsRecord,
    val typeCode: String,
    val typeName: String,
    val carry: LeaveCarryBucketsRecord? = null,
)
