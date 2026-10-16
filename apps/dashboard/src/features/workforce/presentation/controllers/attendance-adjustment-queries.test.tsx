import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import type { AccountId } from "../../../../core/domain/identifiers";
import { success } from "../../../../core/domain/result";
import { toAttendanceAdjustmentDetails } from "../../data/mappers/attendance-adjustment-mapper";
import {
  adjustmentAccess,
  adjustmentDetailsDto,
  adjustmentId,
  company,
  employeeId,
  identifier,
  periodMonth,
} from "../../di/attendance-adjustment-fixture";
import type { WorkforceUseCases } from "../contracts/workforce-use-cases";
import { useAttendanceAdjustment } from "./use-attendance-adjustment";

describe("attendance revision read lifetime", () => {
  it("cancels a hidden read and never exposes its late result", async () => {
    const client = new QueryClient();
    let resolve!: (
      value: Awaited<ReturnType<WorkforceUseCases["loadAttendanceAdjustment"]["execute"]>>,
    ) => void;
    const pending = new Promise<
      Awaited<ReturnType<WorkforceUseCases["loadAttendanceAdjustment"]["execute"]>>
    >((done) => {
      resolve = done;
    });
    const details = toAttendanceAdjustmentDetails(
      adjustmentDetailsDto(),
      company,
      employeeId,
      periodMonth,
      adjustmentId,
    );
    const execute = vi
      .fn<WorkforceUseCases["loadAttendanceAdjustment"]["execute"]>()
      .mockReturnValueOnce(pending)
      .mockResolvedValue(success(details));
    const hook = renderHook(
      ({ visible }) =>
        useAttendanceAdjustment(
          identifier(900) as AccountId,
          adjustmentAccess,
          employeeId,
          periodMonth,
          adjustmentId,
          visible,
          { execute },
        ),
      {
        initialProps: { visible: true },
        wrapper: ({ children }: { children: ReactNode }) => (
          <QueryClientProvider client={client}>{children}</QueryClientProvider>
        ),
      },
    );
    await waitFor(() => expect(execute).toHaveBeenCalledTimes(1));
    const signal = execute.mock.calls[0]?.[4];
    hook.rerender({ visible: false });
    await waitFor(() => expect(signal?.aborted).toBe(true));
    await act(async () => resolve(success(details)));
    expect(hook.result.current.details).toBeNull();
    hook.rerender({ visible: true });
    await waitFor(() => expect(hook.result.current.details?.summary.id).toBe(adjustmentId));
    expect(execute).toHaveBeenCalledTimes(2);
    hook.unmount();
    client.clear();
  });
});
