import { type BrowserContext, expect } from "@playwright/test";
import { z } from "zod";

/** Correct a fictional closed day through its current review and public command. */
export async function recordClosedAttendanceAdjustment(input: {
  operator: BrowserContext;
  company: string;
  employee: string;
  workDate: string;
  absence: boolean;
  headers: () => Record<string, string>;
}) {
  const { operator, company, employee, workDate, absence, headers } = input,
    base = `/api/v1/companies/${company}/workforce/employees/${employee}/attendance/adjustments`;
  const review = await operator.request.get(`${base}/review?workDate=${workDate}`);
  expect(review.status()).toBe(200);
  const observed = z.object({ revision: z.string().min(1) }).parse(await review.json());
  const response = await operator.request.post(base, {
    headers: headers(),
    data: {
      workDate,
      reviewRevision: observed.revision,
      clockIn: absence ? null : `${workDate}T01:00:00Z`,
      clockOut: absence ? null : `${workDate}T09:00:00Z`,
      breakMinutes: absence ? 0 : 60,
      decisions: [],
      reason: absence ? "Reviewed absence after closing" : "Reviewed worked day after closing",
    },
  });
  expect(response.status()).toBe(200);
  return z.object({ id: z.uuid(), version: z.number().int().min(1) }).parse(await response.json());
}
