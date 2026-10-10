import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  title: "Cancel employment revision",
  review: "Review cancellation",
  cancel: "Cancel revision",
  cancelling: "Cancelling revision…",
  cancelled: "Employment revision cancelled.",
  back: "Back to employment history",
  history: "View history",
  reason: "Cancellation reason",
  notice:
    "This scheduled revision will stop applying. Its original terms and the cancellation remain in history.",
  unavailable:
    "This revision cannot be cancelled. Only retained revisions that have not taken effect are eligible.",
  retry: "Retry cancellation",
  reload: "Reload revision",
  companyDate: "Current company date",
  unconfirmed:
    "The cancellation result is not confirmed. Retry the same request to check its outcome.",
};
const id: typeof en = {
  title: "Batalkan revisi employment",
  review: "Tinjau pembatalan",
  cancel: "Batalkan revisi",
  cancelling: "Membatalkan revisi…",
  cancelled: "Revisi employment dibatalkan.",
  back: "Kembali ke riwayat employment",
  history: "Lihat riwayat",
  reason: "Alasan pembatalan",
  notice:
    "Revisi terjadwal ini tidak akan berlaku. Data awal dan bukti pembatalannya tetap tersimpan dalam riwayat.",
  unavailable:
    "Revisi ini tidak dapat dibatalkan. Hanya revisi tersimpan yang belum berlaku yang dapat dibatalkan.",
  retry: "Coba pembatalan kembali",
  reload: "Muat ulang revisi",
  companyDate: "Tanggal perusahaan saat ini",
  unconfirmed:
    "Hasil pembatalan belum terkonfirmasi. Ulangi request yang sama untuk memeriksa hasilnya.",
};
export const employmentCancellationMessages = (locale: Locale) => (locale === "id" ? id : en);
