import { randomUUID } from "node:crypto";
import { type BrowserContext, expect } from "@playwright/test";
import { z } from "zod";
import { payrollCalculationMinor } from "../../src/features/payroll/data/mappers/payroll-run-employee-mapper";
import { compensationTermsDto } from "../../src/features/payroll/data/models/compensation-dto";
import { exercisePayrollAmendmentAuthoring } from "./payroll-amendment-authoring";
import {
  exercisePayrollAmendmentCalculation,
  exercisePayrollAmendmentCancellation,
} from "./payroll-amendment-commands";
import { exercisePayrollAmendmentPublication } from "./payroll-amendment-publication-workflow";
import { exercisePayrollAmendmentRead } from "./payroll-amendment-read";
import { exercisePayrollAmendmentReviews } from "./payroll-amendment-reviews";

/** Create the prerequisite through authorized commands and the actual publication worker. */
export async function publishNegativePayrollAmendment(input: {
  admin: BrowserContext;
  author: BrowserContext;
  reviewer: BrowserContext;
  publisher: BrowserContext;
  company: string;
  assessment: string;
  month: string;
  adminHeaders: () => Record<string, string>;
}) {
  const { admin, author, reviewer, publisher, company, assessment, month, adminHeaders } = input,
    base = `/api/v1/companies/${company}`,
    authorId = "a0000000-0000-4000-8000-000000000051",
    publisherId = "a0000000-0000-4000-8000-000000000053",
    reviewerId = "a0000000-0000-4000-8000-000000000054";
  let financeVersion = 0;
  for (const [account, permissions] of [
    [authorId, ["company.read", "payroll.amend"]],
    [publisherId, ["company.read", "payroll.read", "payroll.amend.publish", "payroll.pay"]],
    [reviewerId, ["company.read", "payroll.pay", "payroll.amend.review", "approvals.read"]],
  ] as const) {
    const current = await admin.request.get(`${base}/members/${account}`);
    expect(current.status()).toBe(200);
    const grant = z
      .object({ member: z.object({ version: z.number().int().nonnegative() }) })
      .parse(await current.json());
    const updated = await admin.request.put(`${base}/members/${account}`, {
      headers: adminHeaders(),
      data: {
        expectedVersion: grant.member.version,
        active: true,
        permissions,
        reason: "Independent amendment and return acceptance",
      },
    });
    expect(updated.status()).toBe(200);
    if (account === reviewerId) financeVersion = grant.member.version + 1;
  }
  const tokenSchema = z.object({ headerName: z.string(), token: z.string() }),
    authorToken = tokenSchema.parse(await (await author.request.get("/api/v1/auth/csrf")).json()),
    reviewerToken = tokenSchema.parse(
      await (await reviewer.request.get("/api/v1/auth/csrf")).json(),
    ),
    publisherToken = tokenSchema.parse(
      await (await publisher.request.get("/api/v1/auth/csrf")).json(),
    );
  const headers = (token: z.infer<typeof tokenSchema>) => ({
    [token.headerName]: token.token,
    "Idempotency-Key": randomUUID(),
  });
  const preparationResponse = await author.request.get(
    `${base}/payroll/payslips/${assessment}/amendment-preparation`,
  );
  expect(preparationResponse.status()).toBe(200);
  const source = z.object({ assessmentId: z.uuid(), revision: z.number().int().nonnegative() }),
    preparation = z
      .object({
        firstAssessmentId: z.uuid(),
        sourceVersions: z.array(source),
        assessments: z.array(z.object({ source, compensation: compensationTermsDto })),
      })
      .parse(await preparationResponse.json());
  expect(preparation.firstAssessmentId).toBe(assessment);
  expect(preparation.assessments).toHaveLength(1);
  const original = preparation.assessments[0];
  if (!original) throw new Error("Published source required");
  const basic = payrollCalculationMinor(original.compensation.basicSalary) - 100_000_000n;
  expect(basic > 0n).toBe(true);
  const amendment = await exercisePayrollAmendmentAuthoring({
      author,
      company,
      assessment,
      month,
      basicSalary: `${basic / 100n}.${String(basic % 100n).padStart(2, "0")}`,
    }),
    draft = `${base}/payroll/amendments/${amendment}`;
  await exercisePayrollAmendmentCalculation({ author, company, amendment, month });
  const calculatedResponse = await author.request.get(draft);
  expect(calculatedResponse.status()).toBe(200);
  const retained = z
    .object({
      amendment: z.object({ summary: z.object({ version: z.number(), status: z.string() }) }),
      latestCalculation: z.object({
        id: z.uuid(),
        status: z.literal("SUCCEEDED"),
        difference: z.object({ takeHome: z.string() }),
      }),
    })
    .parse(await calculatedResponse.json());
  expect(retained.amendment.summary).toMatchObject({ version: 1, status: "CALCULATED" });
  expect(payrollCalculationMinor(retained.latestCalculation.difference.takeHome) < 0n).toBe(true);
  await exercisePayrollAmendmentRead({ author, company, amendment, month });
  await exercisePayrollAmendmentCancellation({
    author,
    company,
    amendment,
    headers: () => headers(authorToken),
  });
  const template = await admin.request.put(`${base}/approvals/templates/${randomUUID()}`, {
    headers: adminHeaders(),
    data: {
      name: "Independent payroll correction",
      kind: "PAYROLL_AMENDMENT",
      effectiveFrom: `${month}-01`,
      minimumAmount: "0",
      stages: [{ assignment: "NAMED", accountIds: [reviewerId] }],
      reason: "Independent amendment review",
    },
  });
  expect(template.status()).toBe(200);
  const approved = await exercisePayrollAmendmentReviews({
    author,
    reviewer,
    company,
    amendment,
    month,
  });
  await exercisePayrollAmendmentPublication({ publisher, company, amendment, month, approved });
  return { financeVersion, publisherHeaders: () => headers(publisherToken), reviewerToken };
}
