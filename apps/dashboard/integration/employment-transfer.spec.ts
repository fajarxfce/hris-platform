import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import type { EmploymentTransferInputDto } from "../src/features/people/data/models/employment-transfer-dto";
import { authenticatorCode } from "./fixtures/authenticator";

test("real cross-company transfer recovers one commit, retains person identity and moves native employee access", async ({
  page,
  context,
  request,
}) => {
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill("browser-transfer-admin@example.invalid");
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
  await expect(page.getByRole("heading", { name: "No company access", exact: true })).toBeVisible();
  const csrf = (await (await context.request.get("/api/v1/auth/csrf")).json()) as {
    headerName: string;
    token: string;
  };
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const companies: string[] = [];
  for (const [code, name] of [
    ["TRANSFERA", "Transfer Source"],
    ["TRANSFERB", "Transfer Destination"],
  ]) {
    const response = await context.request.post("/api/v1/companies", {
      headers: headers(),
      data: { code, name, timezone: "Asia/Jakarta" },
    });
    expect(response.status()).toBe(200);
    companies.push(((await response.json()) as { id: string }).id);
  }
  const source = companies[0];
  const target = companies[1];
  if (!source || !target) throw new Error("Missing transfer companies");
  const sourceBase = `/api/v1/companies/${source}`;
  const targetBase = `/api/v1/companies/${target}`;
  const account = "a0000000-0000-4000-8000-000000000010";
  for (const company of companies)
    expect(
      (
        await context.request.put(`/api/v1/companies/${company}/members/${account}`, {
          headers: headers(),
          data: {
            active: true,
            permissions: ["people.self.read"],
            reason: "Transfer employee company access",
          },
        })
      ).status(),
    ).toBe(200);
  const employee = randomUUID();
  const person = randomUUID();
  expect(
    (
      await context.request.post(`${sourceBase}/employees`, {
        headers: headers(),
        data: {
          id: employee,
          employeeNumber: "TRANSFER-001",
          person: {
            id: person,
            accountId: account,
            legalName: "Browser Transfer Employee",
            nationality: "ID",
          },
          terms: {
            effectiveFrom: "2025-01-01",
            startDate: "2025-01-01",
            status: "ACTIVE",
            contract: "PERMANENT",
          },
          reason: "Transfer browser setup",
        },
      })
    ).status(),
  ).toBe(200);
  const branch = randomUUID();
  expect(
    (
      await context.request.put(`${targetBase}/organization-units/${branch}`, {
        headers: headers(),
        data: {
          code: "DEST",
          name: "Destination Branch",
          kind: "BRANCH",
          timezone: "Asia/Jakarta",
        },
      })
    ).status(),
  ).toBe(200);
  const native = await request.post("/api/v1/auth/native/login", {
    headers: { "Idempotency-Key": randomUUID() },
    data: {
      email: "browser-transfer-employee@example.invalid",
      password: "Browser-fixture-password-123!",
      deviceName: "Transfer browser integration",
    },
  });
  expect(native.status()).toBe(200);
  const { credentials: tokens } = (await native.json()) as {
    credentials: { accessToken: string; sessionId: string };
  };
  const nativeHeaders = {
    Authorization: `Bearer ${tokens.accessToken}`,
    "X-HRIS-Client-Platform": "ANDROID",
    "X-HRIS-Client-Build": "1",
  };
  try {
    const before = await request.get(`${sourceBase}/me/employments`, { headers: nativeHeaders });
    expect(before.status()).toBe(200);
    expect(await before.json()).toMatchObject({ employments: { items: [{ id: employee }] } });
    await page.goto("/");
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(source);
    await page.getByRole("link", { name: "Employees", exact: true }).click();
    await page
      .getByRole("button", {
        name: "View details: Browser Transfer Employee (TRANSFER-001)",
        exact: true,
      })
      .click();
    await page.getByRole("link", { name: "Transfer employee", exact: true }).click();
    await page
      .getByRole("combobox", { name: "Destination company", exact: true })
      .selectOption(target);
    await expect(page.getByLabel("Transfer date", { exact: true })).toBeVisible();
    const date = await page.getByLabel("Transfer date", { exact: true }).inputValue();
    expect(date).toMatch(/^\d{4}-\d{2}-\d{2}$/u);
    await page.getByLabel("Destination employee number", { exact: true }).fill("DEST-001");
    await page.getByRole("button", { name: "Choose Branch", exact: true }).click();
    await page
      .getByRole("dialog", { name: "Choose Branch", exact: true })
      .getByRole("button", { name: "Select: DEST · Destination Branch", exact: true })
      .click();
    await page.getByLabel("Reason", { exact: true }).fill("Approved intercompany placement");
    const resource = `${sourceBase}/employees/${employee}/transfer`;
    const commands: { operation: string | undefined; body: EmploymentTransferInputDto }[] = [];
    await page.route(`**${resource}`, async (route) => {
      commands.push({
        operation: route.request().headers()["idempotency-key"],
        body: route.request().postDataJSON() as EmploymentTransferInputDto,
      });
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      if (commands.length === 1) return route.abort("failed");
      return route.fulfill({ response });
    });
    await page.getByRole("button", { name: "Review transfer", exact: true }).click();
    const confirmation = page.getByRole("dialog", { name: "Confirm transfer", exact: true });
    await expect(confirmation).toContainText("Browser Transfer Employee → Transfer Destination");
    await confirmation.getByRole("button", { name: "Transfer employee", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("The transfer outcome is not confirmed");
    await page.getByRole("button", { name: "Check transfer", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("Employee transferred.");
    expect(commands).toHaveLength(2);
    expect(commands[0]).toEqual(commands[1]);
    const destination = commands[0]?.body.targetEmploymentId;
    if (!destination) throw new Error("Missing destination employment");
    expect(commands[0]?.body).toMatchObject({
      targetCompanyId: target,
      expectedVersion: 0,
      terms: { startDate: date, effectiveFrom: date, branchId: branch },
    });
    await page.unroute(`**${resource}`);
    const history = await context.request.get(`${sourceBase}/employees/${employee}/transfers`);
    expect(history.status()).toBe(200);
    const records = (await history.json()) as {
      targetEmploymentId: string;
      sourceVersion: number;
      effectiveDate: string;
    }[];
    expect(records).toHaveLength(1);
    expect(records[0]).toMatchObject({
      targetEmploymentId: destination,
      sourceVersion: 1,
      effectiveDate: date,
    });
    const old = await context.request.get(`${sourceBase}/employees/${employee}?asOf=${date}`);
    expect(old.status()).toBe(200);
    expect(await old.json()).toMatchObject({ version: 1, terms: { status: "ENDED" } });
    const profile = await context.request.get(`${targetBase}/employees/${destination}/profile`);
    expect(profile.status()).toBe(200);
    expect(await profile.json()).toMatchObject({
      personId: person,
      accountId: account,
      ownerCompanyId: source,
      version: 0,
    });
    expect(
      (await request.get(`${sourceBase}/me/employments`, { headers: nativeHeaders })).status(),
    ).toBe(403);
    const relocated = await request.get(`${targetBase}/me/employments`, { headers: nativeHeaders });
    expect(relocated.status()).toBe(200);
    expect(await relocated.json()).toMatchObject({
      employments: { items: [{ id: destination, employeeNumber: "DEST-001" }], nextCursor: null },
    });
    await page.getByRole("link", { name: "View transfer history", exact: true }).click();
    await expect(page.getByRole("table")).toContainText("Transferred out");
    await expect(page.getByRole("table")).toContainText("TRANSFERB · Transfer Destination");
    await expect(page.getByRole("table")).toContainText("Approved intercompany placement");
  } finally {
    if (!page.isClosed())
      expect(
        (
          await request.delete(`/api/v1/auth/native/sessions/${tokens.sessionId}`, {
            headers: nativeHeaders,
          })
        ).status(),
      ).toBe(204);
  }
});
