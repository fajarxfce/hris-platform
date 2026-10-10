import { describe, expect, it, vi } from "vitest";
import type { TextFile } from "../../../../core/domain/files/text-file";
import type { CompanyId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { initialEmployeeImportCreationState } from "../models/employee-import-creation-state";
import { EmployeeImportCreationController } from "./employee-import-creation-controller";

const companyId = "10000000-0000-4000-8000-000000000001" as CompanyId;
const permissions = [
  "people.import",
  "people.manage",
  "people.profile.read",
  "people.profile.manage",
];
const access = { companyId, permissions };
const file = Object.freeze({ name: "intake.csv", byteLength: 11, text: "PRIVATE CSV" });
const other = Object.freeze({ name: "replacement.csv", byteLength: 5, text: "OTHER" });
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
function fixture(grants = permissions) {
  const select = vi
    .fn<PeopleUseCases["selectEmployeeImportFile"]["execute"]>()
    .mockResolvedValue(success(file));
  const download = vi
    .fn<PeopleUseCases["downloadEmployeeImportTemplate"]["execute"]>()
    .mockResolvedValue(success(undefined));
  const start = vi
    .fn<PeopleUseCases["startEmployeeImport"]["execute"]>()
    .mockImplementation(async (_access, _operation, input) =>
      success({ id: input.id, version: 0 }),
    );
  let sequence = 0;
  const next = vi.fn(() => `30000000-0000-4000-8000-${String(++sequence).padStart(12, "0")}`);
  const controller = new EmployeeImportCreationController(
    {
      selectEmployeeImportFile: { execute: select },
      downloadEmployeeImportTemplate: { execute: download },
      startEmployeeImport: { execute: start },
    },
    { ...access, permissions: grants },
    next,
  );
  controller.activate();
  return { controller, select, download, start, next };
}
describe("CSV preview ownership", () => {
  it("late selected content cannot repopulate a disposed controller", async () => {
    const f = fixture();
    const held = deferred<Result<TextFile | null>>();
    f.select.mockReturnValueOnce(held.promise);
    const pending = f.controller.selectFile();
    f.controller.deactivate();
    held.resolve(success(file));
    await pending;
    expect(f.controller.getSnapshot()).toBe(initialEmployeeImportCreationState);
    f.controller.activate();
    await f.controller.start("New intake");
    expect(f.start).not.toHaveBeenCalled();
    f.controller.deactivate();
  });
  it("late acknowledged submissions cannot restore a disposed preview", async () => {
    const f = fixture();
    await f.controller.selectFile();
    const held = deferred<Result<MutationReceipt>>();
    f.start.mockReturnValueOnce(held.promise);
    const pending = f.controller.start("Intake");
    f.controller.deactivate();
    held.resolve(success({ id: "30000000-0000-4000-8000-000000000001", version: 0 }));
    await pending;
    expect(f.controller.getSnapshot()).toBe(initialEmployeeImportCreationState);
  });
  it("keeps CSV out of public state and admits one immutable start after selection", async () => {
    const f = fixture();
    await f.controller.start("Intake");
    expect(f.start).not.toHaveBeenCalled();
    expect(f.controller.getSnapshot().failure?.code).toBe("employee_import_file_required");
    await f.controller.selectFile();
    expect(f.start).not.toHaveBeenCalled();
    expect(JSON.stringify(f.controller.getSnapshot())).not.toContain("PRIVATE");
    const held = deferred<Result<MutationReceipt>>();
    f.start.mockReturnValueOnce(held.promise);
    const pending = f.controller.start("Intake");
    await f.controller.start("Other");
    await f.controller.selectFile();
    await f.controller.downloadTemplate();
    f.controller.clearFile();
    expect(f.start).toHaveBeenCalledOnce();
    expect(f.select).toHaveBeenCalledOnce();
    expect(f.download).not.toHaveBeenCalled();
    const input = f.start.mock.lastCall?.[2];
    expect(input?.file).toBe(file);
    expect(Object.isFrozen(input)).toBe(true);
    held.resolve(success({ id: input?.id ?? "", version: 0 }));
    await pending;
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "saved",
      file: null,
      operationId: null,
    });
    f.controller.deactivate();
  });
  it("retains the original CSV, reason and identifiers through uncertain delivery and MFA", async () => {
    const f = fixture();
    await f.controller.selectFile();
    f.start
      .mockResolvedValueOnce(failed("connection_unavailable"))
      .mockResolvedValueOnce(failed("mfa_required"))
      .mockResolvedValueOnce(failed("invalid_employee_csv"));
    await f.controller.start("Original intake");
    const captured = f.start.mock.lastCall;
    for (const code of ["mfa_required", "invalid_employee_csv"]) {
      f.controller.clearFile();
      await f.controller.selectFile();
      await f.controller.start("Changed");
      await f.controller.retry();
      expect(f.controller.getSnapshot()).toMatchObject({ stage: "unconfirmed", failure: { code } });
    }
    await f.controller.retry();
    expect(f.controller.getSnapshot().stage).toBe("saved");
    expect(f.next).toHaveBeenCalledTimes(2);
    expect(f.select).toHaveBeenCalledOnce();
    for (const call of f.start.mock.calls) {
      expect(call[1]).toBe(captured?.[1]);
      expect(call[2]).toBe(captured?.[2]);
    }
    f.controller.deactivate();
  });
  it.each(["invalid_employee_csv", "request_body_too_large", "employee_import_size_limit"])(
    "a first %s rejection allows a corrected file and new operation",
    async (code) => {
      const f = fixture();
      await f.controller.selectFile();
      f.start.mockResolvedValueOnce(failed(code));
      await f.controller.start("Intake");
      expect(f.controller.getSnapshot().stage).toBe("editing");
      f.select.mockResolvedValueOnce(success(other));
      await f.controller.selectFile();
      await f.controller.start("Corrected intake");
      expect(f.start.mock.calls[1]?.[2].file).toBe(other);
      expect(f.start.mock.calls[1]?.[1]).not.toBe(f.start.mock.calls[0]?.[1]);
      expect(f.start.mock.calls[1]?.[2].id).toBe(f.start.mock.calls[0]?.[2].id);
      f.controller.deactivate();
    },
  );
  it("cancelling a replacement picker clears the prior selection without submitting it", async () => {
    const f = fixture();
    await f.controller.selectFile();
    f.select.mockResolvedValueOnce(success(null));
    await f.controller.selectFile();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "editing",
      file: null,
      failure: null,
    });
    await f.controller.start("Intake");
    expect(f.start).not.toHaveBeenCalled();
    f.controller.deactivate();
  });
  it("templates cannot replace a selected CSV or be requested during a pending selection", async () => {
    const f = fixture();
    const held = deferred<Result<TextFile | null>>();
    f.select.mockReturnValueOnce(held.promise);
    const pending = f.controller.selectFile();
    await f.controller.selectFile();
    await f.controller.downloadTemplate();
    expect(f.select).toHaveBeenCalledOnce();
    expect(f.download).not.toHaveBeenCalled();
    held.resolve(success(file));
    await pending;
    await f.controller.downloadTemplate();
    expect(f.controller.getSnapshot()).toMatchObject({
      stage: "editing",
      file: { name: file.name },
      templateRequested: true,
    });
    await f.controller.start("Intake");
    expect(f.start.mock.lastCall?.[2].file).toBe(file);
    f.controller.deactivate();
  });
  it.each(["select", "download", "start"] as const)(
    "disposal aborts a pending %s and rejects late completion",
    async (kind) => {
      const f = fixture();
      const held = deferred<never>();
      let pending: Promise<void>;
      let owner: AbortSignal | undefined;
      if (kind === "select") {
        f.select.mockReturnValueOnce(held.promise);
        pending = f.controller.selectFile();
        owner = f.select.mock.lastCall?.[1];
      } else if (kind === "download") {
        f.download.mockReturnValueOnce(held.promise);
        pending = f.controller.downloadTemplate();
        owner = f.download.mock.lastCall?.[1];
      } else {
        await f.controller.selectFile();
        f.start.mockReturnValueOnce(held.promise);
        pending = f.controller.start("Intake");
        owner = f.start.mock.lastCall?.[3];
      }
      const listener = vi.fn();
      const unsubscribe = f.controller.subscribe(listener);
      unsubscribe();
      f.controller.deactivate();
      expect(owner?.aborted).toBe(true);
      held.reject(new Error("late private failure"));
      await pending;
      expect(f.controller.getSnapshot()).toBe(initialEmployeeImportCreationState);
      expect(listener).not.toHaveBeenCalled();
      f.controller.activate();
      await f.controller.start("New intake");
      expect(f.controller.getSnapshot().failure?.code).toBe("employee_import_file_required");
      f.controller.deactivate();
    },
  );
  it("unavailable access cannot acquire a file, template, or command", async () => {
    const f = fixture([]);
    await f.controller.selectFile();
    await f.controller.downloadTemplate();
    await f.controller.start("Intake");
    expect(f.controller.getSnapshot().stage).toBe("unavailable");
    expect(f.select).not.toHaveBeenCalled();
    expect(f.download).not.toHaveBeenCalled();
    expect(f.start).not.toHaveBeenCalled();
    f.controller.deactivate();
  });
});
