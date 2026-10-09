# Payroll

Own effective compensation, component/tax classification, periods, rule packs, snapshots, calculation revisions, approval/finalization, payslips, payment export/reconciliation, and amendments.

Monthly permanent/fixed-term resident/nonresident employees, including WNA, are supported in IDR. Contract category does not determine tax status. Components include fixed/variable earnings, bonus, THR, overtime, approved unpaid leave, proration, deductions, and employer/employee contributions. Support gross/net/gross-up.

Versioned statutory policies cover PPh 21 TER/final-period reconciliation, domestic PPh 26, and applicable BPJS bases/caps/eligibility. Treat treaty/tax equalization/daily-piecework/sector incentives as separately supported rule packs. Missing required policy/input blocks calculation. Use BigDecimal with explicit statutory rounding points.

Run: draft -> calculated -> reviewed/approved -> finalized -> payment processing -> paid. Worker checkpoints per bounded batch. Freeze input versions and line-level results; input revisions invalidate approval. Finalized runs change only through referenced amendments. Payment references prevent duplicate accounting.

Screens: compensation, periods, run progress/errors, period comparison, employee detail, approval, payslips, payment batches, reconciliation.

Acceptance: official golden fixtures, resident/nonresident/year-end/start-exit cases, annualization where applicable, TER with THR/bonus, gross-up convergence, BPJS limits, rounding, cancellation/restart, parallel finalization, and historical immutability.

Sources: https://jdih.kemenkeu.go.id/dok/pp-58-tahun-2023 ; https://jdih.kemenkeu.go.id/dok/pmk-168-tahun-2023 ; https://www.pajak.go.id/id/pph-pasal-26 ; https://www.bpjsketenagakerjaan.go.id/penerima-upah.html . Verify effective regulations and derive reviewed fixtures during implementation.

## Implementation status

Effective company payroll policy, independently managed compensation, immutable history, scoped reads, optimistic updates, and replay are implemented. The pure calculation library includes the reviewed TER tables, resident final-period reconciliation, domestic PPh 26, bounded gross-up/net treatment, separately configured social insurance contributions, standard monthly-wage overtime, explicit-unit proration, and THR service/amount rules. Optional reviewed pay-basis settings are retained in compensation history. See [configuration and rules](../payroll-rules.md) for exact inputs, sources, rounding, access, and bounds.

Versioned opening tax history, independent verification, immutable evidence, and bounded scoped reads are implemented.

Period drafts, frozen participant lists, cancellation, exact workforce references, and independently verified monthly input history are implemented. See [period preparation](../payroll-workflows.md).

Durable calculation, cutoff enforcement, approval/finalization, payslips, payment reconciliation, amendments, and the dashboard remain planned. Configuration or a passing calculator fixture does not represent a completed payroll run.
