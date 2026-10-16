# Reviewed roster editing

Bulk editing applies one instruction to explicit employees and dates. It uses the
existing daily roster history, receipts and synchronization pipeline. It does not
start a background job or change weekly assignments, captured attendance or closed
snapshots.

## Selection and review

`POST /api/v1/companies/{companyId}/workforce/roster/bulk/review` requires
`workforce.read`. The selection contains:

- `employeeIds`: 1–50 unique IDs selected from the scoped employee directory.
- `dates`: 1–31 unique work dates spanning at most 31 consecutive calendar days.
- `mode`: `SHIFT`, `DAY_OFF` or `CALENDAR`.
- `shift`: an observed `{id, version}`, required only for `SHIFT`.

The cross-product is limited to 200 employee/date cells. Dates must be in years
1900–9999. A directory filter never implicitly selects employees from another page.

The review returns the normalized selection, minimal employee references and each
date's before/after schedule, observed roster version and change status. It also
returns a content `revision` and `changeCount`. An active null-shift override is an
explicit day off; `CALENDAR` instead withdraws an active override and restores its
holiday, weekly pattern or unassigned date. Already identical overrides and dates
already following the calendar count as unchanged.

All selected dates require working employment. Selected shift revisions and local
time rules are validated. Processing and closed months cannot be edited. Review
holds shared schedule, People and access guards; it creates no roster, period or
receipt and reserves no later eligibility. Current access and MFA are checked
before acquisition and again before releasing the result.

## Atomic command

`POST /api/v1/companies/{companyId}/workforce/roster/bulk` requires
`workforce.manage`, `Idempotency-Key` and:

```json
{
  "selection": {
    "employeeIds": ["00000000-0000-4000-8000-000000000001"],
    "dates": ["2026-10-02", "2026-10-03"],
    "mode": "DAY_OFF"
  },
  "reviewRevision": "<revision returned by review>",
  "reason": "Adjusted team coverage"
}
```

The command detaches and normalizes its input before any wait. The operation,
exclusive schedule guard, chronologically ordered month guards, People, company,
membership and account guards protect the write. Current permission and MFA are
rechecked after waiting. Under those guards it reacquires employment and calendar
facts and compares them with the reviewed revision.

`roster_bulk_changed` requires another review. A changed shift retains
`shift_changed`; missing or newly ineligible resources retain their existing
classified errors. `roster_bulk_unchanged` indicates that no selected date would
change. None of these outcomes consumes the operation.

Every changed head, immutable revision, per-day journal entry, aggregate journal
entry, receipt and captured sync invalidation commits in one transaction. A failed
cell, late failure, expired assurance or cancellation rolls back the whole command.
Unchanged cells are not rewritten. Resets retain their existing IDs and advance
their versions; later saves reactivate those heads rather than creating version
zero again.

The normal mutation receipt identifies the operation ID at revision zero. Explicit
recovery sends the original selection, revision, reason and key. A stored receipt
requires current command authority but does not repeat mutable period, employment
or shift eligibility. It never reapplies an older batch over newer roster edits.

## Client and deployment

The dashboard must retain the original command after an unknown response, keep
review separate from submission and cancel reads when company/account ownership
changes. A fresh review is needed after changing a selection. Confirmation shows
both changed and unchanged dates; a zero-change review cannot be submitted.

Employee mobile clients consume ordinary `ROSTER_DAYS` changes. Reset compatibility
must be delivered before enabling reset controls, as described in the
[roster reset contract](modules/workforce.md#roster-reset-contract). This workflow
adds no schema migration beyond the retained-head model in V87. The interactive
limit bounds one transaction; larger scheduling changes use explicit selections
or effective weekly assignments, not an unbounded synchronous loop.
