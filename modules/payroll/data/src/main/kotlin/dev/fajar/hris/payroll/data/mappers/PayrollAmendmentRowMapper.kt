package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.PayrollAmendmentSummaryRow
import dev.fajar.hris.schema.tables.PayrollAmendmentRevisions.PAYROLL_AMENDMENT_REVISIONS as R
import dev.fajar.hris.schema.tables.PayrollAmendments.PAYROLL_AMENDMENTS as H
import org.jooq.Record

fun Record.toPayrollAmendmentSummaryRow() =
    PayrollAmendmentSummaryRow(
        into(H),
        get(R.REVISION)!!,
        get(R.STATUS)!!,
        get(R.ACTION)!!,
        get(R.LATEST_CALCULATION_ID),
        get(R.ACTOR_ID)!!,
        get(R.RECORDED_AT)!!,
        get(R.REASON)!!,
    )
