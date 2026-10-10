import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { authenticatorCode } from "./fixtures/authenticator";

test("real account binding recovers one receipt and enables only the linked native employee profile", async ({
  page,
  context,
  request,
}) => {
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill("browser-binding-admin@example.invalid");
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
  const created = await context.request.post("/api/v1/companies", {
    headers: headers(),
    data: { code: "BINDINGBROWSER", name: "Browser Account Binding", timezone: "Asia/Jakarta" },
  });
  expect(created.status()).toBe(200);
  const company = ((await created.json()) as { id: string }).id;
  const base = `/api/v1/companies/${company}`;
  const employee = randomUUID();
  const person = randomUUID();
  const colleague = randomUUID();
  const account = "a0000000-0000-4000-8000-000000000008";
  for (const entry of [
    { id: employee, person, number: "BINDING-EMPLOYEE", name: "Browser Linked Employee" },
    { id: colleague, person: randomUUID(), number: "BINDING-COLLEAGUE", name: "Browser Colleague" },
  ])
    expect(
      (
        await context.request.post(`${base}/employees`, {
          headers: headers(),
          data: {
            id: entry.id,
            employeeNumber: entry.number,
            person: { id: entry.person, legalName: entry.name, nationality: "ID" },
            terms: {
              effectiveFrom: "2025-01-01",
              startDate: "2025-01-01",
              status: "ACTIVE",
              contract: "PERMANENT",
            },
            reason: "Account binding browser setup",
          },
        })
      ).status(),
    ).toBe(200);
  expect(
    (
      await context.request.put(`${base}/members/${account}`, {
        headers: headers(),
        data: { active: true, permissions: ["people.self.read"], reason: "Employee self access" },
      })
    ).status(),
  ).toBe(200);

  // Native credentials belong to a separate request context; no administrator cookie is shared.
  const native = await request.post("/api/v1/auth/native/login", {
    headers: { "Idempotency-Key": randomUUID() },
    data: {
      email: "browser-binding-employee@example.invalid",
      password: "Browser-fixture-password-123!",
      deviceName: "Employee browser integration",
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
    const directory = await request.get(`${base}/me/employments`, { headers: nativeHeaders });
    expect(directory.status()).toBe(200);
    expect(await directory.json()).toMatchObject({ employments: { items: [], nextCursor: null } });
    const profile = `${base}/employees/${employee}/profile`;
    expect((await request.get(profile, { headers: nativeHeaders })).status()).toBe(404);
    await page.goto(`/people/employees/${employee}/profile?company=${company}`);
    await page.getByRole("link", { name: "Link account", exact: true }).click();
    await expect(
      page
        .getByRole("region", { name: "Available accounts", exact: true })
        .getByText("Browser Binding Admin", { exact: true }),
    ).toHaveCount(0);
    await page
      .getByRole("button", { name: "Select account: Browser Binding Employee", exact: true })
      .click();
    await page
      .getByLabel("Reason for linking", { exact: true })
      .fill("Identity reviewed independently");
    const commands: { operation: string | undefined; body: unknown }[] = [];
    const resource = `${base}/employees/${employee}/account-link`;
    await page.route(`**${resource}`, async (route) => {
      commands.push({
        operation: route.request().headers()["idempotency-key"],
        body: route.request().postDataJSON(),
      });
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      if (commands.length === 1) return route.abort("failed");
      return route.fulfill({ response });
    });
    await page.getByRole("button", { name: "Link account", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("The binding outcome is not confirmed");
    await page.getByRole("button", { name: "Check binding", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("Account linked.");
    expect(commands).toHaveLength(2);
    expect(commands[0]).toEqual(commands[1]);
    await page.unroute(`**${resource}`);
    expect(await (await context.request.get(profile)).json()).toMatchObject({
      personId: person,
      accountId: account,
      version: 1,
    });
    const history = (await (await context.request.get(`${profile}/history`)).json()) as {
      items: { accountId: string | null; reason: string }[];
    };
    expect(history.items).toHaveLength(2);
    expect(history.items[0]?.accountId).toBeNull();
    expect(history.items[1]).toMatchObject({
      accountId: account,
      reason: "Identity reviewed independently",
    });
    const linked = await request.get(`${base}/me/employments`, { headers: nativeHeaders });
    expect(linked.status()).toBe(200);
    const self = (await linked.json()) as {
      employments: { items: { id: string }[]; nextCursor: string | null };
    };
    expect(self.employments.items.map((item) => item.id)).toEqual([employee]);
    expect(self.employments.nextCursor).toBeNull();
    expect((await request.get(profile, { headers: nativeHeaders })).status()).toBe(200);
    const scopedDirectory = await request.get(`${base}/employees?asOf=2025-01-01`, {
      headers: nativeHeaders,
    });
    expect(scopedDirectory.status()).toBe(200);
    const scopedEmployees = (await scopedDirectory.json()) as { items: { id: string }[] };
    expect(scopedEmployees.items.map((item) => item.id)).toEqual([employee]);
    expect(
      (
        await request.get(`${base}/employees/${colleague}/profile`, { headers: nativeHeaders })
      ).status(),
    ).toBe(404);
    expect(await (await context.request.get(`${base}/me/employments`)).json()).toMatchObject({
      employments: { items: [] },
    });
    await page.getByRole("link", { name: "View profile", exact: true }).click();
    await expect(page.getByRole("region", { name: "Personal data", exact: true })).toContainText(
      account,
    );
    await expect(page.getByRole("link", { name: "Link account", exact: true })).toHaveCount(0);
  } finally {
    expect(
      (
        await request.delete(`/api/v1/auth/native/sessions/${tokens.sessionId}`, {
          headers: nativeHeaders,
        })
      ).status(),
    ).toBe(204);
  }
});
