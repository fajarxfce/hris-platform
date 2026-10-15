import { expect } from "@playwright/test";
import { companyDateTimeInput } from "../../src/core/presentation/dates/company-date-time";

/** Native fields have second precision; never round a confirmation before release. */
export async function bankConfirmationInput(releasedAt: string, timezone: string): Promise<string> {
  const confirmation = Math.floor(Date.parse(releasedAt) / 1000) * 1000 + 1000;
  expect(Number.isFinite(confirmation)).toBe(true);
  await expect.poll(() => Date.now()).toBeGreaterThanOrEqual(confirmation);
  return companyDateTimeInput(new Date(confirmation).toISOString(), timezone).replace(/:00$/u, "");
}
