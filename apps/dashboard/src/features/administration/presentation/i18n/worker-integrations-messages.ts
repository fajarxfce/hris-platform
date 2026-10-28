import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  title: "Worker reports",
  summary:
    "Status at the observation time. Refresh for a new observation; provider delivery is checked separately.",
  empty: "No worker has reported yet.",
  observedAt: "Observed (UTC)",
  worker: "Worker instance",
  status: "Report status",
  firstSeen: "First reported (UTC)",
  lastSeen: "Last reported (UTC)",
  recent: "Recent report",
  stale: "Stale report",
};
const id: typeof en = {
  title: "Laporan worker",
  summary:
    "Status pada waktu peninjauan. Muat ulang untuk laporan terbaru; pengiriman provider diperiksa terpisah.",
  empty: "Belum ada worker yang melapor.",
  observedAt: "Ditinjau (UTC)",
  worker: "Proses worker",
  status: "Status laporan",
  firstSeen: "Laporan pertama (UTC)",
  lastSeen: "Laporan terakhir (UTC)",
  recent: "Laporan baru",
  stale: "Laporan kedaluwarsa",
};
export const workerIntegrationsMessages = (locale: Locale): typeof en =>
  locale === "id" ? id : en;
