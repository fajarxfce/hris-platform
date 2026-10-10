# Dashboard design

Follow Azure Portal shell and resource interactions using Fluent UI v9. Use an Azure blue accent with semantic tokens, light/dark themes, Segoe UI/system font fallback, and concise English/Indonesian copy.

The persistent header owns permitted-resource search, company switcher, notifications, theme, and account. A collapsible sidebar groups modules. The workspace has breadcrumb, title/status, command bar, and content. Lists have server filters/sort/pagination, optional bulk actions, and resource links. Quick inspection uses a side panel; employee profiles and payroll runs use routed detail pages with tabs. Setup/import/payroll use staged workflows.

Shared components: AppPortalShell, AppPageHeader, AppCommandBar, AppFilterBar, AppDataTable, AppDetailsPanel, AppStatusBadge, and form controls. All support keyboard navigation, focus restoration, semantic status, and responsive layouts. Tables default to 50 rows; only genuinely long client lists need virtualization.

Pages consume display state and dispatch actions. Query/cache ownership includes account and company. Company switching cancels outstanding queries and removes obsolete view data. Filters live in the URL. Dirty forms require an explicit navigation decision. Polling/listeners stop when inactive.

Primary screens: organization and employee directory; roster and attendance exceptions; leave requests/balances; approval inbox; expense claims/payments; payroll periods/runs/reconciliation; documents; announcements; reports; settings/audit/jobs.

Reference: https://learn.microsoft.com/en-us/azure/azure-portal/azure-portal-overview

## Implemented foundation

`apps/dashboard` provides password sign-in, configured SSO entry links, session bootstrap, authenticator enrollment/verification, and explicit acknowledgement of one-use recovery codes. The portal includes current-company selection, an authorized company overview, organization directories and parent details, employee directories and employment history, company/group headcount reports, audit metadata search, client policy review, a job monitor with cancellation, responsive navigation, and English/Indonesian light/dark interfaces. Other business resource screens listed above remain subsequent work; the overview does not invent statistics.

Feature datasources validate transport DTOs; repositories map them inside the HTTP failure boundary; use cases own policy; the identity controller owns requests and presentation state. Pages render that state. Shared `AppXxx` components wrap Fluent controls. The import checker rejects domain/framework dependencies, data access from presentation, repository peers, and use-case peers. These static checks complement review of orchestration and resource ownership.

The same-origin transport uses session cookies and fresh CSRF values for commands. It bounds responses to 1 MiB, owns request deadlines, propagates cancellation, and never retries a mutation automatically. Controllers clear private cached state during company/account changes and reject obsolete responses. Foreground/reconnect revalidation uses the same request owner. Forms clear passwords after submission, and enrollment secrets/recovery codes are not placed in browser storage. An expired enrollment requires an explicit new setup operation.

Tabster is pinned to `8.8.0`, which shares Keyborg 2 with the installed Fluent focus adapter. Tabster `8.8.1` changes that dependency to Keyborg 3 while Fluent `react-tabster@9.26.18` still requires Keyborg 2. Loading both can collide in their window-global instance registry; browser validation exposed incorrect-disposal diagnostics. Remove the override when Fluent and Tabster agree on one Keyborg major, after rerunning focus and lifecycle tests.

Application messages translate stable failure codes; unknown codes receive a generic localized message and an optional correlation reference. Backend diagnostic text is never displayed. Language and theme controls remain available before authentication.

## Company and group headcount

`/reports/headcount?asOf=2026-10-01` loads the [group headcount API](reporting.md), initially selecting the active company. The default date uses that company's time zone. The controlled form can select one to 32 companies and keeps edits separate from the applied URL filters. Apply stores the date and a sorted `companies` selection; browser history restores both. The report shows distinct people, employment records, status, and contract totals with the source definition's historical limits. A group also displays company-level counts. The unique-person total comes from the backend and is never summed from company buckets.

The route is loaded on demand. Its controller owns one company-selection/date request and rejects obsolete results. Applied filter changes, account/company changes, route disposal, and identity revalidation remove the old report and cancel pending work. Refresh clears old counts before loading; access failures cannot leave previous or partial company values visible. Language/theme changes only reformat the current view. Navigation reflects the active company's `reports.read` and `people.read` permissions, while the server independently authorizes every selected company. The selector lists current memberships without treating them as report grants. Direct links still pass through validation and server authorization. Response mapping checks the exact selection, date, definition, company bucket sums, and distinct-person bounds before displaying immutable values.

`AppCommandBar`, `AppMetricCard`, `AppPropertyList`, `AppMultiSelect`, and `AppResourceTable` provide reusable Fluent layouts. Native form controls inherit the selected color scheme. Filters use React Hook Form's controlled adapters so Back/Forward navigation updates the displayed values. The company selector supports keyboard interaction and validation focus. Tables keep numeric columns aligned and contain horizontal overflow within their panel.

## Audit explorer

`/administration/audit` uses the [company audit API](audit.md). The form supports explicit UTC start/end times, action/resource codes, and exact actor/resource IDs. Blank dates use the server's default window. A resource table shows metadata, and `AppDetailsPanel` shows full IDs and timestamps without another request or profile lookup. Action codes remain exact searchable identifiers; interface labels and failures are localized. The shared table and panel use Fluent's focus restoration attributes for keyboard navigation.

The controller retains one page of at most 50 events. Older-page navigation writes the exact server-resolved window, filters, company scope, and cursor to the URL. Browser Back/Forward reloads the relevant page. First page retains that window; Reset filters returns to the default search. Form edits use UTC seconds, while pagination preserves server microseconds. Response mapping verifies scope, window, filter matches, ordering, uniqueness, size, and cursor progress. Clock instants with nanoseconds are compared at database precision.

There is no polling or cumulative page cache. Refresh, filter changes, company/account changes, and unmount cancel previous requests and remove the page and details. Stale successes and failures are ignored. A company change drops a cursor belonging to the previous company. Revoked access cannot leave old events visible. Navigation visibility is only a client convenience; the use case and API independently enforce audit permission.

## Client policy review

`/settings/client-policy` shows the [company settings snapshot](client-policy.md): effective modules, minimum builds, maintenance status, evaluation time, and the latest configured revision. A scheduled head remains distinct from the revision currently enforced by the server. The revision form accepts an explicit immutable revision from 0 to 9999; its company and selection live in the URL, and Back/Forward restores the controlled input. The latest revision needs one settings request; a named historical revision uses a second authorized read.

The feature requires `settings.manage`. Its controller owns one pending review, cancels both stages on replacement/disposal, and clears configuration on refresh or access failure. Company changes discard the previous company's revision selection. Repository mapping validates bounded builds, distinct modules, maintenance intervals, version relationships, and coherent effective values before creating immutable entities. Locale/theme changes only reformat the loaded snapshot. Refresh is explicit; this screen has no polling or local policy cache. Configuration editing remains subsequent work.

## Job monitor

`/administration/jobs` uses the [company jobs API](jobs.md). Members retain access to their own jobs; `jobs.read` selects the company-wide list. A bounded page shows kind, status, progress, and creation time. Exact continuation timestamps/IDs and an optional selected job live in the URL. Details load the canonical job and show schedule, eligibility, progress mode, attempts, version, and safe failure information. Unknown job kinds remain readable without assuming new actions are supported.

The list and details own separate cancellable requests. The details controller requires explicit confirmation before one observed-version cancellation. A missing response clears obsolete details and requires a status read; there is no automatic command retry. An acknowledgement displays cancellation requested until a worker reaches a terminal state. Accepted detail snapshots update matching visible rows by version, preserving their DOM and keyboard focus. Refresh and disposal clear owned data; account/company changes reject obsolete work. `AppConfirmationDialog` uses Fluent's controlled dialog and trigger for focus ownership. There is no polling or accumulating cache.

## Organization directory and unit details

`/organization/units` reads the [organization API](modules/people.md) with a literal name/code query, unit type, active status, and bounded `KIND:CODE` continuation. The controlled filters remain separate from applied URL values; Back/Forward restores them. The page retains at most 50 units. Branches, departments, positions, and cost centers use one resource table. The client validates unique identities, kind/code keys, selected filters, and cursor progress without substituting JavaScript order for PostgreSQL collation.

A unit opens `/organization/units/{id}`. One authorized API read returns the unit and its current parent under a common structure guard. Opening that parent is an explicit navigation and new read; the browser does not recursively load a tree or fetch every parent to decorate the directory. Inactive references remain visible. Current organization metadata does not claim the historical meaning of an employment revision.

Both routes require `company.read`; `company.manage` alone does not grant read access. Direct links are pinned to the selected company before I/O, and switching company from a detail returns to its own directory without retaining the former ID, filters, or cursor. Each controller owns one cancellable request, clears data on refresh/disposal, rejects late results and failures, and retries only on request. Parent and unit disappear together on failure. Locale/theme changes only reformat current values. Shared Fluent tables, property lists, and `AppSelect` provide responsive, labelled controls. Organization editing remains subsequent work.

## Employee directory and employment details

`/people/employees` reads the [scoped employee API](modules/people.md) with an explicit effective date, initially today in the selected company's time zone. The controlled form applies a name/employee-number query of up to 120 characters. Company, date, query, and employee-number continuation live in the URL; Back/Forward restores applied filters. Pages contain at most 50 records without an accumulating cache. Employee-number order belongs to the database collation; the client verifies bounded, unique records and the returned continuation without imposing a different JavaScript order.

An employee opens a routed detail page with an overview and, for `people.read`, an employment-history tab. Company-wide, current direct-report, and self visibility remain server decisions. Historical dates never restore former-manager authority. The overview shows the effective employment terms and the current public name/email projection. Birth dates, nationality, account linkage, and organization IDs are not retained by this client projection. Private profiles, organization labels, and employment editing are subsequent workflows.

History loads only when its tab is opened and has its own bounded request owner and revision cursor. Revision zero, gaps, scheduled changes, and immutable cancellations are preserved. A Fluent detail panel displays the selected revision's terms, reason, recorded time, and cancellation evidence without another request. The overview and history are independently authorized live reads; they do not claim a common transaction snapshot. History navigation never treats the latest aggregate version as the revision applied on a historical date.

Directory, detail, and history controllers clear their owned data before refresh and on disposal, ignore late successes/failures, and perform no polling or automatic retry. A history response reporting loss of the enclosing company/session scope also clears the employee overview. A history-only permission rejection does not infer loss of a separately authorized public employee projection. Direct links are pinned to the current company/date before acquisition. A company switch from an employee detail returns to that company's directory, discarding the previous employee and cursors. URL filters remain distinct from unsubmitted form edits. Locale/theme changes only reformat retained values. `AppTabs` uses Fluent tab selection and labelled panels; revision drawers restore keyboard focus through the shared table/panel components.

## Commands

The shared HTTP transport sends `X-HRIS-Client-Platform: WEB` with the compiled `HRIS_DASHBOARD_BUILD` (default `1`, bounded integer). Set this build-time value consistently for distributed dashboard artifacts. Company policy can require an update or pause business requests during maintenance; availability errors have English/Indonesian messages. Policy editing and company preflight screens remain subsequent business workflows.

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

`test:integration` owns a temporary PostgreSQL container, runs the real API jar with separate migration/runtime credentials, and tests password errors, MFA enrollment, recovery codes, company switching, literal organization search and parent navigation, employee directory/detail/history reads, company/group headcount including an empty company, filtered audit metadata/details, configured/effective client policy and history, scheduled job reads/cancellation, language selection, and completed server logout. It requires free loopback ports 18080 and 4174, tears down its processes/container, and keeps local diagnostics under `.work/dashboard-integration`. The fixture disables external providers, mail, storage, and scanning; no real OIDC provider, delivery service, or production deployment is exercised. No worker processes its scheduled announcement fixture. CI runs both browser suites.

Group headcount validation passed the architecture/Biome/TypeScript checks, 33 unit/controller tests, a production build, 12 browser tests, and one real-API/PostgreSQL browser test. Browser checks cover pending-selection cancellation, scope changes, Back/Forward filters, localized failures, and light/dark responsive layouts. The initial browser fixtures used the wrong ARIA role for Fluent's multiselect entries; they now select `menuitemcheckbox`. The report route remains lazy-loaded. These checks do not claim device validation or runtime performance benchmarks.

The development proxy forwards `/api`, `/oauth2`, and `/login/oauth2` to `127.0.0.1:8080`; `HRIS_API_PROXY` can select another development API origin. No authentication token is exposed to JavaScript configuration. Use the backend's documented local cookie configuration for HTTP development and HTTPS with secure cookies for deployment. Production output is in `dist`; hosting must forward these paths and serve the SPA entry for client routes.
