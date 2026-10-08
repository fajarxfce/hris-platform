package dev.fajar.hris.people.data.datasources

import dev.fajar.hris.schema.tables.records.*

data class AssignedLifecycleRow(val case: LifecycleCasesRecord, val task: LifecycleTasksRecord)
