import { randomUUID } from "node:crypto";
import { expect, type Page, test } from "@playwright/test";
import { authenticatorCode } from "./fixtures/authenticator";
import { createLeaveScenario } from "./fixtures/leave-scenario";

async function signInWithAuthenticator(page: Page, email: string) {
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Password", { exact: true }).fill("Browser-fixture-password-123!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.getByRole("button", { name: "Set up an authenticator", exact: true }).click();
  const key = page.locator(".app-enrollment-key code");
  await expect(key).toBeVisible();
  await page
    .getByLabel("Authenticator code", { exact: true })
    .fill(authenticatorCode((await key.textContent()) ?? ""));
  await page.getByRole("button", { name: "Verify", exact: true }).click();
  await page.getByRole("button", { name: "I have saved the codes", exact: true }).click();
}

async function confirm(page: Page, label: string, reason: string) {
  await page.getByRole("link", { name: label, exact: true }).click();
  await expect(page.getByRole("heading", { name: label, exact: true })).toBeVisible();
  await page.getByLabel("Action reason", { exact: true }).fill(reason);
  await page.getByRole("button", { name: "Confirm action", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Action recorded.");
  await page.getByRole("link", { name: "View current request", exact: true }).click();
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toBeVisible();
}

test("real API leave decisions recover receipts and preserve staged reservations, cancellation and withdrawal", async ({
  page,
  context,
  browser,
  baseURL,
}) => {
  // Two independent MFA sessions and staged workflow reviews share a finite scenario budget.
  test.setTimeout(120_000);
  await signInWithAuthenticator(page, "browser-leave-admin@example.invalid");
  await expect(page.getByRole("heading", { name: "No company access", exact: true })).toBeVisible();
  const csrf = (await (await context.request.get("/api/v1/auth/csrf")).json()) as {
    headerName: string;
    token: string;
  };
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const created = await context.request.post("/api/v1/companies", {
    headers: headers(),
    data: { code: "LEAVEBROWSER", name: "Browser Leave", timezone: "Asia/Jakarta" },
  });
  expect(created.status()).toBe(200);
  const company = ((await created.json()) as { id: string }).id;
  const reviewer = "a0000000-0000-4000-8000-000000000004";
  const grant = await context.request.put(`/api/v1/companies/${company}/members/${reviewer}`, {
    headers: headers(),
    data: {
      active: true,
      permissions: ["leave.approve", "leave.read", "approvals.read"],
      reason: "Independent leave review",
    },
  });
  expect(grant.status()).toBe(200);
  const scenario = await createLeaveScenario(context, company, reviewer, 2, true);
  const detail = `/leave/requests/${scenario.request}?company=${company}`;
  const resource = `${scenario.base}/leave/requests/${scenario.request}`;
  const balancePath = `${scenario.base}/leave/employees/${scenario.employee}/balances/${scenario.type}/${scenario.date.slice(0, 4)}`;
  await page.goto(detail);
  await expect(page.getByRole("region", { name: "Request details", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Approve leave", exact: true })).toHaveCount(0);
  expect(
    (
      await context.request.post(`${resource}/decisions`, {
        headers: headers(),
        data: { version: 0, decision: "APPROVE", reason: "Maker cannot approve" },
      })
    ).status(),
  ).toBe(403);
  if (!baseURL) throw new Error("Integration base URL is required");
  const reviewerContext = await browser.newContext({ baseURL });
  try {
    const reviewPage = await reviewerContext.newPage();
    await signInWithAuthenticator(reviewPage, "browser-leave-reviewer@example.invalid");
    await reviewPage.goto(detail);
    await reviewPage.getByRole("link", { name: "Approve leave", exact: true }).click();
    await reviewPage.getByLabel("Action reason", { exact: true }).fill("First stage reviewed");
    const commands: { operation: string | undefined; body: unknown }[] = [];
    await reviewPage.route(
      `**/api/v1/companies/${company}/leave/requests/${scenario.request}/decisions`,
      async (route) => {
        commands.push({
          operation: route.request().headers()["idempotency-key"],
          body: route.request().postDataJSON(),
        });
        const response = await route.fetch();
        expect(response.status()).toBe(200);
        expect(await response.json()).toMatchObject({ id: scenario.request, version: 1 });
        if (commands.length === 1) return route.abort("failed");
        return route.fulfill({ response });
      },
    );
    await reviewPage.getByRole("button", { name: "Confirm action", exact: true }).click();
    await expect(reviewPage.getByRole("status")).toContainText("The result is unconfirmed");
    await reviewPage.getByRole("button", { name: "Retry action", exact: true }).click();
    await expect(reviewPage.getByRole("status")).toContainText("Action recorded.");
    expect(commands).toHaveLength(2);
    expect(commands[0]).toEqual(commands[1]);
    await reviewPage.unroute(
      `**/api/v1/companies/${company}/leave/requests/${scenario.request}/decisions`,
    );
    expect(await (await context.request.get(resource)).json()).toMatchObject({
      version: 1,
      status: "PENDING",
    });
    expect(await (await context.request.get(balancePath)).json()).toMatchObject({
      balance: { availableDays: "1.5", reservedDays: "0.5", consumedDays: "0" },
    });
    await reviewPage.getByRole("link", { name: "View current request", exact: true }).click();
    await confirm(reviewPage, "Approve leave", "Final stage reviewed");
    expect(await (await context.request.get(resource)).json()).toMatchObject({
      version: 2,
      status: "APPROVED",
    });
    expect(await (await context.request.get(balancePath)).json()).toMatchObject({
      balance: { availableDays: "1.5", reservedDays: "0", consumedDays: "0.5" },
    });

    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await confirm(page, "Request cancellation", "Plans changed");
    await reviewPage.getByRole("button", { name: "Refresh", exact: true }).click();
    await confirm(reviewPage, "Reject cancellation", "Leave still required");
    expect(await (await context.request.get(resource)).json()).toMatchObject({
      version: 4,
      status: "APPROVED",
    });
    expect(await (await context.request.get(balancePath)).json()).toMatchObject({
      balance: { consumedDays: "0.5" },
    });

    await page.getByRole("button", { name: "Refresh", exact: true }).click();
    await confirm(page, "Request cancellation", "Revised plans");
    await reviewPage.getByRole("button", { name: "Refresh", exact: true }).click();
    await confirm(reviewPage, "Approve cancellation", "Cancellation reviewed");
    const completed = (await (await context.request.get(resource)).json()) as {
      status: string;
      version: number;
      history: { items: { kind: string }[] };
    };
    expect(completed).toMatchObject({ version: 6, status: "CANCELLED" });
    expect(completed.history.items).toHaveLength(7);
    expect(completed.history.items.filter((item) => item.kind === "DECIDED")).toHaveLength(4);
    expect(await (await context.request.get(balancePath)).json()).toMatchObject({
      balance: { availableDays: "2", reservedDays: "0", consumedDays: "0" },
    });

    // Balance review uses its own scoped API; a reviewer needs no policy-management grant.
    await reviewPage
      .getByRole("link", { name: `Balance ledger · ${scenario.date.slice(0, 4)}`, exact: true })
      .click();
    const balanceSummary = reviewPage.getByRole("region", { name: "Current balance", exact: true });
    await expect(balanceSummary).toContainText("Browser annual leave");
    const movementTable = reviewPage.getByRole("table", {
      name: "Balance movements (Asia/Jakarta)",
      exact: true,
    });
    await expect(movementTable.getByRole("row")).toHaveCount(5);
    await movementTable.getByRole("button", { name: /^View movement: Refund/ }).click();
    await expect(
      reviewPage.getByRole("region", { name: "Selected movement", exact: true }),
    ).toBeFocused();
    await expect(
      reviewPage.getByRole("link", { name: "View leave request", exact: true }),
    ).toHaveAttribute("href", `${detail}&employee=${scenario.employee}`);
    await reviewPage.getByRole("link", { name: "Back to balances", exact: true }).click();
    const balanceTable = reviewPage.getByRole("table", { name: "Leave balances", exact: true });
    await expect(balanceTable.getByRole("row")).toHaveCount(2);
    await expect(balanceTable.getByRole("row").nth(1)).toContainText("Browser annual leave");
    await balanceTable
      .getByRole("button", { name: "View ledger: Browser annual leave", exact: true })
      .click();
    await expect(balanceSummary).toBeVisible();

    const next = randomUUID();
    expect(
      (
        await context.request.post(`${scenario.base}/leave/requests`, {
          headers: headers(),
          data: {
            id: next,
            employeeId: scenario.employee,
            typeId: scenario.type,
            days: [{ workDate: scenario.date, portion: "FIRST_HALF" }],
            reason: "Replacement request",
          },
        })
      ).status(),
    ).toBe(200);
    await page.goto(`/leave/requests/${next}?company=${company}`);
    await confirm(page, "Withdraw request", "Request no longer needed");
    expect(
      await (await context.request.get(`${scenario.base}/leave/requests/${next}`)).json(),
    ).toMatchObject({ version: 1, status: "CANCELLED" });
    expect(await (await context.request.get(balancePath)).json()).toMatchObject({
      balance: { availableDays: "2", reservedDays: "0", consumedDays: "0" },
    });
  } finally {
    await reviewerContext.close();
  }
});
