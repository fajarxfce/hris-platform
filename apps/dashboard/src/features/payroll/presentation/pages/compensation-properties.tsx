import { AppPropertyList } from "../../../../core/presentation/components/app-property-list";
import { AppResourceTable } from "../../../../core/presentation/components/app-resource-table";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { compensationMessages } from "../i18n/compensation-messages";
import type { compensationView } from "../models/compensation-view";
export function CompensationProperties({
  view,
  title,
  locale,
  showHeading = true,
}: {
  view: ReturnType<typeof compensationView>;
  title: string;
  locale: Locale;
  showHeading?: boolean;
}) {
  const text = compensationMessages(locale);
  return (
    <section className="app-compensation-properties" aria-label={title}>
      {showHeading && <h2>{title}</h2>}
      <p className="app-muted">
        {showHeading && <>{view.title} · </>}
        {text.from}: {view.effectiveFrom}
      </p>
      <div className="app-compensation-property-grid">
        {view.groups.map((group) => (
          <AppPropertyList key={group.title} title={group.title} items={group.items} />
        ))}
      </div>
      {view.earnings.length ? (
        <AppResourceTable
          title={text.fixedEarnings}
          columns={[
            { id: "code", label: text.code },
            { id: "name", label: text.name },
            { id: "amount", label: text.amount, numeric: true },
            { id: "taxable", label: text.taxable },
          ]}
          rows={view.earnings}
        />
      ) : (
        <p className="app-muted">{text.noFixedEarnings}</p>
      )}
      {view.basis ? (
        <AppPropertyList title={text.payBasis} items={view.basis} />
      ) : (
        <p className="app-muted">{text.payBasisUnset}</p>
      )}
    </section>
  );
}
