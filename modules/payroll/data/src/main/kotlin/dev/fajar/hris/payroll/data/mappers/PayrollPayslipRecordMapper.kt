package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.PayrollPayslipSummaryRow
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import org.jooq.Record

/** Technical projection only; conversion to a domain payslip belongs to the repository. */
fun Record.toPayslipSummaryRow() =
    PayrollPayslipSummaryRow(
        get("id", UUID::class.java)!!,
        get("employment_id", UUID::class.java)!!,
        get("employee_number", String::class.java)!!,
        get("employee_name", String::class.java)!!,
        get("tax_month", LocalDate::class.java)!!,
        get("planned_payment_date", LocalDate::class.java)!!,
        get("taxable_gross", BigDecimal::class.java)!!,
        get("withheld", BigDecimal::class.java)!!,
        get("take_home", BigDecimal::class.java)!!,
        get("published_at", OffsetDateTime::class.java)!!,
    )
