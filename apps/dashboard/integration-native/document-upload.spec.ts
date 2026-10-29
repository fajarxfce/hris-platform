import { execFileSync } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { existsSync, unlinkSync, writeFileSync } from "node:fs";
import { readFile } from "node:fs/promises";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import { uploadFixtureJson, uploadProxy } from "./fixtures/upload-proxy";

const prefix = "../../.work/upload-native";
const chunkSize = 1_048_576;

test("owned Android interrupted document upload/review and local summary fixture", async ({
  page,
  context,
}) => {
  for (const suffix of [
    "ready.json",
    "recover",
    "done",
    "source.pdf",
    "partial.json",
    "review-1.json",
    "review-2.json",
  ])
    if (existsSync(`${prefix}-${suffix}`))
      throw new Error("Previous native transfer fixture requires review");
  const proxy = await uploadProxy(prefix);
  try {
    const { company, csrf } = await createAdminCompany(
      page,
      context,
      "browser-native-file-admin@example.invalid",
      "PENDING_UPLOAD",
      "Pending Upload Acceptance",
    );
    const base = `/api/v1/companies/${company}`;
    const member = "a0000000-0000-4000-8000-000000000099";
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
          "documents.self.upload",
        ],
        reason: "Owned upload employee",
      },
    });
    expect(granted.status()).toBe(200);
    const employee = randomUUID();
    const enrolled = await context.request.post(`${base}/employees`, {
      headers: headers(),
      data: {
        id: employee,
        employeeNumber: "UPLOAD-001",
        person: { id: randomUUID(), legalName: "Upload Fixture Employee", nationality: "ID" },
        terms: {
          effectiveFrom: "2026-01-01",
          startDate: "2026-01-01",
          status: "ACTIVE",
          contract: "PERMANENT",
        },
        reason: "Owned native upload",
      },
    });
    expect(enrolled.status()).toBe(200);
    const binding = await context.request.post(`${base}/employees/${employee}/account-link`, {
      headers: headers(),
      data: { accountId: member, expectedVersion: 0, reason: "Owned upload employee" },
    });
    expect(binding.status()).toBe(200);
    const bytes = Buffer.alloc(3 * chunkSize + 113, 0x20);
    bytes.write("%PDF-1.4\n% Fictional native upload acceptance\n");
    bytes.write("\n%%EOF\n", bytes.length - 7);
    const digest = createHash("sha256").update(bytes).digest("hex");
    writeFileSync(`${prefix}-source.pdf`, bytes, { mode: 0o600 });
    const owned = JSON.parse(
      await readFile("../../.work/worker-integration/database.json", "utf8"),
    ) as { container: string; database: string };
    if (
      !/^[0-9a-f]{12,64}$/u.test(owned.container) ||
      owned.database !== "hris_browser" ||
      !/^[a-f0-9-]{36}$/u.test(company)
    )
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
    const query = (sql: string) =>
      execFileSync(
        "docker",
        [
          "exec",
          "-i",
          owned.container,
          "psql",
          "-X",
          "-q",
          "-t",
          "-A",
          "-U",
          "fixture_migrator",
          "-d",
          owned.database,
          "-v",
          "ON_ERROR_STOP=1",
        ],
        {
          input: `BEGIN; SELECT set_config('hris.company_id','${company}',true); ${sql}; ROLLBACK;`,
          encoding: "utf8",
          timeout: 10_000,
          maxBuffer: 16_384,
        },
      )
        .trim()
        .split("\n")
        .slice(1)
        .join("\n");
    type Stored = {
      id: string;
      document_id: string;
      status: string;
      uploaded_bytes: number;
      expected_bytes: number;
      expected_sha256: string;
      version: number;
      validation_attempts: number;
      current_revision_id: string | null;
    };
    const revisions = (): Stored[] =>
      JSON.parse(
        query(
          `SELECT coalesce(json_agg(r),'[]') FROM (SELECT r.id,r.document_id,r.status,r.uploaded_bytes,r.expected_bytes,r.expected_sha256,r.version,r.validation_attempts,d.current_revision_id FROM document_revisions r JOIN documents d ON d.company_id=r.company_id AND d.id=r.document_id WHERE r.company_id='${company}' ORDER BY r.id LIMIT 2) r`,
        ),
      );
    uploadFixtureJson(`${prefix}-ready.json`, {
      company,
      account: member,
      employee,
      size: bytes.length,
      sha256: digest,
      email: "browser-native-file-employee@example.invalid",
      proxyPort: 18082,
    });
    console.log("Owned Android partial-upload/review fixture ready");
    await expect
      .poll(() => proxy.snapshot().interrupted, { timeout: 300_000, intervals: [1000] })
      .toBe(true);
    const partial = revisions();
    expect(partial).toHaveLength(1);
    const first = partial[0];
    expect(first).toBeDefined();
    if (!first || !/^[a-f0-9-]{36}$/u.test(first.id)) throw new Error("Missing owned revision");
    expect(first).toMatchObject({
      status: "UPLOADING",
      uploaded_bytes: chunkSize,
      expected_bytes: bytes.length,
      expected_sha256: digest,
      version: 1,
      validation_attempts: 0,
      current_revision_id: null,
    });
    uploadFixtureJson(`${prefix}-partial.json`, {
      uploadedBytes: chunkSize,
      version: 1,
      revisions: 1,
    });
    const revisionPath = `${base}/documents/revisions/${first.id}`;
    type Revision = {
      status: string;
      uploadedBytes: number;
      validationJobId: string | null;
      validationAttempts: number;
    };
    const revision = async (): Promise<Revision> => {
      const response = await context.request.get(revisionPath);
      expect(response.status()).toBe(200);
      return (await response.json()) as Revision;
    };
    for (let attempt = 1; attempt <= 2; attempt++) {
      await expect
        .poll(async () => (await revision()).validationAttempts, {
          timeout: 480_000,
          intervals: [2000],
        })
        .toBe(attempt);
      const progress = await revision();
      expect(progress.uploadedBytes).toBe(bytes.length);
      const jobId = progress.validationJobId;
      expect(jobId).toMatch(/^[a-f0-9-]{36}$/u);
      const jobPath = `${base}/jobs/${jobId}`;
      type Job = {
        status: string;
        failureCode: string | null;
        version: number;
        cancellationRequested: boolean;
        attempts: number;
      };
      const job = async (): Promise<Job> => {
        const response = await context.request.get(jobPath);
        expect(response.status()).toBe(200);
        return (await response.json()) as Job;
      };
      await expect
        .poll(async () => (await job()).failureCode, { timeout: 60_000, intervals: [1000] })
        .toBe("document_scanner_not_configured");
      // Explicit operator cancellation uses the real API. No database status/clock is rewritten.
      let stopped = false;
      for (let retry = 0; retry < 3 && !stopped; retry++) {
        const current = await job();
        const response = await context.request.post(`${jobPath}/cancel`, {
          headers: headers(),
          data: { expectedVersion: current.version },
        });
        expect([200, 409]).toContain(response.status());
        stopped = response.status() === 200;
      }
      expect(stopped).toBe(true);
      await expect
        .poll(async () => (await job()).status, { timeout: 60_000, intervals: [1000] })
        .toBe("CANCELLED");
      expect((await revision()).status).toBe("VALIDATION_FAILED");
      uploadFixtureJson(`${prefix}-review-${attempt}.json`, {
        validationAttempts: attempt,
        jobStatus: "CANCELLED",
        revisionStatus: "VALIDATION_FAILED",
        uploadedBytes: bytes.length,
      });
      console.log(`Owned Android upload review ${attempt} ready`);
    }
    await expect
      .poll(() => existsSync(`${prefix}-done`), { timeout: 360_000, intervals: [1000] })
      .toBe(true);
    const actual = proxy.snapshot();
    expect(actual.count).toBeLessThanOrEqual(512);
    const chunks = actual.mutations.filter((m) => m.path.endsWith("/chunks"));
    expect(chunks).toHaveLength(5);
    expect(chunks[0]).toEqual(chunks[1]);
    const unique = chunks.slice(1);
    for (let part = 0; part < unique.length; part++) {
      const offset = part * chunkSize,
        content = bytes.subarray(offset, Math.min(offset + chunkSize, bytes.length));
      expect(unique[part]).toMatchObject({
        offset: String(offset),
        bytes: content.length,
        body: createHash("sha256").update(content).digest("hex"),
        status: 200,
      });
    }
    expect(new Set(unique.map((m) => m.operation)).size).toBe(4);
    const starts = actual.mutations.filter((m) => m.path.endsWith("/uploads"));
    const validations = actual.mutations.filter((m) => m.path.endsWith("/validate"));
    const cancellations = actual.mutations.filter((m) => m.path.endsWith("/cancel"));
    expect(starts).toHaveLength(1);
    expect(validations).toHaveLength(2);
    expect(validations[0]?.operation).not.toBe(validations[1]?.operation);
    expect(cancellations).toHaveLength(1);
    expect(actual.mutations.every((m) => m.status === 200)).toBe(true);
    expect(actual.mutations).toHaveLength(9);
    expect(
      actual.events.filter(
        (e) =>
          e.forwarded &&
          e.method !== "GET" &&
          e.method !== "HEAD" &&
          e.path.startsWith("/api/v1/companies/"),
      ),
    ).toHaveLength(9);
    const final = revisions();
    expect(final).toHaveLength(1);
    expect(final[0]).toMatchObject({
      id: first.id,
      status: "CANCELLED",
      uploaded_bytes: bytes.length,
      validation_attempts: 2,
      current_revision_id: null,
    });
    const storedChunks = JSON.parse(
      query(
        `SELECT coalesce(json_agg(c),'[]') FROM (SELECT byte_offset,byte_count,sha256,status,attempts,committed_version FROM document_upload_chunks WHERE company_id='${company}' AND revision_id='${first.id}' ORDER BY ordinal LIMIT 5) c`,
      ),
    ) as unknown[];
    expect(storedChunks).toHaveLength(4);
    for (let part = 0; part < storedChunks.length; part++) {
      const offset = part * chunkSize,
        content = bytes.subarray(offset, Math.min(offset + chunkSize, bytes.length));
      expect(storedChunks[part]).toEqual({
        byte_offset: offset,
        byte_count: content.length,
        sha256: createHash("sha256").update(content).digest("hex"),
        status: "COMMITTED",
        attempts: 1,
        committed_version: part + 1,
      });
    }
    uploadFixtureJson(`${prefix}-server-result.json`, {
      responseDropped: true,
      sameChunkOperationAndInput: true,
      revisions: 1,
      chunks: 4,
      chunkRequests: 5,
      bytes: bytes.length,
      sha256: digest,
      validationAttempts: 2,
      finalStatus: "CANCELLED",
      published: false,
      requests: actual.count,
      nativeCompanyMutations: 9,
    });
  } finally {
    await proxy.close();
    for (const suffix of ["ready.json", "source.pdf"])
      if (existsSync(`${prefix}-${suffix}`)) unlinkSync(`${prefix}-${suffix}`);
  }
});
