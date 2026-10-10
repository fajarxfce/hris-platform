import { expect, type Page, test } from "@playwright/test";
import type { EmployeeDto } from "../src/features/people/data/models/employee-dto";
import { companyIds, installIdentityApi } from "./identity-api";

const path = "/people/employees";
const id = (company = 0, value = 1) =>
  `70000000-0000-4000-8000-${company + 1}${String(value).padStart(11, "0")}`;
const employee = (company = 0, value = 1): EmployeeDto => ({
  id: id(company, value),
  companyId: companyIds[company === 0 ? 0 : 1],
  employeeNumber: `EMP-${String(value).padStart(3, "0")}`,
  person: {
    legalName: `${company === 0 ? "North" : "South"} employee ${String(value).padStart(3, "0")}`,
    email: "fixture@example.invalid",
  },
  terms: {
    effectiveFrom: "2026-01-01",
    startDate: "2026-01-01",
    endDate: null,
    status: "ACTIVE",
    contract: "PERMANENT",
  },
  version: 3,
  appliedRevision: 0,
});

async function installPeople(page: Page, paginate = false) {
  const requests: URL[] = [];
  let failure: string | null = null;
  await page.route("**/api/v1/companies/*/employees{,?*,/**}", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    requests.push(url);
    expect(request.method()).toBe("GET");
    expect(request.headers()["x-hris-client-platform"]).toBe("WEB");
    const company = url.pathname.split("/")[4] === companyIds[0] ? 0 : 1;
    if (failure)
      return route.fulfill({
        status: 403,
        json: { code: failure, detail: "PRIVATE SERVER DETAIL" },
      });
    if (url.pathname.endsWith("/history")) {
      expect(url.searchParams.get("limit")).toBe("50");
      const after = url.searchParams.get("after");
      const first = after === null ? (paginate ? 51 : 1) : Number(after) - 1;
      const count = Math.min(50, first + 1);
      return route.fulfill({
        json: {
          items: Array.from({ length: count }, (_, index) => ({
            revision: first - index,
            terms: {
              ...employee(company).terms,
              effectiveFrom: first - index === 0 ? "2026-01-01" : "2027-01-01",
            },
            actorId: "PRIVATE ACTOR",
            reason: first - index === 0 ? "Initial employment" : "Scheduled contract review",
            recordedAt: "2026-01-01T00:00:00.000123Z",
            cancellation:
              first - index === 0
                ? null
                : {
                    actorId: "PRIVATE ACTOR",
                    reason: "Schedule changed",
                    recordedAt: "2026-02-01T00:00:00Z",
                  },
          })),
          nextCursor: count === 50 ? String(first - count + 1) : null,
        },
      });
    }
    expect(url.searchParams.get("asOf")).toMatch(/^\d{4}-\d{2}-\d{2}$/u);
    const selected = url.pathname.split("/")[6];
    if (selected)
      return route.fulfill({
        json: {
          ...employee(company),
          id: selected,
          person: {
            ...employee(company).person,
            nationality: "PRIVATE NATIONALITY",
            birthDate: "PRIVATE BIRTH DATE",
          },
        },
      });
    expect(url.searchParams.get("limit")).toBe("50");
    const after = url.searchParams.get("after");
    const count = paginate && after === null ? 50 : 2;
    return route.fulfill({
      json: {
        items: Array.from({ length: count }, (_, index) =>
          employee(company, after === null ? index + 1 : 51 + index),
        ),
        nextCursor: count === 50 ? "EMP-050" : null,
      },
    });
  });
  return {
    requests,
    deny: (code: string | null) => {
      failure = code;
    },
  };
}

test("employee directory, details and revision evidence are responsive, localized and loaded on demand", async ({
  page,
}) => {
  const identity = await installIdentityApi(page, { signedIn: true, permissions: ["people.read"] });
  const api = await installPeople(page);
  const browserErrors: string[] = [];
  page.on("pageerror", (error) => browserErrors.push(error.message));
  await page.goto(`${path}?asOf=2026-10-01`);
  const table = page.getByRole("table", { name: "Employee directory", exact: true });
  await expect(table.getByRole("row")).toHaveCount(3);
  await expect(page).toHaveURL(new RegExp(`company=${companyIds[0]}`, "u"));
  await expect(table).toContainText("North employee 001");
  await page.screenshot({
    path: "../../.work/dashboard-people-light.png",
    fullPage: true,
    animations: "disabled",
  });
  await table
    .getByRole("button", { name: "View details: North employee 001 (EMP-001)", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "North employee 001", exact: true }),
  ).toBeVisible();
  const overview = page.getByRole("region", { name: "Employment", exact: true });
  await expect(overview).toContainText("fixture@example.invalid");
  await expect(page.getByRole("main")).not.toContainText("PRIVATE");
  expect(api.requests.filter((url) => url.pathname.endsWith("/history"))).toHaveLength(0);
  const detailReads = api.requests.filter((url) => url.pathname.endsWith(id())).length;
  await page.getByRole("tab", { name: "Employment history", exact: true }).click();
  const history = page.getByRole("table", { name: "Employment history", exact: true });
  await expect(history.getByRole("row")).toHaveCount(3);
  await expect(history).toContainText("Cancelled");
  expect(api.requests.filter((url) => url.pathname.endsWith(id()))).toHaveLength(detailReads);
  const revision = history.getByRole("button", { name: "View details: 1", exact: true });
  await revision.focus();
  await page.keyboard.press("Enter");
  const dialog = page.getByRole("dialog", { name: "Revision details", exact: true });
  await expect(dialog).toContainText("Scheduled contract review");
  await expect(dialog).toContainText("Schedule changed");
  await expect(dialog).not.toContainText("PRIVATE");
  await page.keyboard.press("Escape");
  await expect(dialog).toHaveCount(0);
  await expect(revision).toBeFocused();
  const count = api.requests.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByRole("table", { name: "Riwayat employment", exact: true })).toContainText(
    "Dibatalkan",
  );
  await page.screenshot({
    path: "../../.work/dashboard-people-history-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({
    path: "../../.work/dashboard-people-history-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.getByRole("button", { name: "Lihat detail: 1", exact: true }).click();
  await expect(page.getByRole("dialog", { name: "Detail revisi", exact: true })).toContainText(
    "Schedule changed",
  );
  await page.screenshot({
    path: "../../.work/dashboard-employment-revision-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "Tutup", exact: true }).click();
  expect(api.requests).toHaveLength(count);
  await page.getByRole("tab", { name: "Ringkasan", exact: true }).click();
  await expect(page.getByRole("region", { name: "Employment", exact: true })).toContainText(
    "Aktif",
  );
  await page.screenshot({
    path: "../../.work/dashboard-employee-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("link", { name: "Kembali ke karyawan", exact: true }).click();
  await expect(page.getByLabel("Tanggal efektif", { exact: true })).toHaveValue("2026-10-01");
  expect(identity.unhandled).toEqual([]);
  expect(browserErrors).toEqual([]);
});

test("directory filters and both cursors follow URL navigation without speculative fetches", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true });
  const api = await installPeople(page, true);
  await page.goto(`${path}?asOf=2026-10-01&query=Initial`);
  const table = page.getByRole("table", { name: "Employee directory", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  const reads = api.requests.length;
  await page.getByLabel("Name or employee number", { exact: true }).fill("A&B +_%");
  await page.getByLabel("Effective date", { exact: true }).fill("2026-10-02");
  expect(api.requests).toHaveLength(reads);
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page).toHaveURL(/query=A%26B\+%2B_%25/u);
  await expect(table.getByRole("row")).toHaveCount(51);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(3);
  await expect(table).toContainText("EMP-051");
  expect(api.requests.at(-1)?.searchParams.get("after")).toBe("EMP-050");
  await page.goBack();
  await expect(table.getByRole("row")).toHaveCount(51);
  await page.goBack();
  await expect(page.getByLabel("Name or employee number", { exact: true })).toHaveValue("Initial");
  await expect(page.getByLabel("Effective date", { exact: true })).toHaveValue("2026-10-01");
  await page.goForward();
  await expect(page.getByLabel("Name or employee number", { exact: true })).toHaveValue("A&B +_%");
  await expect(page.getByLabel("Effective date", { exact: true })).toHaveValue("2026-10-02");
  await table
    .getByRole("button", { name: "View details: North employee 001 (EMP-001)", exact: true })
    .click();
  await page.getByRole("tab", { name: "Employment history", exact: true }).click();
  const history = page.getByRole("table", { name: "Employment history", exact: true });
  await expect(history.getByRole("row")).toHaveCount(51);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(history.getByRole("row")).toHaveCount(3);
  expect(api.requests.at(-1)?.searchParams.get("after")).toBe("2");
  await page.getByRole("button", { name: "First page", exact: true }).click();
  await expect(history.getByRole("row")).toHaveCount(51);
  await page.getByRole("link", { name: "Back to employees", exact: true }).click();
  await expect(page.getByLabel("Name or employee number", { exact: true })).toHaveValue("A&B +_%");
  await expect(page.getByLabel("Effective date", { exact: true })).toHaveValue("2026-10-02");
});

for (const permission of ["people.team.read", "people.self.read", "people.profile.read"]) {
  test(`directory access and history restrictions respect ${permission}`, async ({ page }) => {
    await installIdentityApi(page, { signedIn: true, permissions: [permission] });
    const api = await installPeople(page);
    await page.goto(`${path}/${id()}?asOf=2026-10-01&tab=history`);
    if (permission === "people.profile.read") {
      await expect(page.getByRole("alert")).toContainText("You do not have access");
      await expect(page.getByRole("link", { name: "Employees", exact: true })).toHaveCount(0);
      expect(api.requests).toHaveLength(0);
    } else {
      await expect(page.getByRole("region", { name: "Employment", exact: true })).toBeVisible();
      await expect(page.getByRole("link", { name: "Employees", exact: true })).toBeVisible();
    }
    await expect(page.getByRole("tab", { name: "Employment history", exact: true })).toHaveCount(0);
    expect(api.requests.filter((url) => url.pathname.endsWith("/history"))).toHaveLength(0);
  });
}

test("invalid links fail before I/O and can be corrected through filters or pagination", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true });
  const api = await installPeople(page);
  await page.goto(`${path}?asOf=2026-02-30`);
  await expect(page.getByRole("alert")).toContainText("Choose a valid effective date.");
  expect(api.requests).toHaveLength(0);
  await page.getByLabel("Effective date", { exact: true }).fill("2026-10-01");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("table", { name: "Employee directory", exact: true })).toBeVisible();
  await page.goto(`${path}/${id()}?asOf=2026-10-01&tab=history&historyAfter=-1`);
  await expect(page.getByRole("alert")).toContainText("This page link is invalid.");
  expect(api.requests.filter((url) => url.pathname.endsWith("/history"))).toHaveLength(0);
  await page.getByRole("button", { name: "First page", exact: true }).click();
  await expect(page.getByRole("table", { name: "Employment history", exact: true })).toBeVisible();
});

test("refresh clears data on revoked access and only explicit retries load it again", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true });
  const api = await installPeople(page);
  await page.goto(`${path}?asOf=2026-10-01`);
  await expect(page.getByRole("table")).toBeVisible();
  api.deny("company_access_denied");
  const reads = api.requests.length;
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Company access is unavailable.");
  await expect(page.getByRole("table")).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("North employee");
  await expect(page.getByRole("main")).not.toContainText("PRIVATE");
  expect(api.requests).toHaveLength(reads + 1);
  api.deny(null);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("table")).toBeVisible();
  expect(api.requests).toHaveLength(reads + 2);
});

test("revoked company access during history removes the enclosing employee overview", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true });
  const api = await installPeople(page);
  await page.goto(`${path}/${id()}?asOf=2026-10-01`);
  await expect(
    page.getByRole("heading", { name: "North employee 001", exact: true }),
  ).toBeVisible();
  const reads = api.requests.length;
  api.deny("company_access_denied");
  await page.getByRole("tab", { name: "Employment history", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Company access is unavailable.");
  await expect(page.getByRole("main")).not.toContainText("North employee 001");
  await expect(page.getByRole("region", { name: "Employment", exact: true })).toHaveCount(0);
  expect(api.requests.slice(reads).every((url) => url.pathname.endsWith("/history"))).toBe(true);
  api.deny(null);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("table", { name: "Employment history", exact: true })).toBeVisible();
});

test("switching companies discards a pending directory page and its continuation", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true });
  const api = await installPeople(page);
  let release!: () => void;
  let entered!: () => void;
  const started = new Promise<void>((done) => {
    entered = done;
  });
  const held = new Promise<void>((done) => {
    release = done;
  });
  await page.route(`**/api/v1/companies/${companyIds[0]}/employees?**`, async (route) => {
    entered();
    await held;
    await route.fulfill({ json: { items: [employee()], nextCursor: null } });
  });
  try {
    await page.goto(`${path}?company=${companyIds[0]}&asOf=2026-10-01&after=EMP-050`);
    await started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page.getByRole("table")).toContainText("South employee");
    release();
    await expect(page.getByRole("main")).not.toContainText("North employee");
    expect(api.requests.at(-1)?.searchParams.has("after")).toBe(false);
    expect(api.requests.at(-1)?.pathname).toContain(companyIds[1]);
  } finally {
    release();
  }
});

test("company switching during history closes the employee and cannot render the old result", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true });
  const api = await installPeople(page);
  let release!: () => void;
  let entered!: () => void;
  const started = new Promise<void>((done) => {
    entered = done;
  });
  const held = new Promise<void>((done) => {
    release = done;
  });
  await page.route(
    `**/api/v1/companies/${companyIds[0]}/employees/${id()}/history?**`,
    async (route) => {
      entered();
      await held;
      await route.fulfill({ json: { items: [], nextCursor: null } });
    },
  );
  try {
    await page.goto(`${path}/${id()}?asOf=2026-10-01&tab=history`);
    await started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page).toHaveURL(new RegExp(`${path}\\?company=${companyIds[1]}`, "u"));
    await expect(page.getByRole("table")).toContainText("South employee");
    release();
    await expect(page.getByRole("main")).not.toContainText("North employee");
    expect(
      api.requests.some((url) => url.pathname.includes(`${companyIds[1]}/employees/${id()}`)),
    ).toBe(false);
    await page.getByRole("button", { name: "Sign out", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
    await expect(page.getByRole("table")).toHaveCount(0);
  } finally {
    release();
  }
});
