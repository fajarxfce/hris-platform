# Dashboard design

Follow Azure Portal shell and resource interactions using Fluent UI v9. Use an Azure blue accent with semantic tokens, light/dark themes, Segoe UI/system font fallback, and concise English/Indonesian copy.

The persistent header owns permitted-resource search, company switcher, notifications, theme, and account. A collapsible sidebar groups modules. The workspace has breadcrumb, title/status, command bar, and content. Lists have server filters/sort/pagination, optional bulk actions, and resource links. Quick inspection uses a side panel; employee profiles and payroll runs use routed detail pages with tabs. Setup/import/payroll use staged workflows.

Shared components: AppPortalShell, AppPageHeader, AppCommandBar, AppFilterBar, AppDataTable, AppDetailsPanel, AppStatusBadge, and form controls. All support keyboard navigation, focus restoration, semantic status, and responsive layouts. Tables default to 50 rows; only genuinely long client lists need virtualization.

Pages consume display state and dispatch actions. Query/cache ownership includes account and company. Company switching cancels outstanding queries and removes obsolete view data. Filters live in the URL. Dirty forms require an explicit navigation decision. Polling/listeners stop when inactive.

Primary screens: organization and employee directory; roster and attendance exceptions; leave requests/balances; approval inbox; expense claims/payments; payroll periods/runs/reconciliation; documents; announcements; reports; settings/audit/jobs.

Reference: https://learn.microsoft.com/en-us/azure/azure-portal/azure-portal-overview

## Implemented foundation

`apps/dashboard` provides password sign-in, configured SSO entry links, session bootstrap, authenticator enrollment/verification, and explicit acknowledgement of one-use recovery codes. The portal includes current-company selection, an authorized company overview, responsive navigation, and English/Indonesian light/dark interfaces. Business resource screens listed above remain subsequent work; the overview does not invent statistics.

Feature datasources validate transport DTOs; repositories map them inside the HTTP failure boundary; use cases own policy; the identity controller owns requests and presentation state. Pages render that state. Shared `AppXxx` components wrap Fluent controls. The import checker rejects domain/framework dependencies, data access from presentation, repository peers, and use-case peers. These static checks complement review of orchestration and resource ownership.

The same-origin transport uses session cookies and fresh CSRF values for commands. It bounds responses to 1 MiB, owns request deadlines, propagates cancellation, and never retries a mutation automatically. Controllers clear private cached state during company/account changes and reject obsolete responses. Foreground/reconnect revalidation uses the same request owner. Forms clear passwords after submission, and enrollment secrets/recovery codes are not placed in browser storage. An expired enrollment requires an explicit new setup operation.

Application messages translate stable failure codes; unknown codes receive a generic localized message and an optional correlation reference. Backend diagnostic text is never displayed. Language and theme controls remain available before authentication.

## Commands

From `apps/dashboard`:

```sh
npm ci
npm run dev
npm run check
npm run build
npx playwright install chromium
npm run test:e2e
# Requires JDK 21 and Docker, with the backend built first:
../../gradlew -p ../.. :apps:server:bootJar --no-daemon
npm run test:integration
```

`check` runs import boundaries, Biome, strict TypeScript, and transport/controller tests. `test:e2e` launches an isolated local Vite server and runs the real client composition against owned API fixtures, including delayed responses, company-switch cancellation, form cleanup, and mobile keyboard navigation.

`test:integration` owns a temporary PostgreSQL container, runs the real API jar with separate migration/runtime credentials, and tests password errors, MFA enrollment, recovery codes, company switching, language selection, and completed server logout. It requires free loopback ports 18080 and 4174, tears down its processes/container, and keeps local diagnostics under `.work/dashboard-integration`. The fixture disables external providers, mail, storage, and scanning; no real OIDC provider, delivery service, or production deployment is exercised. CI runs both browser suites.

The development proxy forwards `/api`, `/oauth2`, and `/login/oauth2` to `127.0.0.1:8080`; `HRIS_API_PROXY` can select another development API origin. No authentication token is exposed to JavaScript configuration. Use the backend's documented local cookie configuration for HTTP development and HTTPS with secure cookies for deployment. Production output is in `dist`; hosting must forward these paths and serve the SPA entry for client routes.
