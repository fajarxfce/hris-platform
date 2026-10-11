import type { CompanyId } from "../../../../core/domain/identifiers";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import type { CompanyMembership } from "../../../identity/domain/entities/session";
import type { EmploymentTransfer } from "../../domain/entities/employment-transfer";
import { employmentTransferMessages } from "../i18n/employment-transfer-messages";

export function employmentTransfersView(
  items: readonly EmploymentTransfer[],
  companyId: CompanyId,
  companies: readonly CompanyMembership[],
  locale: Locale,
) {
  const text = employmentTransferMessages(locale);
  const date = new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeZone: "UTC" });
  return items.map((item) => {
    const outgoing = item.sourceCompanyId === companyId;
    const counterpart = companies.find(
      (company) => company.id === (outgoing ? item.targetCompanyId : item.sourceCompanyId),
    );
    return {
      id: item.id,
      cells: [
        outgoing ? text.outgoing : text.incoming,
        counterpart ? `${counterpart.code} · ${counterpart.name}` : text.otherCompany,
        date.format(new Date(`${item.effectiveDate}T00:00:00Z`)),
        item.reason,
      ],
    };
  });
}
