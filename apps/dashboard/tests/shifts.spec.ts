import { expect, type Page, test } from "@playwright/test";
import { companyIds } from "./identity-api";
import { dayShiftId, installShiftsApi } from "./shifts-api";

async function createForm(page: Page) {
  await page.goto("/workforce/shifts");
  await page.getByRole("link", { name: "Create shift", exact: true }).click();
  await page.getByLabel("Code", { exact: true }).fill(" night_n ");
  await page.getByLabel("Name", { exact: true }).fill("Night coverage");
  await page.getByLabel("Reason", { exact: true }).fill("Coverage setup");
}
const departure = (page: Page) =>
  page.getByRole("dialog", { name: "Leave this page?", exact: true });

test("shift pages paginate and preserve edits across language, theme and mobile layout", async ({
  page,
}) => {
  const api = await installShiftsApi(page, { many: true });
  await page.goto("/workforce/shifts");
  await expect(page.getByRole("table").getByRole("row")).toHaveCount(51);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(page.getByRole("table")).toContainText("Shift 56");
  expect(api.reads.at(-1)?.searchParams.get("after")).toBeTruthy();
  const reads = api.reads.length;
  await page.getByLabel("Name or code", { exact: true }).fill("North day");
  expect(api.reads).toHaveLength(reads);
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("table").getByRole("row")).toHaveCount(2);
  await page.getByRole("link", { name: "Create shift", exact: true }).click();
  await page.getByLabel("Code", { exact: true }).fill(" night_n ");
  await page.getByLabel("Name", { exact: true }).fill("Night coverage");
  await page.getByLabel("Start time", { exact: true }).fill("22:00");
  await page.getByLabel("End time", { exact: true }).fill("06:00");
  await page.getByLabel("Break (minutes)", { exact: true }).fill("30");
  await page.getByLabel("Work mode", { exact: true }).selectOption("FIELD");
  await page.getByLabel("Location required", { exact: true }).selectOption("true");
  await page.getByLabel("Restrict to an area", { exact: true }).selectOption("true");
  await page.getByLabel("Latitude", { exact: true }).fill("-6.2");
  await page.getByLabel("Longitude", { exact: true }).fill("106.8");
  await page.getByLabel("Radius (m)", { exact: true }).fill("200");
  await page.getByLabel("Reason", { exact: true }).fill("Night coverage setup");
  await page.screenshot({ path: "../../.work/dashboard-shift-light.png", fullPage: true });
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByLabel("Nama", { exact: true })).toHaveValue("Night coverage");
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  await page.screenshot({ path: "../../.work/dashboard-shift-dark-mobile.png", fullPage: true });
  await page.getByRole("button", { name: "Simpan", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Shift tersimpan.");
  expect(api.writes).toHaveLength(1);
  expect(api.commits).toBe(1);
  expect(api.writes[0]?.body).toMatchObject({
    code: "NIGHT_N",
    startsAt: "22:00",
    endsAt: "06:00",
    breakMinutes: 30,
    mode: "FIELD",
    expectedVersion: null,
    fence: { latitude: -6.2, longitude: 106.8, radiusMeters: 200 },
  });
  expect(api.writes[0]?.csrf).toMatch(/^fixture-csrf-/u);
  await page.getByRole("link", { name: "Lihat detail", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Night coverage", exact: true })).toBeVisible();
  expect(api.identity.unhandled).toHaveLength(0);
});

test("an unknown shift save survives MFA and replays only the original request", async ({
  page,
}) => {
  const api = await installShiftsApi(page);
  await createForm(page);
  api.loseNextSave();
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save outcome is not confirmed");
  await expect(page.getByLabel("Name", { exact: true })).toHaveJSProperty("readOnly", true);
  api.identity.expireMfa();
  api.rejectNextSave("mfa_required");
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await page
    .getByRole("alert")
    .getByRole("button", { name: "Account verification", exact: true })
    .click();
  const verification = page.getByRole("dialog", { name: "Account verification", exact: true });
  await verification.getByLabel("Authenticator code", { exact: true }).fill("123456");
  await verification.getByRole("button", { name: "Verify", exact: true }).click();
  await expect(verification).toHaveCount(0);
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("Night coverage");
  expect(api.writes).toHaveLength(2);
  await page.getByRole("button", { name: "Retry save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Shift saved.");
  expect(api.commits).toBe(1);
  expect(api.writes).toHaveLength(3);
  for (const write of api.writes) {
    expect(write.operation).toBe(api.writes[0]?.operation);
    expect(write.id).toBe(api.writes[0]?.id);
    expect(write.body).toEqual(api.writes[0]?.body);
  }
  await expect(page.getByText("PRIVATE TECHNICAL ERROR", { exact: true })).toHaveCount(0);
});

test("reload discards confirmed edits even when the server values are unchanged and resolves version conflicts", async ({
  page,
}) => {
  const api = await installShiftsApi(page);
  await page.goto(`/workforce/shifts/${dayShiftId}/edit`);
  await page.getByLabel("Name", { exact: true }).fill("Draft");
  await page.getByRole("button", { name: "Reload latest", exact: true }).click();
  await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("Draft");
  await page.getByRole("button", { name: "Reload latest", exact: true }).click();
  await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("North day");
  await page.getByLabel("Name", { exact: true }).fill("My revision");
  await page.getByLabel("Reason", { exact: true }).fill("Update coverage");
  api.replaceShift("Concurrent revision", 3);
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("This record has changed");
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("My revision");
  await page.getByRole("button", { name: "Reload latest", exact: true }).click();
  await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("Concurrent revision");
  await page.getByLabel("Status", { exact: true }).selectOption("false");
  await page.getByLabel("Reason", { exact: true }).fill("Deactivate template");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Shift saved.");
  expect(api.writes.map((write) => write.body.expectedVersion)).toEqual([2, 3]);
  expect(api.writes[1]?.body.active).toBe(false);
  expect(api.writes[0]?.operation).not.toBe(api.writes[1]?.operation);
});

test("a pending save protects company departure and cannot restore the prior workspace after disposal", async ({
  page,
}) => {
  const api = await installShiftsApi(page);
  await createForm(page);
  const pending = api.holdSave();
  try {
    await page.getByRole("button", { name: "Save", exact: true }).click();
    await pending.started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(departure(page)).toBeVisible();
    await departure(page).getByRole("button", { name: "Stay on this page", exact: true }).click();
    await expect(page.getByRole("combobox", { name: "Company", exact: true })).toHaveValue(
      companyIds[0],
    );
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await departure(page).getByRole("button", { name: "Leave page", exact: true }).click();
    pending.release();
    await expect(page.getByRole("table")).toContainText("South day");
    await expect(page.getByRole("status").filter({ hasText: "Shift saved." })).toHaveCount(0);
    expect(api.writes).toHaveLength(1);
    expect(api.writes[0]?.company).toBe(companyIds[0]);
  } finally {
    pending.release();
  }
});

test("read-only access hides mutations and a pinned foreign link returns to the active company", async ({
  page,
}) => {
  const api = await installShiftsApi(page, { permissions: ["company.read", "workforce.read"] });
  await page.goto(`/workforce/shifts/${dayShiftId}?company=${companyIds[1]}`);
  await expect(page.getByRole("table")).toContainText("North day");
  expect(api.reads.every((url) => url.pathname.includes(companyIds[0]))).toBe(true);
  await expect(page.getByRole("link", { name: "Create shift", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "View details: North day (DAY)", exact: true }).click();
  await expect(page.getByRole("heading", { name: "North day", exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Edit shift", exact: true })).toHaveCount(0);
  await page.goto("/workforce/shifts/new");
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByRole("button", { name: "Save", exact: true })).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});

test("invalid shift durations fail before transport and remain editable", async ({ page }) => {
  const api = await installShiftsApi(page);
  await createForm(page);
  await page.getByLabel("End time", { exact: true }).fill("08:00");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Start and end times must differ");
  expect(api.writes).toHaveLength(0);
  await expect(page.getByLabel("Name", { exact: true })).toHaveJSProperty("readOnly", false);
  await page.getByLabel("End time", { exact: true }).fill("17:00");
  await page.getByRole("button", { name: "Save", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Shift saved.");
  expect(api.writes).toHaveLength(1);
});
