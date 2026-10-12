import type { Page } from "@playwright/test";
import type { DocumentRevisionDto } from "../src/features/documents/data/models/document-dto";
import { installDocumentsApi } from "./documents-api";
import { documentId, documentRevision } from "./fixtures/documents";
import { companyIds } from "./identity-api";

export const actionRevisionId = documentId(32);
export const documentManagerPermissions = [
  "company.read",
  "documents.read",
  "people.profile.read",
  "documents.manage",
  "people.profile.manage",
];
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
export async function installDocumentActionsApi(
  page: Page,
  options: {
    permissions?: readonly string[];
    revision?: Partial<DocumentRevisionDto>;
  } = {},
) {
  const base = await installDocumentsApi(page, {
    replacement: true,
    permissions: options.permissions ?? documentManagerPermissions,
  });
  const revision: DocumentRevisionDto = {
    ...documentRevision(),
    id: actionRevisionId,
    number: 2,
    fileName: "replacement.pdf",
    status: "UPLOADING",
    validatedAt: null,
    validationJobId: null,
    validationAttempts: 0,
    availableActions: ["VALIDATE", "CANCEL_UPLOAD"],
    ...options.revision,
  };
  const reads: URL[] = [];
  const commands: {
    operation: string;
    path: string;
    body: { expectedVersion: number; reason: string };
  }[] = [];
  const receipts = new Map<
    string,
    { fingerprint: string; value: { id: string; version: number } }
  >();
  let rejectedCommand: string | null = null,
    rejectedRead: string | null = null,
    loseResponse = false;
  let heldCommand: ReturnType<typeof gate> | null = null,
    heldRead: ReturnType<typeof gate> | null = null;
  await page.route(
    `**/api/v1/companies/*/documents/revisions/${actionRevisionId}**`,
    async (route) => {
      const request = route.request(),
        url = new URL(request.url());
      const method = request.method(),
        command = method === "POST";
      const failure = (code: string) =>
        route.fulfill({
          status: code === "stale_version" ? 409 : 403,
          json: { code, detail: "PRIVATE COMMAND ERROR" },
        });
      if (url.pathname.split("/")[4] !== companyIds[0])
        return failure("document_revision_not_found");
      if (command)
        commands.push({
          operation: request.headers()["idempotency-key"] ?? "",
          path: url.pathname,
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
      const rejection = command ? rejectedCommand : rejectedRead;
      if (command) rejectedCommand = null;
      else rejectedRead = null;
      if (rejection) return failure(rejection);
      if (command) {
        const body = request.postDataJSON() as { expectedVersion: number; reason: string };
        const operation = request.headers()["idempotency-key"] ?? "";
        const fingerprint = JSON.stringify({ path: url.pathname, body });
        const previous = receipts.get(operation);
        if (previous)
          return previous.fingerprint === fingerprint
            ? route.fulfill({ json: previous.value })
            : failure("idempotency_key_reused");
        if (body.expectedVersion !== revision.version) return failure("stale_version");
        const validate = url.pathname.endsWith("/validate"),
          cancel = url.pathname.endsWith("/cancel");
        if (
          (!validate && !cancel) ||
          !revision.availableActions.includes(validate ? "VALIDATE" : "CANCEL_UPLOAD")
        )
          return failure("document_action_unavailable");
        revision.version += 1;
        revision.status = validate ? "VALIDATING" : "CANCELLED";
        revision.availableActions = validate ? ["CANCEL_UPLOAD"] : [];
        if (validate) {
          revision.validationAttempts += 1;
          revision.validationJobId = documentId(40 + revision.validationAttempts);
        }
        const value = { id: revision.id, version: revision.version };
        receipts.set(operation, { fingerprint, value });
        if (loseResponse) {
          loseResponse = false;
          return route.abort("failed");
        }
        return route.fulfill({ json: value });
      }
      if (url.pathname.endsWith("/validation-attempts"))
        return route.fulfill({
          json: Array.from({ length: revision.validationAttempts }, (_, index) => ({
            jobId: documentId(41 + index),
            number: index + 1,
            actorId: documentId(51),
            createdAt: "2026-10-01T00:00:30Z",
            reason: "Inspect content",
          })),
        });
      if (url.pathname.endsWith(actionRevisionId)) return route.fulfill({ json: revision });
      return route.fulfill({ status: 404, json: { code: "document_revision_not_found" } });
    },
  );
  return {
    identity: base.identity,
    reads,
    commands,
    receipts,
    update: (value: Partial<DocumentRevisionDto>) => Object.assign(revision, value),
    loseNextResponse: () => {
      loseResponse = true;
    },
    rejectCommand: (code: string) => {
      rejectedCommand = code;
    },
    rejectRead: (code: string) => {
      rejectedRead = code;
    },
    holdCommand: () => {
      const value = gate();
      heldCommand = value;
      return value;
    },
    holdRead: () => {
      const value = gate();
      heldRead = value;
      return value;
    },
  };
}
