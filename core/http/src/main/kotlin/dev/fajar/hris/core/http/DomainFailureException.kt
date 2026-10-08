package dev.fajar.hris.core.http

import dev.fajar.hris.core.domain.Failure

class DomainFailureException(val failure: Failure) : RuntimeException(null, null, false, false)
