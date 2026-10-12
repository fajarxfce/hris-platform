import { describe, expect, it, vi } from "vitest";
import {
  documentCompanyId,
  documentHeaderId,
  documentRevisionId,
} from "../../../../../tests/fixtures/documents";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { DocumentsUseCases } from "../contracts/documents-use-cases";
import { DocumentDownloadController } from "./document-download-controller";

const access = {
  companyId: documentCompanyId as CompanyId,
  permissions: ["documents.read", "people.profile.read"],
};
function setup() {
  const execute = vi
    .fn<DocumentsUseCases["download"]["execute"]>()
    .mockResolvedValue(success(undefined));
  const controller = new DocumentDownloadController(
    { execute },
    access,
    documentHeaderId,
    documentRevisionId,
  );
  controller.activate();
  return { execute, controller };
}
function pending() {
  let resolve!: (result: Result<void>) => void;
  const promise = new Promise<Result<void>>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

describe("document download ownership", () => {
  it("allows one transfer and drops its late result after explicit cancellation", async () => {
    const { execute, controller } = setup(),
      held = pending();
    execute.mockReturnValueOnce(held.promise);
    const first = controller.start();
    await controller.start();
    expect(execute).toHaveBeenCalledTimes(1);
    controller.cancel();
    expect(execute.mock.calls[0]?.[3].aborted).toBe(true);
    await controller.start();
    expect(controller.getSnapshot().stage).toBe("started");
    held.resolve(failed("document_access_denied"));
    await first;
    expect(controller.getSnapshot().stage).toBe("started");
    controller.deactivate();
  });

  it.each([
    "document_access_denied",
    "document_not_ready",
    "mfa_required",
    "session_revoked",
    "document_revision_not_found",
  ])("does not clear %s through cancellation, reactivation or another download", async (code) => {
    const { execute, controller } = setup();
    execute.mockResolvedValueOnce(failed(code));
    await controller.start();
    controller.cancel();
    controller.deactivate();
    controller.activate();
    await controller.start();
    expect(controller.getSnapshot()).toEqual({
      stage: "unavailable",
      failure: { code, fields: {}, parameters: {} },
    });
    expect(execute).toHaveBeenCalledTimes(1);
    controller.deactivate();
  });

  it("disposal cancels pending work and removes observer ownership", async () => {
    const { execute, controller } = setup(),
      held = pending(),
      changed = vi.fn();
    const unsubscribe = controller.subscribe(changed);
    execute.mockReturnValue(held.promise);
    const result = controller.start();
    unsubscribe();
    controller.deactivate();
    held.resolve(success(undefined));
    await result;
    await controller.start();
    expect(execute.mock.calls[0]?.[3].aborted).toBe(true);
    expect(execute).toHaveBeenCalledTimes(1);
    expect(controller.getSnapshot().stage).toBe("idle");
    expect(changed).toHaveBeenCalledTimes(1);
  });

  it("reports unexpected failures safely and permits an explicit retry of transient failures", async () => {
    const { execute, controller } = setup();
    execute.mockRejectedValueOnce(new Error("PRIVATE RESPONSE"));
    await controller.start();
    expect(controller.getSnapshot()).toEqual({
      stage: "failed",
      failure: { code: "unexpected_error", fields: {}, parameters: {} },
    });
    await controller.start();
    expect(controller.getSnapshot().stage).toBe("started");
    expect(execute).toHaveBeenCalledTimes(2);
    controller.deactivate();
  });
});
