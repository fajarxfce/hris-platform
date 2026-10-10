import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { FileRepository } from "../../../core/domain/files/file-repository";
import type { CompanyId, OperationId } from "../../../core/domain/identifiers";
import { failed, success } from "../../../core/domain/result";
import { createPeopleFeature } from "./people-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const id = "a0000000-0000-4000-8000-000000000001";
const operation = "30000000-0000-4000-8000-000000000001" as OperationId;
const permissions = [
  "people.import",
  "people.manage",
  "people.profile.read",
  "people.profile.manage",
];
const access = { companyId, permissions };
const csv =
  '\ufeffemployee_number,legal_name,nationality,start_date,contract\r\nE01,"Employee, One",ID,2026-01-01,PERMANENT';
const file = Object.freeze({
  name: "intake.CSV",
  text: csv,
  byteLength: new TextEncoder().encode(csv).byteLength,
});
const input = { id: id.toUpperCase(), file, reason: "  Reviewed intake  " };
const signal = () => new AbortController().signal;
function fixture() {
  const request = vi.fn<HttpClient["request"]>();
  const selectText = vi.fn<FileRepository["selectText"]>().mockResolvedValue(success(file));
  const downloadText = vi
    .fn<FileRepository["downloadText"]>()
    .mockResolvedValue(success(undefined));
  return {
    request,
    selectText,
    downloadText,
    feature: createPeopleFeature(
      { request },
      { selectText, downloadText, downloadBinary: vi.fn() },
    ),
  };
}
describe("employee import file and preview boundary", () => {
  it("requires every private import grant before files, template downloads or submission", async () => {
    const f = fixture();
    for (const missing of permissions) {
      const denied = { ...access, permissions: permissions.filter((p) => p !== missing) };
      for (const result of await Promise.all([
        f.feature.selectEmployeeImportFile.execute(denied, signal()),
        f.feature.downloadEmployeeImportTemplate.execute(denied, signal()),
        f.feature.startEmployeeImport.execute(denied, operation, input, signal()),
      ]))
        expect(result).toMatchObject({
          ok: false,
          failure: { code: "employee_import_access_required" },
        });
    }
    expect(f.request).not.toHaveBeenCalled();
    expect(f.selectText).not.toHaveBeenCalled();
    expect(f.downloadText).not.toHaveBeenCalled();
  });
  it("owns file eligibility without parsing or changing the CSV and preserves native picker cancellation", async () => {
    const f = fixture();
    const selected = await f.feature.selectEmployeeImportFile.execute(access, signal());
    expect(selected).toEqual(success(file));
    expect(f.selectText.mock.lastCall?.[0]).toEqual({
      accept: ".csv,text/csv",
      maximumBytes: 524_288,
    });
    expect(f.request).not.toHaveBeenCalled();
    f.selectText.mockResolvedValueOnce(success(null));
    expect(await f.feature.selectEmployeeImportFile.execute(access, signal())).toEqual(
      success(null),
    );
    for (const name of [
      "../intake.csv",
      "folder\\intake.csv",
      "bad\u0000.csv",
      "bad\u0085.csv",
      "x".repeat(121),
      "intake.json",
      " ",
    ]) {
      f.selectText.mockResolvedValueOnce(success({ ...file, name }));
      expect(await f.feature.selectEmployeeImportFile.execute(access, signal())).toMatchObject({
        ok: false,
        failure: { code: "employee_import_file_invalid" },
      });
    }
    for (const invalid of [
      { byteLength: 0 },
      { byteLength: 524_289 },
      { byteLength: 1.5 },
      { text: "" },
    ]) {
      f.selectText.mockResolvedValueOnce(success({ ...file, ...invalid }));
      expect(await f.feature.selectEmployeeImportFile.execute(access, signal())).toMatchObject({
        ok: false,
        failure: { code: "employee_import_size_limit" },
      });
    }
  });
  it("sends a normalized immutable start command without file metadata or CSV rewriting", async () => {
    const f = fixture();
    f.request.mockResolvedValueOnce({ id, version: 0 });
    expect(await f.feature.startEmployeeImport.execute(access, operation, input, signal())).toEqual(
      success({ id, version: 0 }),
    );
    expect(f.request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${companyId}/employee-imports`,
      method: "POST",
      operationId: operation,
      body: { id, fileName: file.name, csv, reason: "Reviewed intake" },
    });
    expect(f.selectText).not.toHaveBeenCalled();
  });
  it("rejects unsafe starts before I/O and requires the exact initial receipt", async () => {
    const f = fixture();
    for (const invalid of [
      { id: "../id" },
      { reason: " " },
      { reason: "x".repeat(1001) },
      { file: { ...file, name: "file.exe" } },
    ])
      expect(
        await f.feature.startEmployeeImport.execute(
          access,
          operation,
          { ...input, ...invalid },
          signal(),
        ),
      ).toMatchObject({ ok: false });
    expect(
      await f.feature.startEmployeeImport.execute(
        access,
        "invalid" as OperationId,
        input,
        signal(),
      ),
    ).toMatchObject({ ok: false });
    expect(f.request).not.toHaveBeenCalled();
    for (const receipt of [
      { id, version: 1 },
      { id: operation, version: 0 },
      { id, version: -1 },
    ]) {
      f.request.mockResolvedValueOnce(receipt);
      expect(
        await f.feature.startEmployeeImport.execute(access, operation, input, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
  });
  it("coordinates a bounded authorized template read with a file download", async () => {
    const f = fixture();
    const text = "employee_number,legal_name,nationality,start_date,contract\r\n";
    f.request.mockResolvedValueOnce(text);
    expect(await f.feature.downloadEmployeeImportTemplate.execute(access, signal())).toEqual(
      success(undefined),
    );
    expect(f.request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${companyId}/employee-imports/template`,
      response: { type: "text", mediaType: "text/csv", maximumBytes: 16_384 },
    });
    expect(f.downloadText.mock.lastCall?.[0]).toEqual({
      name: "employee-import-template.csv",
      mediaType: "text/csv",
      text,
    });
    f.request.mockResolvedValueOnce(text);
    f.downloadText.mockResolvedValueOnce(failed("file_access_denied"));
    expect(await f.feature.downloadEmployeeImportTemplate.execute(access, signal())).toMatchObject({
      ok: false,
      failure: { code: "file_access_denied" },
    });
  });
  it("does not download failed or malformed templates and preserves safe errors", async () => {
    const f = fixture();
    f.request.mockRejectedValueOnce(
      new HttpResponseError(403, {
        code: "mfa_required",
        detail: "PRIVATE CSV",
        fields: {},
        parameters: {},
      }),
    );
    const result = await f.feature.downloadEmployeeImportTemplate.execute(access, signal());
    expect(result).toMatchObject({ ok: false, failure: { code: "mfa_required" } });
    expect(JSON.stringify(result)).not.toContain("PRIVATE");
    for (const invalid of [null, 1, "", "x".repeat(16_385)]) {
      f.request.mockResolvedValueOnce(invalid);
      expect(
        await f.feature.downloadEmployeeImportTemplate.execute(access, signal()),
      ).toMatchObject({ ok: false, failure: { code: "invalid_response" } });
    }
    expect(f.downloadText).not.toHaveBeenCalled();
  });
  it("propagates cancellation between file and API boundaries", async () => {
    const f = fixture();
    const owner = new AbortController();
    f.request.mockImplementationOnce(async () => {
      owner.abort();
      return csv;
    });
    await expect(
      f.feature.downloadEmployeeImportTemplate.execute(access, owner.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(f.downloadText).not.toHaveBeenCalled();
    await expect(
      f.feature.selectEmployeeImportFile.execute(access, owner.signal),
    ).rejects.toMatchObject({ name: "AbortError" });
    expect(f.selectText).not.toHaveBeenCalled();
    expect(() =>
      f.feature.startEmployeeImport.execute(access, operation, input, owner.signal),
    ).toThrow();
  });
});
