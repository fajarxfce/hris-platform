import type { Page } from "@playwright/test";
import type { DocumentRetentionChangeDto } from "../src/features/documents/data/models/document-retention-state-dto";
import type { DocumentRetirementReviewDto } from "../src/features/documents/data/models/document-retirement-dto";
import { installDocumentRetentionApi } from "./document-retention-api";
import { documentRetirementReview } from "./fixtures/document-retirement";
import {
  documentHeader,
  documentHeaderId,
  documentId,
  documentRevision,
  documentRevisionId,
} from "./fixtures/documents";
import { companyIds } from "./identity-api";

function gate() {
  let release!: () => void, enter!: () => void;
  return {
    held: new Promise<void>((done) => {
      release = done;
    }),
    started: new Promise<void>((done) => {
      enter = done;
    }),
    release: () => release(),
    enter: () => enter(),
  };
}
export async function installDocumentRetirementApi(
  page: Page,
  options: { permissions?: readonly string[]; review?: Partial<DocumentRetirementReviewDto> } = {},
) {
  const base = await installDocumentRetentionApi(page, options);
  let current = { ...documentRetirementReview(), ...options.review };
  const history: DocumentRetentionChangeDto[] = [
    {
      state: structuredClone(current.retention),
      action: "ARCHIVE",
      actorId: documentId(51),
      recordedAt: current.retention.archive?.archivedAt ?? current.reviewedAt,
      reason: "Archived fixture record",
      retiredRevisionId: null,
    },
  ];
  const reads: URL[] = [],
    commands: {
      operation: string;
      body: { expectedRevisionVersion: number; expectedRetentionVersion: number; reason: string };
    }[] = [];
  const receipts = new Map<
    string,
    { fingerprint: string; value: { id: string; version: number } }
  >();
  let rejectedRead: string | null = null,
    rejectedCommand: string | null = null,
    loseResponse = false;
  let heldRead: ReturnType<typeof gate> | null = null,
    heldCommand: ReturnType<typeof gate> | null = null;
  await page.route("**/api/v1/companies/*/documents/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      path = url.pathname,
      revisionPath = `/documents/revisions/${documentRevisionId}`,
      retirement = path.endsWith(`${revisionPath}/retirement`),
      command = path.endsWith(`${revisionPath}/retire`) && request.method() === "POST";
    if (!path.includes(`/documents/${documentHeaderId}`) && !path.includes(revisionPath))
      return route.fallback();
    const failure = (code: string) =>
      route.fulfill({
        status: code === "stale_version" || code === "document_legal_hold" ? 409 : 403,
        json: { code, detail: "PRIVATE RETIREMENT DETAIL" },
      });
    if (path.split("/")[4] !== companyIds[0]) return failure("document_revision_not_found");
    if (command)
      commands.push({
        operation: request.headers()["idempotency-key"] ?? "",
        body: request.postDataJSON(),
      });
    else reads.push(url);
    const waiting = command ? heldCommand : retirement ? heldRead : null;
    if (command) heldCommand = null;
    else if (retirement) heldRead = null;
    if (waiting) {
      waiting.enter();
      await waiting.held;
    }
    const rejected = command ? rejectedCommand : retirement ? rejectedRead : null;
    if (command) rejectedCommand = null;
    else if (retirement) rejectedRead = null;
    if (rejected) return failure(rejected);
    if (command) {
      if (!request.headers()["x-csrf-token"]) return failure("csrf_invalid");
      const body = request.postDataJSON() as (typeof commands)[number]["body"],
        operation = request.headers()["idempotency-key"] ?? "",
        fingerprint = JSON.stringify(body),
        previous = receipts.get(operation);
      if (previous)
        return previous.fingerprint === fingerprint
          ? route.fulfill({ json: previous.value })
          : failure("idempotency_key_reused");
      if (
        body.expectedRevisionVersion !== current.revisionVersion ||
        body.expectedRetentionVersion !== current.retention.version
      )
        return failure("stale_version");
      if (current.blockingReason) return failure(current.blockingReason);
      current = {
        ...current,
        revisionVersion: current.revisionVersion + 1,
        status: "RETIRED",
        published: false,
        retention: { ...current.retention, version: (current.retention.version ?? -1) + 1 },
        blockingReason: "document_revision_not_ready",
      };
      history.push({
        state: structuredClone(current.retention),
        action: "RETIRE",
        actorId: documentId(51),
        recordedAt: current.reviewedAt,
        reason: body.reason,
        retiredRevisionId: current.revisionId,
      });
      const value = { id: current.revisionId, version: current.revisionVersion };
      receipts.set(operation, { fingerprint, value });
      if (loseResponse) {
        loseResponse = false;
        return route.abort("failed");
      }
      return route.fulfill({ json: value });
    }
    if (retirement) return route.fulfill({ json: current });
    if (path.endsWith("/retention/history"))
      return route.fulfill({ json: { items: history, nextCursor: null } });
    if (path.endsWith("/retention")) return route.fulfill({ json: current.retention });
    if (path.endsWith(documentHeaderId)) return route.fulfill({ json: documentHeader() });
    if (path.endsWith("/validation-attempts")) return route.fulfill({ json: [] });
    if (path.endsWith(documentRevisionId))
      return route.fulfill({
        json: { ...documentRevision(), version: current.revisionVersion, status: current.status },
      });
    return route.fallback();
  });
  return {
    identity: base.identity,
    reads,
    commands,
    receipts,
    snapshot: () => structuredClone(current),
    update: (next: Partial<DocumentRetirementReviewDto>) => {
      current = { ...current, ...next };
    },
    loseNextResponse: () => {
      loseResponse = true;
    },
    rejectCommand: (code: string) => {
      rejectedCommand = code;
    },
    rejectRead: (code: string) => {
      rejectedRead = code;
    },
    holdRead: () => {
      const next = gate();
      heldRead = next;
      return next;
    },
    holdCommand: () => {
      const next = gate();
      heldCommand = next;
      return next;
    },
  };
}
