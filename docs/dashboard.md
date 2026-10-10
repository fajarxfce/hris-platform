# Dashboard design

Follow Azure Portal shell and resource interactions using Fluent UI v9. Use an Azure blue accent with semantic tokens, light/dark themes, Segoe UI/system font fallback, and concise English/Indonesian copy.

The persistent header owns permitted-resource search, company switcher, notifications, theme, and account. A collapsible sidebar groups modules. The workspace has breadcrumb, title/status, command bar, and content. Lists have server filters/sort/pagination, optional bulk actions, and resource links. Quick inspection uses a side panel; employee profiles and payroll runs use routed detail pages with tabs. Setup/import/payroll use staged workflows.

Shared components: AppPortalShell, AppPageHeader, AppCommandBar, AppFilterBar, AppDataTable, AppDetailsPanel, AppStatusBadge, and form controls. All support keyboard navigation, focus restoration, semantic status, and responsive layouts. Tables default to 50 rows; only genuinely long client lists need virtualization.

Pages consume display state and dispatch actions. Query/cache ownership includes account and company. Company switching cancels outstanding queries and removes obsolete view data. Filters live in the URL. Dirty forms require an explicit navigation decision. Polling/listeners stop when inactive.

Primary screens: organization and employee directory; roster and attendance exceptions; leave requests/balances; approval inbox; expense claims/payments; payroll periods/runs/reconciliation; documents; announcements; reports; settings/audit/jobs.

Reference: https://learn.microsoft.com/en-us/azure/azure-portal/azure-portal-overview

## Implemented foundation

`apps/dashboard` provides password sign-in, configured SSO entry links, session bootstrap, authenticator enrollment/verification, and explicit acknowledgement of one-use recovery codes. The portal includes current-company selection, an authorized company overview, organization directories, parent details and editing, employee directories and employment history, separately authorized personal profiles with editing/history, company/group headcount reports, audit metadata search, client policy review, a job monitor with cancellation, responsive navigation, and English/Indonesian light/dark interfaces. Other business resource screens listed above remain subsequent work; the overview does not invent statistics.

Feature datasources validate transport DTOs; repositories map them inside the HTTP failure boundary; use cases own policy; the identity controller owns requests and presentation state. Pages render that state. Shared `AppXxx` components wrap Fluent controls. The import checker rejects domain/framework dependencies, data access from presentation, repository peers, and use-case peers. These static checks complement review of orchestration and resource ownership.

The same-origin transport uses session cookies and fresh CSRF values for commands. It bounds responses to 1 MiB, owns request deadlines, propagates cancellation, and never retries a mutation automatically. Controllers clear private cached state during company/account changes and reject obsolete responses. Foreground/reconnect revalidation uses the same request owner. Forms clear passwords after submission, and enrollment secrets/recovery codes are not placed in browser storage. An expired enrollment requires an explicit new setup operation.

Tabster is pinned to `8.8.0`, which shares Keyborg 2 with the installed Fluent focus adapter. Tabster `8.8.1` changes that dependency to Keyborg 3 while Fluent `react-tabster@9.26.18` still requires Keyborg 2. Loading both can collide in their window-global instance registry; browser validation exposed incorrect-disposal diagnostics. Remove the override when Fluent and Tabster agree on one Keyborg major, after rerunning focus and lifecycle tests.

Application messages translate stable failure codes; unknown codes receive a generic localized message and an optional correlation reference. Backend diagnostic text is never displayed. Language and theme controls remain available before authentication.

## Session verification and retained forms

The application owns the verification dialog across workspace removal. Closing clears its contents immediately, pending content changes keep focus inside, and cancellation restores the triggering control. Feature portals mount inside the retained workspace. Detail and confirmation overlays suspend with its visibility, so pending or failed identity checks cannot leave a private drawer visible or trap focus away from recovery controls.

Foreground/reconnect checks retain one previously authorized workspace in memory while its content is hidden and inert. An unchanged account, company membership set, company metadata, platform grants, and selected-company grants restore the same mounted feature owners and controlled inputs. Equivalent ordering does not invalidate that scope. A changed account/company/permission partition, known revocation, logout, or application disposal removes it and clears private query state. Business values are not refreshed by this identity check; each feature retains its explicit Refresh action.

Expired MFA responses deliberately redact memberships and grants. The client does not interpret those redacted arrays as an authorized empty scope or restore the previous view from them. A Fluent verification dialog keeps the workspace hidden until a complete session read and live company-access read both succeed. Configured accounts can also request verification from the header or a supported authorization failure. Cancelling an optional challenge rechecks the session; a mandatory challenge cannot be dismissed into an unverified workspace. Initial enrollment and one-use recovery-code acknowledgement use the same form controls as initial sign-in.

Verification owns no business command and never resubmits one. If proof delivery is uncertain, Check session reads the current cookie session before another proof is sent. A successful proof followed by a failed access read retries only the read. Revoked credentials or company access purge the former form. Verification codes clear after submission and on stage changes; retained form state is not written to browser storage. Feature bindings can report a credential/company-scope failure through the workspace session contract; the identity owner revalidates access without retrying the feature command.

The authorized workspace owns one pending navigation decision. Editors declare dirty, pending, or unconfirmed changes; route navigation, Back, company selection, and header logout require an explicit Leave or Stay decision. Reload/close uses the browser's native unsaved-changes prompt. Identity checks suspend pending decisions, and scope changes discard them with the editor. A security/recovery screen keeps sign-out available. These prompts protect browser memory only; confirming departure or closing the process does not retain a recoverable draft or prove that a pending server operation was cancelled.

The keyboard skip control moves focus to the owned main content element without adding a native hash-history entry. This keeps browser history inside the router's navigation protection.

## Company and group headcount

`/reports/headcount?asOf=2026-10-01` loads the [group headcount API](reporting.md), initially selecting the active company. The default date uses that company's time zone. The controlled form can select one to 32 companies and keeps edits separate from the applied URL filters. Apply stores the date and a sorted `companies` selection; browser history restores both. The report shows distinct people, employment records, status, and contract totals with the source definition's historical limits. A group also displays company-level counts. The unique-person total comes from the backend and is never summed from company buckets.

The route is loaded on demand. Its controller owns one company-selection/date request and rejects obsolete results. Applied filter changes, account/company changes, route disposal, and changed identity scope remove the old report and cancel pending work. An unchanged foreground identity check preserves the mounted view while it is hidden pending authorization. Refresh clears old counts before loading; access failures cannot leave previous or partial company values visible. Language/theme changes only reformat the current view. Navigation reflects the active company's `reports.read` and `people.read` permissions, while the server independently authorizes every selected company. The selector lists current memberships without treating them as report grants. Direct links still pass through validation and server authorization. Response mapping checks the exact selection, date, definition, company bucket sums, and distinct-person bounds before displaying immutable values.

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

Both routes require `company.read`; `company.manage` alone does not grant read access. Direct links are pinned to the selected company before I/O, and switching company from a detail returns to its own directory without retaining the former ID, filters, or cursor. Each controller owns one cancellable request, clears data on refresh/disposal, rejects late results and failures, and retries only on request. Parent and unit disappear together on failure. Locale/theme changes only reformat current values. Shared Fluent tables, property lists, and `AppSelect` provide responsive, labelled controls.

`/organization/units/new` and `/organization/units/{id}/edit` require both read and management grants. The editor supports name/code, parent selection, branch time zone, and active status; an existing unit's kind is immutable. The parent picker owns a separate bounded page with explicit search, type/status filters, and continuation. It excludes the edited unit and unsupported parent kinds without loading the complete organization. The backend independently validates current ancestry, depth, kind, availability, and IANA zones.

Save captures one immutable payload, the loaded version, and an operation ID. A known pre-commit rejection permits correction; a stale version requires an explicit, protected reload. A missing or invalid response keeps the submission read-only until an explicit retry obtains its original receipt. Once an outcome is uncertain, a later MFA or version rejection does not make editing safe. Verification never repeats Save. Acknowledgement and subsequent detail loading are separate, so a failed detail read cannot replay a successful command. Pending work is aborted on disposal and late results cannot restore a previous company. Cancellation of the browser request does not imply server rollback.

## Employee directory and employment details

`/people/employees` reads the [scoped employee API](modules/people.md) with an explicit effective date, initially today in the selected company's time zone. The controlled form applies a name/employee-number query of up to 120 characters. Company, date, query, and employee-number continuation live in the URL; Back/Forward restores applied filters. Pages contain at most 50 records without an accumulating cache. Employee-number order belongs to the database collation; the client verifies bounded, unique records and the returned continuation without imposing a different JavaScript order.

An employee opens a routed detail page with an overview and, for `people.read`, an employment-history tab. Company-wide, current direct-report, and self visibility remain server decisions. Historical dates never restore former-manager authority. The overview shows the effective employment terms and the current public name/email projection. Birth dates, nationality, account linkage, and organization IDs are not retained by this client projection. Authorized users can open a separate personal profile. Organization labels and employment editing remain subsequent workflows.

History loads only when its tab is opened and has its own bounded request owner and descending revision cursor. Next pages contain revisions strictly below the previous page's last revision; zero terminates the sequence. Revision zero, gaps, scheduled changes, and immutable cancellations are preserved. A Fluent detail panel displays the selected revision's terms, reason, recorded time, and cancellation evidence without another request. The overview and history are independently authorized live reads; they do not claim a common transaction snapshot. History navigation never treats the latest aggregate version as the revision applied on a historical date.

Directory, detail, and history controllers clear their owned data before refresh and on disposal, ignore late successes/failures, and perform no polling or automatic retry. A history response reporting loss of the enclosing company/session scope also clears the employee overview. A history-only permission rejection does not infer loss of a separately authorized public employee projection. Direct links are pinned to the current company/date before acquisition. A company switch from an employee detail returns to that company's directory, discarding the previous employee and cursors. URL filters remain distinct from unsubmitted form edits. Locale/theme changes only reformat retained values. `AppTabs` uses Fluent tab selection and labelled panels; revision drawers restore keyboard focus through the shared table/panel components.

## Employee creation

`/people/employees/new` requires `people.manage`. The form captures a new person's legal name, optional birth date/email, nationality, an employee number, initial employment terms, optional organization/manager assignments, and a reason. The first effective date is the start date. The shared pure person policy normalizes profile fields; contract and date policies reject invalid proposals before I/O. Initial account binding and lifecycle task assignment remain separate workflows.

Assignment drawers call organization and employee domain use cases. They open only on request, retain one page of at most 50 results, and use explicit search and continuation. Organization choices require `company.read` and an active matching kind; manager choices require a readable employee scope and working employment on the proposed start date. The server rechecks assignment eligibility under its own guards. TanStack Query owns the consumed abort signal with retries, foreground/reconnect refresh, and retained inactive caches disabled. Changing scope or disposing the drawer cancels pending work; a failed refresh removes the displayed results.

The creation controller owns two resource identifiers and one frozen command per attempt. Its receipt must identify the employee at version zero. A definite first rejection allows correction with a new operation key; an uncertain response pins the original identifiers, key, and payload through later verification or rejection. Only explicit retry can resolve it. The form uses the shared departure protection and workspace verification boundary. The acknowledged result is separate from any subsequent detail read, whose effective date includes a future start. An operator without directory access can create an unassigned employee without acquiring employee/profile directories. Draft personal data and reasons stay in memory and are discarded on departure or revoked scope.

## Personal profiles

`/people/employees/{id}/profile` acquires the current sensitive profile through its separate API. Directory and employment screens never preload this data. `people.profile.read` permits sensitive reads; `people.self.read` permits only a profile the server identifies as the caller's own. History requires the explicit sensitive-read grant, even when a direct URL asks for it. Current membership names can label the owning company without another cross-company request. Viewing a shared person never grants management of the owning company.

The overview shows legal name, birth date, nationality, email, ownership, and account/person identifiers. Its version belongs to the person profile, independently of the employment version or selected employment date. The optional history tab loads at most 50 immutable revisions with an ascending cursor. Null initial attribution, historical account linkage, reasons, and exact recording times remain intact. First/next and Back/Forward navigation use the URL. Profile and history are separate authorized live reads, not a claimed common transaction snapshot. Invalidating sensitive history access clears the enclosing private overview; refresh and disposal remove old values and cancel pending work.

`/people/employees/{id}/profile/edit` requires readable profile access, `people.profile.manage`, and the owning company. It edits only personal fields with a required reason. Account linkage and ownership are not form inputs. Blank optional dates/emails map explicitly to null; names/reasons are trimmed, nationality is uppercase, and email is lowercase. The server validates recognized ISO country codes and birth-date policy. Stable field errors receive localized messages without rendering technical details.

Each submission captures the loaded profile version, person ID, payload, and one operation ID. Receipt mapping expects that person ID and the next profile version, not the employee ID. Ambiguous saves remain immutable until explicit recovery; a later MFA/version rejection cannot authorize a different payload. Version conflicts require a protected reload. Verification, subsequent detail reads, and business submission have separate owners, and none automatically repeats a mutation. Shared navigation protection covers dirty or pending changes. Sensitive fields, reasons, and command snapshots stay in memory and disappear on disposal or scope revocation.

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

`test:integration` owns a temporary PostgreSQL container, runs the real API jar with separate migration/runtime credentials, and tests password errors, MFA enrollment, recovery codes, explicit MFA renewal with CSRF rotation and retained inputs, company switching, literal organization search and parent navigation, organization creation with a deliberately lost committed response and explicit receipt recovery, versioned deactivation, employee creation with organization assignments and lost-response receipt recovery, employee directory/detail/history reads, personal profile editing and history with an unchanged employment version, company/group headcount including an empty company, filtered audit metadata/details, configured/effective client policy and history, scheduled job reads/cancellation, language selection, and completed server logout. It requires free loopback ports 18080 and 4174, tears down its processes/container, and keeps local diagnostics under `.work/dashboard-integration`. The fixture disables external providers, mail, storage, and scanning; no real OIDC provider, delivery service, or production deployment is exercised. No worker processes its scheduled announcement fixture. CI runs both browser suites.

Group headcount validation passed the architecture/Biome/TypeScript checks, 33 unit/controller tests, a production build, 12 browser tests, and one real-API/PostgreSQL browser test. Browser checks cover pending-selection cancellation, scope changes, Back/Forward filters, localized failures, and light/dark responsive layouts. The initial browser fixtures used the wrong ARIA role for Fluent's multiselect entries; they now select `menuitemcheckbox`. The report route remains lazy-loaded. These checks do not claim device validation or runtime performance benchmarks.

The development proxy forwards `/api`, `/oauth2`, and `/login/oauth2` to `127.0.0.1:8080`; `HRIS_API_PROXY` can select another development API origin. No authentication token is exposed to JavaScript configuration. Use the backend's documented local cookie configuration for HTTP development and HTTPS with secure cookies for deployment. Production output is in `dist`; hosting must forward these paths and serve the SPA entry for client routes.
