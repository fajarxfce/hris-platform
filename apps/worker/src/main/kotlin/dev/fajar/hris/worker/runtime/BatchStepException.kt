package dev.fajar.hris.worker.runtime

import dev.fajar.hris.core.domain.Failure

class BatchStepException(val failure: Failure) : RuntimeException(failure.code)
