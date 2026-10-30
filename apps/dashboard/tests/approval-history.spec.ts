import { expect, type Page, test } from "@playwright/test";
import { approvalId, installApprovalsApi } from "./approvals-api";
import { companyIds } from "./identity-api";

const id = approvalId(0, 2),
  detail = `/approvals/${id}?company=${companyIds[0]}`;
const path = `/approvals/${id}/history?company=${companyIds[0]}`;
const actor = "20000000-0000-4000-8000-000000000003";
const delegator = "20000000-0000-4000-8000-000000000004";
async function install(page: Page) {
  const api = await installApprovalsApi(page);
  const request = api.records.get(companyIds[0])?.find((row) => row.id === id);
  if (!request) throw new Error("Missing approval fixture");
  const reads: URL[] = [];
  let version = 53,
    failure: string | null = null;
  let held: { entered: () => void; wait: Promise<void> } | null = null;
  await page.route("**/api/v1/companies/*/approvals/*/history**", async (route) => {
    expect(route.request().method()).toBe("GET");
    const url = new URL(route.request().url());
    reads.push(url);
    expect(url.pathname).toBe(`/api/v1/companies/${companyIds[0]}/approvals/${id}/history`);
    expect(url.searchParams.get("limit")).toBe("50");
    const snapshot = version,
      hold = held;
    held = null;
    if (hold) {
      hold.entered();
      await hold.wait;
    }
    const expected = url.searchParams.get("expectedVersion");
    const code =
      failure ?? (expected !== null && Number(expected) !== snapshot ? "approval_changed" : null);
    if (code)
      return route.fulfill({
        status: code === "approval_changed" ? 409 : 404,
        json: { code, fields: {}, parameters: {}, detail: "PRIVATE TECHNICAL" },
      });
    const after = Number(url.searchParams.get("after") ?? 0);
    const items = Array.from({ length: 52 }, (_, i) => ({
      revision: i + 1,
      step: 0,
      assignees: [delegator],
      actorId: actor,
      reason: `Retained change ${i + 1}`,
      recordedAt: "2026-10-01T09:35:00Z",
    })).filter((row) => row.revision > after);
    return route.fulfill({
      json: {
        request: { ...request, version: snapshot },
        decisions: [
          {
            step: 0,
            actorId: actor,
            decidingFor: delegator,
            decision: "APPROVE",
            reason: "Original delegated review <script>private()</script>",
            decidedAt: "2026-10-01T09:36:00Z",
          },
        ],
        reassignments: { items: items.slice(0, 50), nextCursor: items.length > 50 ? "50" : null },
      },
    });
  });
  return {
    ...api,
    reads,
    change: () => {
      version++;
    },
    fail: () => {
      failure = "approval_not_found";
    },
    hold: () => {
      let entered!: () => void, release!: () => void;
      const started = new Promise<void>((done) => {
        entered = done;
      });
      const wait = new Promise<void>((done) => {
        release = done;
      });
      held = { entered, wait };
      return { started, release };
    },
  };
}

test("history reads original decisions and bounded changes, retaining request return context", async ({
  page,
}) => {
  const api = await install(page);
  await page.goto(`${detail}&after=${approvalId(0, 1)}`);
  await page.getByRole("link", { name: "Approval history", exact: true }).click();
  const decisions = page.getByRole("table", {
    name: "Recorded decisions · Asia/Jakarta",
    exact: true,
  });
  const changes = page.getByRole("table", { name: "Approver changes · Asia/Jakarta", exact: true });
  await expect(decisions).toContainText("Original delegated review <script>private()</script>");
  await expect(decisions).toContainText(actor);
  await expect(decisions).toContainText(delegator);
  await expect(changes.getByRole("row")).toHaveCount(51);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(changes.getByRole("row")).toHaveCount(3);
  expect(api.reads.at(-1)?.searchParams.get("after")).toBe("50");
  expect(api.reads.at(-1)?.searchParams.get("expectedVersion")).toBe("53");
  await expect(page.getByRole("button", { name: "Next page", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "First page", exact: true }).click();
  await expect(changes.getByRole("row")).toHaveCount(51);
  await page.getByRole("link", { name: "Back to request", exact: true }).click();
  await expect(page).toHaveURL(`${detail}&after=${approvalId(0, 1)}`);
  expect(api.reads).toHaveLength(3);
  expect(api.identity.unhandled).toEqual([]);
});

test("changed versions and revoked scope clear history and require an explicit new read", async ({
  page,
}) => {
  const api = await install(page);
  await page.goto(path);
  await expect(page.getByRole("table")).toHaveCount(2);
  api.change();
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("The approval changed");
  await expect(page.getByRole("table")).toHaveCount(0);
  expect(api.reads).toHaveLength(2);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("table")).toHaveCount(2);
  expect(api.reads.at(-1)?.searchParams.has("after")).toBe(false);
  expect(api.reads.at(-1)?.searchParams.has("expectedVersion")).toBe(false);
  api.fail();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("The approval request was not found");
  await expect(page.getByRole("table")).toHaveCount(0);
  await expect(page.getByRole("main")).not.toContainText("Original delegated");
  await expect(page.getByRole("main")).not.toContainText("PRIVATE TECHNICAL");
});

test("company switch cancels a pending history observation and keeps its identifiers out of the new scope", async ({
  page,
}) => {
  const api = await install(page),
    held = api.hold();
  try {
    await page.goto(path);
    await held.started;
    await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(companyIds[1]);
    await expect(page).toHaveURL(`/approvals?company=${companyIds[1]}`);
    held.release();
    await expect(page.getByRole("table", { name: "Approval inbox", exact: true })).toBeVisible();
    await expect(page.getByRole("main")).not.toContainText("Original delegated");
    expect(api.reads).toHaveLength(1);
    expect(api.identity.unhandled).toEqual([]);
  } finally {
    held.release();
  }
});

test("background observation stops and foreground admission reloads the first page once", async ({
  page,
}) => {
  const api = await install(page);
  await page.goto(path);
  await expect(page.getByRole("table")).toHaveCount(2);
  await page.getByRole("button", { name: "Next page", exact: true }).click();
  await expect(page.getByRole("button", { name: "Next page", exact: true })).toBeDisabled();
  await page.evaluate(() => {
    Object.defineProperty(document, "visibilityState", { value: "hidden", configurable: true });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await expect(page.getByRole("table")).toHaveCount(0);
  expect(api.reads).toHaveLength(2);
  await page.evaluate(() => {
    Object.defineProperty(document, "visibilityState", { value: "visible", configurable: true });
    document.dispatchEvent(new Event("visibilitychange"));
  });
  await expect(page.getByRole("button", { name: "Next page", exact: true })).toBeEnabled();
  expect(api.reads).toHaveLength(3);
  expect(api.reads.at(-1)?.searchParams.has("after")).toBe(false);
});

test("Indonesian dark narrow history retains contained tables and performs no locale reload", async ({
  page,
}) => {
  const api = await install(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(path);
  await expect(page.getByRole("table")).toHaveCount(2);
  await page.getByRole("combobox", { name: "Language", exact: true }).selectOption("id");
  await page.getByRole("button", { name: "Tema", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Riwayat persetujuan", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("table", { name: "Keputusan tercatat · Asia/Jakarta", exact: true }),
  ).toContainText("Disetujui");
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
    .toBe(true);
  expect(api.reads).toHaveLength(1);
  await page.screenshot({
    path: "../../.work/approval-history-2026-10-30/history-id-narrow.png",
    animations: "disabled",
  });
});
