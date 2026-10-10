import type { Employee } from "./employee";

export type EmployeePage = Readonly<{
  items: readonly Employee[];
  nextCursor: string | null;
}>;
