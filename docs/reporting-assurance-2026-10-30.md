# Reporting export policy completion

The Reporting audit reproduced seven late-MFA failures in export commands and
downloads. `ManageReportExports.checkSources` and `ReadReportExports.checkContent`
validated session assurance before reading each company's effective policy, then
returned without checking the proof again. A policy acquisition could consume the
remaining proof lifetime, including the last acquisition after a journal write or
private object read.

Both use cases now retain their already-guarded `AccountAccess` values and check
every selected source at one final completion instant. The account/company/member
and policy guards remain held. No additional database query, permission, schema,
endpoint, expiry extension or worker MFA requirement is introduced. Stored source
grants still intersect original/current authority; owner history and cancellation
retain their independent recovery access.

The actual SQL baseline has seven genuine failures:

- Start, start receipt replay, resume and resume receipt replay accepted expired
  proof after their final effective-policy acquisition.
- Download preparation returned private metadata after the same expiry.
- Range preparation rejected eventually, but had already read one private object.
- Range completion returned acquired bytes after its final policy check expired.

The corrected cases assert `mfa_required`, unchanged export/job state after failed
commands, recovery with the original operation and renewed proof, unchanged
download metadata/content and zero storage reads after rejected preparation.
The post-storage case acquires one object and cannot release its bytes. The
application clock moves only two seconds around the proof boundary; export
retention and database time remain unchanged.

The first compilation attempt used an override parameter name different from its
interface, which the existing `-Werror` rule rejects. It executed no tests and is
excluded from the defect count. After correcting that fixture signature, all seven
baseline cases failed in 34.233s. All seven focused API, 52 Reporting domain and
61 repository cases then passed with architecture/format checks in 44.695s.

[Measurements](measurements/reporting-assurance-2026-10-30.json) retain exact
commands, baseline classifications, broader reports, actual browser acceptance and
package hashes. The [requirement review](reporting-feature-review-2026-10-30.md)
maps the complete module. Full source CI remains a separate gate; the private
runtime still serves the accepted metadata image, without this correction.

## Completed local acceptance

The broader run passed 186 API cases, eight active report-export worker cases,
52 Reporting domain and 61 repository cases, architecture/format checks and both
application packages in 349.481s. The separately accepted one-hour retention case
was explicitly skipped. The actual API/PostgreSQL/worker/Garage/browser workflow
passed in 45.417s including setup (18.8s scenario), with real response-loss recovery,
retained CSV facts and current owner/source denial. Its owned ports and database
metadata were removed. Final source CI follows the running Administration run.
