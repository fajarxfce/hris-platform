import { expect, type Page, test } from "@playwright/test";
import { installExpenseDraftsApi } from "./expense-drafts-api";
import { expenseIdentifier } from "./fixtures/expense-claims";
import { companyIds } from "./identity-api";

const query = `company=${companyIds[0]}&from=2026-10-01&until=2026-10-31`,
  directory = `/expenses/claims?${query}`,
  editor = `/expenses/claims/new?${query}`;
async function choose(page: Page, number = 1) {
  await page.getByRole("button", { name: `Select: North Employee ${number}`, exact: true }).click();
  await page.getByLabel("Claim title", { exact: true }).fill("October client travel");
  await page.getByLabel("Description", { exact: true }).fill("Client office visit");
  await page.getByLabel("Change note", { exact: true }).fill("Travel recorded");
}
async function addItem(page: Page, receiptCount = 1) {
  await page.getByRole("button", { name: "Add item", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Item details", exact: true });
  await panel.getByLabel("Expense date", { exact: true }).fill("2026-10-12");
  await panel.getByLabel("Amount (IDR)", { exact: true }).fill("125000.50");
  await panel.getByLabel("Item description", { exact: true }).fill("Train ticket");
  await panel.getByRole("button", { name: "Select category", exact: true }).click();
  await panel.getByRole("button", { name: "Select: Business travel", exact: true }).click();
  await expect(panel.getByLabel("Item description", { exact: true })).toHaveValue("Train ticket");
  await panel.getByRole("button", { name: "Select cost center", exact: true }).click();
  await panel.getByRole("button", { name: "Select: Operations", exact: true }).click();
  for (let index = 1; index <= receiptCount; index++) {
    await panel.getByRole("button", { name: "Add receipt", exact: true }).click();
    await panel.getByRole("button", { name: `Select: receipt-${index}.pdf`, exact: true }).click();
  }
  if (receiptCount === 3)
    await expect(panel.getByRole("button", { name: "Add receipt", exact: true })).toBeDisabled();
  await panel.getByRole("button", { name: "Apply item", exact: true }).click();
  await expect(panel).toHaveCount(0);
}
test("selects an employee across bounded pages, stages references and saves exact reviewed items once", async ({
  page,
}) => {
  const api = await installExpenseDraftsApi(page);
  await page.goto(directory);
  await page.getByRole("button", { name: "New expense claim", exact: true }).click();
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await choose(page, 21);
  await addItem(page, 3);
  await expect(page.getByRole("table", { name: "Expense items", exact: true })).toContainText(
    "125,000.50",
  );
  await page
    .getByRole("heading", { name: "New expense claim", exact: true })
    .scrollIntoViewIfNeeded();
  await page.screenshot({
    path: "../../.work/dashboard-expense-draft.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(api.draftingReads.filter((row) => row.kind === "employees")).toHaveLength(2);
  expect(
    api.draftingReads.find((row) => row.kind === "categories")?.url.searchParams.get("asOf"),
  ).toBe("2026-10-12");
  const held = api.holdWrite();
  try {
    await page.getByRole("button", { name: "Save draft", exact: true }).click();
    await held.started;
    await expect(page.getByRole("button", { name: "Save draft", exact: true })).toBeDisabled();
    expect(api.writes).toHaveLength(1);
    expect(api.writes[0]?.body).toMatchObject({
      employmentId: expenseIdentifier(120),
      expectedVersion: null,
      title: "October client travel",
      lines: [
        {
          amount: "125000.50",
          receiptRevisionIds: [
            expenseIdentifier(6001),
            expenseIdentifier(6002),
            expenseIdentifier(6003),
          ],
        },
      ],
    });
    held.release();
    await expect(page.getByRole("status")).toContainText("Draft saved.");
    await page.getByRole("link", { name: "View claim", exact: true }).click();
    await expect(
      page.getByRole("heading", { name: "October client travel", exact: true }),
    ).toBeVisible();
    expect(api.commits).toBe(1);
    expect(api.identity.unhandled).toEqual([]);
  } finally {
    held.release();
  }
});
test("employee search is explicit and a draft can be saved before adding items", async ({
  page,
}) => {
  const api = await installExpenseDraftsApi(page);
  await page.goto(editor);
  await page.getByLabel("Search name or employee number", { exact: true }).fill("EMP-004");
  expect(api.draftingReads).toHaveLength(1);
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await choose(page, 4);
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Draft saved.");
  expect(api.writes[0]?.body.lines).toEqual([]);
});
test("returned-claim corrections preserve employee, item and submission identity", async ({
  page,
}) => {
  const api = await installExpenseDraftsApi(page, { returned: true });
  await page.goto(`/expenses/claims/${expenseIdentifier(1)}?${query}`);
  await page.getByRole("link", { name: "Edit draft", exact: true }).click();
  await expect(
    page.getByText("This claim was returned. Save your corrections before submitting again.", {
      exact: true,
    }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Edit item: Return train ticket", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Item details", exact: true });
  await expect(panel).toContainText("receipt-1.pdf");
  await panel.getByLabel("Amount (IDR)", { exact: true }).fill("149999.25");
  await panel.getByRole("button", { name: "Apply item", exact: true }).click();
  await page.getByLabel("Change note", { exact: true }).fill("Corrected fare per review");
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Draft saved.");
  expect(api.writes[0]?.body).toMatchObject({
    employmentId: expenseIdentifier(100),
    expectedVersion: 2,
    lines: [{ id: expenseIdentifier(3001), amount: "149999.25" }],
  });
  const saved = api.records.get(companyIds[0])?.[0];
  expect(saved?.claim).toMatchObject({
    version: 3,
    draftRevision: 1,
    status: "DRAFT",
    submissionCount: 1,
    latestSubmissionId: expenseIdentifier(1001),
  });
});
test("unknown references remain visible and their IDs are retained until explicitly replaced", async ({
  page,
}) => {
  const api = await installExpenseDraftsApi(page),
    line = api.records.get(companyIds[0])?.[0]?.claim.draft.lines[0];
  expect(line).toBeDefined();
  if (!line) throw new Error("Fixture item is missing");
  line.categoryId = expenseIdentifier(499);
  line.receiptRevisionIds = [expenseIdentifier(6999)];
  await page.goto(`/expenses/claims/${expenseIdentifier(1)}/edit?${query}`);
  await expect(page.getByRole("table")).toContainText("Unavailable category");
  await page.getByRole("button", { name: "Edit item: Return train ticket", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Item details", exact: true });
  await expect(panel).toContainText("Unavailable receipt");
  await panel.getByRole("button", { name: "Cancel", exact: true }).click();
  await page.getByLabel("Change note", { exact: true }).fill("Retained for correction");
  api.rejectWrite("expense_category_unavailable");
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("selected category is unavailable");
  expect(api.writes[0]?.body.lines[0]).toMatchObject({
    categoryId: expenseIdentifier(499),
    receiptRevisionIds: [expenseIdentifier(6999)],
  });
});
test("an unconfirmed save survives MFA and later rejection with the same command", async ({
  page,
}) => {
  const api = await installExpenseDraftsApi(page);
  await page.goto(editor);
  await choose(page);
  await addItem(page);
  api.loseNext();
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  const retry = page.getByRole("button", { name: "Retry original save", exact: true });
  await expect(retry).toBeVisible();
  api.identity.expireMfa();
  api.rejectWrite("mfa_required", 403);
  await retry.click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("North Employee");
  await expect(page.getByLabel("Claim title", { exact: true })).toHaveCount(0);
  api.rejectWrite("stale_version");
  await retry.click();
  await expect(retry).toBeVisible();
  await retry.click();
  await expect(page.getByRole("status")).toContainText("Draft saved.");
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(4);
  for (const write of api.writes) expect(write).toEqual(api.writes[0]);
});
test("competing edits require protected reload before saving at the new version", async ({
  page,
}) => {
  const api = await installExpenseDraftsApi(page);
  await page.goto(`/expenses/claims/${expenseIdentifier(1)}/edit?${query}`);
  await page.getByLabel("Claim title", { exact: true }).fill("My unsaved title");
  await page.getByLabel("Change note", { exact: true }).fill("My edit");
  const record = api.records.get(companyIds[0])?.[0];
  if (!record) throw new Error("Fixture claim is missing");
  record.claim.version = 1;
  record.claim.draftRevision = 1;
  record.claim.draft.revision = 1;
  record.claim.draft.title = "Saved by colleague";
  record.history.items.unshift({
    version: 1,
    draftRevision: 1,
    kind: "DRAFT_SAVED",
    status: "DRAFT",
    actorId: record.claim.createdBy,
    reason: "Other edit",
    recordedAt: "2026-10-13T06:00:00Z",
    submissionId: null,
  });
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("button", { name: "Save draft", exact: true })).toBeDisabled();
  await expect(page.getByLabel("Claim title", { exact: true })).toHaveValue("My unsaved title");
  await page.getByRole("button", { name: "Reload current draft", exact: true }).click();
  await page.getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(page.getByLabel("Claim title", { exact: true })).toHaveValue("My unsaved title");
  await page.getByRole("button", { name: "Reload current draft", exact: true }).click();
  await page.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Claim title", { exact: true })).toHaveValue("Saved by colleague");
  await page.getByLabel("Change note", { exact: true }).fill("Updated current revision");
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Draft saved.");
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([0, 1]);
});
test("one navigation guard retains uncommitted item input and discards only after confirmation", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  const api = await installExpenseDraftsApi(page);
  await page.goto(directory);
  await page.getByRole("button", { name: "New expense claim", exact: true }).click();
  await choose(page);
  await page.getByRole("button", { name: "Add item", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Item details", exact: true });
  await panel.getByLabel("Item description", { exact: true }).fill("Uncommitted item");
  await page.goBack();
  await page.getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(panel.getByLabel("Item description", { exact: true })).toHaveValue(
    "Uncommitted item",
  );
  await panel.getByRole("button", { name: "Cancel", exact: true }).click();
  await page.goBack();
  await page.getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page).toHaveURL(directory);
  expect(api.writes).toHaveLength(0);
  expect(errors).toEqual([]);
});
test("denied reference discovery hides the employee and item panel", async ({ page }) => {
  const api = await installExpenseDraftsApi(page);
  await page.goto(editor);
  await choose(page);
  await addItem(page);
  api.rejectDraftRead("receipts", "expense_access_denied");
  await page.getByRole("button", { name: "Edit item: Train ticket", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Item details", exact: true })
    .getByRole("button", { name: "Add receipt", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("main")).not.toContainText("North Employee");
  await expect(page.getByRole("main")).not.toContainText("Train ticket");
  await expect(page.getByRole("main")).not.toContainText("PRIVATE EMPLOYEE DATA");
  expect(api.writes).toHaveLength(0);
});
test("late discovery cannot cross a company change", async ({ page }) => {
  const api = await installExpenseDraftsApi(page),
    held = api.holdDraftRead("employees");
  try {
    await page.goto(editor);
    await held.started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table", { name: "Expense claims", exact: true })).toContainText(
      "South",
    );
    held.release();
    await expect(page.getByRole("main")).not.toContainText("North Employee");
    expect(api.writes).toHaveLength(0);
  } finally {
    held.release();
  }
});
test("authoring requires manager scope before employee metadata acquisition", async ({ page }) => {
  const api = await installExpenseDraftsApi(page, { permissions: ["expenses.read"] });
  await page.goto(editor);
  await expect(page.getByRole("alert")).toBeVisible();
  expect(api.draftingReads).toHaveLength(0);
  await expect(page.getByLabel("Claim title", { exact: true })).toHaveCount(0);
});
test("item editing retains input through language, dark appearance and narrow layout", async ({
  page,
}) => {
  const api = await installExpenseDraftsApi(page);
  await page.goto(editor);
  await choose(page);
  await addItem(page);
  await page.setViewportSize({ width: 760, height: 900 });
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByLabel("Judul klaim", { exact: true })).toHaveValue(
    "October client travel",
  );
  await page.getByRole("button", { name: "Edit item: Train ticket", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "Detail item", exact: true });
  await expect(panel.getByLabel("Jumlah (IDR)", { exact: true })).toHaveValue("125000.50");
  await page.screenshot({
    path: "../../.work/dashboard-expense-draft-narrow.png",
    fullPage: true,
    animations: "disabled",
  });
  await panel.getByRole("button", { name: "Hapus receipt: receipt-1.pdf", exact: true }).click();
  await panel.getByRole("button", { name: "Hapus pilihan", exact: true }).click();
  await panel.getByRole("button", { name: "Terapkan item", exact: true }).click();
  await page.getByRole("button", { name: "Simpan draft", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Draft tersimpan.");
  expect(api.writes[0]?.body.lines[0]).toMatchObject({
    receiptRevisionIds: [],
    costCenterId: null,
  });
});
