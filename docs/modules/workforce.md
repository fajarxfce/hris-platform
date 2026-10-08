# Workforce

Own work calendars, holidays, shift templates, rosters, immutable attendance events, corrections, verification, overtime, and period closing.

Shift IDs and work dates anchor overnight events. Raw timestamps retain captured and server-received times and device evidence. Offline events remain pending verification. HR corrections require actor/reason and append an adjustment. Location evidence is evaluated against the applicable policy.

Overtime separates requested/actual/approved time. A payroll cutoff requires resolution of relevant exceptions. Workforce exposes factual time; leave remains separately owned and reporting/payroll combine through repository contracts.

Screens: roster, daily attendance, exceptions, offline verification, corrections, overtime, and closing.

Acceptance: duplicate/offline events, checkout across midnight, timezone transitions, overlapping roster, immutable corrections, concurrent verification, and unverified records excluded from payroll.

## Implementation status

Versioned shift templates, effective weekly assignments, explicit daily roster overrides, holidays, and scoped calendar reads are implemented. Published schedules store shift snapshots; changing a template does not rewrite them. Selecting a shift requires its observed version. Assignment, roster, shift, and holiday revisions are append-only and audited.

Calendar precedence is explicit roster, active holiday, then the effective weekly pattern. A null roster shift means an explicit day off. Calendars distinguish work, off, and unassigned dates. Overnight shifts retain their starting work date. Nonexistent DST local times return a dated validation failure; ambiguous local times use the earlier offset and elapsed UTC minutes. Calendar reads are bounded to 62 days and holiday queries to 366 days.

Attendance capture/review/corrections, overtime, period closing, roster reset/bulk editing, and lifecycle cleanup remain planned. Calendar behavior is not yet a completed time-and-attendance workflow.
