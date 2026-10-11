import { randomUUID } from "node:crypto";
import { expect, test } from "@playwright/test";
import { authenticatorCode } from "../integration/fixtures/authenticator";

test("publication reaches the native inbox and sync preserves receipts, withdrawal, and recipient scope", async ({
  page,
  context,
  request,
}) => {
  await page.goto("/");
  await page.getByLabel("Email", { exact: true }).fill("browser-binding-admin@example.invalid");
  await page.getByLabel("Password", { exact: true }).fill("Browser-fixture-password-123!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.getByRole("button", { name: "Set up an authenticator", exact: true }).click();
  const key = page.locator(".app-enrollment-key code");
  await expect(key).toBeVisible();
  await page
    .getByLabel("Authenticator code", { exact: true })
    .fill(authenticatorCode((await key.textContent()) ?? ""));
  await page.getByRole("button", { name: "Verify", exact: true }).click();
  await page.getByRole("button", { name: "I have saved the codes", exact: true }).click();
  await expect(page.getByRole("heading", { name: "No company access", exact: true })).toBeVisible();
  const csrf = (await (await context.request.get("/api/v1/auth/csrf")).json()) as {
    headerName: string;
    token: string;
  };
  const headers = () => ({ [csrf.headerName]: csrf.token, "Idempotency-Key": randomUUID() });
  const companies: string[] = [];
  for (const code of ["INBOXWORKER", "INBOXOTHER"]) {
    const response = await context.request.post("/api/v1/companies", {
      headers: headers(),
      data: { code, name: `Fictional ${code}`, timezone: "Asia/Jakarta" },
    });
    expect(response.status()).toBe(200);
    companies.push(((await response.json()) as { id: string }).id);
  }
  const company = companies[0];
  const other = companies[1];
  if (!company || !other) throw new Error("Fixture companies were not created");
  const base = `/api/v1/companies/${company}`;
  const account = "a0000000-0000-4000-8000-000000000008";
  for (const id of companies) {
    expect(
      (
        await context.request.put(`/api/v1/companies/${id}/members/${account}`, {
          headers: headers(),
          data: {
            active: true,
            permissions: ["announcements.read"],
            reason: "Native inbox fixture",
          },
        })
      ).status(),
    ).toBe(200);
  }
  const employee = randomUUID();
  expect(
    (
      await context.request.post(`${base}/employees`, {
        headers: headers(),
        data: {
          id: employee,
          employeeNumber: "INBOX-RECIPIENT",
          person: { id: randomUUID(), legalName: "Fictional Inbox Employee", nationality: "ID" },
          terms: {
            effectiveFrom: "2025-01-01",
            startDate: "2025-01-01",
            status: "ACTIVE",
            contract: "PERMANENT",
          },
          reason: "Publication recipient fixture",
        },
      })
    ).status(),
  ).toBe(200);
  expect(
    (
      await context.request.post(`${base}/employees/${employee}/account-link`, {
        headers: headers(),
        data: { accountId: account, expectedVersion: 0, reason: "Independent identity review" },
      })
    ).status(),
  ).toBe(200);

  await page.goto(`/communications/announcements?company=${company}`);
  await page.getByRole("combobox", { name: "Company", exact: true }).selectOption(company);
  await page.getByRole("link", { name: "New announcement", exact: true }).click();
  await page.getByLabel("Title", { exact: true }).fill("Office closure");
  await page.getByLabel("Message", { exact: true }).fill("The office will be closed on Friday.");
  await page.getByLabel("Reason", { exact: true }).fill("Worker delivery fixture");
  await page.getByRole("checkbox", { name: "Require acknowledgement", exact: true }).check();
  const draftWrites: { operation: string | undefined; body: unknown }[] = [];
  let dropDraftResponse = true;
  await page.route(`**/api/v1/companies/${company}/announcements/*`, async (route) => {
    if (route.request().method() !== "PUT") return route.continue();
    draftWrites.push({
      operation: route.request().headers()["idempotency-key"],
      body: route.request().postDataJSON(),
    });
    if (!dropDraftResponse) return route.continue();
    dropDraftResponse = false;
    const response = await route.fetch();
    expect(response.status()).toBe(200);
    await response.dispose();
    return route.abort("failed");
  });
  await page.getByRole("button", { name: "Save draft", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("The save result could not be confirmed");
  await page.getByRole("button", { name: "Check save result", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Draft saved.");
  expect(draftWrites).toHaveLength(2);
  expect(draftWrites[1]).toEqual(draftWrites[0]);
  await page.getByRole("link", { name: "View announcement", exact: true }).click();
  const announcement = new URL(page.url()).pathname.split("/").at(-1);
  expect(announcement).toMatch(/^[0-9a-f-]{36}$/u);
  const announcements = `${base}/announcements/${announcement}`;
  expect(await (await context.request.get(`${announcements}/history`)).json()).toMatchObject({
    items: [{ version: 0 }],
    nextCursor: null,
  });
  await expect(page.getByRole("region", { name: "Message", exact: true })).toContainText(
    "The office will be closed on Friday.",
  );
  await page.getByRole("link", { name: "Revision history", exact: true }).click();
  await page.getByRole("button", { name: "View: Office closure · Version 0", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Historical revision");
  expect(
    await (await context.request.get(`${announcements}/audience-preview?expectedVersion=0`)).json(),
  ).toMatchObject({ recipientCount: 1 });
  expect(
    (
      await context.request.post(`${announcements}/publish`, {
        headers: headers(),
        data: { expectedVersion: 0, reason: "Publish reviewed notice" },
      })
    ).status(),
  ).toBe(200);
  await expect
    .poll(
      async () => {
        const response = await context.request.get(announcements);
        expect(response.status()).toBe(200);
        return ((await response.json()) as { status: string }).status;
      },
      { timeout: 25_000, intervals: [1000] },
    )
    .toBe("PUBLISHED");
  expect(await (await context.request.get(announcements)).json()).toMatchObject({
    recipientCount: 1,
    version: 2,
  });
  await page.getByRole("link", { name: "Current version", exact: true }).click();
  await expect(
    page.getByRole("region", { name: "Publication · Asia/Jakarta", exact: true }),
  ).toContainText("Published");
  await page.getByRole("link", { name: "View publication job", exact: true }).click();
  await expect(page).toHaveURL(/\/administration\/jobs\?company=.+&job=.+/);
  await expect(page.getByRole("dialog", { name: "Job details", exact: true })).toContainText(
    "Succeeded",
  );

  // The native employee has no administrator cookies or management permission.
  const signedIn = await request.post("/api/v1/auth/native/login", {
    headers: { "Idempotency-Key": randomUUID() },
    data: {
      email: "browser-binding-employee@example.invalid",
      password: "Browser-fixture-password-123!",
      deviceName: "Inbox integration",
    },
  });
  expect(signedIn.status()).toBe(200);
  const { credentials } = (await signedIn.json()) as {
    credentials: { accessToken: string; sessionId: string };
  };
  const native = {
    Authorization: `Bearer ${credentials.accessToken}`,
    "X-HRIS-Client-Platform": "ANDROID",
    "X-HRIS-Client-Build": "1",
  };
  try {
    const bootstrapResponse = await request.get(`${base}/sync/bootstrap`, {
      headers: native,
      params: { collections: "INBOX", limit: 50 },
    });
    expect(bootstrapResponse.status()).toBe(200);
    const bootstrap = (await bootstrapResponse.json()) as {
      collections: string[];
      items: { collection: string; id: string; version: number }[];
      nextCursor: string | null;
      changesCursor: string;
    };
    expect(bootstrap.collections).toEqual(["INBOX"]);
    expect(bootstrap.nextCursor).toBeNull();
    expect(bootstrap.items).toHaveLength(1);
    const reference = bootstrap.items[0];
    expect(reference).toMatchObject({ collection: "INBOX", version: 0 });
    const resource = `${base}/inbox/${reference?.id}`;
    const canonical = await request.get(resource, { headers: native });
    expect(canonical.status()).toBe(200);
    expect(await canonical.json()).toMatchObject({
      title: "Office closure",
      readAt: null,
      acknowledgedAt: null,
      version: 0,
    });
    expect((await context.request.get(resource)).status()).toBe(404);
    expect(
      (
        await request.get(`/api/v1/companies/${other}/inbox/${reference?.id}`, { headers: native })
      ).status(),
    ).toBe(404);
    expect(
      (
        await request.get(`/api/v1/companies/${other}/sync/changes`, {
          headers: native,
          params: { collections: "INBOX", limit: 50, cursor: bootstrap.changesCursor },
        })
      ).status(),
    ).toBe(422);

    let cursor = bootstrap.changesCursor;
    const observed: { id: string; version: number; operation: string }[] = [];
    const drain = async () => {
      await expect
        .poll(
          async () => {
            const response = await request.get(`${base}/sync/changes`, {
              headers: native,
              params: { collections: "INBOX", limit: 50, cursor },
            });
            expect(response.status()).toBe(200);
            const result = (await response.json()) as {
              cursor: string;
              hasMore: boolean;
              pendingPublication: boolean;
              items: { id: string; version: number; operation: string }[];
            };
            cursor = result.cursor;
            observed.push(...result.items);
            return !result.hasMore && !result.pendingPublication;
          },
          { timeout: 25_000, intervals: [5000] },
        )
        .toBe(true);
    };
    await drain();
    observed.length = 0;
    const readHeaders = { ...native, "Idempotency-Key": randomUUID() };
    const read = { headers: readHeaders, data: { expectedVersion: 0 } };
    // Ignore the first committed response, then replay the immutable command.
    expect((await request.post(`${resource}/read`, read)).status()).toBe(200);
    const replay = await request.post(`${resource}/read`, read);
    expect(replay.status()).toBe(200);
    expect(await replay.json()).toMatchObject({ id: reference?.id, version: 1 });
    await drain();
    expect(observed.filter((change) => change.id === reference?.id)).toEqual([
      { collection: "INBOX", id: reference?.id, version: 1, operation: "UPSERT" },
    ]);
    expect(
      (
        await request.post(`${resource}/acknowledge`, {
          headers: { ...native, "Idempotency-Key": randomUUID() },
          data: { expectedVersion: 0 },
        })
      ).status(),
    ).toBe(409);
    expect(
      (
        await request.post(`${resource}/acknowledge`, {
          headers: { ...native, "Idempotency-Key": randomUUID() },
          data: { expectedVersion: 1 },
        })
      ).status(),
    ).toBe(200);
    const acknowledged = (await (await request.get(resource, { headers: native })).json()) as {
      version: number;
      readAt: string;
      acknowledgedAt: string;
    };
    expect(acknowledged.version).toBe(2);
    expect(acknowledged.readAt).toBeTruthy();
    expect(acknowledged.acknowledgedAt).toBeTruthy();
    await drain();
    observed.length = 0;
    expect(
      (
        await context.request.post(`${announcements}/archive`, {
          headers: headers(),
          data: { expectedVersion: 2, reason: "Withdraw outdated correspondence" },
        })
      ).status(),
    ).toBe(200);
    expect((await request.get(resource, { headers: native })).status()).toBe(404);
    expect((await request.post(`${resource}/read`, read)).status()).toBe(404);
    await drain();
    expect(observed).toContainEqual({
      collection: "INBOX",
      id: reference?.id,
      version: 3,
      operation: "DELETE",
    });
    expect(
      (
        await context.request.put(`${base}/members/${account}`, {
          headers: headers(),
          data: {
            active: true,
            expectedVersion: 0,
            permissions: [],
            reason: "Remove inbox access",
          },
        })
      ).status(),
    ).toBe(200);
    expect(
      (
        await request.get(`${base}/sync/changes`, {
          headers: native,
          params: { collections: "INBOX", cursor },
        })
      ).status(),
    ).toBe(403);
  } finally {
    expect(
      (
        await request.delete(`/api/v1/auth/native/sessions/${credentials.sessionId}`, {
          headers: native,
        })
      ).status(),
    ).toBe(204);
  }
});
