import type { Page } from "@playwright/test";
import type { DocumentMetadataChangeDto } from "../src/features/documents/data/models/document-metadata-dto";
import { installDocumentsApi } from "./documents-api";
import { documentMetadataHeader, documentMetadataRevision } from "./fixtures/document-metadata";
import { documentHeaderId, documentId } from "./fixtures/documents";

export async function installDocumentMetadataApi(
  page: Page,
  options: { readOnly?: boolean; unsupported?: boolean } = {},
) {
  const base = await installDocumentsApi(page, {
    permissions: [
      "company.read",
      "documents.read",
      "people.profile.read",
      ...(options.readOnly ? [] : ["documents.manage", "people.profile.manage"]),
    ],
  });
  const header = documentMetadataHeader();
  const history = [documentMetadataRevision()];
  const commands: { key: string; body: DocumentMetadataChangeDto }[] = [];
  const receipts = new Map<string, { id: string; version: number }>();
  let loseNext = false;
  await page.route("**/api/v1/companies/*/documents**", async (route) => {
    const url = new URL(route.request().url()),
      tail = url.pathname.split("/").slice(6).join("/");
    if (options.unsupported) return route.fallback();
    if (tail === documentHeaderId) return route.fulfill({ json: header });
    if (tail === `${documentHeaderId}/metadata-history`) {
      const after = Number(url.searchParams.get("after") ?? -1),
        items = history.filter((row) => row.version > after);
      return route.fulfill({
        json: {
          items: items.slice(0, 20),
          nextCursor: items.length > 20 ? String(items[19]?.version) : null,
        },
      });
    }
    if (tail !== `${documentHeaderId}/metadata`) return route.fallback();
    const key = route.request().headers()["idempotency-key"] ?? "",
      body = route.request().postDataJSON() as DocumentMetadataChangeDto;
    commands.push({ key, body });
    if (receipts.has(key)) return route.fulfill({ json: receipts.get(key) });
    if (body.expectedVersion !== header.version)
      return route.fulfill({ status: 409, json: { code: "stale_version" } });
    header.version++;
    header.metadataVersion = (header.metadataVersion ?? 0) + 1;
    header.metadata = structuredClone(body.metadata);
    history.push({
      documentId: documentHeaderId,
      version: header.metadataVersion,
      documentVersion: header.version,
      metadata: structuredClone(body.metadata),
      actorId: documentId(51),
      recordedAt: "2026-10-02T00:00:00Z",
      reason: body.reason,
    });
    const receipt = { id: documentHeaderId, version: header.version };
    receipts.set(key, receipt);
    if (loseNext) {
      loseNext = false;
      return route.abort("failed");
    }
    return route.fulfill({ json: receipt });
  });
  return {
    ...base,
    header,
    history,
    commands,
    loseNext: () => {
      loseNext = true;
    },
  };
}
