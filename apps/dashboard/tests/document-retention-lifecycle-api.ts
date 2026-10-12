import type { Page } from "@playwright/test";
import type {
  DocumentRetentionChangeDto,
  DocumentRetentionDto,
} from "../src/features/documents/data/models/document-retention-state-dto";
import { installDocumentRetentionApi } from "./document-retention-api";
import {
  documentRetention,
  documentRetentionChange,
  retentionPolicyId,
} from "./fixtures/document-retention";
import {
  documentHeader,
  documentHeaderId,
  documentId,
  documentRevision,
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
export async function installDocumentRetentionLifecycleApi(
  page: Page,
  options: { permissions?: readonly string[]; historySize?: number } = {},
) {
  const base = await installDocumentRetentionApi(page, {
    ...(options.permissions ? { permissions: options.permissions } : {}),
  });
  const history = Array.from({ length: options.historySize ?? 0 }, (_, version) =>
    documentRetentionChange(version),
  );
  let current = structuredClone(history.at(-1)?.state ?? documentRetention());
  const reads: URL[] = [],
    commands: {
      path: string;
      operation: string;
      body: { expectedVersion: number | null; reason: string };
    }[] = [];
  const receipts = new Map<
    string,
    { fingerprint: string; value: { id: string; version: number } }
  >();
  let rejectedRead: string | null = null,
    rejectedCommand: string | null = null,
    loseResponse = false,
    uploadActive = false,
    nextArchiveDays: number | null = 365,
    policyVersion = 0;
  let heldRead: ReturnType<typeof gate> | null = null,
    heldCommand: ReturnType<typeof gate> | null = null;
  await page.route(`**/api/v1/companies/*/documents/${documentHeaderId}**`, async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      company = url.pathname.split("/")[4],
      command = request.method() === "POST";
    const failure = (code: string) =>
      route.fulfill({
        status: code === "stale_version" || code === "document_upload_active" ? 409 : 403,
        json: { code, detail: "PRIVATE LIFECYCLE ERROR" },
      });
    if (company !== companyIds[0]) return failure("document_not_found");
    if (!url.pathname.includes("/retention")) {
      if (url.pathname.endsWith(documentHeaderId)) return route.fulfill({ json: documentHeader() });
      if (url.pathname.endsWith("/revisions"))
        return route.fulfill({ json: { items: [documentRevision()], nextCursor: null } });
      return failure("document_not_found");
    }
    if (command)
      commands.push({
        path: url.pathname,
        operation: request.headers()["idempotency-key"] ?? "",
        body: request.postDataJSON(),
      });
    else reads.push(url);
    const waiting = command ? heldCommand : heldRead;
    if (command) heldCommand = null;
    else heldRead = null;
    if (waiting) {
      waiting.enter();
      await waiting.held;
    }
    const rejected = command ? rejectedCommand : rejectedRead;
    if (command) rejectedCommand = null;
    else rejectedRead = null;
    if (rejected) return failure(rejected);
    if (command) {
      if (!request.headers()["x-csrf-token"]) return failure("csrf_invalid");
      const body = request.postDataJSON() as { expectedVersion: number | null; reason: string },
        operation = request.headers()["idempotency-key"] ?? "";
      const fingerprint = JSON.stringify({ path: url.pathname, body }),
        previous = receipts.get(operation);
      if (previous)
        return previous.fingerprint === fingerprint
          ? route.fulfill({ json: previous.value })
          : failure("idempotency_key_reused");
      if (body.expectedVersion !== current.version) return failure("stale_version");
      const intent = url.pathname.split("/").at(-1),
        now = "2026-10-12T00:00:00Z";
      let action: DocumentRetentionChangeDto["action"];
      switch (intent) {
        case "archive":
          if (current.archive) return failure("document_already_archived");
          if (uploadActive) return failure("document_upload_active");
          action = "ARCHIVE";
          current.archive = {
            archivedAt: now,
            policyId: retentionPolicyId,
            policyVersion,
            retentionDays: nextArchiveDays,
            eligibleAt:
              nextArchiveDays === null
                ? null
                : new Date(Date.parse(now) + nextArchiveDays * 86_400_000).toISOString(),
          };
          break;
        case "restore":
          if (!current.archive) return failure("document_not_archived");
          action = "RESTORE";
          current.archive = null;
          break;
        case "hold":
          if (current.legalHold) return failure("document_already_held");
          action = "PLACE_HOLD";
          current.legalHold = true;
          break;
        case "release-hold":
          if (!current.legalHold) return failure("document_not_held");
          action = "RELEASE_HOLD";
          current.legalHold = false;
          break;
        default:
          return failure("invalid_document_retention_change");
      }
      current.version = (current.version ?? -1) + 1;
      history.push({
        state: structuredClone(current),
        action,
        actorId: documentId(51),
        recordedAt: now,
        reason: body.reason,
        retiredRevisionId: null,
      });
      const value = { id: current.documentId, version: current.version };
      receipts.set(operation, { fingerprint, value });
      if (loseResponse) {
        loseResponse = false;
        return route.abort("failed");
      }
      return route.fulfill({ json: value });
    }
    if (url.pathname.endsWith("/history")) {
      const after = Number(url.searchParams.get("after") ?? "-1"),
        remaining = history.filter((entry) => (entry.state.version ?? -1) > after),
        items = remaining.slice(0, 20);
      return route.fulfill({
        json: {
          items,
          nextCursor: remaining.length > 20 ? String(items.at(-1)?.state.version) : null,
        },
      });
    }
    return route.fulfill({ json: current });
  });
  return {
    identity: base.identity,
    reads,
    commands,
    receipts,
    snapshot: () => structuredClone(current),
    update: (state: Partial<DocumentRetentionDto>) => {
      current = { ...current, ...state };
    },
    setUploadActive: (active: boolean) => {
      uploadActive = active;
    },
    setArchiveDays: (days: number | null) => {
      nextArchiveDays = days;
      policyVersion += 1;
      base.revisePolicy({ retentionDays: days });
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
      const value = gate();
      heldRead = value;
      return value;
    },
    holdCommand: () => {
      const value = gate();
      heldCommand = value;
      return value;
    },
  };
}
