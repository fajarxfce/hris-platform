import { expect, type Page, test } from "@playwright/test";
import { companyIds, installIdentityApi } from "./identity-api";

async function workspace(page: Page) {
  const identity = await installIdentityApi(page, {
    signedIn: true,
    mfa: true,
    mfaConfigured: true,
    mfaVerified: true,
  });
  const requests: string[] = [];
  await page.route("**/api/v1/companies/*/organization-units?**", async (route) => {
    requests.push(route.request().url());
    const company = new URL(route.request().url()).pathname.split("/")[4];
    await route.fulfill({
      json: {
        items: [
          {
            id: "80000000-0000-4000-8000-000000000001",
            code: "RND",
            name: company === companyIds[0] ? "North research" : "South research",
            kind: "DEPARTMENT",
            parentId: null,
            timezone: null,
            active: true,
            version: 0,
          },
        ],
        nextCursor: null,
      },
    });
  });
  await page.goto("/organization/units");
  await expect(page.getByRole("table")).toContainText("North research");
  const field = page.getByLabel("Name or code", { exact: true });
  await field.fill("Unsubmitted search");
  await field.evaluate((input) => input.setAttribute("data-retention-probe", "retained"));
  return { identity, requests, field };
}

test("foreground and reconnect checks preserve a mounted draft after both identity and access succeed", async ({
  page,
}) => {
  const f = await workspace(page);
  const reads = f.requests.length;
  let release!: () => void;
  let entered!: () => void;
  const held = new Promise<void>((done) => {
    release = done;
  });
  const started = new Promise<void>((done) => {
    entered = done;
  });
  await page.route(`**/api/v1/companies/${companyIds[0]}/me/access`, async (route) => {
    entered();
    await held;
    await route.fulfill({
      json: { companyId: companyIds[0], permissions: ["people.read", "company.read"] },
    });
  });
  try {
    await page.evaluate(() => window.dispatchEvent(new Event("online")));
    await started;
    await expect(f.field).toBeHidden();
    await expect(f.field).toHaveValue("Unsubmitted search");
    await expect(f.field).toHaveAttribute("data-retention-probe", "retained");
    release();
    await expect(f.field).toBeVisible();
    await expect(f.field).toHaveValue("Unsubmitted search");
    expect(f.requests).toHaveLength(reads);
    expect(f.identity.commands).toHaveLength(0);
  } finally {
    release();
  }
});

test("transient session failures keep the form hidden and retry only identity reads", async ({
  page,
}) => {
  const f = await workspace(page);
  const reads = f.requests.length;
  await page.route(
    "**/api/v1/me",
    (route) =>
      route.fulfill({ status: 503, json: { code: "connection_unavailable", detail: "PRIVATE" } }),
    { times: 1 },
  );
  await page.evaluate(() => document.dispatchEvent(new Event("visibilitychange")));
  await expect(page.getByRole("alert")).toContainText("Unable to connect");
  await expect(f.field).toBeHidden();
  await expect(f.field).toHaveValue("Unsubmitted search");
  await page.getByRole("button", { name: "Retry", exact: true }).click();
  await expect(f.field).toBeVisible();
  await expect(f.field).toHaveValue("Unsubmitted search");
  await expect(f.field).toHaveAttribute("data-retention-probe", "retained");
  expect(f.requests).toHaveLength(reads);
  expect(f.identity.commands).toHaveLength(0);
});

test("required MFA retains hidden fields through invalid codes and waits for the live company check", async ({
  page,
}) => {
  const f = await workspace(page);
  const reads = f.requests.length;
  f.identity.expireMfa();
  await page.evaluate(() => window.dispatchEvent(new Event("online")));
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  await expect(dialog).toBeVisible();
  await expect(f.field).toBeHidden();
  await expect(f.field).toHaveValue("Unsubmitted search");
  await expect(dialog.getByRole("button", { name: "Cancel", exact: true })).toHaveCount(0);
  await page.keyboard.press("Escape");
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("000000");
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog.getByRole("alert")).toContainText("Enter a valid verification code");
  await expect(dialog.getByLabel("Authenticator code", { exact: true })).toHaveValue("");
  let release!: () => void;
  let entered!: () => void;
  const held = new Promise<void>((done) => {
    release = done;
  });
  const started = new Promise<void>((done) => {
    entered = done;
  });
  await page.route(`**/api/v1/companies/${companyIds[0]}/me/access`, async (route) => {
    entered();
    await held;
    await route.fulfill({
      json: { companyId: companyIds[0], permissions: ["company.read", "people.read"] },
    });
  });
  try {
    await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
    await dialog.getByRole("button", { name: "Verify", exact: true }).click();
    await started;
    await expect(dialog).toBeVisible();
    await expect(f.field).toBeHidden();
    release();
    await expect(dialog).toHaveCount(0);
    await expect(f.field).toBeVisible();
    await expect(f.field).toHaveValue("Unsubmitted search");
    await expect(f.field).toHaveAttribute("data-retention-probe", "retained");
    expect(f.requests).toHaveLength(reads);
    expect(
      f.identity.commands.filter((command) => command.path.endsWith("/mfa/verify")),
    ).toHaveLength(2);
  } finally {
    release();
  }
});

test("optional verification can be cancelled after a fresh check and restores keyboard focus", async ({
  page,
}) => {
  const f = await workspace(page);
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("console", (message) => {
    if (/Keyborg|Tabster|incorrect.*dispos/iu.test(message.text())) errors.push(message.text());
  });
  const verify = page
    .getByRole("banner")
    .getByRole("button", { name: "Account verification", exact: true });
  for (const dismiss of ["button", "escape", "button"]) {
    await verify.focus();
    await page.keyboard.press("Enter");
    const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
    await expect(dialog.getByLabel("Authenticator code", { exact: true })).toBeVisible();
    await expect(f.field).toBeHidden();
    if (dismiss === "escape") await page.keyboard.press("Escape");
    else await dialog.getByRole("button", { name: "Cancel", exact: true }).click();
    await expect(dialog).toHaveCount(0);
    await expect(verify).toBeFocused();
    await expect(f.field).toHaveValue("Unsubmitted search");
    await expect(f.field).toHaveAttribute("data-retention-probe", "retained");
  }
  expect(f.identity.commands).toHaveLength(0);
  expect(errors).toEqual([]);
});

test("a lost verification response can be checked without submitting the proof again", async ({
  page,
}) => {
  const f = await workspace(page);
  let attempts = 0;
  await page.route("**/api/v1/auth/mfa/verify", async (route) => {
    attempts += 1;
    f.identity.renewMfa();
    await route.abort("failed");
  });
  await page
    .getByRole("banner")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
  await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await dialog.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(dialog.getByRole("alert")).toContainText("Unable to connect");
  await dialog.getByRole("button", { name: "Check session", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(
    page.getByRole("banner").getByRole("button", { name: "Account verification", exact: true }),
  ).toBeFocused();
  await expect(f.field).toHaveValue("Unsubmitted search");
  await expect(f.field).toHaveAttribute("data-retention-probe", "retained");
  expect(attempts).toBe(1);
});

for (const change of ["permission", "membership", "credentials"]) {
  test(`a ${change} change during verification removes the former form and its data`, async ({
    page,
  }) => {
    const f = await workspace(page);
    f.identity.expireMfa();
    await page.evaluate(() => window.dispatchEvent(new Event("online")));
    const dialog = page.getByRole("dialog", { name: "Account verification", exact: true });
    await expect(dialog).toBeVisible();
    if (change === "permission") f.identity.setPermissions(["people.read"]);
    if (change === "membership") f.identity.denyAccess("company_access_denied");
    if (change === "credentials") f.identity.revoke();
    await dialog.getByLabel("Authenticator code", { exact: true }).fill("123456");
    await dialog.getByRole("button", { name: "Verify", exact: true }).click();
    await expect(dialog).toHaveCount(0);
    await expect(page.locator('[data-retention-probe="retained"]')).toHaveCount(0);
    await expect(page.getByRole("main")).not.toContainText("North research");
    if (change === "permission") {
      await expect(page.getByRole("alert")).toContainText("You do not have access");
      await expect(page.getByLabel("Name or code", { exact: true })).toHaveValue("");
    } else if (change === "membership") {
      await expect(page.getByRole("alert")).toContainText("Company access is unavailable");
    } else {
      await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
    }
    expect(f.identity.unhandled).toEqual([]);
  });
}
