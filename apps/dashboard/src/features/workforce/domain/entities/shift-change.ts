import type { ShiftDetails } from "./shift-definition";

export type ShiftChange = Readonly<{
  id: string;
  details: ShiftDetails;
  active: boolean;
  expectedVersion: number | null;
  reason: string;
}>;
