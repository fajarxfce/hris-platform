import type { OperationId } from "../../../../core/domain/identifiers";
import type { MutationReceipt } from "../../../../core/domain/mutation-receipt";
import type { Failure } from "../../../../core/domain/result";
import type { PayrollPeriodCreation } from "../../domain/entities/payroll-period-creation";
import type {
  PayrollPeriodEmployee,
  PayrollPeriodEmployees,
} from "../../domain/entities/payroll-period-employee";
export type PayrollPeriodCreationState = Readonly<{
  stage: "editing" | "reviewing" | "saving" | "unconfirmed" | "unavailable" | "saved";
  selected: readonly PayrollPeriodEmployee[];
  selectedPage: number;
  page: PayrollPeriodEmployees | null;
  loading: boolean;
  query: string;
  after: string | null;
  review: PayrollPeriodCreation | null;
  failure: Failure | null;
  searchFailure: Failure | null;
  receipt: MutationReceipt | null;
  operationId: OperationId | null;
}>;
export const initialPayrollPeriodCreationState: PayrollPeriodCreationState = {
  stage: "editing",
  selected: [],
  selectedPage: 0,
  page: null,
  loading: false,
  query: "",
  after: null,
  review: null,
  failure: null,
  searchFailure: null,
  receipt: null,
  operationId: null,
};
