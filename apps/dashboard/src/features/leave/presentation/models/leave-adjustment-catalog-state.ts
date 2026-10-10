import type { Failure } from "../../../../core/domain/result";
import type { LeaveAdjustmentCatalog } from "../../domain/entities/leave-adjustment-catalog";

export type LeaveAdjustmentCatalogState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  catalog: LeaveAdjustmentCatalog | null;
  failure: Failure | null;
}>;
export const initialLeaveAdjustmentCatalogState: LeaveAdjustmentCatalogState = Object.freeze({
  stage: "loading",
  catalog: null,
  failure: null,
});
