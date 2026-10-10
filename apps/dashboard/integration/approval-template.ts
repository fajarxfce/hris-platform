import { type BrowserContext, expect, type Page } from "@playwright/test";

/** Runs against the authenticated API fixture and real company permissions. */
export async function reviewApprovalTemplate(
  page: Page,
  context: BrowserContext,
  companies: readonly string[],
) {
  await page.getByRole("link", { name: "Approval templates", exact: true }).click();
  await page.getByRole("link", { name: "Create approval template", exact: true }).click();
  await page.getByLabel("Name", { exact: true }).fill("Browser expense approval");
  await page.getByRole("combobox", { name: "Type", exact: true }).selectOption("EXPENSE");
  await page.getByLabel("Effective from", { exact: true }).fill("2026-01-01");
  await page.getByLabel("Minimum amount", { exact: true }).fill("1234567890123456.78");
  await page.getByLabel("Assignment", { exact: true }).selectOption("NAMED");
  await page.getByRole("button", { name: "Add approver", exact: true }).click();
  const picker = page.getByRole("dialog", { name: "Select approver", exact: true });
  await picker
    .getByRole("button", { name: /^Select: /u })
    .first()
    .click();
  await page.getByLabel("Reason", { exact: true }).fill("Browser approval setup");
  await page.getByRole("button", { name: "Save template", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Template saved.");
  await page.getByRole("link", { name: "View template", exact: true }).click();
  const id = new URL(page.url()).pathname.split("/").at(-1);
  const path = `/api/v1/companies/${companies[1]}/approvals/templates/${id}`;
  const response = await context.request.get(path);
  expect(response.status()).toBe(200);
  expect(await response.json()).toMatchObject({
    version: 0,
    appliedRevision: 0,
    kind: "EXPENSE",
    minimumAmount: "1234567890123456.78",
    stages: [{ assignment: "NAMED", permission: null }],
  });
  await page.getByRole("link", { name: "Edit current template", exact: true }).click();
  await page.getByLabel("Name", { exact: true }).fill("Browser revised approval");
  await page.getByLabel("Minimum amount", { exact: true }).fill("5000.00");
  await page.getByLabel("Effective from", { exact: true }).fill("2026-02-01");
  await page.getByLabel("Reason", { exact: true }).fill("Browser threshold review");
  await page.getByRole("button", { name: "Save template", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Template saved.");
  await page.getByRole("link", { name: "View template", exact: true }).click();
  await page.getByRole("link", { name: "Previous revision", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Browser revised approval", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("region", { name: "Template details", exact: true })).toContainText(
    "1234567890123456.78",
  );
  const old = await context.request.get(`${path}?revision=0`);
  expect(await old.json()).toMatchObject({
    version: 1,
    appliedRevision: 0,
    minimumAmount: "1234567890123456.78",
  });
  expect(
    (
      await context.request.get(`/api/v1/companies/${companies[0]}/approvals/templates/${id}`)
    ).status(),
  ).toBe(404);
  await page.getByRole("link", { name: "Overview", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Overview", exact: true })).toBeVisible();
}
