import { describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { BackgroundJob, JobId } from "../../domain/entities/background-job";
import type { JobPage } from "../../domain/entities/job-page";
import { firstJobPage } from "../../domain/entities/job-search";
import type { JobsUseCases } from "../contracts/jobs-use-cases";
import { JobListController } from "./job-list-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: [] };
const page: JobPage = { companyId, items: [], next: null };
function pending<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}

describe("job list ownership", () => {
  it("accepts authorized detail observations only for visible jobs in this company without downgrading versions", async () => {
    const job: BackgroundJob = {
      id: "40000000-0000-4000-8000-000000000001" as JobId,
      companyId,
      kind: "WORKFORCE_CLOSE",
      status: "QUEUED",
      completedItems: 0,
      totalItems: 10,
      progressMode: "FIXED_TOTAL",
      attempts: 0,
      cancellationRequested: false,
      failureCode: null,
      createdAt: "2026-10-01T00:00:00Z",
      finishedAt: null,
      version: 3,
      availableActions: ["cancel"],
      scheduledFor: null,
      availableAt: "2026-10-01T00:00:00Z",
    };
    const execute = vi
      .fn<JobsUseCases["loadJobs"]["execute"]>()
      .mockResolvedValue(success({ ...page, items: [job] }));
    const controller = new JobListController({ execute }, access, firstJobPage);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    const cancelled = { ...job, cancellationRequested: true, availableActions: [], version: 4 };
    controller.observeJob(cancelled);
    controller.observeJob(job);
    controller.observeJob({ ...cancelled, companyId: "other-company" as CompanyId, version: 5 });
    controller.observeJob({ ...cancelled, id: "another-job" as JobId, version: 5 });
    expect(controller.getSnapshot().page?.items).toEqual([cancelled]);
    expect(execute).toHaveBeenCalledTimes(1);
    controller.deactivate();
    controller.observeJob(cancelled);
    expect(controller.getSnapshot().page).toBeNull();
  });
  it("discards replaced results and failures while retaining one owned search snapshot", async () => {
    for (const reject of [false, true]) {
      const old = pending<Result<JobPage>>();
      const execute = vi
        .fn<JobsUseCases["loadJobs"]["execute"]>()
        .mockReturnValueOnce(old.promise)
        .mockResolvedValue(success(page));
      const search = { ...firstJobPage };
      const controller = new JobListController({ execute }, access, search);
      search.beforeAt = "changed-after-construction";
      controller.activate();
      controller.activate();
      expect(execute).toHaveBeenCalledTimes(1);
      expect(execute.mock.lastCall?.[1]).toEqual(firstJobPage);
      const signal = execute.mock.lastCall?.[2];
      await controller.refresh();
      expect(signal?.aborted).toBe(true);
      if (reject) old.reject(new Error("PRIVATE LATE FAILURE"));
      else old.resolve(failed("session_revoked"));
      await Promise.resolve();
      expect(controller.getSnapshot()).toEqual({ stage: "ready", page, failure: null });
      controller.deactivate();
    }
  });

  it("clears private data on refresh, access loss, disposal and remount", async () => {
    const held = pending<Result<JobPage>>();
    const execute = vi
      .fn<JobsUseCases["loadJobs"]["execute"]>()
      .mockResolvedValueOnce(success(page))
      .mockReturnValueOnce(held.promise)
      .mockResolvedValue(failed("company_access_denied"));
    const controller = new JobListController({ execute }, access, firstJobPage);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().page).toBe(page));
    const refresh = controller.refresh();
    expect(controller.getSnapshot()).toEqual({ stage: "loading", page: null, failure: null });
    const signal = execute.mock.lastCall?.[2];
    controller.deactivate();
    expect(signal?.aborted).toBe(true);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("unavailable"));
    held.resolve(success(page));
    await refresh;
    expect(controller.getSnapshot()).toMatchObject({
      page: null,
      failure: { code: "company_access_denied" },
    });
    controller.deactivate();
    await controller.refresh();
    expect(execute).toHaveBeenCalledTimes(3);
    expect(controller.getSnapshot().stage).toBe("idle");
  });

  it("contains unexpected errors and removes unsubscribed listeners without retry loops", async () => {
    const execute = vi
      .fn<JobsUseCases["loadJobs"]["execute"]>()
      .mockRejectedValue(new Error("PRIVATE ADAPTER DETAIL"));
    const controller = new JobListController({ execute }, access, firstJobPage);
    const changed = vi.fn();
    const remove = controller.subscribe(changed);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().failure?.code).toBe("unexpected_error"));
    expect(JSON.stringify(controller.getSnapshot())).not.toContain("PRIVATE");
    expect(execute).toHaveBeenCalledTimes(1);
    remove();
    changed.mockClear();
    controller.deactivate();
    expect(changed).not.toHaveBeenCalled();
  });
});
