package dev.fajar.hris.leave.domain.entities

data class LeaveBatchCounts(
    val applied: Int,
    val unchanged: Int,
    val skipped: Int,
    val failed: Int,
) {
    val completed: Int
        get() = applied + unchanged + skipped + failed
}
