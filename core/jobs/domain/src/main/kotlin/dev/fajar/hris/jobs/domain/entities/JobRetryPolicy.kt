package dev.fajar.hris.jobs.domain.entities

data class JobRetryPolicy(val maximumAttempts: Int = 8) {
    init {
        require(maximumAttempts in 1..8)
    }

    fun delaySeconds(attempt: Int): Long {
        require(attempt in 1..maximumAttempts)
        return minOf(300L, 5L shl (attempt - 1))
    }
}
