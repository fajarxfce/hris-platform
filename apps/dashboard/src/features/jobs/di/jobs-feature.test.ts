import { describe, expect, it, vi } from "vitest";
import type { HttpClient } from "../../../core/data/http/http-client";
import { HttpResponseError } from "../../../core/data/http/http-response-error";
import type { CompanyId } from "../../../core/domain/identifiers";
import type { JobDto } from "../data/models/job-dto";
import type { BackgroundJob, JobId } from "../domain/entities/background-job";
import { firstJobPage } from "../domain/entities/job-search";
import { createJobsFeature } from "./jobs-feature";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: [] };
const id = (value: number) => `40000000-abcd-4000-8000-${String(value).padStart(12, "0")}` as JobId;
const job = (value = 1): JobDto => ({
  id: id(value),
  kind: "WORKFORCE_CLOSE",
  status: "QUEUED",
  completedItems: 2,
  totalItems: 10,
  progressMode: "FIXED_TOTAL",
  attempts: 0,
  cancellationRequested: false,
  failureCode: null,
  createdAt: "2026-10-01T00:00:00.000123Z",
  finishedAt: null,
  version: 2,
  availableActions: ["cancel"],
  scheduledFor: null,
  availableAt: "2026-10-01T00:00:00Z",
});
const observed = (): BackgroundJob => ({ ...job(), id: id(1), companyId });
const signal = () => new AbortController().signal;

describe("job feature boundaries", () => {
  it("validates cursor pairs and job identities before I/O while retaining own-job access", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockResolvedValue({ items: [], nextCreatedAt: null, nextId: null });
    const feature = createJobsFeature({ request });
    for (const search of [
      { beforeAt: job().createdAt, beforeId: null },
      { beforeAt: null, beforeId: id(1) },
      { beforeAt: "2026-02-30T00:00:00Z", beforeId: id(1) },
      { beforeAt: job().createdAt, beforeId: "../another" },
    ])
      expect(await feature.loadJobs.execute(access, search, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_pagination" },
      });
    expect(await feature.loadJob.execute(access, "../another", signal())).toMatchObject({
      ok: false,
      failure: { code: "job_not_found" },
    });
    expect(request).not.toHaveBeenCalled();
    expect(await feature.loadJobs.execute(access, firstJobPage, signal())).toMatchObject({
      ok: true,
      value: { companyId, items: [] },
    });
    expect(request.mock.lastCall?.[0].path).toBe(`/api/v1/companies/${companyId}/jobs?size=50`);
  });

  it("maps immutable metadata without request/checkpoint payloads and keeps microsecond cursor progress", async () => {
    const dto = {
      items: Array.from({ length: 50 }, (_, index) => ({
        ...job(100 - index),
        id: id(100 - index).toUpperCase(),
        request: { private: "PRIVATE REQUEST" },
        checkpoint: "PRIVATE CHECKPOINT",
      })),
      nextCreatedAt: job().createdAt,
      nextId: id(51).toUpperCase(),
    };
    const request = vi.fn<HttpClient["request"]>().mockResolvedValueOnce(dto);
    const feature = createJobsFeature({ request });
    const first = await feature.loadJobs.execute(access, firstJobPage, signal());
    if (!first.ok || !first.value.next) throw new Error("Job page fixture was rejected");
    expect(first.value.items[0]?.id).toBe(id(100));
    expect(first.value.next).toEqual({ beforeAt: job().createdAt, beforeId: id(51) });
    expect(Object.isFrozen(first.value)).toBe(true);
    expect(Object.isFrozen(first.value.items)).toBe(true);
    expect(Object.isFrozen(first.value.items[0])).toBe(true);
    expect(Object.isFrozen(first.value.items[0]?.availableActions)).toBe(true);
    expect(Object.isFrozen(first.value.next)).toBe(true);
    expect(JSON.stringify(first)).not.toContain("PRIVATE");
    request.mockResolvedValueOnce({ items: [job(50), job(49)], nextCreatedAt: null, nextId: null });
    expect(await feature.loadJobs.execute(access, first.value.next, signal())).toMatchObject({
      ok: true,
    });
    expect(
      Object.fromEntries(
        new URL(request.mock.lastCall?.[0].path ?? "", "https://fixture.test").searchParams,
      ),
    ).toEqual({ size: "50", ...first.value.next });
    for (const items of [
      [job(51)],
      [job(50), job(50)],
      [job(49), job(50)],
      [{ ...job(), createdAt: "2026-10-01T00:00:00.000124Z" }],
    ]) {
      request.mockResolvedValueOnce({ items, nextCreatedAt: null, nextId: null });
      expect(await feature.loadJobs.execute(access, first.value.next, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });

  it("rejects inconsistent progress, actions, identities and pagination without rejecting new job kinds", async () => {
    const request = vi.fn<HttpClient["request"]>();
    const feature = createJobsFeature({ request });
    for (const change of [
      { id: id(2) },
      { completedItems: 11 },
      { totalItems: 0 },
      { version: Number.MAX_SAFE_INTEGER + 1 },
      { status: "SUCCEEDED", finishedAt: "2026-10-01T01:00:00Z", availableActions: [] },
      { finishedAt: "2026-10-01T01:00:00Z" },
      { cancellationRequested: true },
      { availableActions: ["cancel", "cancel"] },
      { availableAt: "2026-02-30T00:00:00Z" },
    ]) {
      request.mockResolvedValueOnce({ ...job(), ...change });
      expect(await feature.loadJob.execute(access, id(1), signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
    request.mockResolvedValueOnce({
      ...job(),
      kind: "FUTURE_KIND",
      availableActions: ["future_action"],
    });
    expect(await feature.loadJob.execute(access, id(1).toUpperCase(), signal())).toMatchObject({
      ok: true,
      value: { id: id(1), kind: "FUTURE_KIND" },
    });
    request.mockResolvedValueOnce({
      ...job(),
      status: "SUCCEEDED",
      progressMode: "UPPER_BOUND",
      finishedAt: "2026-10-01T01:00:00Z",
      availableActions: [],
    });
    expect(await feature.loadJob.execute(access, id(1), signal())).toMatchObject({ ok: true });
    for (const changes of [
      { nextId: id(1) },
      { nextCreatedAt: job().createdAt },
      { nextId: id(1), nextCreatedAt: job().createdAt },
      { items: Array.from({ length: 51 }, (_, index) => job(100 - index)) },
    ]) {
      request.mockResolvedValueOnce({
        items: [job()],
        nextId: null,
        nextCreatedAt: null,
        ...changes,
      });
      expect(await feature.loadJobs.execute(access, firstJobPage, signal())).toMatchObject({
        ok: false,
        failure: { code: "invalid_response" },
      });
    }
  });

  it("cancels only an observed authorized job with its exact version and verifies acknowledgement", async () => {
    const request = vi.fn<HttpClient["request"]>().mockResolvedValue({
      ...job(),
      cancellationRequested: true,
      availableActions: [],
      version: 3,
    });
    const feature = createJobsFeature({ request });
    for (const value of [
      { ...observed(), companyId: "other-company" as CompanyId },
      { ...observed(), availableActions: [] },
      { ...observed(), cancellationRequested: true },
      { ...observed(), status: "SUCCEEDED" as const },
    ])
      expect(await feature.requestCancellation.execute(access, value, signal())).toMatchObject({
        ok: false,
        failure: { code: "access_denied" },
      });
    expect(request).not.toHaveBeenCalled();
    expect(await feature.requestCancellation.execute(access, observed(), signal())).toMatchObject({
      ok: true,
      value: { cancellationRequested: true, status: "QUEUED", version: 3 },
    });
    expect(request.mock.lastCall?.[0]).toEqual({
      path: `/api/v1/companies/${companyId}/jobs/${id(1)}/cancel`,
      method: "POST",
      body: { expectedVersion: 2 },
    });
    request.mockResolvedValueOnce(job());
    expect(await feature.requestCancellation.execute(access, observed(), signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
    request.mockResolvedValueOnce({ ...job(), cancellationRequested: true, availableActions: [] });
    expect(await feature.requestCancellation.execute(access, observed(), signal())).toMatchObject({
      ok: false,
      failure: { code: "invalid_response" },
    });
  });

  it("preserves failure codes and cancellation without retrying a command after a lost response", async () => {
    const request = vi
      .fn<HttpClient["request"]>()
      .mockRejectedValueOnce(new TypeError("PRIVATE CONNECTION DETAIL"))
      .mockRejectedValueOnce(
        new HttpResponseError(409, {
          code: "stale_version",
          fields: {},
          parameters: {},
          detail: "PRIVATE SQL DETAIL",
        }),
      );
    const feature = createJobsFeature({ request });
    const lost = await feature.requestCancellation.execute(access, observed(), signal());
    expect(lost).toMatchObject({ ok: false, failure: { code: "connection_unavailable" } });
    expect(request).toHaveBeenCalledTimes(1);
    const rejected = await feature.requestCancellation.execute(access, observed(), signal());
    expect(rejected).toMatchObject({ ok: false, failure: { code: "stale_version" } });
    expect(JSON.stringify([lost, rejected])).not.toContain("PRIVATE");
    const abort = new AbortController();
    let resolve!: (value: unknown) => void;
    request.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done;
      }),
    );
    const pending = feature.requestCancellation.execute(access, observed(), abort.signal);
    abort.abort();
    resolve({ ...job(), cancellationRequested: true, availableActions: [], version: 3 });
    await expect(pending).rejects.toThrow();
    expect(request).toHaveBeenCalledTimes(3);
    expect(() => feature.loadJob.execute(access, id(1), abort.signal)).toThrow();
    expect(request).toHaveBeenCalledTimes(3);
  });
});
