import { expect, it, vi } from "vitest";
import type { CompanyId } from "../../../../core/domain/identifiers";
import { failed, type Result, success } from "../../../../core/domain/result";
import type { SavedReportFilter } from "../../domain/entities/saved-report-filter";
import type { ReportingUseCases } from "../contracts/reporting-use-cases";
import { SavedReportFiltersController } from "./saved-report-filters-controller";

const companyId = "00000000-0000-4000-8000-000000000001" as CompanyId;
const filter: SavedReportFilter = {
  id: companyId,
  companyId,
  name: "Personal",
  kind: "HEADCOUNT",
  definitionVersion: "headcount.v1",
  parameters: { asOf: "2026-10-01", from: null, until: null, status: null, companies: [companyId] },
  version: 0,
  createdAt: "2026-10-01T00:00:00Z",
  updatedAt: "2026-10-01T00:00:00Z",
};
it("clears a filter directory on denial, cancels replaced reads and ignores late data after disposal", async () => {
  let resolve!: (value: Result<readonly SavedReportFilter[]>) => void;
  const held = new Promise<Result<readonly SavedReportFilter[]>>((done) => {
    resolve = done;
  });
  const execute = vi
    .fn<ReportingUseCases["listSavedFilters"]["execute"]>()
    .mockResolvedValueOnce(success([filter]))
    .mockReturnValueOnce(held)
    .mockResolvedValueOnce(failed("access_denied"));
  const controller = new SavedReportFiltersController(
    { execute },
    { companyId, permissions: ["reports.read"] },
  );
  controller.activate();
  await vi.waitFor(() => expect(controller.getSnapshot().items).toHaveLength(1));
  const loading = controller.refresh();
  expect(controller.getSnapshot().items).toEqual([]);
  const signal = execute.mock.calls[1]?.[1];
  await controller.refresh();
  expect(signal?.aborted).toBe(true);
  expect(controller.getSnapshot()).toMatchObject({ stage: "unavailable", items: [] });
  const listener = vi.fn();
  const stop = controller.subscribe(listener);
  stop();
  controller.deactivate();
  resolve(success([filter]));
  await loading;
  expect(controller.getSnapshot()).toMatchObject({ stage: "idle", items: [] });
  expect(listener).not.toHaveBeenCalled();
  await controller.refresh();
  expect(execute).toHaveBeenCalledTimes(3);
});
