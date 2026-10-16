import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { createAdminCompany } from "./fixtures/admin-company";

test("new company assignment recovers its original receipt and enables only the selected company's native access", async ({
  page,
  context,
  request,
}) => {
  const { company, csrf } = await createAdminCompany(
    page,
    context,
    "browser-assignment-admin@example.invalid",
    "BROWSERASSIGN",
    "Browser account assignment",
  );
  await page.getByRole("combobox", { name: "Bahasa", exact: true }).selectOption("en");
  const member = "a0000000-0000-4000-8000-000000000070";
  const base = `/api/v1/companies/${company}`;
  const memberPath = `${base}/members/${member}`;
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const roleId = randomUUID();
  const role = await context.request.put(`${base}/role-templates/${roleId}`, {
    headers: headers(),
    data: {
      code: "EMPLOYEE",
      name: "Employee access",
      active: true,
      permissions: ["company.read", "people.self.read"],
      reason: "Initial employee role",
    },
  });
  expect(role.status()).toBe(200);

  // This separate fixture session has no administrator cookie or initial membership.
  const native = await request.post("/api/v1/auth/native/login", {
    headers: { "Idempotency-Key": randomUUID() },
    data: {
      email: "browser-assignment-member@example.invalid",
      password: "Browser-fixture-password-123!",
      deviceName: "Company assignment integration",
    },
  });
  expect(native.status()).toBe(200);
  const { credentials } = (await native.json()) as {
    credentials: { accessToken: string; sessionId: string };
  };
  const nativeHeaders = {
    Authorization: `Bearer ${credentials.accessToken}`,
    "X-HRIS-Client-Platform": "ANDROID",
    "X-HRIS-Client-Build": "1",
  };
  try {
    expect((await request.get(`${base}/me/access`, { headers: nativeHeaders })).status()).toBe(403);
    await page.goto(`/administration/members?company=${company}`);
    await page.getByRole("link", { name: "Add account", exact: true }).click();
    await page
      .getByRole("searchbox", { name: "Find an account", exact: true })
      .fill("browser-assignment-member@example.invalid");
    await page.getByRole("button", { name: "Search", exact: true }).click();
    await page
      .getByRole("button", { name: "Choose account: Browser Assignment Member", exact: true })
      .click();
    await page.getByRole("button", { name: "Add role", exact: true }).click();
    const picker = page.getByRole("dialog", { name: "Choose role", exact: true });
    await picker
      .getByRole("button", { name: "Use this version: Employee access (EMPLOYEE)", exact: true })
      .click();
    await expect(picker).toHaveCount(0);
    await page.getByLabel("Reason", { exact: true }).fill("Reviewed new company membership");

    const commands: { operation: string | undefined; body: unknown }[] = [];
    await page.route(`**${memberPath}`, async (route) => {
      if (route.request().method() !== "PUT") return route.fallback();
      commands.push({
        operation: route.request().headers()["idempotency-key"],
        body: route.request().postDataJSON(),
      });
      const response = await route.fetch();
      expect(response.status()).toBe(200);
      if (commands.length === 1) return route.abort("failed");
      return route.fulfill({ response });
    });
    await page.getByRole("button", { name: "Grant access", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("The save outcome is unconfirmed.");
    await page.getByRole("button", { name: "Check save outcome", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("Company access saved.");
    expect(commands).toHaveLength(2);
    expect(commands[0]?.operation).toMatch(/^[0-9a-f-]{36}$/u);
    expect(commands[0]).toEqual(commands[1]);
    expect(commands[0]?.body).toEqual({
      active: true,
      expectedVersion: null,
      permissions: [],
      roleTemplates: [{ id: roleId, version: 0, retainApplied: false }],
      reason: "Reviewed new company membership",
    });
    await page.unroute(`**${memberPath}`);
    const retained = await context.request.get(memberPath);
    expect(retained.status()).toBe(200);
    const grant = await retained.json();
    expect(grant).toMatchObject({
      member: { id: member, active: true, version: 0 },
      directPermissions: [],
      roleTemplates: [{ id: roleId, version: 0, name: "Employee access" }],
    });
    expect(grant.member.permissions.toSorted()).toEqual(["company.read", "people.self.read"]);
    await page.getByRole("link", { name: "View access", exact: true }).click();
    await expect(
      page.getByRole("heading", { name: "Browser Assignment Member", exact: true }),
    ).toBeVisible();
    await page.goto(`/administration/members/new/${member}?company=${company}`);
    await expect(
      page.getByRole("link", { name: "View existing access", exact: true }),
    ).toBeVisible();

    const accessed = await request.get(`${base}/me/access`, { headers: nativeHeaders });
    expect(accessed.status()).toBe(200);
    expect((await accessed.json()).permissions.toSorted()).toEqual([
      "company.read",
      "people.self.read",
    ]);
    const ownEmployment = await request.get(`${base}/me/employments`, { headers: nativeHeaders });
    expect(ownEmployment.status()).toBe(200);
    expect(await ownEmployment.json()).toMatchObject({ employments: { items: [] } });

    const another = await context.request.post("/api/v1/companies", {
      headers: headers(),
      data: { code: "ASSIGNOTHER", name: "Other assignment company", timezone: "Asia/Jakarta" },
    });
    expect(another.status()).toBe(200);
    const other = ((await another.json()) as { id: string }).id;
    expect(
      (
        await request.get(`/api/v1/companies/${other}/me/access`, { headers: nativeHeaders })
      ).status(),
    ).toBe(403);
  } finally {
    expect(
      (
        await request.delete(`/api/v1/auth/native/sessions/${credentials.sessionId}`, {
          headers: nativeHeaders,
        })
      ).status(),
    ).toBe(204);
  }
});
