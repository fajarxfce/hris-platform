package dev.fajar.hris.people.domain

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.policies.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LifecyclePolicyTest {
    private val definition = LifecycleTaskDefinition("equipment", "Equipment returned", true, 0)
    private val template =
        LifecycleTemplate(
            UUID.randomUUID(),
            "EXIT",
            "Departure",
            LifecycleKind.OFFBOARDING,
            true,
            0,
            listOf(definition),
        )

    private fun case(tasks: List<LifecycleTask>) =
        LifecycleCase(
            UUID.randomUUID(),
            UUID.randomUUID(),
            LifecycleKind.OFFBOARDING,
            LocalDate.of(2026, 9, 30),
            template.id,
            0,
            template.name,
            LifecycleStatus.OPEN,
            2,
            UUID.randomUUID(),
            Instant.EPOCH,
            tasks,
        )

    private fun task(required: Boolean, status: LifecycleTaskStatus) =
        LifecycleTask(
            "equipment",
            "Equipment returned",
            required,
            LocalDate.of(2026, 9, 30),
            null,
            status,
            if (status == LifecycleTaskStatus.PENDING) null else UUID.randomUUID(),
            if (status == LifecycleTaskStatus.PENDING) null else Instant.EPOCH,
        )

    @Test
    fun definitionsHaveBoundedKeysTasksOffsetsAndReasons() {
        assertEquals(
            Result.Success(Unit),
            validateLifecycleTemplate(template, "Standard departure"),
        )
        for (invalid in
            listOf(
                template.copy(tasks = emptyList()),
                template.copy(tasks = listOf(definition, definition)),
                template.copy(tasks = (1..65).map { definition.copy(key = "task_$it") }),
                template.copy(tasks = listOf(definition.copy(dueDays = 366))),
                template.copy(tasks = listOf(definition.copy(key = "../file"))),
            )) assertInstanceOf(
            Result.Failed::class.java,
            validateLifecycleTemplate(invalid, "Reason"),
        )
        assertInstanceOf(Result.Failed::class.java, validateLifecycleTemplate(template, " "))
        assertInstanceOf(Result.Failed::class.java, validateLifecycleDate(LocalDate.MAX, "Reason"))
    }

    @Test
    fun mandatoryTasksCannotBeWaivedAndOptionalTasksNeedAnExplicitOutcome() {
        for (state in listOf(LifecycleTaskStatus.PENDING, LifecycleTaskStatus.WAIVED)) assertEquals(
            Result.Failed(Failure(FailureKind.CONFLICT, "required_lifecycle_tasks_pending")),
            validateLifecycleCompletion(
                case(listOf(task(true, state))),
                2,
                LifecycleKind.OFFBOARDING,
                "Ready",
            ),
        )
        assertEquals(
            Result.Failed(Failure(FailureKind.CONFLICT, "lifecycle_tasks_unresolved")),
            validateLifecycleCompletion(
                case(listOf(task(false, LifecycleTaskStatus.PENDING))),
                2,
                LifecycleKind.OFFBOARDING,
                "Ready",
            ),
        )
        assertEquals(
            Result.Success(Unit),
            validateLifecycleCompletion(
                case(listOf(task(false, LifecycleTaskStatus.WAIVED))),
                2,
                LifecycleKind.OFFBOARDING,
                "Ready",
            ),
        )
        assertEquals(
            Result.Success(Unit),
            validateLifecycleCompletion(
                case(listOf(task(true, LifecycleTaskStatus.DONE))),
                2,
                LifecycleKind.OFFBOARDING,
                "Ready",
            ),
        )
    }

    @Test
    fun completionRejectsObsoleteVersionsClosedCasesAndTheWrongWorkflow() {
        val ready = case(listOf(task(true, LifecycleTaskStatus.DONE)))
        assertEquals(
            Result.Failed(Failure(FailureKind.CONFLICT, "stale_version")),
            validateLifecycleCompletion(ready, 1, LifecycleKind.OFFBOARDING, "Ready"),
        )
        assertEquals(
            Result.Failed(Failure(FailureKind.CONFLICT, "lifecycle_case_not_open")),
            validateLifecycleCompletion(ready, 2, LifecycleKind.ONBOARDING, "Ready"),
        )
        assertEquals(
            Result.Failed(Failure(FailureKind.CONFLICT, "lifecycle_case_not_open")),
            validateLifecycleCompletion(
                ready.copy(status = LifecycleStatus.COMPLETED),
                2,
                LifecycleKind.OFFBOARDING,
                "Ready",
            ),
        )
    }
}
