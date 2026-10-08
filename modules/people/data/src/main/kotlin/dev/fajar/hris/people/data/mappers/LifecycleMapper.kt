package dev.fajar.hris.people.data.mappers

import dev.fajar.hris.people.data.models.LifecycleTaskDefinitionData
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.schema.tables.records.*
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

fun LifecycleTemplatesRecord.toLifecycleTemplate(json: ObjectMapper) =
    LifecycleTemplate(
        id,
        code,
        name,
        LifecycleKind.valueOf(kind),
        active,
        version,
        json.readValue(tasks.data(), Array<LifecycleTaskDefinitionData>::class.java).map {
            LifecycleTaskDefinition(it.key, it.title, it.required, it.dueDays)
        },
    )

fun LifecycleTemplate.toRow(company: UUID, json: ObjectMapper) =
    LifecycleTemplatesRecord().also {
        it.companyId = company
        it.id = id
        it.code = code
        it.name = name
        it.kind = kind.name
        it.active = active
        it.version = version
        it.tasks =
            JSONB.valueOf(
                json.writeValueAsString(
                    tasks.map { definition ->
                        LifecycleTaskDefinitionData(
                            definition.key,
                            definition.title,
                            definition.required,
                            definition.dueDays,
                        )
                    }
                )
            )
    }

fun LifecycleTasksRecord.toLifecycleTask() =
    LifecycleTask(
        key,
        title,
        required,
        dueDate,
        assigneeId,
        LifecycleTaskStatus.valueOf(status),
        completedBy,
        completedAt?.toInstant(),
    )

fun LifecycleCasesRecord.toLifecycleCase(tasks: List<LifecycleTasksRecord>) =
    LifecycleCase(
        id,
        employmentId,
        LifecycleKind.valueOf(kind),
        targetDate,
        templateId,
        templateVersion,
        templateName,
        LifecycleStatus.valueOf(status),
        version,
        createdBy,
        createdAt.toInstant(),
        tasks.map { it.toLifecycleTask() },
    )

fun LifecycleTask.toRow(company: UUID, case: UUID) =
    LifecycleTasksRecord().also {
        it.companyId = company
        it.caseId = case
        it.key = key
        it.title = title
        it.required = required
        it.dueDate = dueDate
        it.assigneeId = assigneeId
        it.status = status.name
        it.completedBy = completedBy
        it.completedAt = completedAt?.atOffset(ZoneOffset.UTC)
    }

fun LifecycleCase.toRow(company: UUID) =
    LifecycleCasesRecord().also {
        it.companyId = company
        it.id = id
        it.employmentId = employmentId
        it.kind = kind.name
        it.targetDate = targetDate
        it.templateId = templateId
        it.templateVersion = templateVersion
        it.templateName = templateName
        it.status = status.name
        it.version = version
        it.createdBy = createdBy
        it.createdAt = createdAt.atOffset(ZoneOffset.UTC)
    }

fun LifecycleEventsRecord.toLifecycleEvent() =
    LifecycleEvent(
        version,
        taskKey,
        LifecycleAction.valueOf(action),
        assigneeId,
        actorId,
        reason,
        recordedAt.toInstant(),
    )

fun LifecycleEvent.toRow(company: UUID, case: UUID) =
    LifecycleEventsRecord().also {
        it.companyId = company
        it.caseId = case
        it.version = version
        it.taskKey = taskKey
        it.action = action.name
        it.assigneeId = assigneeId
        it.actorId = actorId
        it.reason = reason
        it.recordedAt = recordedAt.atOffset(ZoneOffset.UTC)
    }
