import { randomUUID } from "node:crypto";
import { type APIRequestContext, type APIResponse, expect } from "@playwright/test";
import {
  payrollCalculationReviewDto,
  payrollRunDetailsDto,
} from "../../src/features/payroll/data/models/payroll-calculation-dto";
import {
  payrollFinalizationDetailsDto,
  payrollFinalizationPreparationDto,
} from "../../src/features/payroll/data/models/payroll-finalization-dto";
import { payrollInputReviewDto } from "../../src/features/payroll/data/models/payroll-input-dto";
import {
  payrollReviewDetailsDto,
  payrollReviewPreparationDto,
} from "../../src/features/payroll/data/models/payroll-review-dto";
import type { WorkPeriodOverviewDto } from "../../src/features/workforce/data/models/work-period-overview-dto";
import { compensationTerms } from "../../tests/fixtures/compensations";

export type ComparisonActor = Readonly<{
  request: APIRequestContext;
  headers: () => Record<string, string>;
}>;
export async function accepted(response: Promise<APIResponse>) {
  const result = await response;
  expect(result.status(), await result.text()).toBe(200);
  return result;
}
/** Real public commands, retained worker calculations and independently verified monthly inputs. */
export async function calculateComparisonPeriod(input: {
  base: string;
  employee: string;
  month: string;
  admin: ComparisonActor;
  preparer: ComparisonActor;
  reviewer: ComparisonActor;
  previousCompensation: boolean;
}) {
  const { base, employee, month, admin, preparer, reviewer, previousCompensation } = input;
  const last = new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0))
    .toISOString()
    .slice(0, 10);
  const overview = (await (
    await accepted(admin.request.get(`${base}/workforce/periods/${month}`))
  ).json()) as WorkPeriodOverviewDto;
  await accepted(
    admin.request.post(`${base}/workforce/periods/${month}/close`, {
      headers: admin.headers(),
      data: {
        expectedVersion: overview.period?.version ?? 0,
        reason: "Close fictional comparison attendance",
      },
    }),
  );
  await expect
    .poll(
      async () => {
        const value = (await (
          await accepted(admin.request.get(`${base}/workforce/periods/${month}`))
        ).json()) as WorkPeriodOverviewDto;
        return value.period?.status;
      },
      { timeout: 30_000, intervals: [250, 500, 1000] },
    )
    .toBe("CLOSED");
  const terms = compensationTerms();
  await accepted(
    preparer.request.put(`${base}/payroll/employees/${employee}/compensation`, {
      headers: preparer.headers(),
      data: {
        expectedVersion: previousCompensation ? 0 : null,
        expectedEmploymentVersion: 0,
        effectiveFrom: month,
        terms: {
          ...terms,
          basicSalary: previousCompensation ? "12000000.02" : "10000000.01",
          tax: { ...terms.tax, verifiedOn: `${month}-01` },
        },
        reason: "Reviewed salary change between fictional periods",
      },
    }),
  );
  const inputPath = `${base}/payroll/employees/${employee}/inputs/${month}`;
  const review = payrollInputReviewDto.parse(
    await (await accepted(preparer.request.get(`${inputPath}/review`))).json(),
  );
  if (!review.source?.jobId) throw new Error("Closed attendance evidence required");
  await accepted(
    preparer.request.put(inputPath, {
      headers: preparer.headers(),
      data: {
        expectedVersion: null,
        expectedEmploymentVersion: review.employee.employmentVersion,
        workJobId: review.source.jobId,
        expectedWorkPeriodVersion: review.source.version,
        terms: {
          earnings: [],
          deductions: [],
          nonCashTaxable: "0",
          scheduledMonthUnits: null,
          dayResolutions: Array.from({ length: Number(last.slice(8, 10)) }, (_, index) => ({
            workDate: `${month}-${String(index + 1).padStart(2, "0")}`,
            portion: "FULL",
            disposition: "PAID",
            reference: "Reviewed unassigned day",
          })),
          holidayAllowance: null,
          reviewReference: "Reviewed actual closed monthly evidence",
        },
        reason: "Prepare fictional monthly inputs",
      },
    }),
  );
  await accepted(
    reviewer.request.post(`${inputPath}/verify`, {
      headers: reviewer.headers(),
      data: { expectedVersion: 0, reason: "Independent monthly input verification" },
    }),
  );
  if (!previousCompensation) {
    const opening = `${base}/payroll/employees/${employee}/tax-openings/${month.slice(0, 4)}`;
    await accepted(
      preparer.request.put(opening, {
        headers: preparer.headers(),
        data: {
          expectedVersion: null,
          expectedEmploymentVersion: 0,
          terms: {
            throughMonth: 0,
            residency: "RESIDENT",
            ptkp: "TK0",
            reference: "Verified zero history before first employment month",
            history: {
              taxableGross: "0",
              retirementContributions: "0",
              qualifiedDonations: "0",
              withheld: "0",
              employmentMonths: 0,
              previousEmployerNet: "0",
              previousEmployerWithheld: "0",
            },
          },
          reason: "Declare new employee zero tax opening",
        },
      }),
    );
    await accepted(
      reviewer.request.post(`${opening}/verify`, {
        headers: reviewer.headers(),
        data: { expectedVersion: 0, reason: "Independent verification of zero tax opening" },
      }),
    );
  }
  const period = randomUUID(),
    run = randomUUID();
  await accepted(
    preparer.request.post(`${base}/payroll/periods`, {
      headers: preparer.headers(),
      data: {
        id: period,
        earningsMonth: month,
        plannedPaymentDate: last,
        employeeIds: [employee],
        reason: "Compare two completed periods",
      },
    }),
  );
  const prepared = payrollCalculationReviewDto.parse(
    await (
      await accepted(preparer.request.get(`${base}/payroll/periods/${period}/calculation-review`))
    ).json(),
  );
  expect(prepared.availableActions).toEqual(["START"]);
  if (!prepared.source || !prepared.policy) throw new Error("Calculation evidence required");
  await accepted(
    preparer.request.post(`${base}/payroll/periods/${period}/runs`, {
      headers: preparer.headers(),
      data: {
        id: run,
        incomeDueDate: last,
        expectedPeriodVersion: prepared.period.version,
        expectedWorkPeriodVersion: prepared.source.version,
        expectedPolicyVersion: prepared.policy.version,
        reviewReference: "Independent comparison acceptance",
        reason: "Calculate retained monthly payroll",
      },
    }),
  );
  await expect
    .poll(
      async () =>
        payrollRunDetailsDto.parse(
          await (
            await accepted(preparer.request.get(`${base}/payroll/runs/${run}?limit=1`))
          ).json(),
        ).run.status,
      { timeout: 30_000, intervals: [250, 500, 1000] },
    )
    .toBe("CALCULATED");
  const details = payrollRunDetailsDto.parse(
    await (await accepted(preparer.request.get(`${base}/payroll/runs/${run}?limit=1`))).json(),
  );
  expect(details.results.items.map((item) => item.failure)).toEqual([null]);
  expect(details.run).toMatchObject({ totalEmployees: 1, processed: 1, succeeded: 1, failed: 0 });
  return details;
}

/** Publish the baseline so February consumes January's real retained tax assessment. */
export async function publishComparisonBaseline(input: {
  base: string;
  run: string;
  preparer: ComparisonActor;
  reviewers: readonly ComparisonActor[];
  finalizer: ComparisonActor;
}) {
  const { base, run, preparer, reviewers, finalizer } = input,
    runPath = `${base}/payroll/runs/${run}`,
    review = randomUUID();
  const prepared = payrollReviewPreparationDto.parse(
    await (await accepted(preparer.request.get(`${runPath}/review-preparation`))).json(),
  );
  await accepted(
    preparer.request.post(`${runPath}/reviews`, {
      headers: preparer.headers(),
      data: {
        id: review,
        expectedRunVersion: prepared.run.version,
        reason: "Review original baseline payroll",
      },
    }),
  );
  for (const reviewer of reviewers) {
    const current = payrollReviewDetailsDto.parse(
      await (await accepted(reviewer.request.get(`${base}/payroll/reviews/${review}`))).json(),
    );
    await accepted(
      reviewer.request.post(`${base}/payroll/reviews/${review}/decisions`, {
        headers: reviewer.headers(),
        data: {
          expectedVersion: current.review.version,
          expectedApprovalVersion: current.approval.version,
          decision: "APPROVE",
          reason: "Independent baseline approval",
        },
      }),
    );
  }
  const publication = payrollFinalizationPreparationDto.parse(
    await (await accepted(finalizer.request.get(`${runPath}/finalization-preparation`))).json(),
  );
  expect(publication.availableActions).toEqual(["START"]);
  if (!publication.latestReview) throw new Error("Approved baseline review required");
  const id = randomUUID();
  await accepted(
    finalizer.request.post(`${runPath}/finalizations`, {
      headers: finalizer.headers(),
      data: {
        id,
        reviewId: review,
        expectedRunVersion: publication.run.version,
        expectedReviewVersion: publication.latestReview.version,
        expectedApprovalVersion: publication.approvalVersion,
        reason: "Publish baseline for genuine tax continuity",
      },
    }),
  );
  await expect
    .poll(
      async () =>
        payrollFinalizationDetailsDto.parse(
          await (
            await accepted(finalizer.request.get(`${base}/payroll/finalizations/${id}`))
          ).json(),
        ).job.status,
      { timeout: 30_000, intervals: [250, 500, 1000] },
    )
    .toBe("SUCCEEDED");
}
