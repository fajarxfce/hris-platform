import type { Page } from "@playwright/test";
import type {
  DocumentRetentionPolicyChangeDto,
  DocumentRetentionPolicyDto,
} from "../src/features/documents/data/models/document-retention-dto";
import { retentionPolicy } from "./fixtures/document-retention";
import { companyIds, installIdentityApi } from "./identity-api";

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
export async function installDocumentRetentionApi(
  page: Page,
  options: { permissions?: readonly string[]; versions?: number; indefinite?: boolean } = {},
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfaConfigured: true,
    permissions: options.permissions ?? ["company.read", "documents.retention"],
  });
  const histories = new Map<string, DocumentRetentionPolicyDto[]>(
    companyIds.map((company, index) => [
      company,
      index === 0
        ? Array.from({ length: options.versions ?? 1 }, (_, version) => ({
            ...retentionPolicy(version),
            retentionDays: options.indefinite ? null : 365,
          }))
        : [],
    ]),
  );
  const reads: URL[] = [],
    commands: { path: string; operation: string; body: DocumentRetentionPolicyChangeDto }[] = [];
  const receipts = new Map<
    string,
    { fingerprint: string; value: { id: string; version: number } }
  >();
  let rejectedRead: string | null = null,
    rejectedCommand: string | null = null,
    loseResponse = false;
  let heldRead: ReturnType<typeof gate> | null = null,
    heldCommand: ReturnType<typeof gate> | null = null;
  await page.route("**/api/v1/companies/*/documents/retention-policies**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      company = url.pathname.split("/")[4] ?? "",
      command = request.method() === "POST";
    const failure = (code: string) =>
      route.fulfill({
        status: code === "stale_version" ? 409 : 403,
        json: { code, detail: "PRIVATE RETENTION FAILURE" },
      });
    const history = histories.get(company);
    if (!history) return failure("company_access_denied");
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
    const rejection = command ? rejectedCommand : rejectedRead;
    if (command) rejectedCommand = null;
    else rejectedRead = null;
    if (rejection) return failure(rejection);
    if (command) {
      if (!request.headers()["x-csrf-token"]) return failure("csrf_invalid");
      const body = request.postDataJSON() as DocumentRetentionPolicyChangeDto,
        operation = request.headers()["idempotency-key"] ?? "";
      const fingerprint = JSON.stringify({ path: url.pathname, body }),
        previous = receipts.get(operation);
      if (previous)
        return previous.fingerprint === fingerprint
          ? route.fulfill({ json: previous.value })
          : failure("idempotency_key_reused");
      const current = history.findLast((entry) => entry.classification === body.classification);
      if ((current?.version ?? null) !== body.expectedVersion) return failure("stale_version");
      if (current && current.id !== body.policyId)
        return failure("document_retention_policy_exists");
      const next = {
        ...retentionPolicy((current?.version ?? -1) + 1),
        id: body.policyId,
        classification: body.classification,
        retentionDays: body.retentionDays,
        reason: body.reason,
      };
      history.push(next);
      const value = { id: next.id, version: next.version };
      receipts.set(operation, { fingerprint, value });
      if (loseResponse) {
        loseResponse = false;
        return route.abort("failed");
      }
      return route.fulfill({ json: value });
    }
    if (url.pathname.endsWith("/history")) {
      const policy = url.pathname.split("/").at(-2),
        after = url.searchParams.get("after"),
        limit = Number(url.searchParams.get("limit"));
      if (limit !== 20 || (after !== null && !/^(0|[1-9]\d{0,3})$/u.test(after)))
        return failure("invalid_document_page");
      const remaining = history.filter(
          (entry) => entry.id === policy && entry.version > (after === null ? -1 : Number(after)),
        ),
        items = remaining.slice(0, limit);
      return route.fulfill({
        json: {
          items,
          nextCursor: remaining.length > limit ? String(items.at(-1)?.version) : null,
        },
      });
    }
    const current = new Map(history.map((entry) => [entry.classification, entry]));
    return route.fulfill({ json: [...current.values()] });
  });
  return {
    identity,
    reads,
    commands,
    receipts,
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
    revisePolicy: (changes: Partial<DocumentRetentionPolicyDto> = {}) => {
      const history = histories.get(companyIds[0]);
      if (!history) throw new Error("Fixture history missing");
      history.push({ ...retentionPolicy((history.at(-1)?.version ?? -1) + 1), ...changes });
    },
  };
}
