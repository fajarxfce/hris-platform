import type { CompanyId } from "../../../../core/domain/identifiers";
import type { ExpenseLine } from "./expense-line";
export type ExpenseDraftChange = Readonly<{
  companyId: CompanyId;
  id: string;
  employmentId: string;
  expectedVersion: number | null;
  title: string;
  description: string;
  lines: readonly ExpenseLine[];
  reason: string;
}>;
