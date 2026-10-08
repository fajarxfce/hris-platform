# Product specification

## Decisions

Self-hosted, one group with multiple companies, up to 5,000 employees initially. Kotlin/Spring Boot backend; React/TypeScript/Fluent UI dashboard with Azure Portal layout. Web serves administrators, HR, managers, finance, and auditors. Employee self-service belongs to the subsequent Compose application.

Support office schedules, rotating/overnight shifts, remote work, and field assignments. Offline attendance always requires supervisor verification before payroll. Payroll is IDR and covers monthly permanent/fixed-term employees including expatriates with verified resident/nonresident tax status. Tax treaty, tax equalization, daily/piece-rate payroll, and sector-specific tax incentives need separate rule packs. Payment execution consists of export and reconciliation, not direct bank instructions.

## Modules

- [Identity and access](modules/identity.md)
- [Organization and people](modules/people.md)
- [Workforce](modules/workforce.md)
- [Leave](modules/leave.md)
- [Approvals](modules/approvals.md)
- [Expenses](modules/expenses.md)
- [Payroll](modules/payroll.md)
- [Documents](modules/documents.md)
- [Communications](modules/communications.md)
- [Reporting](modules/reporting.md)
- [Administration](modules/administration.md)

## Shared contracts

Company, person, employment, account, and operation IDs remain distinct. Company-scoped resources cannot reference a different company's data. Effective-dated policies and employment history never silently rewrite historical calculations. Submitted requests retain their policy snapshots. Final payroll is corrected through an explicitly approved adjustment/amendment.

Money never uses binary floating point. Timezone, payroll cutoff, work date, and captured/received event timestamps are explicit. Controllers return stable error codes and correlation IDs without technical exception messages.

The initial demonstration data must be fictional. No credentials, private documents, or Firebase service accounts enter Git.
