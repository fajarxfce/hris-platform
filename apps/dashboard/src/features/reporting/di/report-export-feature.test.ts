import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { BinaryFileDownload } from "../../../core/domain/files/binary-file";
import { binaryFilePartBytes } from "../../../core/domain/files/binary-file";
import { failed, success } from "../../../core/domain/result";
import { HttpReportExportDataSource } from "../data/datasources/http-report-export-data-source";
import { RemoteReportExportRepository } from "../data/repositories/remote-report-export-repository";
import { type ReportKind, reportDefinitions } from "../domain/entities/report-catalogue";
import { DownloadReportExport } from "../domain/usecases/download-report-export";
import {
  exportAccess as access,
  exportCompany as company,
  exportFixture as fixture,
  exportForeign as foreign,
  exportId as id,
  exportNow as now,
  exportOperation as operation,
  exportSelection as selection,
} from "./report-export-fixture";
import { createReportingFeature } from "./reporting-feature";

const signal = () => new AbortController().signal;
const rejected = (code: string) => ({ ok: false, failure: { code } });
function setup() {
  const request = vi.fn<HttpClient["request"]>();
  const downloadBinary = vi
    .fn<
      (value: BinaryFileDownload, signal: AbortSignal) => Promise<ReturnType<typeof success<void>>>
    >()
    .mockResolvedValue(success(undefined));
  const feature = createReportingFeature({ request }, { downloadBinary });
  const download = new DownloadReportExport(
    new RemoteReportExportRepository(new HttpReportExportDataSource({ request })),
    { downloadBinary },
    now,
  );
  return { request, downloadBinary, ...feature, download };
}

describe("report export boundary", () => {
  it("requires report scope; status and cancellation remain available after source loss", async () => {
    const a = setup(),
      denied = { companyId: company, permissions: [] },
      owner = { companyId: company, permissions: ["reports.read"] };
    expect(await a.readExports.list(denied, null, signal())).toMatchObject(
      rejected("access_denied"),
    );
    expect(await a.readExports.get(denied, id, signal())).toMatchObject(rejected("access_denied"));
    expect(await a.manageExports.start(owner, operation, selection, signal())).toMatchObject(
      rejected("access_denied"),
    );
    expect(
      await a.manageExports.change(
        denied,
        operation,
        "cancel",
        { id, expectedVersion: 0 },
        signal(),
      ),
    ).toMatchObject(rejected("access_denied"));
    expect(a.request).not.toHaveBeenCalled();
    a.request
      .mockResolvedValueOnce({ items: [fixture()], next: null })
      .mockResolvedValueOnce(fixture())
      .mockResolvedValueOnce({ id, version: 1 });
    expect((await a.readExports.list(owner, null, signal())).ok).toBe(true);
    expect((await a.readExports.get(owner, id, signal())).ok).toBe(true);
    expect(
      await a.manageExports.change(
        owner,
        operation,
        "cancel",
        { id, expectedVersion: 0 },
        signal(),
      ),
    ).toEqual(success({ id, version: 1 }));
    expect(a.request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${company}/reports/exports/${id}/cancel`,
      method: "POST",
      operationId: operation,
      body: { expectedVersion: 0 },
    });
  });
  it.each(Object.keys(reportDefinitions) as ReportKind[])(
    "starts %s with a detached canonical selection and server identity",
    async (kind) => {
      const a = setup(),
        month = ["ATTENDANCE_CLOSINGS", "PAYROLL_COSTS"].includes(kind);
      const parameters =
        kind === "HEADCOUNT"
          ? { ...selection.parameters, companies: [foreign] }
          : {
              asOf: null,
              from: month ? "2026-01" : "2026-10-01",
              until: month ? "2026-10" : "2026-10-20",
              status: kind === "OVERTIME" ? "APPROVED" : null,
              companies: [],
            };
      a.request.mockResolvedValue({ id, version: 0 });
      expect(
        await a.manageExports.start(
          access,
          operation,
          { kind, definitionVersion: reportDefinitions[kind], parameters, dataset: "SUMMARY" },
          signal(),
        ),
      ).toEqual(success({ id, version: 0 }));
      expect(a.request.mock.lastCall?.[0]).toEqual({
        path: `/api/v1/companies/${company}/reports/exports`,
        method: "POST",
        operationId: operation,
        body: { kind, definitionVersion: reportDefinitions[kind], parameters, dataset: "SUMMARY" },
      });
    },
  );
  it("rejects malformed references, bounds, unsupported detail and invalid selections without I/O", async () => {
    const a = setup();
    for (const cursor of [
      { id, createdAt: "never" },
      { id: "../private", createdAt: fixture().createdAt },
      { id, createdAt: "2026-10-01T00:00:00.0000001Z" },
    ])
      expect((await a.readExports.list(access, cursor, signal())).ok).toBe(false);
    expect((await a.readExports.get(access, "../private", signal())).ok).toBe(false);
    for (const version of [-1, 0.5, 10000])
      expect(
        (
          await a.manageExports.change(
            access,
            operation,
            "resume",
            { id, expectedVersion: version },
            signal(),
          )
        ).ok,
      ).toBe(false);
    expect(
      (
        await a.manageExports.start(
          access,
          operation,
          { ...selection, dataset: "DETAIL" },
          signal(),
        )
      ).ok,
    ).toBe(false);
    expect(
      (
        await a.manageExports.start(
          access,
          operation,
          { ...selection, parameters: { ...selection.parameters, asOf: "2026-02-30" } },
          signal(),
        )
      ).ok,
    ).toBe(false);
    expect(a.request).not.toHaveBeenCalled();
  });
  it("rejects foreign, private, impossible and unsupported status responses", async () => {
    const a = setup();
    for (const patch of [
      { id: foreign },
      { companyId: foreign },
      { accountId: foreign },
      { version: 10000 },
      { attempt: 9 },
      { bytes: 104857601 },
      { completedRows: 100001 },
      { status: "UNKNOWN" },
      { jobStatus: "UNKNOWN" },
      { status: "READY", bytes: 0 },
      { status: "READY", bytes: 10, jobStatus: "QUEUED" },
      { updatedAt: "2026-09-30T23:59:59Z" },
      { expiresAt: "2026-10-08T00:00:00.000002Z" },
      { retiredAt: "2026-10-08T00:00:00Z" },
      { failureCode: "PRIVATE diagnostic" },
      { dataset: "DETAIL" },
      { selection: { ...fixture().selection, definitionVersion: "headcount.v2" } },
      {
        selection: {
          ...fixture().selection,
          parameters: { ...selection.parameters, companies: [] },
        },
      },
    ]) {
      a.request.mockResolvedValueOnce({ ...fixture(), ...patch });
      expect(await a.readExports.get(access, id, signal())).toMatchObject(
        rejected("invalid_response"),
      );
    }
  });
  it("validates ordered microsecond keysets, size, unique identities and exact continuation", async () => {
    const a = setup();
    const values = Array.from({ length: 20 }, (_, i) =>
      fixture({ id: `00000000-0000-4000-8000-${String(100 - i).padStart(12, "0")}` }),
    );
    const last = values[19];
    if (!last) throw new Error("Missing cursor fixture");
    const cursor = { id: last.id, createdAt: last.createdAt };
    a.request.mockResolvedValueOnce({ items: values, next: cursor });
    const loaded = await a.readExports.list(access, null, signal());
    expect(loaded.ok).toBe(true);
    if (!loaded.ok) throw new Error("Expected history");
    expect(Object.isFrozen(loaded.value.items)).toBe(true);
    expect(Object.isFrozen(loaded.value.items[0]?.selection.parameters.companies)).toBe(true);
    for (const raw of [
      { items: [values[1], values[0]], next: null },
      { items: [fixture(), fixture()], next: null },
      { items: Array(21).fill(fixture()), next: null },
      { items: [fixture()], next: { id, createdAt: fixture().createdAt } },
      { items: values, next: { ...cursor, id } },
      { items: [{ ...fixture(), companyId: foreign }], next: null },
    ]) {
      a.request.mockResolvedValueOnce(raw);
      expect(await a.readExports.list(access, null, signal())).toMatchObject(
        rejected("invalid_response"),
      );
    }
    a.request.mockResolvedValueOnce({ items: [last], next: null });
    expect(await a.readExports.list(access, cursor, signal())).toMatchObject(
      rejected("invalid_response"),
    );
    a.request.mockResolvedValueOnce({
      items: [fixture({ createdAt: "2026-10-01T00:00:00Z", expiresAt: "2026-10-08T00:00:00Z" })],
      next: null,
    });
    expect((await a.readExports.list(access, cursor, signal())).ok).toBe(true);
    expect(a.request.mock.lastCall?.[0].path).toContain(
      `beforeAt=2026-10-01T00%3A00%3A00.000001Z&beforeId=${last.id}`,
    );
  });
  it("rejects invalid command receipts and retains the observed version contract", async () => {
    const a = setup();
    for (const receipt of [
      { id: operation, version: 0 },
      { id, version: 1 },
      { id, version: -1 },
    ]) {
      a.request.mockResolvedValueOnce(receipt);
      expect(await a.manageExports.start(access, operation, selection, signal())).toMatchObject(
        rejected("invalid_response"),
      );
    }
    for (const receipt of [
      { id: foreign, version: 3 },
      { id, version: 2 },
      { id, version: 4 },
    ]) {
      a.request.mockResolvedValueOnce(receipt);
      expect(
        await a.manageExports.change(
          access,
          operation,
          "resume",
          { id, expectedVersion: 2 },
          signal(),
        ),
      ).toMatchObject(rejected("invalid_response"));
    }
    a.request.mockResolvedValueOnce({ id, version: 3 });
    expect(
      await a.manageExports.change(
        access,
        operation,
        "resume",
        { id, expectedVersion: 2 },
        signal(),
      ),
    ).toEqual(success({ id, version: 3 }));
  });
  it("passes private binary content to the file boundary and erases buffers after saving", async () => {
    const a = setup(),
      part = Uint8Array.from([239, 187, 191, 97, 13, 10]).buffer;
    a.request
      .mockResolvedValueOnce(
        fixture({ status: "READY", jobStatus: "SUCCEEDED", version: 4, bytes: 6 }),
      )
      .mockResolvedValueOnce({
        parts: [part],
        byteLength: 6,
        mediaType: "text/csv",
        etag: `"${id}-${"a".repeat(64)}"`,
      });
    a.downloadBinary.mockImplementationOnce(async (file) => {
      const first = file.parts[0];
      if (!first) throw new Error("Missing file content");
      expect([...new Uint8Array(first)]).toEqual([239, 187, 191, 97, 13, 10]);
      expect(file.name).toBe(`report-headcount-${id}.csv`);
      return success(undefined);
    });
    expect(await a.download.execute(access, id, signal())).toEqual(success(undefined));
    expect(a.request.mock.lastCall?.[0]).toMatchObject({
      timeoutMilliseconds: 60000,
      response: { type: "binary", mediaType: "text/csv", maximumBytes: 6 },
    });
    expect([...new Uint8Array(part)]).toEqual([0, 0, 0, 0, 0, 0]);
  });
  it.each(["short", "wrong-tag", "wrong-kind", "large-part", "denied", "late-abort", "save-error"])(
    "contains and cleans %s download failures",
    async (mode) => {
      const a = setup(),
        abort = new AbortController(),
        length = mode === "large-part" ? binaryFilePartBytes + 1 : 6,
        part = new Uint8Array(length).fill(9).buffer;
      const raw = {
        parts: [part],
        byteLength: length,
        mediaType: "text/csv",
        etag: `"${id}-${"b".repeat(64)}"`,
      };
      a.request.mockResolvedValueOnce(
        fixture({ status: "READY", jobStatus: "SUCCEEDED", version: 4, bytes: length }),
      );
      if (mode === "denied")
        a.request.mockRejectedValueOnce(
          new HttpResponseError(403, { code: "mfa_required", detail: "PRIVATE" }),
        );
      else
        a.request.mockImplementationOnce(async () => {
          if (mode === "late-abort") abort.abort();
          return {
            ...raw,
            ...(mode === "short"
              ? { byteLength: 5 }
              : mode === "wrong-tag"
                ? { etag: `"${foreign}-${"b".repeat(64)}"` }
                : mode === "wrong-kind"
                  ? { mediaType: "application/pdf" }
                  : {}),
          };
        });
      if (mode === "save-error") a.downloadBinary.mockRejectedValueOnce(new Error("File failure"));
      const result = a.download.execute(access, id, abort.signal);
      if (mode === "late-abort" || mode === "save-error") await expect(result).rejects.toThrow();
      else
        expect(await result).toMatchObject(
          rejected(mode === "denied" ? "mfa_required" : "invalid_response"),
        );
      if (mode !== "denied") expect(new Uint8Array(part).every((byte) => byte === 0)).toBe(true);
      if (mode !== "save-error") expect(a.downloadBinary).not.toHaveBeenCalled();
    },
  );
  it("requires fresh ready content and expiry before and after acquisition", async () => {
    const a = setup();
    for (const value of [
      fixture(),
      fixture({ status: "READY", jobStatus: "SUCCEEDED", bytes: 6, expired: true }),
    ]) {
      a.request.mockResolvedValueOnce(value);
      expect((await a.download.execute(access, id, signal())).ok).toBe(false);
    }
    a.request.mockResolvedValueOnce(fixture({ status: "READY", jobStatus: "SUCCEEDED", bytes: 6 }));
    expect(
      await a.download.execute({ ...access, permissions: ["reports.read"] }, id, signal()),
    ).toMatchObject(rejected("access_denied"));
    const part = new Uint8Array(6).fill(7).buffer,
      clock = vi
        .fn()
        .mockReturnValueOnce(now())
        .mockReturnValueOnce(Date.parse("2026-10-09T00:00:00Z"));
    a.request
      .mockResolvedValueOnce(fixture({ status: "READY", jobStatus: "SUCCEEDED", bytes: 6 }))
      .mockResolvedValueOnce({
        parts: [part],
        byteLength: 6,
        mediaType: "text/csv",
        etag: `"${id}-${"a".repeat(64)}"`,
      });
    const download = new DownloadReportExport(
      new RemoteReportExportRepository(new HttpReportExportDataSource({ request: a.request })),
      { downloadBinary: a.downloadBinary },
      clock,
    );
    expect(await download.execute(access, id, signal())).toMatchObject(
      rejected("report_export_expired"),
    );
    expect(new Uint8Array(part).every((byte) => byte === 0)).toBe(true);
    expect(a.downloadBinary).not.toHaveBeenCalled();
  });
  it("detaches selected companies before waits and preserves cancellation and safe errors without retry", async () => {
    const a = setup();
    let resolve!: (value: unknown) => void;
    const companies = [foreign];
    a.request.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const result = a.manageExports.start(
      access,
      operation,
      { ...selection, parameters: { ...selection.parameters, companies } },
      signal(),
    );
    companies.push(company);
    expect(a.request.mock.lastCall?.[0].body).toMatchObject({
      parameters: { companies: [foreign] },
    });
    resolve({ id, version: 0 });
    expect((await result).ok).toBe(true);
    const abort = new AbortController();
    a.request.mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const late = a.readExports.get(access, id, abort.signal);
    abort.abort();
    resolve(fixture());
    await expect(late).rejects.toThrow();
    a.request.mockRejectedValueOnce(
      new HttpResponseError(403, { code: "mfa_required", detail: "PRIVATE" }),
    );
    expect(await a.readExports.list(access, null, signal())).toMatchObject(
      rejected("mfa_required"),
    );
    a.request.mockRejectedValueOnce(new TypeError("PRIVATE"));
    expect(await a.manageExports.start(access, operation, selection, signal())).toEqual(
      failed("connection_unavailable"),
    );
    expect(a.request).toHaveBeenCalledTimes(4);
  });
});
