import type { Failure } from "../../../../core/domain/result";
import type { PayrollFinalizationDetails } from "../../domain/entities/payroll-finalization-details";
import type { PayrollFinalizations } from "../../domain/entities/payroll-finalization-preparation";
import type { PayrollPublicationPage } from "../../domain/entities/payroll-publication-search";
export type PayrollPublicationsState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  page: PayrollPublicationPage | null;
  failure: Failure | null;
}>;
export const initialPayrollPublicationsState: PayrollPublicationsState = {
  stage: "loading",
  page: null,
  failure: null,
};
export type PayrollFinalizationsState = Readonly<{
  stage: "loading" | "ready" | "unavailable";
  data: PayrollFinalizations | null;
  failure: Failure | null;
}>;
export const initialPayrollFinalizationsState: PayrollFinalizationsState = {
  stage: "loading",
  data: null,
  failure: null,
};
export type PayrollFinalizationState = Readonly<{
  stage: "loading" | "updating" | "ready" | "unavailable";
  details: PayrollFinalizationDetails | null;
  failure: Failure | null;
  updatesPaused: boolean;
}>;
export const initialPayrollFinalizationState: PayrollFinalizationState = {
  stage: "loading",
  details: null,
  failure: null,
  updatesPaused: false,
};
