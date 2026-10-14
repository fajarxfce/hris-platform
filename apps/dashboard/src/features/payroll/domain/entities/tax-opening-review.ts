import type { TaxOpening, TaxOpeningSummary } from "./tax-opening";
export type TaxOpeningEmployeeReference = Readonly<{
  id: string;
  employeeNumber: string;
  name: string;
  employmentVersion: number;
}>;
export const taxOpeningEmployeeStatuses = ["UNPREPARED", "DRAFT", "VERIFIED"] as const;
export const taxOpeningActions = ["PREPARE", "VERIFY"] as const;
export type TaxOpeningAction = (typeof taxOpeningActions)[number];
export type TaxOpeningEmployeeStatus = (typeof taxOpeningEmployeeStatuses)[number];
export type TaxOpeningEmployee = TaxOpeningEmployeeReference &
  Readonly<{
    openingVersion: number | null;
    openingStatus: "DRAFT" | "VERIFIED" | null;
  }>;
export type TaxOpeningSearch = Readonly<{
  year: string;
  query: string;
  status: TaxOpeningEmployeeStatus | null;
  after: string | null;
}>;
export type TaxOpeningEmployees = Readonly<{
  items: readonly TaxOpeningEmployee[];
  nextCursor: string | null;
}>;
export type TaxOpeningReview = Readonly<{
  year: string;
  employee: TaxOpeningEmployeeReference;
  current: TaxOpening | null;
  history: Readonly<{ items: readonly TaxOpeningSummary[]; nextCursor: string | null }>;
  frozen: boolean;
  availableActions: readonly TaxOpeningAction[];
}>;
