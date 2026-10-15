package dev.fajar.hris.leave.data.models

import dev.fajar.hris.schema.tables.records.LeaveCarryAllocationsRecord

data class LeaveCarryAllocationRow(val allocation: LeaveCarryAllocationsRecord, val year: Int)
