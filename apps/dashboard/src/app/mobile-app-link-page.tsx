import { Title2 } from "@fluentui/react-components";
import { AppButton } from "../core/presentation/components/app-button";
import { type Locale, messages } from "../core/presentation/i18n/messages";
import type { MobileAppRoute } from "./routing/mobile-app-route";

export function MobileAppLinkPage({ route, locale }: { route: MobileAppRoute; locale: Locale }) {
  const text =
    locale === "id"
      ? {
          title: "Buka di HRIS Mobile",
          description: "Lanjutkan di aplikasi dengan akun dan perusahaan yang sesuai.",
          open: "Buka HRIS Mobile",
          install:
            "Belum memasang aplikasinya? Hubungi administrator untuk mendapatkan aplikasi HRIS Mobile.",
          unavailable: "Tautan tidak tersedia",
          invalid: "Tautan ini tidak dapat dibuka. Minta pengirim memberikan tautan yang benar.",
        }
      : {
          title: "Open in HRIS Mobile",
          description: "Continue in the app with the matching account and company.",
          open: "Open HRIS Mobile",
          install: "Need the app? Contact your administrator to get HRIS Mobile.",
          unavailable: "Link unavailable",
          invalid: "This link cannot be opened. Ask the sender for a valid link.",
        };
  return (
    <main className="app-auth-layout">
      <section className="app-auth-panel" aria-label={messages(locale).product}>
        <span className="app-auth-brand">{messages(locale).product}</span>
        <Title2 as="h1">{route.href === null ? text.unavailable : text.title}</Title2>
        <p>{route.href === null ? text.invalid : text.description}</p>
        {route.href !== null && (
          <>
            <AppButton as="a" appearance="primary" href={route.href} referrerPolicy="no-referrer">
              {text.open}
            </AppButton>
            <p>{text.install}</p>
          </>
        )}
      </section>
    </main>
  );
}
