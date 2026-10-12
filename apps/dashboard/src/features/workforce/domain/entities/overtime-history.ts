import type { OvertimeChange } from "./overtime-change";

export type OvertimeHistory = Readonly<{
  items: readonly OvertimeChange[];
  nextCursor: string | null;
}>;
