package dev.fajar.hris.leave.data.models

import dev.fajar.hris.schema.tables.records.LeaveAccountsRecord
import dev.fajar.hris.schema.tables.records.LeaveCarryBucketsRecord

/** One statement reads the ledger projection and its optional carried-credit breakdown. */
data class LeaveBalanceRow(val account: LeaveAccountsRecord, val carry: LeaveCarryBucketsRecord?)
