import { existsSync, unlinkSync } from "node:fs";
import { expect, test } from "@playwright/test";
import { metadataFixtureJson, metadataProxy } from "./fixtures/metadata-proxy";
import {
  createMetadataNativeSource,
  laterMetadata,
  nativeMetadata,
} from "./fixtures/metadata-source";

const prefix = "../../.work/metadata-native";

test("owned Android document metadata draft and original correction recovery", async ({
  page,
  context,
}) => {
  for (const suffix of ["ready.json", "later.json", "recover", "done"])
    if (existsSync(`${prefix}-${suffix}`))
      throw new Error("Previous native metadata fixture requires review");
  const proxy = await metadataProxy(prefix);
  try {
    const source = await createMetadataNativeSource(page, context);
    const { company, member, document, revision, base, headers } = source;
    const headPath = `${base}/documents/${document}`;
    const revisionPath = `${base}/documents/revisions/${revision}`;
    const originalRevision = await (await context.request.get(revisionPath)).json();
    expect(await (await context.request.get(headPath)).json()).toMatchObject({
      version: 2,
      metadataVersion: 0,
      metadata: { category: "PASSPORT", validUntil: "2020-01-01" },
    });
    metadataFixtureJson(`${prefix}-ready.json`, {
      company,
      account: member,
      document,
      revision,
      email: "browser-native-file-employee@example.invalid",
      proxyPort: 18082,
      sourceSha256: source.sha256,
    });
    console.log("Owned Android metadata fixture ready");
    await expect
      .poll(() => proxy.snapshot().interrupted, { timeout: 720_000, intervals: [1000] })
      .toBe(true);
    const saved = await (await context.request.get(headPath)).json();
    expect(saved).toMatchObject({
      version: 3,
      metadataVersion: 1,
      metadata: nativeMetadata,
      classification: "PERSONAL",
    });
    expect(
      (
        await context.request.put(`${headPath}/metadata`, {
          headers: headers(),
          data: {
            expectedVersion: saved.version,
            metadata: laterMetadata,
            reason: "Independent later native acceptance correction",
          },
        })
      ).status(),
    ).toBe(200);
    metadataFixtureJson(`${prefix}-later.json`, { version: 4, metadataVersion: 2 });
    console.log(
      "Owned Android original correction committed; later administrative correction ready",
    );
    await expect
      .poll(() => existsSync(`${prefix}-done`), { timeout: 840_000, intervals: [1000] })
      .toBe(true);
    const actual = proxy.snapshot();
    expect(actual.count).toBeLessThanOrEqual(512);
    expect(actual.mutations).toHaveLength(2);
    expect(actual.mutations.map((mutation) => mutation.status)).toEqual([200, 200]);
    expect(actual.mutations[0]).toEqual(actual.mutations[1]);
    expect(
      actual.events.filter(
        (event) =>
          event.forwarded &&
          event.method !== "GET" &&
          event.method !== "HEAD" &&
          event.path.startsWith("/api/v1/companies/"),
      ),
    ).toHaveLength(2);
    const current = await (await context.request.get(headPath)).json();
    expect(current).toMatchObject({
      version: 4,
      metadataVersion: 2,
      revisionCount: 1,
      currentRevisionId: revision,
      classification: "PERSONAL",
      metadata: laterMetadata,
    });
    const history = await (await context.request.get(`${headPath}/metadata-history`)).json();
    expect(history.items.map((row: { version: number }) => row.version)).toEqual([0, 1, 2]);
    expect(history.items[1].metadata).toEqual(nativeMetadata);
    expect(await (await context.request.get(revisionPath)).json()).toEqual(originalRevision);
    metadataFixtureJson(`${prefix}-server-result.json`, {
      originalCorrectionReplayed: true,
      requests: actual.count,
      companyMutations: 2,
      metadataVersions: 3,
      currentDocumentVersion: current.version,
      currentMetadataVersion: current.metadataVersion,
      unchangedBinaryRevision: true,
      sourceSha256: source.sha256,
    });
  } finally {
    await proxy.close();
    if (existsSync(`${prefix}-ready.json`)) unlinkSync(`${prefix}-ready.json`);
  }
});
