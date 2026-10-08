package dev.fajar.hris.documents.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.jobs.domain.entities.JobStatus
import dev.fajar.hris.storage.domain.entities.ObjectInventoryEntry
import java.time.Instant

const val DOCUMENT_INVENTORY_PAGE_SIZE = 100
const val DOCUMENT_INVENTORY_MAX_PAGES = 10000

fun validateDocumentInventoryCommand(
    actor: Actor,
    now: Instant,
    security: IdentitySecurityPolicy,
): Result<Unit> =
    actor
        .requirePermission("documents.inventory")
        .flatMap { actor.requirePermission("jobs.retry") }
        .flatMap {
            if (security.enforceMfa) requireRecentMfa(actor, now, security.recentAuthenticationAge)
            else requireRecentAuthentication(actor, now, security.recentAuthenticationAge)
        }

/**
 * This policy only classifies observations. The use case owns queueing, leases, and transactions.
 */
fun classifyDocumentInventoryEntry(
    entry: ObjectInventoryEntry,
    reference: DocumentInventoryReference?,
    cutoff: Instant,
    now: Instant,
): DocumentInventoryDisposition {
    if (reference == null) return DocumentInventoryDisposition.UNKNOWN
    if (reference.size != entry.size) return DocumentInventoryDisposition.ANOMALOUS
    // Even old, superseded document revisions retain their accepted historical content.
    if (
        reference.currentAttemptId == reference.attemptId &&
            reference.revisionStatus == DocumentRevisionStatus.READY
    )
        return DocumentInventoryDisposition.RETAINED
    if (entry.modifiedAt.isAfter(cutoff)) return DocumentInventoryDisposition.RETAINED
    val unusable =
        reference.attemptId != reference.currentAttemptId ||
            reference.revisionStatus in
                setOf(
                    DocumentRevisionStatus.CANCELLED,
                    DocumentRevisionStatus.REJECTED,
                    DocumentRevisionStatus.EXPIRED,
                ) ||
            (reference.revisionStatus in ACTIVE_DOCUMENT_STATUSES &&
                !reference.expiresAt.isAfter(now))
    if (!unusable) return DocumentInventoryDisposition.RETAINED
    if (reference.cleanupRegistered) return DocumentInventoryDisposition.QUEUED
    if (reference.recoveryCount >= 3) return DocumentInventoryDisposition.RECOVERY_EXHAUSTED
    return DocumentInventoryDisposition.SCHEDULE
}

fun documentInventoryCounts(values: Collection<DocumentInventoryDisposition>) =
    DocumentInventoryCounts(
        values.count { it == DocumentInventoryDisposition.RETAINED },
        values.count { it == DocumentInventoryDisposition.UNKNOWN },
        values.count { it == DocumentInventoryDisposition.ANOMALOUS },
        values.count { it == DocumentInventoryDisposition.QUEUED },
        values.count { it == DocumentInventoryDisposition.RECOVERY_EXHAUSTED },
        values.count { it == DocumentInventoryDisposition.SCHEDULE },
    )

fun documentInventoryStatus(run: DocumentInventoryRun): String =
    if (run.status != DocumentInventoryStatus.SCANNING) run.status.name else run.jobStatus.name

fun canResumeDocumentInventory(run: DocumentInventoryRun): Boolean =
    run.status == DocumentInventoryStatus.SCANNING &&
        run.jobStatus in setOf(JobStatus.FAILED, JobStatus.CANCELLED) &&
        run.attempts < 8 &&
        run.pages < DOCUMENT_INVENTORY_MAX_PAGES

private val documentUploadKey =
    Regex(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    )

/** Unknown provider keys are reported without passing arbitrary text into PostgreSQL parameters. */
fun documentInventoryCandidateKeys(entries: List<ObjectInventoryEntry>): Set<String> =
    entries.map { it.key }.filter { documentUploadKey.matches(it) }.toSet()
