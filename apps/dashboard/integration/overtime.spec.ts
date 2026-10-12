import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import type { OvertimeDetailsDto } from "../src/features/workforce/data/models/overtime-details-dto";
import { createAdminCompany } from "./fixtures/admin-company";
import { createOvertimeScenario } from "./fixtures/overtime-scenario";
import { signInAndEnrollAuthenticator } from "./fixtures/sign-in-and-enroll-authenticator";

test("overtime review preserves original calendar evidence and independent request access through the real API", async ({
  page,
  context,
  browser,
  request,
}) => {
  const { company } = await createAdminCompany(
    page,
    context,
    "browser-overtime-admin@example.invalid",
    "BROWSER_OVERTIME",
    "Browser Overtime",
  );
  const scenario = await createOvertimeScenario(context, company);
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  const query = `company=${company}&from=${scenario.workDate}&until=${scenario.workDate}`;
  await page.goto(`/workforce/overtime?${query}`);
  const directory = page.getByRole("table", { name: "Overtime requests", exact: true });
  await expect(directory).toContainText("Overtime Fixture Employee");
  await expect(directory).toContainText("Pending review");
  await directory.getByRole("button").click();
  const overview = page.getByRole("region", { name: "Overview", exact: true });
  await expect(overview).toContainText("Overtime Fixture Employee");
  await expect(page.getByRole("region", { name: "Original schedule", exact: true })).toContainText(
    "Day off",
  );
  const records = page.getByRole("table", { name: "Time records", exact: true });
  await expect(records.getByRole("row").filter({ hasText: "Requested" })).toContainText("180");
  await expect(records.getByRole("row").filter({ hasText: "Actual" })).toContainText("135");
  await page.getByRole("button", { name: "View change: 0", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Change details", exact: true });
  await expect(panel).toContainText("Inventory count on the original day off");
  await panel.getByRole("button", { name: "Close", exact: true }).click();
  const selfApproval = await context.request.post(`${scenario.overtimePath}/decisions`, {
    headers: scenario.headers(),
    data: { expectedVersion: 1, decision: "APPROVE", reason: "Maker cannot approve" },
  });
  expect(selfApproval.status()).toBe(403);
  expect(await selfApproval.json()).toMatchObject({ code: "self_approval_denied" });

  const reviewer = await browser.newContext({ baseURL: new URL(page.url()).origin });
  try {
    const review = await reviewer.newPage();
    await signInAndEnrollAuthenticator(review, "browser-overtime-reviewer@example.invalid");
    await review.goto(`/approvals?company=${company}`);
    await review
      .getByRole("table", { name: "Approval inbox", exact: true })
      .getByRole("button")
      .click();
    await review.getByRole("link", { name: "View source request", exact: true }).click();
    await expect(review.getByRole("region", { name: "Overview", exact: true })).toContainText(
      "Overtime Fixture Employee",
    );
    await expect(
      review.getByRole("link", { name: "Back to approvals", exact: true }),
    ).toBeVisible();
    await expect(
      review.getByRole("navigation").getByRole("link", { name: "Overtime", exact: true }),
    ).toHaveCount(0);
    expect(
      (
        await reviewer.request.get(
          `${scenario.planPath}?from=${scenario.workDate}&until=${scenario.workDate}`,
        )
      ).status(),
    ).toBe(403);
    const csrf = (await (await reviewer.request.get("/api/v1/auth/csrf")).json()) as {
      headerName: string;
      token: string;
    };
    const headers = { [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() };
    const decision = { expectedVersion: 1, decision: "APPROVE", reason: "Independently verified" };
    const accepted = await reviewer.request.post(`${scenario.overtimePath}/decisions`, {
      headers,
      data: decision,
    });
    expect(accepted.status()).toBe(200);
    const receipt: unknown = await accepted.json();
    const replay = await reviewer.request.post(`${scenario.overtimePath}/decisions`, {
      headers,
      data: decision,
    });
    expect(replay.status()).toBe(200);
    expect(await replay.json()).toEqual(receipt);
    await review.getByRole("button", { name: "Refresh", exact: true }).click();
    await expect(review.getByRole("region", { name: "Overview", exact: true })).toContainText(
      "Approved",
    );
    await expect(
      review.getByRole("table", { name: "History", exact: true }).getByRole("row"),
    ).toHaveCount(4);
    await review.screenshot({
      path: "../../.work/dashboard-overtime-real-api.png",
      fullPage: true,
      animations: "disabled",
    });
  } finally {
    await reviewer.close();
  }

  const current = await context.request.get(scenario.overtimePath);
  expect(current.status()).toBe(200);
  const confirmed = (await current.json()) as OvertimeDetailsDto;
  expect(confirmed.request).toMatchObject({
    status: "APPROVED",
    approvedMinutes: 135,
    version: 2,
    requested: { workedMinutes: 180 },
    actual: { workedMinutes: 135 },
    schedule: { kind: "OFF", holidayId: scenario.holiday },
  });
  expect(confirmed.history.items).toHaveLength(3);
  for (const [path, key, data, expected] of [
    [scenario.planPath, scenario.planKey, scenario.plan, scenario.plannedReceipt],
    [
      `${scenario.overtimePath}/actual`,
      scenario.actualKey,
      scenario.actual,
      scenario.submittedReceipt,
    ],
  ] as const) {
    const replay = await context.request.post(path, {
      headers: { ...scenario.headers(), "Idempotency-Key": key },
      data,
    });
    expect(replay.status()).toBe(200);
    expect(await replay.json()).toEqual(expected);
  }
  const native = await request.post("/api/v1/auth/native/login", {
    headers: { "Idempotency-Key": randomUUID() },
    data: {
      email: "browser-overtime-employee@example.invalid",
      password: "Browser-fixture-password-123!",
      deviceName: "Overtime browser acceptance",
    },
  });
  expect(native.status()).toBe(200);
  const { credentials } = (await native.json()) as { credentials: { accessToken: string } };
  const nativeHeaders = {
    Authorization: `Bearer ${credentials.accessToken}`,
    "X-HRIS-Client-Platform": "ANDROID",
    "X-HRIS-Client-Build": "1",
  };
  const owned = await request.get(scenario.overtimePath, { headers: nativeHeaders });
  expect(owned.status()).toBe(200);
  expect(((await owned.json()) as OvertimeDetailsDto).request.approvedMinutes).toBe(135);
  const other = await context.request.post("/api/v1/companies", {
    headers: scenario.headers(),
    data: {
      code: "BROWSER_OVERTIME_OTHER",
      name: "Other Overtime Company",
      timezone: "Asia/Jakarta",
    },
  });
  expect(other.status()).toBe(200);
  const otherId = ((await other.json()) as { id: string }).id;
  expect(
    (
      await context.request.get(`/api/v1/companies/${otherId}/workforce/overtime/${scenario.id}`)
    ).status(),
  ).toBe(404);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(overview).toContainText("Approved");
  await expect(
    page.getByRole("table", { name: "History", exact: true }).getByRole("row"),
  ).toHaveCount(4);
});
