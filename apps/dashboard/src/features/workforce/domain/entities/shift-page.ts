import type { ShiftDefinition } from "./shift-definition";

export type ShiftPage = Readonly<{
  items: readonly ShiftDefinition[];
  nextCursor: string | null;
}>;
