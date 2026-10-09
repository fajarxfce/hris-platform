import { describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { BackgroundJob, JobId } from "../../domain/entities/background-job";
import type { JobsUseCases } from "../contracts/jobs-use-cases";
import { JobDetailController } from "./job-detail-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const access = { companyId, permissions: [] };
const job: BackgroundJob = Object.freeze({
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
});
const accepted = Object.freeze({
  ...job,
  cancellationRequested: true,
  availableActions: [],
  version: 4,
});
function pending<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}
const actions = () => ({
  loadJob: { execute: vi.fn<JobsUseCases["loadJob"]["execute"]>().mockResolvedValue(success(job)) },
  requestCancellation: {
    execute: vi
      .fn<JobsUseCases["requestCancellation"]["execute"]>()
      .mockResolvedValue(success(accepted)),
  },
});

describe("job detail ownership and commands", () => {
  it("requires explicit confirmation and sends the observed version only once during competing clicks", async () => {
    const useCases = actions();
    const response = pending<Result<BackgroundJob>>();
    useCases.requestCancellation.execute.mockReturnValueOnce(response.promise);
    const controller = new JobDetailController(useCases, access, job.id);
    await controller.confirmCancellation();
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    await controller.confirmCancellation();
    expect(useCases.requestCancellation.execute).not.toHaveBeenCalled();
    controller.requestConfirmation();
    controller.dismissConfirmation();
    expect(controller.getSnapshot().stage).toBe("ready");
    controller.requestConfirmation();
    const submitting = controller.confirmCancellation();
    await controller.confirmCancellation();
    controller.dismissConfirmation();
    await controller.refresh();
    expect(controller.getSnapshot().stage).toBe("cancelling");
    expect(useCases.requestCancellation.execute).toHaveBeenCalledTimes(1);
    expect(useCases.requestCancellation.execute.mock.lastCall?.[1]).toBe(job);
    expect(useCases.loadJob.execute).toHaveBeenCalledTimes(1);
    response.resolve(success(accepted));
    await submitting;
    expect(controller.getSnapshot()).toEqual({ stage: "ready", job: accepted, failure: null });
    expect(controller.getSnapshot().job?.status).toBe("QUEUED");
    controller.requestConfirmation();
    expect(controller.getSnapshot().stage).toBe("ready");
    controller.deactivate();
  });

  it("requires a status read after an unconfirmed response and never silently repeats cancellation", async () => {
    for (const crash of [false, true]) {
      const useCases = actions();
      if (crash)
        useCases.requestCancellation.execute.mockRejectedValueOnce(
          new Error("PRIVATE LATE RESPONSE"),
        );
      else
        useCases.requestCancellation.execute.mockResolvedValueOnce(
          failed("connection_unavailable"),
        );
      const controller = new JobDetailController(useCases, access, job.id);
      controller.activate();
      await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
      controller.requestConfirmation();
      await controller.confirmCancellation();
      expect(controller.getSnapshot()).toMatchObject({ stage: "unconfirmed", job: null });
      expect(JSON.stringify(controller.getSnapshot())).not.toContain("PRIVATE");
      controller.requestConfirmation();
      await controller.confirmCancellation();
      expect(useCases.requestCancellation.execute).toHaveBeenCalledTimes(1);
      useCases.loadJob.execute.mockResolvedValueOnce(success(accepted));
      await controller.refresh();
      expect(controller.getSnapshot().job).toBe(accepted);
      controller.requestConfirmation();
      await controller.confirmCancellation();
      expect(useCases.requestCancellation.execute).toHaveBeenCalledTimes(1);
      controller.deactivate();
    }
  });

  it("uses a fresh version after a stale conflict and removes details if current access is denied", async () => {
    const useCases = actions();
    useCases.requestCancellation.execute.mockResolvedValueOnce(failed("stale_version"));
    const controller = new JobDetailController(useCases, access, job.id);
    controller.activate();
    await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
    controller.requestConfirmation();
    await controller.confirmCancellation();
    const updated = { ...job, version: 4 };
    useCases.loadJob.execute.mockResolvedValueOnce(success(updated));
    await controller.refresh();
    controller.requestConfirmation();
    useCases.requestCancellation.execute.mockResolvedValueOnce(failed("company_access_denied"));
    await controller.confirmCancellation();
    expect(useCases.requestCancellation.execute.mock.lastCall?.[1]).toBe(updated);
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unconfirmed",
      job: null,
      failure: { code: "company_access_denied" },
    });
    useCases.loadJob.execute.mockResolvedValueOnce(failed("company_access_denied"));
    await controller.refresh();
    expect(controller.getSnapshot()).toMatchObject({ stage: "unavailable", job: null });
    controller.deactivate();
  });

  it("aborts pending commands on disposal and rejects their late success or failure after remount", async () => {
    for (const reject of [false, true]) {
      const useCases = actions();
      const held = pending<Result<BackgroundJob>>();
      useCases.requestCancellation.execute.mockReturnValueOnce(held.promise);
      const controller = new JobDetailController(useCases, access, job.id);
      controller.activate();
      await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
      controller.requestConfirmation();
      const submitting = controller.confirmCancellation();
      const signal = useCases.requestCancellation.execute.mock.lastCall?.[2];
      controller.deactivate();
      expect(signal?.aborted).toBe(true);
      expect(controller.getSnapshot()).toEqual({ stage: "idle", job: null, failure: null });
      controller.activate();
      await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
      if (reject) held.reject(new Error("PRIVATE OBSOLETE RESPONSE"));
      else held.resolve(success(accepted));
      await submitting;
      expect(controller.getSnapshot()).toEqual({ stage: "ready", job, failure: null });
      controller.deactivate();
    }
  });

  it("clears replaced reads and releases subscriptions without retaining a late failure", async () => {
    const useCases = actions();
    const held = pending<Result<BackgroundJob>>();
    useCases.loadJob.execute.mockReturnValueOnce(held.promise);
    const controller = new JobDetailController(useCases, access, job.id);
    const changed = vi.fn();
    const remove = controller.subscribe(changed);
    controller.activate();
    controller.activate();
    const signal = useCases.loadJob.execute.mock.lastCall?.[2];
    await controller.refresh();
    expect(signal?.aborted).toBe(true);
    held.reject(new Error("PRIVATE OBSOLETE READ"));
    await Promise.resolve();
    expect(controller.getSnapshot().job).toBe(job);
    useCases.loadJob.execute.mockRejectedValueOnce(new Error("PRIVATE ADAPTER FAILURE"));
    await controller.refresh();
    expect(controller.getSnapshot()).toMatchObject({
      stage: "unavailable",
      job: null,
      failure: { code: "unexpected_error" },
    });
    remove();
    changed.mockClear();
    controller.deactivate();
    await controller.refresh();
    expect(changed).not.toHaveBeenCalled();
    expect(useCases.loadJob.execute).toHaveBeenCalledTimes(3);
  });
});
