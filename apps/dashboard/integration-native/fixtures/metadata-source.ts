import { execFileSync } from "node:child_process";
import { createHash, randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { type BrowserContext, expect, type Page } from "@playwright/test";
import { createAdminCompany } from "../../integration/fixtures/admin-company";
import { documentBytes } from "../../tests/fixtures/documents";

export const nativeMetadata = {
  category: "WORK_PERMIT",
  identityNumber: "NATIVE-WORK-001",
  issuingCountry: "AU",
  issuedOn: "2010-01-01",
  validUntil: "2019-01-01",
};
export const laterMetadata = {
  ...nativeMetadata,
  category: "RESIDENCE_PERMIT",
  identityNumber: "ADMIN-LATER-001",
  validUntil: "2035-01-01",
};

export async function createMetadataNativeSource(page: Page, context: BrowserContext) {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-native-file-admin@example.invalid",
    "NATIVE_METADATA",
    "Document Metadata Acceptance",
  );
  const base = `/api/v1/companies/${company}`;
  const member = "a0000000-0000-4000-8000-000000000099";
  const actor = "a0000000-0000-4000-8000-000000000098";
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  expect(
    (
      await context.request.put(`${base}/members/${member}`, {
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
          reason: "Owned native metadata editor",
        },
      })
    ).status(),
  ).toBe(200);
  const employee = randomUUID();
  expect(
    (
      await context.request.post(`${base}/employees`, {
        headers: headers(),
        data: {
          id: employee,
          employeeNumber: "METADATA-001",
          person: { id: randomUUID(), legalName: "Metadata Fixture Employee", nationality: "AU" },
          terms: {
            effectiveFrom: "2026-01-01",
            startDate: "2026-01-01",
            status: "ACTIVE",
            contract: "PERMANENT",
          },
          reason: "Owned native metadata",
        },
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await context.request.post(`${base}/employees/${employee}/account-link`, {
        headers: headers(),
        data: { accountId: member, expectedVersion: 0, reason: "Owned metadata editor" },
      })
    ).status(),
  ).toBe(200);
  const document = randomUUID(),
    revision = randomUUID();
  const bytes = Buffer.from(documentBytes());
  const sha256 = createHash("sha256").update(bytes).digest("hex");
  expect(
    (
      await context.request.post(`${base}/documents/uploads`, {
        headers: headers(),
        data: {
          documentId: document,
          revisionId: revision,
          employmentId: employee,
          title: "Historical passport",
          classification: "PERSONAL",
          expectedDocumentVersion: 0,
          fileName: "historical-passport.pdf",
          mediaType: "application/pdf",
          size: bytes.length,
          sha256,
          metadata: {
            category: "PASSPORT",
            identityNumber: "PASSPORT-SEED-001",
            issuingCountry: "AU",
            issuedOn: "2010-01-01",
            validUntil: "2020-01-01",
          },
          reason: "Owned metadata source",
        },
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await context.request.post(`${base}/documents/revisions/${revision}/chunks`, {
        headers: {
          ...headers(),
          "Content-Type": "application/octet-stream",
          "Upload-Offset": "0",
          "Upload-Checksum-Sha256": sha256,
        },
        data: bytes,
      })
    ).status(),
  ).toBe(200);
  await publishFictionalBinary(company, actor, document, revision);
  const content = await context.request.get(`${base}/documents/revisions/${revision}/content`);
  expect(content.status()).toBe(200);
  expect(
    createHash("sha256")
      .update(await content.body())
      .digest("hex"),
  ).toBe(sha256);
  await content.dispose();
  return { company, member, document, revision, base, headers, sha256 };
}

async function publishFictionalBinary(
  company: string,
  actor: string,
  document: string,
  revision: string,
) {
  for (const id of [company, actor, document, revision]) expect(id).toMatch(/^[0-9a-f-]{36}$/u);
  const owned = JSON.parse(
    await readFile("../../.work/worker-integration/database.json", "utf8"),
  ) as { container: string; database: string };
  if (!/^[0-9a-f]{12,64}$/u.test(owned.container) || owned.database !== "hris_browser")
    throw new Error("Unexpected owned database");
  expect(
    execFileSync(
      "docker",
      ["inspect", "--format", '{{index .Config.Labels "dev.fajar.hris.fixture"}}', owned.container],
      { encoding: "utf8", timeout: 10_000, maxBuffer: 4096 },
    ).trim(),
  ).toBe("dashboard");
  const job = randomUUID();
  // Fixture-only inspection facts, with normal constraints/triggers. Source bytes and
  // initial metadata used the actual API/Garage. This case does not establish scanning.
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
      SELECT set_config('hris.company_id','${company}',true), set_config('hris.actor_id','${actor}',true);
      INSERT INTO background_jobs(id,company_id,actor_id,kind,operation_id,request,authenticated_at,credential_version,correlation_id,created_at,status,available_at,attempts,completed_items,total_items,finished_at,version)
      VALUES('${job}','${company}','${actor}','DOCUMENT_VALIDATE',gen_random_uuid(),jsonb_build_object('revisionId','${revision}'),now(),0,gen_random_uuid(),now(),'SUCCEEDED',now(),1,1,1,now(),2);
      INSERT INTO document_validation_attempts(company_id,revision_id,job_id,attempt,actor_id,created_at,reason)
      VALUES('${company}','${revision}','${job}',1,'${actor}',now(),'Fictional accepted metadata source');
      UPDATE document_revisions SET status='VALIDATING',validation_job_id='${job}',validation_attempts=1,version=version+1 WHERE company_id='${company}' AND id='${revision}';
      UPDATE document_revisions SET status='READY',content_bytes=expected_bytes,content_sha256=expected_sha256,detected_media_type=media_type,scan_clean=true,scanner_version='fixture-inspection',validated_at=now(),version=version+1 WHERE company_id='${company}' AND id='${revision}';
      UPDATE documents SET current_revision_id='${revision}',version=version+1 WHERE company_id='${company}' AND id='${document}';
      COMMIT;`,
      encoding: "utf8",
      timeout: 10_000,
      maxBuffer: 4096,
    },
  );
}
