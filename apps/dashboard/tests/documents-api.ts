import type { Page } from "@playwright/test";
import type { DocumentRevisionDto } from "../src/features/documents/data/models/document-dto";
import {
  documentBytes,
  documentEmployeeDirectory,
  documentEmployeeId,
  documentHeader,
  documentHeaderId,
  documentId,
  documentRevision,
  documentRevisionId,
} from "./fixtures/documents";
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
export async function installDocumentsApi(
  page: Page,
  options: { permissions?: readonly string[]; replacement?: boolean; employeeCount?: number } = {},
) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
    permissions: options.permissions ?? ["company.read", "documents.read", "people.profile.read"],
  });
  const reads: URL[] = [];
  let rejectedRead: string | null = null,
    rejectedContent: string | null = null;
  let heldContent: ReturnType<typeof gate> | null = null,
    heldRead: ReturnType<typeof gate> | null = null;
  let ready = true;
  await page.route("**/api/v1/companies/*/documents**", async (route) => {
    const url = new URL(route.request().url()),
      company = url.pathname.split("/")[4] ?? "";
    const tail = url.pathname.split("/").slice(6).join("/");
    reads.push(url);
    const content = tail.endsWith("/content"),
      waiting = content ? heldContent : heldRead;
    if (content) heldContent = null;
    else heldRead = null;
    if (waiting) {
      waiting.enter();
      await waiting.held;
    }
    const code = content ? rejectedContent : rejectedRead;
    if (content) rejectedContent = null;
    else rejectedRead = null;
    if (code)
      return route.fulfill({ status: 403, json: { code, detail: "PRIVATE TECHNICAL ERROR" } });
    const name = company === companyIds[0] ? "North employee" : "South employee";
    const header = {
      ...documentHeader(),
      title: company === companyIds[0] ? "Employment certificate" : "South certificate",
      revisionCount: options.replacement ? 2 : 1,
    };
    const revision: DocumentRevisionDto = {
      ...documentRevision(),
      status: ready ? "READY" : "RETIRED",
    };
    const replacement: DocumentRevisionDto = {
      ...revision,
      id: documentId(32),
      number: 2,
      fileName: "replacement.pdf",
      status: "UPLOADING",
      uploadedBytes: 0,
      validatedAt: null,
      validationJobId: null,
      validationAttempts: 0,
    };
    if (tail === "employees") {
      const query = url.searchParams.get("query") ?? "",
        after = url.searchParams.get("after");
      const exact = url.searchParams.get("employmentId");
      const all = Array.from({ length: options.employeeCount ?? 1 }, (_, index) => ({
        id: index === 0 ? documentEmployeeId : documentId(100 + index),
        employeeNumber: `EMP-${String(index + 1).padStart(3, "0")}`,
        name: index === 0 ? name : `${name} ${index + 1}`,
        status: "ACTIVE",
        version: 0,
      })).filter(
        (employee) =>
          (!exact || employee.id === exact) &&
          (!after || employee.employeeNumber > after) &&
          `${employee.name} ${employee.employeeNumber}`.toLowerCase().includes(query.toLowerCase()),
      );
      const items = all.slice(0, 20);
      return route.fulfill({
        json: {
          ...documentEmployeeDirectory(),
          companyId: company,
          query,
          employees: { items, nextCursor: all.length > 20 ? items.at(-1)?.employeeNumber : null },
        },
      });
    }
    if (!tail)
      return route.fulfill({
        json: {
          items: url.searchParams.get("employmentId") === documentEmployeeId ? [header] : [],
          nextCursor: null,
        },
      });
    if (tail === documentHeaderId) return route.fulfill({ json: header });
    if (tail === `${documentHeaderId}/revisions`)
      return route.fulfill({
        json: {
          items: options.replacement ? [replacement, revision] : [revision],
          nextCursor: null,
        },
      });
    if (tail === `revisions/${documentRevisionId}`) return route.fulfill({ json: revision });
    if (options.replacement && tail === `revisions/${replacement.id}`)
      return route.fulfill({ json: replacement });
    if (tail.endsWith("/validation-attempts"))
      return route.fulfill({
        json: tail.includes(documentRevisionId)
          ? [
              {
                jobId: documentId(41),
                number: 1,
                actorId: documentId(51),
                createdAt: "2026-10-01T00:00:30Z",
                reason: "Employee submission",
              },
            ]
          : [],
      });
    if (tail === `revisions/${documentRevisionId}/content`)
      return route.fulfill({
        body: Buffer.from(documentBytes()),
        headers: {
          "Content-Type": revision.mediaType,
          ETag: `"${revision.id}-${revision.sha256}"`,
          "Content-Length": String(revision.size),
        },
      });
    return route.fulfill({ status: 404, json: { code: "document_revision_not_found" } });
  });
  return {
    identity,
    reads,
    rejectRead: (code: string) => {
      rejectedRead = code;
    },
    rejectContent: (code: string) => {
      rejectedContent = code;
    },
    holdContent: () => {
      const held = gate();
      heldContent = held;
      return held;
    },
    holdRead: () => {
      const held = gate();
      heldRead = held;
      return held;
    },
    retire: () => {
      ready = false;
    },
  };
}
