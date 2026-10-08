package dev.fajar.hris.leave.domain.entities

enum class LeavePortion(val mask: Int, val halfDays: Int) {
    FULL(3, 2),
    FIRST_HALF(1, 1),
    SECOND_HALF(2, 1),
}
