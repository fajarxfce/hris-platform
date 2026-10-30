# Document category and identity validity — confirmed gap

Current status: `documents.02` and `people.03` are accepted. Backend/dashboard,
schema-103 restore, actual Android/API offline/original-receipt recovery and final
Mobile CI `41077194465` passed. [Final evidence](measurements/document-metadata-final-ci-2026-10-30.json)
retains every terminal CI job/step and verified install-only phone delivery.
The full inventory now has 36 accepted and 25 unverified Platform rows.

## Original finding and implementation record

Source review at `b0b7c21c` found two related missing requirements. `documents.02`
requires a category distinct from classification. `people.03` requires separate
foreign identity/document expiry and tax residency attributes. Document currently
has title/classification/owner/version, but no category. Its revision `expiresAt`
is the temporary upload deadline; it does not represent passport/permit validity.
PersonProfile has nationality, while Payroll TaxRegistration independently owns
verified resident/nonresident status and subjective periods. The latter must stay
independent of nationality and identity-document expiry.

The earlier Documents review omitted the category part of `documents.02`; that row
is reopened. Its other upload/inspection/content/retention assertions and four
other accepted rows retain their evidence. All five People rows remain unverified.
The current inventory has thirty accepted/reviewed rows and thirty-one unverified.
This is a source/contract finding, not a claimed failed HTTP test or completed fix.
[Exact source measurements](measurements/document-identity-metadata-gap-2026-10-30.json)
retain the inspected entity/command hashes.

Complete the active People command/Organization acceptance first. Then implement
explicit bounded category and identity-document metadata with separate business
validity dates, retained revision history, scoped reads/commands and reviewable
client forms. Preserve the operational upload deadline, content publication,
immutable evidence, original command replay, legacy-client compatibility and
Payroll's separate tax-registration authority. Expired business documents remain
retained evidence; expiry cannot silently delete them or change employment/tax
status. Exercise the complete API/storage/worker/browser workflow and affected
Mobile behavior before accepting both requirements. HRIS business metadata does
not belong in the generic Starter.
