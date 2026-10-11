import type { ShiftDetails, ShiftId } from "./shift-definition";

export type ShiftSnapshot = Readonly<{ id: ShiftId; revision: number; details: ShiftDetails }>;
