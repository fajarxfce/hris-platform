package dev.fajar.hris.payroll.data.models

import dev.fajar.hris.schema.tables.records.PayrollPolicyRevisionsRecord

data class PayrollPolicyRow(val version: Long, val revision: PayrollPolicyRevisionsRecord)
