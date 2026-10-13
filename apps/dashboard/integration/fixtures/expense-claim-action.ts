import { expect, type Page } from "@playwright/test";

export async function confirmExpenseClaimAction(
  page: Page,
  input: {
    company: string;
    claimId: string;
    action: "submit" | "cancel" | "withdraw";
    expectedVersion: number;
    reason: string;
    submissionId?: string;
    loseResponse?: boolean;
  },
) {
  const label = { submit: "Submit claim", cancel: "Cancel draft", withdraw: "Withdraw submission" }[
    input.action
  ];
  const saved = {
    submit: "Claim submitted.",
    cancel: "Draft cancelled.",
    withdraw: "Submission withdrawn.",
  }[input.action];
  const path =
    input.action === "withdraw"
      ? `/api/v1/companies/${input.company}/expenses/submissions/${input.submissionId}/withdraw`
      : `/api/v1/companies/${input.company}/expenses/claims/${input.claimId}/${input.action}`;
  const writes: {
    operation: string;
    body: { expectedVersion: number; reason: string; submissionId?: string };
  }[] = [];
  const pattern = `**${path}`;
  await page.route(pattern, async (route) => {
    const request = route.request();
    expect(request.method()).toBe("POST");
    const body = request.postDataJSON() as (typeof writes)[number]["body"];
    writes.push({ operation: request.headers()["idempotency-key"] ?? "", body });
    expect(body).toMatchObject({ expectedVersion: input.expectedVersion, reason: input.reason });
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    expect(await response.json()).toMatchObject({
      id: input.claimId,
      version: input.expectedVersion + 1,
    });
    if (input.loseResponse && writes.length === 1) return route.abort("failed");
    return route.fulfill({ response });
  });
  try {
    await page.goto(`/expenses/claims/${input.claimId}/${input.action}?company=${input.company}`);
    await page.getByLabel("Reason", { exact: true }).fill(input.reason);
    await page.getByRole("button", { name: label, exact: true }).click();
    if (input.loseResponse)
      await page.getByRole("button", { name: "Retry original action", exact: true }).click();
    await expect(page.getByRole("status")).toContainText(saved);
    expect(writes).toHaveLength(input.loseResponse ? 2 : 1);
    const first = writes[0];
    if (!first) throw new Error("The reviewed action must reach the API");
    expect(first.operation).toMatch(/^[a-f0-9-]{36}$/u);
    if (input.loseResponse) expect(writes[1]).toEqual(first);
    if (input.action === "submit") {
      if (!first.body.submissionId) throw new Error("Submission identity must be generated once");
      expect(first.body.submissionId).toMatch(/^[a-f0-9-]{36}$/u);
    }
    return first.body.submissionId ?? input.submissionId ?? null;
  } finally {
    await page.unroute(pattern);
  }
}
