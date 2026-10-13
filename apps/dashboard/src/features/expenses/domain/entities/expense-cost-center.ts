import type { CompanyId } from "../../../../core/domain/identifiers";
export type ExpenseCostCenter = Readonly<{
  companyId: CompanyId;
  id: string;
  code: string;
  name: string;
  version: number;
}>;
export type ExpenseCostCenterPage = Readonly<{
  items: readonly ExpenseCostCenter[];
  nextCursor: string | null;
}>;
