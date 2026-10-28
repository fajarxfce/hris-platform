import type { Page } from "@playwright/test";
import { companyIds } from "./identity-api";

const statuses = ["DRAFT", "PENDING", "RETURNED", "APPROVED", "REJECTED", "CANCELLED"];
export async function installExpenseReports(page: Page) {
  const reads: URL[] = [];
  let failing = false;
  await page.route("**/api/v1/companies/*/reports/expenses**", async (route) => {
    const url = new URL(route.request().url());
    reads.push(url);
    if (failing)
      return route.fulfill({
        status: 503,
        json: {
          code: "service_unavailable",
          fields: {},
          parameters: {},
          detail: "PRIVATE EXPENSE REPORT",
        },
      });
    const companyId = url.pathname.split("/")[4] ?? "";
    const from = url.searchParams.get("from") ?? "",
      until = url.searchParams.get("until") ?? "";
    const metadata = {
      companyId,
      from,
      until,
      evaluatedAt: "2026-10-01T00:00:00Z",
      definitionVersion: "expenses.v1",
      currency: "IDR",
    };
    const count = companyId === companyIds[0] ? 51 : 0;
    const zero = { claims: 0, employments: 0, lines: 0, amount: "0.00" };
    if (!url.pathname.endsWith("/claims")) {
      const totals = count
        ? { claims: count, employments: 1, lines: count, amount: "7650038.25" }
        : zero;
      return route.fulfill({
        json: {
          ...metadata,
          totals,
          statuses: statuses.map((status) => ({
            status,
            counts: status === "PENDING" ? totals : zero,
          })),
        },
      });
    }
    const status = url.searchParams.get("status");
    const source =
      count && (!status || status === "PENDING")
        ? Array.from({ length: 51 }, (_, index) => ({
            id: `00000000-0000-4000-8000-${String(1000 - index).padStart(12, "0")}`,
            employeeId: "eeaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            employeeNumber: "E1",
            employeeName: `Report employee ${index + 1}`,
            title: `Travel ${index + 1}`,
            submissionId: `10000000-0000-4000-8000-${String(index + 1).padStart(12, "0")}`,
            submissionNumber: 1,
            status: "PENDING",
            submittedAt: "2023-12-01T00:00:00Z",
            version: 0,
            lines: 1,
            amount: "150000.75",
            submissionAmount: "200000.85",
          }))
        : [];
    const items = url.searchParams.has("after") ? source.slice(50) : source.slice(0, 50);
    const last = items.at(-1);
    const nextCursor =
      source.length > 50 && !url.searchParams.has("after") && last
        ? `expenses.v1|${companyId}|${from}|${until}|${status ?? "ALL"}|${last.submittedAt}|${last.id}`
        : null;
    return route.fulfill({ json: { ...metadata, status, items, nextCursor } });
  });
  return {
    reads,
    fail: (value: boolean) => {
      failing = value;
    },
  };
}
