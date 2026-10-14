import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  release: "Release payment batch",
  cancel: "Cancel payment batch",
  releaseAction: "Release batch",
  cancelAction: "Cancel batch",
  releaseNote:
    "Verify each bank destination and amount before releasing the instructions for payment.",
  cancelNote:
    "Cancel these instructions before release. The payslips will become available for a replacement batch.",
  details: "Batch review",
  reason: "Reason",
  reasonError: "Enter a reason of up to 1,000 characters.",
  back: "Batch details",
  refresh: "Reload review",
  saving: "Submitting…",
  saved: "Payment batch updated.",
  unconfirmed: "The result is unconfirmed. Retry the original action before starting another.",
  retry: "Retry original action",
  conflict: "The batch changed. Reload its review before confirming another action.",
  instructions: "Payment instructions",
  viewItem: "View payment",
  itemDetails: "Payment details",
};
const id: typeof en = {
  release: "Rilis batch pembayaran",
  cancel: "Batalkan batch pembayaran",
  releaseAction: "Rilis batch",
  cancelAction: "Batalkan batch",
  releaseNote: "Periksa tujuan rekening dan nominal setiap pembayaran sebelum merilis instruksi.",
  cancelNote: "Batalkan instruksi sebelum dirilis. Slip gaji akan tersedia untuk batch pengganti.",
  details: "Tinjauan batch",
  reason: "Alasan",
  reasonError: "Isi alasan maksimal 1.000 karakter.",
  back: "Detail batch",
  refresh: "Muat ulang tinjauan",
  saving: "Mengirim…",
  saved: "Batch pembayaran diperbarui.",
  unconfirmed: "Hasil belum terkonfirmasi. Ulangi tindakan awal sebelum memulai tindakan lain.",
  retry: "Ulangi tindakan awal",
  conflict: "Batch telah berubah. Muat ulang tinjauan sebelum mengonfirmasi tindakan lain.",
  instructions: "Instruksi pembayaran",
  viewItem: "Lihat pembayaran",
  itemDetails: "Detail pembayaran",
};
export const payrollPaymentTransitionMessages = (locale: Locale) => (locale === "id" ? id : en);
