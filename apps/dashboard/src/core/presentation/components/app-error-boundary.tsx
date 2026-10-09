import { Component, type ReactNode } from "react";
import { failureMessage } from "../i18n/failure-message";
import { type Locale, messages } from "../i18n/messages";

type Props = { children: ReactNode; locale: Locale; onReload: () => void };

/** React owns render failures here; error text and component props never enter product copy. */
export class AppErrorBoundary extends Component<Props, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <main className="app-fatal-error" role="alert">
        <h1>{messages(this.props.locale).product}</h1>
        <p>
          {failureMessage(
            { code: "unexpected_error", fields: {}, parameters: {} },
            this.props.locale,
          )}
        </p>
        <button type="button" onClick={this.props.onReload}>
          {messages(this.props.locale).refresh}
        </button>
      </main>
    );
  }
}
