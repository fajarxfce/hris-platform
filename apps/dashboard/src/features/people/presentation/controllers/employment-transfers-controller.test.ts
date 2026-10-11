import { describe, expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { EmploymentTransfer } from "../../domain/entities/employment-transfer";
import type { PeopleUseCases } from "../contracts/people-use-cases";
import { EmploymentTransfersController } from "./employment-transfers-controller";

const access = {
  companyId: "10000000-0000-4000-8000-000000000001" as CompanyId,
  permissions: ["people.read"],
};
const id = "40000000-0000-4000-8000-000000000001";
describe("employment transfer history ownership", () => {
  it("clears an acquired history when access is revoked", async () => {
    const load = {
      execute: vi
        .fn<PeopleUseCases["loadEmploymentTransfers"]["execute"]>()
        .mockResolvedValue(success([])),
    };
    const controller = new EmploymentTransfersController(load, access, id);
    try {
      controller.activate();
      await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
      load.execute.mockResolvedValueOnce(failed("company_access_denied"));
      await controller.refresh();
      expect(controller.getSnapshot()).toMatchObject({
        stage: "unavailable",
        items: null,
        failure: { code: "company_access_denied" },
      });
    } finally {
      controller.deactivate();
    }
  });

  it.each([false, true])(
    "aborts replaced reads and ignores late outcomes after disposal (failure: %s)",
    async (rejects) => {
      let resolve!: (value: Result<readonly EmploymentTransfer[]>) => void;
      let reject!: (error: unknown) => void;
      const held = new Promise<Result<readonly EmploymentTransfer[]>>((done, fail) => {
        resolve = done;
        reject = fail;
      });
      const load = {
        execute: vi
          .fn<PeopleUseCases["loadEmploymentTransfers"]["execute"]>()
          .mockResolvedValue(success([])),
      };
      const controller = new EmploymentTransfersController(load, access, id);
      try {
        controller.activate();
        await vi.waitFor(() => expect(controller.getSnapshot().stage).toBe("ready"));
        load.execute.mockReturnValueOnce(held);
        const old = controller.refresh();
        const signal = load.execute.mock.lastCall?.[2];
        await controller.refresh();
        expect(signal?.aborted).toBe(true);
        controller.deactivate();
        if (rejects) reject(new Error("PRIVATE LATE ERROR"));
        else resolve(success([]));
        await old;
        expect(controller.getSnapshot()).toMatchObject({
          stage: "loading",
          items: null,
          failure: null,
        });
      } finally {
        controller.deactivate();
      }
    },
  );
});
