export const leaveLedgerKinds = [
  "ADJUSTMENT",
  "GRANT",
  "RESERVE",
  "CONSUME",
  "RELEASE",
  "REFUND",
  "EXPIRE",
  "CARRY_OUT",
  "CARRY_IN",
] as const;
export type LeaveLedgerEntry = Readonly<{
  id: string;
  kind: (typeof leaveLedgerKinds)[number];
  sourceId: string;
  requestId: string | null;
  availableDeltaDays: string;
  reservedDeltaDays: string;
  consumedDeltaDays: string;
  actorId: string;
  recordedAt: string;
  reason: string;
}>;
