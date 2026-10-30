# Identity requirement review — 30 October 2026

All five initial Identity requirements and eight detailed sections are accepted
against the unchanged implementation and its actual full-CI results. The module
and dashboard Identity Git trees match source `8014d733` exactly. This review
introduces no new credentials, account changes or client implementation.

| Requirement | Source and matching behavior |
| --- | --- |
| `identity.01`: accounts, sessions, invitations, OIDC, MFA, memberships, roles | Identity use cases, scoped repositories and transport chains; account/role/member, native-session, OIDC and credential-challenge API suites. Eight actual browser/API workflows include invitation SMTP and native-session replacement. |
| `identity.02`: verified issuer/subject, single-use links, secure cookie/CSRF, refresh reuse, MFA and recent proof | `SignInWithOidc`, Firebase signature/project/time/provider verification, `ConfirmAccountCredential`, `RefreshNativeSession` and session policies. OIDC tests reject matching-email/provider-role bypass, invalid claims/state and expired exchange. Native tests serialize rotation/replay and commit reuse revocation before rejecting. Credential tests exercise competing consumption and expiry during hashing/journal completion. |
| `identity.03`: explicit global/company/resource scopes and current revocation | `SaveCompanyMembership` uses original/current permission intersection and ordered account guards; direct/template grants share sensitive-grant checks. Account deactivation advances credential version. Actual tests reject self-grants, stale membership, cross-company access and removed global authority, while retaining immutable applied-role snapshots. |
| `identity.04`: users, roles, assignments, SSO configuration and sessions | Dashboard account, member, role, binding, own-session and administrative-session routes/controllers. Provider registration is deployment configuration by the detailed OIDC contract; the product exposes configured sign-in and approved bindings without storing provider secrets in settings. Real workflows exercise edits, archival, response loss, explicit recovery and independent-session isolation. |
| `identity.05`: isolation, refresh replay, invitation reuse, stale membership, CSRF and safe diagnostics | The 190 selected API cases include dedicated completion-assurance, access-race, callback/transport, rate-limit, one-use credential and native registration tests. Pure policy and data tests cover minimal projections and failure mapping; browser scenarios cover denied access, renewal, departure and ambiguous operations. |

Detailed review covers password/MFA admission and finite attempt/hash budgets;
native cookie-to-token exchange, bounded families/rotation/replay and separate
native MFA elevation; own/global session administration; bounded worker cleanup
that retains consumed-hash reuse detection; OIDC state/PKCE/nonce and exact binding;
versioned role definitions and retained membership applications; global account
administration and last-administrator protection; and invitation/recovery queues,
expiry, encrypted material, single-use confirmation and credential revocation.

Authority remains independent of role names, external email/MFA claims and feature
availability. Membership changes never activate a disabled global account. Account
and company grants stay distinct, and a new grant cannot authorize an action that
was already waiting. Current MFA is checked after acquisition or through command
completion where applicable. Cleanup, natural session revocation and refresh reuse
retain their specific semantics rather than borrowing company command policy.

[Measurements](measurements/identity-feature-review-2026-10-30.json) retain the
32 API suites (190 cases), nine domain suites (35), six data suites (22), two worker
suites (17), and 95 matching Chromium scenarios, including eight actual API/browser
workflows. Downloaded Gradle HTML and embedded Playwright JSON were inspected from
[full CI](https://github.com/fajarxfce/hris-platform/actions/runs/41005223374);
shared suites repeated across four shards are counted once. All selected cases
passed without skips, retries or flaky outcomes. Actual Google/Home and Android
registration observations retain their separately recorded scope; no new provider
call, phone interaction or live session mutation was performed for this review.

Continue Documents, the remaining module contracts and full Mobile review in the
[feature audit](feature-audit-2026-10-30.md). Accepted Identity behavior does not
close the full objective.
