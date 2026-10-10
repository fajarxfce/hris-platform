import { expect, type Page, test } from "@playwright/test";
import type { OrganizationUnitDto } from "../src/features/organization/data/models/organization-unit-dto";
import { companyIds, installIdentityApi } from "./identity-api";

const path = "/organization/units";
const id = (company = 0, value = 1) =>
  `80000000-0000-4000-8000-${company + 1}${String(value).padStart(11, "0")}`;
const unit = (
  company = 0,
  value = 1,
  kind: OrganizationUnitDto["kind"] = "DEPARTMENT",
): OrganizationUnitDto => ({
  id: id(company, value),
  code: `UNIT-${String(value).padStart(3, "0")}`,
  name: `${company === 0 ? "North" : "South"} unit ${String(value).padStart(3, "0")}`,
  kind,
  parentId:
    kind === "DEPARTMENT" ? id(company, 999) : kind === "POSITION" ? id(company, 998) : null,
  timezone: kind === "BRANCH" ? "Asia/Jakarta" : null,
  active: true,
  version: 2,
});

async function installOrganization(page: Page, paginate = false) {
  const requests: URL[] = [];
  let failure: string | null = null;
  await page.route("**/api/v1/companies/*/organization-units{,?*,/**}", async (route) => {
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
    const selected = url.pathname.split("/")[6];
    if (selected) {
      const isParent = selected === id(company, 999);
      const current = unit(company, isParent ? 999 : 1, isParent ? "BRANCH" : "DEPARTMENT");
      return route.fulfill({
        json: {
          companyId: companyIds[company],
          unit: { ...current, id: selected },
          parent: isParent ? null : unit(company, 999, "BRANCH"),
        },
      });
    }
    expect(url.searchParams.get("limit")).toBe("50");
    const after = url.searchParams.get("after");
    const kind = url.searchParams.get("kind") as OrganizationUnitDto["kind"] | null;
    const active = url.searchParams.get("active");
    const count =
      url.searchParams.get("query") === "missing" ? 0 : paginate && after === null ? 50 : 4;
    const kinds: OrganizationUnitDto["kind"][] = [
      "DEPARTMENT",
      "POSITION",
      "COST_CENTER",
      "BRANCH",
    ];
    const items = Array.from({ length: count }, (_, index) => ({
      ...unit(
        company,
        after === null ? index + 1 : 51 + index,
        kind ?? (paginate ? "DEPARTMENT" : (kinds[index] ?? "DEPARTMENT")),
      ),
      active: active === null ? index !== 2 : active === "true",
    }));
    const last = items.at(-1);
    return route.fulfill({
      json: { items, nextCursor: count === 50 ? `${last?.kind}:${last?.code}` : null },
    });
  });
  return {
    requests,
    deny: (code: string | null) => {
      failure = code;
    },
  };
}

test("organization tables and parent details are responsive, localized and loaded only when opened", async ({
  page,
}) => {
  const identity = await installIdentityApi(page, { signedIn: true });
  const api = await installOrganization(page);
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto(path);
  await expect(page).toHaveURL(new RegExp(`company=${companyIds[0]}`, "u"));
  const table = page.getByRole("table", { name: "Organization units", exact: true });
  await expect(table.getByRole("row")).toHaveCount(5);
  for (const text of ["Department", "Position", "Cost center", "Branch", "Inactive"])
    await expect(table).toContainText(text);
  expect(api.requests.every((url) => url.pathname.endsWith("organization-units"))).toBe(true);
  await page.screenshot({
    path: "../../.work/dashboard-organization-light.png",
    fullPage: true,
    animations: "disabled",
  });
  const open = table.getByRole("button", {
    name: "View details: North unit 001 (UNIT-001)",
    exact: true,
  });
  await open.focus();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("heading", { name: "North unit 001", exact: true })).toBeVisible();
  await expect(page.getByRole("region", { name: "Parent unit", exact: true })).toContainText(
    "North unit 999",
  );
  await expect(page.getByRole("region", { name: "Parent unit", exact: true })).toContainText(
    "Asia/Jakarta",
  );
  expect(api.requests.filter((url) => url.pathname.endsWith(id(0, 999)))).toHaveLength(0);
  const reads = api.requests.length;
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(page.getByRole("region", { name: "Unit induk", exact: true })).toContainText(
    "Cabang",
  );
  await page.screenshot({
    path: "../../.work/dashboard-organization-detail-dark.png",
    fullPage: true,
    animations: "disabled",
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({
    path: "../../.work/dashboard-organization-detail-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  expect(api.requests).toHaveLength(reads);
  await page.getByRole("link", { name: "Buka unit induk", exact: true }).click();
  await expect(page.getByRole("heading", { name: "North unit 999", exact: true })).toBeVisible();
  await expect(page.getByRole("status")).toHaveText("Unit ini tidak memiliki induk.");
  await page.getByRole("link", { name: "Kembali ke organisasi", exact: true }).click();
  await expect(page.getByRole("table", { name: "Unit organisasi", exact: true })).toBeVisible();
  await page.screenshot({
    path: "../../.work/dashboard-organization-mobile.png",
    fullPage: true,
    animations: "disabled",
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  expect(errors).toEqual([]);
  expect(identity.unhandled).toEqual([]);
});

test("applied filters and continuation follow Back and Forward without fetching as the user types", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true });
  const api = await installOrganization(page, true);
  await page.goto(`${path}?query=Initial`);
  const table = page.getByRole("table", { name: "Organization units", exact: true });
  await expect(table.getByRole("row")).toHaveCount(51);
  const reads = api.requests.length;
  await page.getByLabel("Name or code", { exact: true }).fill("R&D_100% +");
  await page.getByRole("combobox", { name: "Type", exact: true }).selectOption("POSITION");
  await page.getByRole("combobox", { name: "Status", exact: true }).selectOption("false");
  expect(api.requests).toHaveLength(reads);
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(51);
  await expect(page).toHaveURL(/kind=POSITION&active=false/u);
  expect(api.requests.at(-1)?.searchParams.get("query")).toBe("R&D_100% +");
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(table.getByRole("row")).toHaveCount(5);
  expect(api.requests.at(-1)?.searchParams.get("after")).toBe("POSITION:UNIT-050");
  await page.goBack();
  await expect(table.getByRole("row")).toHaveCount(51);
  await page.goBack();
  await expect(page.getByLabel("Name or code", { exact: true })).toHaveValue("Initial");
  await expect(page.getByRole("combobox", { name: "Type", exact: true })).toHaveValue("");
  await expect(page.getByRole("combobox", { name: "Status", exact: true })).toHaveValue("");
  await page.goForward();
  await expect(page.getByLabel("Name or code", { exact: true })).toHaveValue("R&D_100% +");
  await expect(page.getByRole("combobox", { name: "Type", exact: true })).toHaveValue("POSITION");
  await expect(page.getByRole("combobox", { name: "Status", exact: true })).toHaveValue("false");
  await page.getByLabel("Name or code", { exact: true }).fill("missing");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("No organization units match these filters.");
  await expect(page.getByRole("button", { name: "Next page", exact: true })).toBeDisabled();
});

test("company management alone does not authorize organization reads or links", async ({
  page,
}) => {
  await installIdentityApi(page, {
    signedIn: true,
    permissions: ["company.manage", "people.read"],
  });
  const api = await installOrganization(page);
  for (const target of [path, `${path}/${id()}`]) {
    await page.goto(target);
    await expect(page.getByRole("alert")).toContainText("You do not have access");
    await expect(page.getByRole("link", { name: "Organization", exact: true })).toHaveCount(0);
  }
  expect(api.requests).toHaveLength(0);
});

test("invalid directory links fail before I/O and can be corrected explicitly", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true });
  const api = await installOrganization(page);
  await page.goto(`${path}?kind=POSITION&after=BRANCH:UNIT-050`);
  await expect(page.getByRole("alert")).toContainText("This page link is invalid");
  expect(api.requests).toHaveLength(0);
  await page.getByRole("button", { name: "First page", exact: true }).click();
  await expect(page.getByRole("table")).toContainText("Position");
  await page.goto(`${path}?active=invalid`);
  await expect(page.getByRole("alert")).toContainText("Choose a valid unit type and status");
  const reads = api.requests.length;
  await page.getByRole("combobox", { name: "Status", exact: true }).selectOption("false");
  await page.getByRole("button", { name: "Apply", exact: true }).click();
  await expect(page.getByRole("table")).toContainText("Inactive");
  expect(api.requests).toHaveLength(reads + 1);
  await page.goto(`${path}/invalid-unit-id`);
  await expect(page.getByRole("alert")).toContainText("The organization unit was not found");
  expect(api.requests).toHaveLength(reads + 1);
});

test("a failed refresh clears both unit and parent and does not silently retry", async ({
  page,
}) => {
  await installIdentityApi(page, { signedIn: true });
  const api = await installOrganization(page);
  await page.goto(`${path}/${id()}`);
  await expect(page.getByRole("region", { name: "Parent unit", exact: true })).toContainText(
    "North unit 999",
  );
  const reads = api.requests.length;
  api.deny("company_access_denied");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Company access is unavailable.");
  await expect(page.getByRole("main")).not.toContainText("North unit");
  await expect(page.getByRole("main")).not.toContainText("PRIVATE");
  expect(api.requests).toHaveLength(reads + 1);
  api.deny(null);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("region", { name: "Parent unit", exact: true })).toBeVisible();
  expect(api.requests).toHaveLength(reads + 2);
});

for (const detail of [false, true]) {
  test(`switching company discards a pending ${detail ? "detail" : "directory"} and its URL scope`, async ({
    page,
  }) => {
    await installIdentityApi(page, { signedIn: true });
    const api = await installOrganization(page);
    let release!: () => void;
    let entered!: () => void;
    const held = new Promise<void>((done) => {
      release = done;
    });
    const started = new Promise<void>((done) => {
      entered = done;
    });
    await page.route(
      `**/api/v1/companies/${companyIds[0]}/organization-units${detail ? `/${id()}` : "?**"}`,
      async (route) => {
        entered();
        await held;
        await route.fulfill({
          json: detail
            ? { companyId: companyIds[0], unit: unit(), parent: unit(0, 999, "BRANCH") }
            : { items: [unit()], nextCursor: null },
        });
      },
    );
    try {
      await page.goto(
        `${path}${detail ? `/${id()}` : ""}?company=${companyIds[0]}&kind=DEPARTMENT&query=North&after=DEPARTMENT:OLD`,
      );
      await started;
      await page
        .getByRole("combobox", { name: "Company", exact: true })
        .selectOption(companyIds[1]);
      await expect(page).toHaveURL(new RegExp(`${path}\\?company=${companyIds[1]}$`, "u"));
      await expect(page.getByRole("table")).toContainText("South unit");
      release();
      await expect(page.getByRole("main")).not.toContainText("North unit");
      expect(api.requests.at(-1)?.searchParams.has("after")).toBe(false);
      expect(api.requests.at(-1)?.searchParams.has("kind")).toBe(false);
      expect(api.requests.at(-1)?.searchParams.get("query")).toBe("");
      expect(
        api.requests.some((url) =>
          url.pathname.includes(`${companyIds[1]}/organization-units/${id()}`),
        ),
      ).toBe(false);
      await page.getByRole("button", { name: "Sign out", exact: true }).click();
      await expect(page.getByRole("heading", { name: "Sign in", exact: true })).toBeVisible();
      await expect(page.getByRole("table")).toHaveCount(0);
    } finally {
      release();
    }
  });
}
