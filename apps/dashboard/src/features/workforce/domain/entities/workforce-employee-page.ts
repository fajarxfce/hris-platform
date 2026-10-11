import type { WorkforceEmployee } from "./workforce-employee";

export type WorkforceEmployeePage = Readonly<{
  items: readonly WorkforceEmployee[];
  nextCursor: string | null;
}>;
