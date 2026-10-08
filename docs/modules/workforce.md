# Workforce

Own work calendars, holidays, shift templates, rosters, immutable attendance events, corrections, verification, overtime, and period closing.

Shift IDs and work dates anchor overnight events. Raw timestamps retain captured and server-received times and device evidence. Offline events remain pending verification. HR corrections require actor/reason and append an adjustment. Location evidence is evaluated against the applicable policy.

Overtime separates requested/actual/approved time. A payroll cutoff requires resolution of relevant exceptions. Workforce exposes factual time; leave remains separately owned and reporting/payroll combine through repository contracts.

Screens: roster, daily attendance, exceptions, offline verification, corrections, overtime, and closing.

Acceptance: duplicate/offline events, checkout across midnight, timezone transitions, overlapping roster, immutable corrections, concurrent verification, and unverified records excluded from payroll.

## Implementation status

Versioned shift templates, effective weekly assignments, explicit daily roster overrides, holidays, and scoped calendar reads are implemented. Published schedules store shift snapshots; changing a template does not rewrite them. Selecting a shift requires its observed version. Assignment, roster, shift, and holiday revisions are append-only and audited.

Calendar precedence is explicit roster, active holiday, then the effective weekly pattern. A null roster shift means an explicit day off. Calendars distinguish work, off, and unassigned dates. Overnight shifts retain their starting work date. Nonexistent DST local times return a dated validation failure; ambiguous local times use the earlier offset and elapsed UTC minutes. Calendar reads are bounded to 62 days and holiday queries to 366 days.

Attendance capture and independent verification are implemented. Server-issued capture windows last 120 seconds, bind account/employment/device, and can be consumed once. They establish recent server contact, not device attestation. A client offline flag, absent/expired proof, doubtful capture time, location violations, or an invalid punch sequence produces a pending event. Offline events never enter accepted totals before review.

The first event freezes that work date's schedule. One accepted clock-in/out pair produces elapsed minutes minus the snapshot break; incomplete/pending pairs contribute no minutes. Raw events and reviews are immutable. HR or the current manager can review, but never their own event. Competing reviewers transition once. Commands replay their original receipt even when their time window has subsequently expired.

Capture accepts up to 31 days of offline history, tolerates at most 30 seconds of future clock skew, and bounds one date to 32 raw events. Employee reads cover at most 31 days. Location evidence includes declared accuracy/mock status and is not a guarantee against a compromised device. Capture-window issuance is bounded to ten per employee per minute.

Corrections, overtime, period closing/late adjustments, roster reset/bulk editing, and lifecycle cleanup remain planned. Accepted attendance totals are factual inputs; they are not a finalized payroll result.
