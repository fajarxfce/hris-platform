package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.requests.*
import dev.fajar.hris.payroll.domain.entities.*

fun PayrollAmendmentSourceReferenceRequest.toReference() =
    PayrollAmendmentSourceReference(assessmentId, revision)

fun PayrollAmendmentRemunerationRequest.toRemuneration() =
    PayrollAmendmentRemuneration(assessmentId, compensation?.toTerms(), input?.toTerms())
