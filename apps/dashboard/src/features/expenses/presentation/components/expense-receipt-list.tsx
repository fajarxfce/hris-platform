import { Text } from "@fluentui/react-components";
import { ArrowDownload20Regular } from "@fluentui/react-icons";
import { AppButton } from "../../../../core/presentation/components/app-button";
import { AppFailure } from "../../../../core/presentation/components/app-failure";
import type { Locale } from "../../../../core/presentation/i18n/messages";
import { expenseClaimMessages } from "../i18n/expense-claim-messages";
import type { ExpenseReceiptDownloadState } from "../models/expense-submission-state";
export function ExpenseReceiptList(props: {
  rows: readonly Readonly<{ id: string; name: string; size: string; duplicate: boolean }>[];
  state: ExpenseReceiptDownloadState;
  locale: Locale;
  onDownload: (id: string) => void;
  onCancel: () => void;
}) {
  const text = expenseClaimMessages(props.locale);
  return (
    <section aria-label={text.receipts}>
      <h2>{text.receipts}</h2>
      {props.rows.length === 0 ? (
        <Text>{text.noReceipts}</Text>
      ) : (
        <ul className="app-expense-receipts">
          {props.rows.map((receipt) => (
            <li key={receipt.id}>
              <div>
                <strong>{receipt.name}</strong>
                <span className="app-muted">{receipt.size}</span>
                {receipt.duplicate && <Text>{text.duplicate}</Text>}
              </div>
              <AppButton
                appearance="subtle"
                icon={<ArrowDownload20Regular />}
                disabled={props.state.stage === "downloading"}
                aria-label={`${text.download}: ${receipt.name}`}
                onClick={() => props.onDownload(receipt.id)}
              >
                {text.download}
              </AppButton>
            </li>
          ))}
        </ul>
      )}
      <AppFailure failure={props.state.failure} locale={props.locale} />
      <div aria-live="polite">
        {props.state.stage === "downloading" && (
          <div className="app-form-actions">
            <Text role="status">{text.downloading}</Text>
            <AppButton appearance="subtle" onClick={props.onCancel}>
              {text.cancelDownload}
            </AppButton>
          </div>
        )}
        {props.state.stage === "started" && <Text role="status">{text.started}</Text>}
      </div>
    </section>
  );
}
