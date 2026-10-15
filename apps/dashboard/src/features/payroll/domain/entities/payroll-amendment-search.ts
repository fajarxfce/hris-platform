import type {
  PayrollAmendment,
  PayrollAmendmentDetails,
  PayrollAmendmentPage,
  PayrollAmendmentStatus,
} from "./payroll-amendment";
import type { PayrollAmendmentCalculation } from "./payroll-amendment-calculation";
export type PayrollAmendmentSearch = Readonly<{
  year: number;
  employeeId: string | null;
  query: string;
  status: PayrollAmendmentStatus | null;
  after: string | null;
}>;
export type PayrollAmendmentSelection = Readonly<{ revision: number | null; after: number | null }>;
export type PayrollAmendmentInspection = Readonly<{
  current: PayrollAmendmentDetails;
  selected: PayrollAmendment;
  history: PayrollAmendmentPage;
  calculation: PayrollAmendmentCalculation | null;
}>;
