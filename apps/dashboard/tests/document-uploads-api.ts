import { createHash } from "node:crypto";
import type { Page } from "@playwright/test";
import type {
  DocumentDto,
  DocumentRevisionDto,
} from "../src/features/documents/data/models/document-dto";
import type { DocumentUploadStartDto } from "../src/features/documents/data/models/document-upload-dto";
import { documentManagerPermissions } from "./document-actions-api";
import { installDocumentsApi } from "./documents-api";
import { documentHeader, documentRevision } from "./fixtures/documents";
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
export async function installDocumentUploadsApi(
  page: Page,
  permissions = documentManagerPermissions,
) {
  const base = await installDocumentsApi(page, { permissions });
  const documents = new Map<string, DocumentDto>([[documentHeader().id, documentHeader()]]);
  const revisions = new Map<string, DocumentRevisionDto>([
    [documentRevision().id, documentRevision()],
  ]);
  const starts: { operation: string; body: DocumentUploadStartDto }[] = [];
  const chunks: {
    operation: string;
    revisionId: string;
    offset: number;
    sha256: string;
    bytes: Buffer;
  }[] = [];
  const reads: URL[] = [];
  const receipts = new Map<
    string,
    { fingerprint: string; value: { id: string; version: number } }
  >();
  let lostStart = false,
    lostChunk = false,
    reserveChunk = false,
    rejectedCommand: string | null = null;
  let waiting: ReturnType<typeof gate> | null = null;
  await page.route("**/api/v1/companies/*/documents**", async (route) => {
    const request = route.request(),
      url = new URL(request.url());
    const tail = url.pathname.split("/").slice(6).join("/"),
      method = request.method();
    const failure = (code: string, status = 409) =>
      route.fulfill({ status, json: { code, detail: "PRIVATE upload diagnostic" } });
    if (url.pathname.split("/")[4] !== companyIds[0])
      return method === "GET" && (tail === "employees" || !tail)
        ? route.fallback()
        : failure("document_not_found", 404);
    if (method === "POST" && tail === "uploads") {
      const operation = request.headers()["idempotency-key"] ?? "",
        body = request.postDataJSON() as DocumentUploadStartDto;
      starts.push({ operation, body });
      if (rejectedCommand) {
        const code = rejectedCommand;
        rejectedCommand = null;
        return failure(code, 403);
      }
      const fingerprint = JSON.stringify(body),
        previous = receipts.get(operation);
      if (previous)
        return previous.fingerprint === fingerprint
          ? route.fulfill({ json: previous.value })
          : failure("idempotency_payload_mismatch");
      const existing = documents.get(body.documentId);
      if (body.expectedDocumentVersion !== (existing?.version ?? 0))
        return failure("stale_version");
      documents.set(body.documentId, {
        ...documentHeader(),
        id: body.documentId,
        title: body.title,
        classification: body.classification,
        employmentId: body.employmentId,
        version: (existing?.version ?? 0) + 1,
        revisionCount: (existing?.revisionCount ?? 0) + 1,
        currentRevisionId: existing?.currentRevisionId ?? null,
      });
      revisions.set(body.revisionId, {
        ...documentRevision(),
        id: body.revisionId,
        documentId: body.documentId,
        fileName: body.fileName,
        mediaType: body.mediaType,
        size: body.size,
        sha256: body.sha256,
        reason: body.reason,
        number: (existing?.revisionCount ?? 0) + 1,
        version: 0,
        uploadedBytes: 0,
        status: "UPLOADING",
        validatedAt: null,
        validationJobId: null,
        validationAttempts: 0,
        availableActions: ["UPLOAD_CONTENT", "CANCEL_UPLOAD"],
        uploadContinuation: null,
        expiresAt: "2099-10-13T00:00:00Z",
      });
      const value = { id: body.revisionId, version: 0 };
      receipts.set(operation, { fingerprint, value });
      if (lostStart) {
        lostStart = false;
        return route.abort("failed");
      }
      return route.fulfill({ json: value });
    }
    const [prefix, revisionId, action] = tail.split("/");
    const revision = prefix === "revisions" && revisionId ? revisions.get(revisionId) : undefined;
    if (method === "POST" && revision && action === "chunks") {
      const operation = request.headers()["idempotency-key"] ?? "",
        offset = Number(request.headers()["upload-offset"]),
        sha256 = request.headers()["upload-checksum-sha256"] ?? "",
        bytes = request.postDataBuffer() ?? Buffer.alloc(0);
      chunks.push({ operation, revisionId: revision.id, offset, sha256, bytes });
      if (waiting) {
        const held = waiting;
        waiting = null;
        held.enter();
        await held.held;
      }
      if (rejectedCommand) {
        const code = rejectedCommand;
        rejectedCommand = null;
        return failure(code, 403);
      }
      if (
        request.headers()["content-type"] !== "application/octet-stream" ||
        createHash("sha256").update(bytes).digest("hex") !== sha256
      )
        return failure("document_chunk_digest_mismatch", 400);
      const fingerprint = JSON.stringify({ revisionId, offset, sha256, size: bytes.byteLength }),
        previous = receipts.get(operation);
      if (previous)
        return previous.fingerprint === fingerprint
          ? route.fulfill({ json: previous.value })
          : failure("idempotency_payload_mismatch");
      if (
        offset !== revision.uploadedBytes ||
        bytes.byteLength !== Math.min(revision.chunkSize, revision.size - offset)
      )
        return failure("document_offset_mismatch");
      if (
        revision.uploadContinuation !== null &&
        operation !== revision.uploadContinuation.operationId
      )
        return failure("document_chunk_reserved");
      if (reserveChunk) {
        reserveChunk = false;
        revision.uploadContinuation = {
          operationId: operation,
          offset,
          size: bytes.byteLength,
          sha256,
          attempts: 1,
          retryAt: null,
        };
        return failure("storage_unavailable", 503);
      }
      revision.uploadedBytes += bytes.byteLength;
      revision.version += 1;
      revision.uploadContinuation = null;
      if (revision.uploadedBytes === revision.size)
        revision.availableActions = ["VALIDATE", "CANCEL_UPLOAD"];
      const value = { id: revision.id, version: revision.version };
      receipts.set(operation, { fingerprint, value });
      if (lostChunk) {
        lostChunk = false;
        return route.abort("failed");
      }
      return route.fulfill({ json: value });
    }
    if (method === "GET") {
      reads.push(url);
      if (revision && !action) return route.fulfill({ json: revision });
      if (revision && action === "validation-attempts") return route.fulfill({ json: [] });
      if (!tail)
        return route.fulfill({
          json: {
            items: [...documents.values()].filter(
              (document) => document.employmentId === url.searchParams.get("employmentId"),
            ),
            nextCursor: null,
          },
        });
      const document = documents.get(tail.split("/")[0] ?? "");
      if (document && tail === document.id) return route.fulfill({ json: document });
      if (document && tail.endsWith("/revisions"))
        return route.fulfill({
          json: {
            items: [...revisions.values()]
              .filter((revision) => revision.documentId === document.id)
              .reverse(),
            nextCursor: null,
          },
        });
    }
    return route.fallback();
  });
  return {
    ...base,
    reads,
    starts,
    chunks,
    receipts,
    documents,
    revisions,
    loseStart: () => {
      lostStart = true;
    },
    loseChunk: () => {
      lostChunk = true;
    },
    reserveChunk: () => {
      reserveChunk = true;
    },
    rejectCommand: (code: string) => {
      rejectedCommand = code;
    },
    holdChunk: () => {
      const held = gate();
      waiting = held;
      return held;
    },
    setDocumentVersion: (id: string, version: number) => {
      const document = documents.get(id);
      if (document) document.version = version;
    },
  };
}
