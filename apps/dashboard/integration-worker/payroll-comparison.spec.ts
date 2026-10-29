import { randomUUID } from "node:crypto";
import { type BrowserContext, expect, test } from "@playwright/test";
import { createAdminCompany } from "../integration/fixtures/admin-company";
import { signInAndEnrollAuthenticator } from "../integration/fixtures/sign-in-and-enroll-authenticator";
import { payrollRunEmployeeDto } from "../src/features/payroll/data/models/payroll-calculation-dto";
import { payrollRunComparisonDto } from "../src/features/payroll/data/models/payroll-run-comparison-dto";
import {
  accepted,
  type ComparisonActor,
  calculateComparisonPeriod,
  publishComparisonBaseline,
} from "./fixtures/payroll-comparison-periods";

test("two real worker periods preserve published tax continuity and scoped read-only browser comparison", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  test.setTimeout(180_000);
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-comparison-admin@example.invalid",
    "COMPARISON",
    "Payroll Comparison Company",
  );
  if (!baseURL) throw new Error("Integration URL required");
  const base = `/api/v1/companies/${company}`;
  const admin: ComparisonActor = {
    request: context.request,
    headers: () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() }),
  };
  const grants = [
    [
      "104",
      "preparer",
      ["company.read", "payroll.calculate", "payroll.policy.manage", "payroll.compensation.manage"],
    ],
    ["105", "reviewer", ["company.read", "payroll.review"]],
    ["106", "second-reviewer", ["company.read", "payroll.review"]],
    ["107", "finalizer", ["company.read", "payroll.finalize"]],
  ] as const;
  const owned: BrowserContext[] = [];
  try {
    const actors: ComparisonActor[] = [];
    for (const [suffix, role, permissions] of grants) {
      await accepted(
        admin.request.put(`${base}/members/a0000000-0000-4000-8000-000000000${suffix}`, {
          headers: admin.headers(),
          data: { active: true, permissions, reason: "Independent comparison fixture roles" },
        }),
      );
      const session = await browser.newContext({ baseURL });
      owned.push(session);
      const login = await session.newPage();
      await signInAndEnrollAuthenticator(login, `browser-comparison-${role}@example.invalid`);
      await login.getByRole("combobox", { name: "Company", exact: true }).selectOption(company);
      const actorCsrf = (await (
        await accepted(session.request.get("/api/v1/auth/csrf"))
      ).json()) as { headerName: string; token: string };
      actors.push({
        request: session.request,
        headers: () => ({
          [actorCsrf.headerName]: actorCsrf.token,
          "Idempotency-Key": randomUUID(),
        }),
      });
    }
    const [preparer, reviewer, second, finalizer] = actors;
    const editor = owned[0]?.pages()[0];
    if (!preparer || !reviewer || !second || !finalizer || !editor)
      throw new Error("Independent fixture actors required");
    await accepted(
      admin.request.put(`${base}/approvals/templates/${randomUUID()}`, {
        headers: admin.headers(),
        data: {
          name: "Independent comparison payroll review",
          kind: "PAYROLL",
          active: true,
          expectedVersion: null,
          effectiveFrom: "2026-01-01",
          minimumAmount: "0",
          stages: ["105", "106"].map((suffix) => ({
            assignment: "NAMED",
            accountIds: [`a0000000-0000-4000-8000-000000000${suffix}`],
          })),
          reason: "Review baseline before comparison",
        },
      }),
    );
    const employee = randomUUID();
    await accepted(
      admin.request.post(`${base}/employees`, {
        headers: admin.headers(),
        data: {
          id: employee,
          employeeNumber: "COMPARE-001",
          person: { id: randomUUID(), legalName: "Comparison Employee", nationality: "ID" },
          terms: {
            effectiveFrom: "2026-01-01",
            startDate: "2026-01-01",
            status: "ACTIVE",
            contract: "PERMANENT",
          },
          reason: "Fictional comparison employment",
        },
      }),
    );
    await accepted(
      preparer.request.put(`${base}/payroll/policy`, {
        headers: preparer.headers(),
        data: {
          expectedVersion: null,
          effectiveFrom: "2026-01",
          effectiveUntil: "2026-12",
          incomeTaxRuleId: "ID-PP58-PMK168-2024-v1",
          insuranceRuleId: "ID-BPJS-PU-v1",
          minimumMonthlyWage: "4000000",
          healthWageCap: "12000000",
          pensionWageCap: "10000000",
          contributionRounding: "HALF_UP",
          reviewReferences: ["https://example.test/comparison-policy"],
          reason: "Reviewed comparison payroll policy",
        },
      }),
    );
    const baseline = await calculateComparisonPeriod({
      base,
      employee,
      month: "2026-01",
      admin,
      preparer,
      reviewer,
      previousCompensation: false,
    });
    await publishComparisonBaseline({
      base,
      run: baseline.run.id,
      preparer,
      reviewers: [reviewer, second],
      finalizer,
    });
    const current = await calculateComparisonPeriod({
      base,
      employee,
      month: "2026-02",
      admin,
      preparer,
      reviewer,
      previousCompensation: true,
    });
    const path = `${base}/payroll/runs/${current.run.id}/comparison?baselineId=${baseline.run.id}&limit=50&changedOnly=false`;
    const compared = payrollRunComparisonDto.parse(
      await (await accepted(reviewer.request.get(path))).json(),
    );
    expect(compared.baseline.status).toBe("FINALIZED");
    expect(compared.current.status).toBe("CALCULATED");
    expect(compared.summary).toMatchObject({
      baselineEmployees: 1,
      currentEmployees: 1,
      matched: 1,
      added: 0,
      removed: 0,
      changed: 1,
      unchanged: 0,
    });
    const old = baseline.results.items[0],
      latest = current.results.items[0];
    if (!old || !latest) throw new Error("Two retained results required");
    const cents = (v: string) => {
      const [whole, fraction = ""] = v.split(".");
      return BigInt(`${whole}${fraction.padEnd(2, "0")}`);
    };
    for (const key of ["taxableGross", "withheld", "takeHome"] as const) {
      expect(cents(compared.summary.baseline[key])).toBe(cents(old[key] ?? ""));
      expect(cents(compared.summary.current[key])).toBe(cents(latest[key] ?? ""));
      expect(cents(compared.summary.delta[key])).toBe(
        cents(latest[key] ?? "") - cents(old[key] ?? ""),
      );
    }
    const detail = payrollRunEmployeeDto.parse(
      await (
        await accepted(
          preparer.request.get(`${base}/payroll/runs/${current.run.id}/employees/${employee}`),
        )
      ).json(),
    );
    expect(detail.item.target.previousAssessmentId).not.toBeNull();
    // Comparison UI must only acquire information; all setup commands above have finished.
    const writes: string[] = [],
      comparisonReads: string[] = [];
    editor.on("request", (request) => {
      if (request.url().includes(base) && !["GET", "HEAD"].includes(request.method()))
        writes.push(request.url());
      if (request.url().includes(`${base}/payroll/runs/${current.run.id}/comparison?`))
        comparisonReads.push(request.url());
    });
    await editor.goto(`/payroll/runs/${current.run.id}?company=${company}`);
    await editor.getByRole("link", { name: "Period comparison", exact: true }).click();
    await expect(editor.getByLabel("Earlier earnings month", { exact: true })).toHaveValue(
      "2026-01",
    );
    expect(comparisonReads).toHaveLength(0);
    await editor
      .getByRole("button", { name: "Compare: 2026-01 · 2026-01-31", exact: true })
      .click();
    const table = editor.getByRole("table", { name: "Employee comparison", exact: true });
    await expect(table.getByRole("row")).toHaveCount(2);
    await expect(table).toContainText("Comparison Employee");
    await editor
      .getByRole("button", { name: "Review employee: Comparison Employee", exact: true })
      .click();
    const drawer = editor.getByRole("dialog", { name: "Employee amounts", exact: true });
    await expect(drawer).toBeVisible();
    await expect(
      drawer.getByRole("link", { name: "Baseline result", exact: true }),
    ).toHaveAttribute("href", new RegExp(baseline.run.id));
    await editor.screenshot({
      path: "../../.work/dashboard-payroll-comparison-real.png",
      fullPage: true,
      animations: "disabled",
    });
    await drawer.getByRole("button", { name: "Close", exact: true }).click();
    await editor.getByRole("checkbox", { name: "Show changes only", exact: true }).click();
    await expect(
      editor.getByRole("checkbox", { name: "Show changes only", exact: true }),
    ).toBeChecked();
    await expect(table).toContainText("Comparison Employee");
    expect(writes).toEqual([]);
    expect(comparisonReads).toHaveLength(2);
    // A live grant change removes displayed money on the next explicit read.
    await accepted(
      admin.request.put(`${base}/members/a0000000-0000-4000-8000-000000000104`, {
        headers: admin.headers(),
        data: {
          expectedVersion: 0,
          active: true,
          permissions: ["company.read"],
          reason: "Withdraw comparison access",
        },
      }),
    );
    await editor.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(table).toHaveCount(0);
    await expect(editor.getByRole("table", { name: "Payroll totals", exact: true })).toHaveCount(0);
    expect((await preparer.request.get(path)).status()).toBe(403);
    expect(writes).toEqual([]);
  } finally {
    for (const session of owned.reverse()) await session.close();
  }
});
