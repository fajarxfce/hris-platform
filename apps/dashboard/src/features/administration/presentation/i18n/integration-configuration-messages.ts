import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  title: "Integration configuration",
  summary:
    "API startup settings. Provider connection and successful delivery require separate verification.",
  observedAt: "Observed (UTC)",
  integrations: "Configured services",
  integration: "Integration",
  configuration: "API configuration",
  enabled: "Enabled",
  disabled: "Disabled",
  kinds: {
    FIREBASE_AUTH: "Firebase Authentication",
    OIDC: "Single sign-on (OIDC)",
    MAIL: "Email (SMTP)",
    PUSH: "Push notifications (FCM)",
    STORAGE: "File storage (S3)",
    DOCUMENT_SCANNER: "Document scanner (ClamAV)",
  },
};
const id: typeof en = {
  title: "Konfigurasi integrasi",
  summary:
    "Pengaturan saat API dimulai. Koneksi provider dan keberhasilan pengiriman memerlukan verifikasi terpisah.",
  observedAt: "Ditinjau (UTC)",
  integrations: "Layanan terkonfigurasi",
  integration: "Integrasi",
  configuration: "Konfigurasi API",
  enabled: "Aktif",
  disabled: "Nonaktif",
  kinds: {
    FIREBASE_AUTH: "Firebase Authentication",
    OIDC: "Single sign-on (OIDC)",
    MAIL: "Email (SMTP)",
    PUSH: "Notifikasi push (FCM)",
    STORAGE: "Penyimpanan file (S3)",
    DOCUMENT_SCANNER: "Pemindai dokumen (ClamAV)",
  },
};
export const integrationConfigurationMessages = (locale: Locale): typeof en =>
  locale === "id" ? id : en;
