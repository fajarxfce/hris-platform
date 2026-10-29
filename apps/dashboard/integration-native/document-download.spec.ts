import { execFileSync } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { existsSync, unlinkSync } from "node:fs";
import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import { downloadProxy, privateJson } from "./fixtures/download-proxy";

const prefix = "../../.work/download-native";
const chunkSize = 1_048_576;

test("owned Android interrupted document download and local summary fixture", async ({
  page,
  context,
}) => {
  for (const suffix of ["ready.json", "recover", "done"])
    if (existsSync(`${prefix}-${suffix}`))
      throw new Error("Previous native transfer fixture requires review");
  const proxy = await downloadProxy(prefix);
  try {
    const { company, csrf } = await createAdminCompany(
      page,
      context,
      "browser-native-file-admin@example.invalid",
      "PENDING_TRANSFER",
      "Pending Transfer Acceptance",
    );
    const base = `/api/v1/companies/${company}`;
    const member = "a0000000-0000-4000-8000-000000000099";
    const author = "a0000000-0000-4000-8000-000000000098";
    const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
    const granted = await context.request.put(`${base}/members/${member}`, {
      headers: headers(),
      data: {
        active: true,
        permissions: [
          "company.read",
          "people.self.read",
          "announcements.read",
          "leave.self.manage",
          "attendance.self.record",
          "overtime.self.manage",
          "expenses.self.manage",
          "documents.self.read",
        ],
        reason: "Owned transfer reader",
      },
    });
    expect(granted.status()).toBe(200);
    const employee = randomUUID();
    const enrolled = await context.request.post(`${base}/employees`, {
      headers: headers(),
      data: {
        id: employee,
        employeeNumber: "TRANSFER-001",
        person: { id: randomUUID(), legalName: "Transfer Fixture Employee", nationality: "ID" },
        terms: {
          effectiveFrom: "2026-01-01",
          startDate: "2026-01-01",
          status: "ACTIVE",
          contract: "PERMANENT",
        },
        reason: "Owned native transfer",
      },
    });
    expect(enrolled.status()).toBe(200);
    const binding = await context.request.post(`${base}/employees/${employee}/account-link`, {
      headers: headers(),
      data: { accountId: member, expectedVersion: 0, reason: "Owned transfer reader" },
    });
    expect(binding.status()).toBe(200);
    const bytes = Buffer.alloc(3 * chunkSize + 113, 0x20);
    bytes.write("%PDF-1.4\n% Fictional native transfer acceptance\n");
    bytes.write("\n%%EOF\n", bytes.length - 7);
    const digest = createHash("sha256").update(bytes).digest("hex");
    const document = randomUUID(),
      revision = randomUUID();
    const declared = await context.request.post(`${base}/documents/uploads`, {
      headers: headers(),
      data: {
        documentId: document,
        revisionId: revision,
        employmentId: employee,
        title: "Pending transfer document",
        classification: "PERSONAL",
        expectedDocumentVersion: 0,
        fileName: "pending-transfer.pdf",
        mediaType: "application/pdf",
        size: bytes.length,
        sha256: digest,
        reason: "Owned native transfer source",
      },
    });
    expect(declared.status()).toBe(200);
    for (let offset = 0; offset < bytes.length; offset += chunkSize) {
      const part = bytes.subarray(offset, Math.min(offset + chunkSize, bytes.length));
      const uploaded = await context.request.post(
        `${base}/documents/revisions/${revision}/chunks`,
        {
          headers: {
            ...headers(),
            "Content-Type": "application/octet-stream",
            "Upload-Offset": String(offset),
            "Upload-Checksum-Sha256": createHash("sha256").update(part).digest("hex"),
          },
          data: part,
        },
      );
      expect(uploaded.status()).toBe(200);
    }
    const owned = JSON.parse(
      await readFile("../../.work/worker-integration/database.json", "utf8"),
    ) as { container: string; database: string };
    if (!/^[0-9a-f]{12,64}$/u.test(owned.container) || owned.database !== "hris_browser")
      throw new Error("Unexpected owned database");
    expect(
      execFileSync(
        "docker",
        [
          "inspect",
          "--format",
          '{{index .Config.Labels "dev.fajar.hris.fixture"}}',
          owned.container,
        ],
        { encoding: "utf8", timeout: 10_000, maxBuffer: 4096 },
      ).trim(),
    ).toBe("dashboard");
    const job = randomUUID();
    // Publish fictional accepted metadata with normal constraints/triggers. The actual
    // source bytes above went through API/Garage; this case does not test scanning.
    execFileSync(
      "docker",
      [
        "exec",
        "-i",
        owned.container,
        "psql",
        "-X",
        "-q",
        "-U",
        "fixture_migrator",
        "-d",
        owned.database,
        "-v",
        "ON_ERROR_STOP=1",
      ],
      {
        input: `BEGIN;
        SELECT set_config('hris.company_id','${company}',true), set_config('hris.actor_id','${author}',true);
        INSERT INTO background_jobs(id,company_id,actor_id,kind,operation_id,request,authenticated_at,credential_version,correlation_id,created_at,status,available_at,attempts,completed_items,total_items,finished_at,version)
        VALUES('${job}','${company}','${author}','DOCUMENT_VALIDATE',gen_random_uuid(),jsonb_build_object('revisionId','${revision}'),now(),0,gen_random_uuid(),now(),'SUCCEEDED',now(),1,1,1,now(),2);
        INSERT INTO document_validation_attempts(company_id,revision_id,job_id,attempt,actor_id,created_at,reason)
        VALUES('${company}','${revision}','${job}',1,'${author}',now(),'Fictional accepted transfer source');
        UPDATE document_revisions SET status='VALIDATING',validation_job_id='${job}',validation_attempts=1,version=version+1 WHERE company_id='${company}' AND id='${revision}';
        UPDATE document_revisions SET status='READY',content_bytes=expected_bytes,content_sha256=expected_sha256,detected_media_type=media_type,scan_clean=true,scanner_version='fixture-inspection',validated_at=now(),version=version+1 WHERE company_id='${company}' AND id='${revision}';
        UPDATE documents SET current_revision_id='${revision}',version=version+1 WHERE company_id='${company}' AND id='${document}';
        COMMIT;`,
        encoding: "utf8",
        timeout: 10_000,
        maxBuffer: 4096,
      },
    );
    const contentPath = `${base}/documents/revisions/${revision}/content`;
    const source = await context.request.get(contentPath);
    expect(source.status()).toBe(200);
    expect(
      createHash("sha256")
        .update(await source.body())
        .digest("hex"),
    ).toBe(digest);
    await source.dispose();
    privateJson(`${prefix}-ready.json`, {
      company,
      account: member,
      document,
      revision,
      size: bytes.length,
      sha256: digest,
      email: "browser-native-file-employee@example.invalid",
      proxyPort: 18082,
    });
    console.log("Owned Android partial-download fixture ready");
    await expect
      .poll(() => existsSync(`${prefix}-done`), { timeout: 840_000, intervals: [1000] })
      .toBe(true);
    const actual = proxy.snapshot();
    expect(actual.interrupted).toBe(true);
    const delivered = actual.ranges.filter((range) => range.status === 206);
    expect(delivered).toHaveLength(4);
    for (let part = 0; part < delivered.length; part++) {
      const offset = part * chunkSize,
        end = Math.min(offset + chunkSize, bytes.length);
      expect(delivered[part]).toEqual({
        range: `bytes=${offset}-${end - 1}`,
        status: 206,
        bytes: end - offset,
        sha256: createHash("sha256").update(bytes.subarray(offset, end)).digest("hex"),
      });
    }
    expect(
      actual.events.filter(
        (event) =>
          event.forwarded &&
          event.method !== "GET" &&
          event.method !== "HEAD" &&
          event.path.startsWith("/api/v1/companies/"),
      ),
    ).toHaveLength(0);
    privateJson(`${prefix}-server-result.json`, {
      interrupted: true,
      resumedOffset: chunkSize,
      deliveredRanges: delivered.length,
      deliveredBytes: bytes.length,
      sha256: digest,
      requests: actual.count,
      companyMutations: 0,
    });
  } finally {
    await proxy.close();
    if (existsSync(`${prefix}-ready.json`)) unlinkSync(`${prefix}-ready.json`);
  }
});
