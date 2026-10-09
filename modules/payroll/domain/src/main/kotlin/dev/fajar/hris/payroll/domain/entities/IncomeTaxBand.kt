package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal

data class IncomeTaxBand(val upperInclusive: BigDecimal?, val rate: BigDecimal)
