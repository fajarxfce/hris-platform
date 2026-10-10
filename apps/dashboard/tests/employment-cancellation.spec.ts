import { expect, type Page, test } from "@playwright/test";
import { employmentId, installEmploymentApi } from "./employment-api";
import { companyIds } from "./identity-api";

const historyPath = `/people/employees/${employmentId}?company=${companyIds[0]}&asOf=2026-10-01&tab=history`;
const cancellationPath = (revision = "7") =>
  `/people/employees/${employmentId}/revisions/${revision}/cancel?company=${companyIds[0]}&asOf=2026-10-01`;
const cancel = (page: Page) => page.getByRole("button", { name: "Cancel revision", exact: true });
const departure = (page: Page) =>
  page.getByRole("dialog", { name: "Leave this page?", exact: true });
async function open(page: Page) {
  await page.goto(cancellationPath());
  await expect(page.getByRole("region", { name: "Revision", exact: true })).toContainText(
    "Existing scheduled revision",
  );
  await page.getByLabel("Cancellation reason", { exact: true }).fill("Schedule no longer required");
}
test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date("2026-10-01T00:00:00Z"));
});

test("a scheduled revision opens a localized cancellation review and retains immutable evidence after confirmation", async ({
  page,
}) => {
  const api = await installEmploymentApi(page);
  await page.goto(historyPath);
  const history = page.getByRole("table", { name: "Employment history", exact: true });
  await history.getByRole("button", { name: "View details: 7", exact: true }).click();
  await page
    .getByRole("dialog", { name: "Revision details", exact: true })
    .getByRole("link", { name: "Review cancellation", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Cancel employment revision", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("main")).toContainText("Current company date: 2026-10-01");
  await page.getByLabel("Cancellation reason", { exact: true }).fill("Schedule no longer required");
  await page.screenshot({
    path: "../../.work/dashboard-employment-cancellation-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Theme", exact: true }).click();
  await page.screenshot({
    path: "../../.work/dashboard-employment-cancellation-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await expect(page.getByLabel("Alasan pembatalan", { exact: true })).toHaveValue(
    "Schedule no longer required",
  );
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.screenshot({
    path: "../../.work/dashboard-employment-cancellation-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Batalkan revisi", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Revisi employment dibatalkan.");
  expect(api.cancellations).toHaveLength(1);
  expect(api.cancellations[0]).toMatchObject({
    revision: 7,
    body: { expectedVersion: 7, reason: "Schedule no longer required" },
  });
  expect(api.commits).toBe(1);
  await page.getByRole("link", { name: "Lihat riwayat", exact: true }).click();
  const restored = page.getByRole("table", { name: "Riwayat employment", exact: true });
  await expect(restored.getByRole("row")).toHaveCount(3);
  await restored.getByRole("button", { name: "Lihat detail: 7", exact: true }).click();
  const evidence = page.getByRole("dialog", { name: "Detail revisi", exact: true });
  await expect(evidence).toContainText("Existing scheduled revision");
  await expect(evidence).toContainText("Schedule no longer required");
  await expect(evidence.getByRole("link", { name: "Tinjau pembatalan", exact: true })).toHaveCount(
    0,
  );
});

test("an unknown cancellation survives MFA and already-cancelled replies until the original receipt is recovered", async ({
  page,
}) => {
  const api = await installEmploymentApi(page);
  await open(page);
  api.loseNext();
  await cancel(page).click();
  await expect(page.getByRole("status")).toContainText("The cancellation result is not confirmed");
  await expect(page.getByLabel("Cancellation reason", { exact: true })).toHaveAttribute(
    "readonly",
    "",
  );
  api.identity.expireMfa();
  api.rejectNext("mfa_required");
  await page.getByRole("button", { name: "Retry cancellation", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(page.getByLabel("Cancellation reason", { exact: true })).toHaveValue(
    "Schedule no longer required",
  );
  expect(api.cancellations).toHaveLength(2);
  api.rejectNext("revision_already_cancelled");
  await page.getByRole("button", { name: "Retry cancellation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The cancellation result is not confirmed");
  await page.getByRole("button", { name: "Retry cancellation", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Employment revision cancelled.");
  expect(api.cancellations).toHaveLength(4);
  expect(
    api.cancellations.every(
      (write) =>
        write.operation === api.cancellations[0]?.operation &&
        JSON.stringify(write.body) === JSON.stringify(api.cancellations[0]?.body),
    ),
  ).toBe(true);
  expect(api.cancellations[3]?.csrf).not.toBe(api.cancellations[0]?.csrf);
  expect(api.commits).toBe(1);
  api.failReads("connection_unavailable");
  await page.getByRole("link", { name: "View history", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Unable to connect");
  expect(api.cancellations).toHaveLength(4);
});

test("a definite conflict requires protected reload and a new observed version", async ({
  page,
}) => {
  const api = await installEmploymentApi(page);
  await open(page);
  api.advanceVersion();
  await cancel(page).click();
  await expect(page.getByRole("alert")).toContainText("This record has changed");
  await expect(cancel(page)).toBeDisabled();
  await page.getByRole("button", { name: "Reload revision", exact: true }).click();
  await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(page.getByLabel("Cancellation reason", { exact: true })).toHaveValue(
    "Schedule no longer required",
  );
  await page.getByRole("button", { name: "Reload revision", exact: true }).click();
  await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Cancellation reason", { exact: true })).toHaveValue("");
  await page.getByLabel("Cancellation reason", { exact: true }).fill("Reviewed current schedule");
  await cancel(page).click();
  await expect(page.getByRole("status")).toContainText("Employment revision cancelled.");
  expect(api.cancellations.map((write) => write.body.expectedVersion)).toEqual([7, 9]);
  expect(api.commits).toBe(1);
});

test("cancellation obeys server date and availability rather than the historical view date or browser clock", async ({
  page,
}) => {
  const api = await installEmploymentApi(page);
  await page.goto(cancellationPath("0"));
  await expect(page.getByRole("status")).toContainText("This revision cannot be cancelled");
  await expect(cancel(page)).toHaveCount(0);
  api.setCompanyDate("2027-06-01");
  await page.goto(cancellationPath());
  await expect(page.getByRole("status")).toContainText("This revision cannot be cancelled");
  await expect(page.getByLabel("Cancellation reason", { exact: true })).toHaveCount(0);
  expect(api.cancellations).toHaveLength(0);
  api.identity.setPermissions(["people.read"]);
  const count = api.reads.length;
  await page.reload();
  await expect(page.getByRole("alert")).toContainText("You do not have access");
  expect(api.reads).toHaveLength(count);
});

test("local reason validation and definite graph rejection retain the proposed reason", async ({
  page,
}) => {
  const api = await installEmploymentApi(page);
  await open(page);
  await page.getByLabel("Cancellation reason", { exact: true }).fill(" ");
  await cancel(page).click();
  await expect(page.getByRole("alert")).toContainText("Enter a reason");
  expect(api.cancellations).toHaveLength(0);
  await page.getByLabel("Cancellation reason", { exact: true }).fill("Schedule no longer required");
  api.rejectNext("reporting_cycle_or_depth");
  await cancel(page).click();
  await expect(page.getByRole("alert")).toContainText("reporting cycle");
  await expect(page.getByText("PRIVATE TECHNICAL ERROR", { exact: true })).toHaveCount(0);
  await expect(page.getByLabel("Cancellation reason", { exact: true })).toHaveValue(
    "Schedule no longer required",
  );
  await expect(cancel(page)).toBeEnabled();
  expect(api.commits).toBe(0);
});

test("a pending cancellation cannot restore the former company after confirmed departure", async ({
  page,
}) => {
  const api = await installEmploymentApi(page);
  await open(page);
  const held = api.holdNext("save");
  try {
    await cancel(page).click();
    await held.entered;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
    held.release();
    await expect(page.getByRole("heading", { name: "Employees", exact: true })).toBeVisible();
    await expect(page.getByRole("table")).toContainText("Bayu Selatan");
    await expect(page.getByLabel("Cancellation reason", { exact: true })).toHaveCount(0);
    await expect(page.getByText("Employment revision cancelled.", { exact: true })).toHaveCount(0);
    expect(api.cancellations).toHaveLength(1);
  } finally {
    held.release();
  }
});

test("revoked credentials clear an uncertain cancellation and its reason", async ({ page }) => {
  const api = await installEmploymentApi(page);
  await open(page);
  api.loseNext();
  await cancel(page).click();
  await expect(page.getByRole("status")).toContainText("The cancellation result is not confirmed");
  api.identity.revoke();
  api.rejectNext("session_revoked");
  await page.getByRole("button", { name: "Retry cancellation", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
  await expect(page.getByLabel("Cancellation reason", { exact: true })).toHaveCount(0);
  expect(api.cancellations).toHaveLength(2);
  expect(api.commits).toBe(1);
});
