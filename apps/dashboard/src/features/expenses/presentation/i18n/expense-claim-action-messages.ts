import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  SUBMIT: "Submit claim",
  CANCEL: "Cancel draft",
  WITHDRAW: "Withdraw submission",
  submitNote:
    "Review the items before submitting this claim for approval. Submitted evidence is retained for each submission.",
  cancelNote: "Cancel this draft. Cancelled claims cannot be submitted.",
  withdrawNote:
    "Withdraw the current submission and return the claim to draft. Its approval will be cancelled.",
  submitSaved: "Claim submitted.",
  cancelSaved: "Draft cancelled.",
  withdrawSaved: "Submission withdrawn.",
  reason: "Reason",
  reasonError: "Enter a reason of up to 1,000 characters.",
  saving: "Updating claim",
  back: "Claim details",
  retry: "Retry original action",
  refresh: "Reload review",
  edit: "Edit draft",
  unconfirmed: "The result is unconfirmed. Retry the original action before starting another.",
  conflict: "This claim has changed. Reload and review the current state before continuing.",
  item: "Item details",
  category: "Category",
  costCenter: "Cost center",
  noReceipts: "No receipts selected.",
};
const id: typeof en = {
  SUBMIT: "Ajukan klaim",
  CANCEL: "Batalkan draft",
  WITHDRAW: "Tarik pengajuan",
  submitNote:
    "Periksa item sebelum mengajukan klaim untuk persetujuan. Bukti disimpan pada setiap pengajuan.",
  cancelNote: "Batalkan draft ini. Klaim yang dibatalkan tidak dapat diajukan.",
  withdrawNote:
    "Tarik pengajuan terkini dan kembalikan klaim ke draft. Proses persetujuannya akan dibatalkan.",
  submitSaved: "Klaim diajukan.",
  cancelSaved: "Draft dibatalkan.",
  withdrawSaved: "Pengajuan ditarik.",
  reason: "Alasan",
  reasonError: "Isi alasan maksimal 1.000 karakter.",
  saving: "Memperbarui klaim",
  back: "Detail klaim",
  retry: "Ulangi tindakan semula",
  refresh: "Muat ulang tinjauan",
  edit: "Edit draft",
  unconfirmed: "Hasil belum terkonfirmasi. Ulangi tindakan semula sebelum memulai tindakan lain.",
  conflict: "Klaim telah berubah. Muat ulang dan tinjau status terkini sebelum melanjutkan.",
  item: "Detail item",
  category: "Kategori",
  costCenter: "Cost center",
  noReceipts: "Tidak ada receipt dipilih.",
};
export const expenseClaimActionMessages = (locale: Locale) => (locale === "id" ? id : en);
