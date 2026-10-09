import { utcInstantMicroseconds } from "../../../../core/domain/utc-instant";

/** Audit windows cannot begin before 1900. Clock precision is normalized by the shared UTC parser. */
export function auditInstantMicros(value: string): bigint | null {
  return value < "1900-01-01" ? null : utcInstantMicroseconds(value);
}
