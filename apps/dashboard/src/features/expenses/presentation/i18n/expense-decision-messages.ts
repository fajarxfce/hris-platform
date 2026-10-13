import type { Locale } from "../../../../core/presentation/i18n/messages";

const en = {
  approve: "Approve",
  reject: "Reject",
  return: "Return for correction",
  titles: { approve: "Approve expense", reject: "Reject expense", return: "Return expense" },
  hints: {
    approve: "Approve the current review stage.",
    reject: "Reject this submission and close the claim.",
    return: "Return this submission for correction and resubmission.",
  },
  saved: {
    approve: "Decision recorded.",
    reject: "Submission rejected.",
    return: "Returned for correction.",
  },
  review: "Submitted expense",
  back: "Back to submission",
  view: "View submission",
  reason: "Reason",
  reasonRequired: "Enter a reason.",
  saving: "Recording decision…",
  reload: "Reload review",
  conflict: "The reviewed facts have changed. Reload before deciding.",
  unconfirmed:
    "The decision has not been confirmed. Retry the original operation before making another decision.",
  retry: "Retry original operation",
  operation: "Operation ID",
  duplicate:
    "These receipts have current duplicate signals. Review the evidence and explain why approval is appropriate.",
  acknowledgement: "I have reviewed the duplicate signals and supporting receipts.",
  acknowledgementRequired: "Confirm that the duplicate signals have been reviewed.",
};
const id: typeof en = {
  approve: "Setujui",
  reject: "Tolak",
  return: "Kembalikan untuk koreksi",
  titles: {
    approve: "Setujui pengajuan biaya",
    reject: "Tolak pengajuan biaya",
    return: "Kembalikan pengajuan biaya",
  },
  hints: {
    approve: "Setujui tahap review saat ini.",
    reject: "Tolak pengajuan ini dan tutup klaim.",
    return: "Kembalikan pengajuan ini agar dapat diperbaiki dan diajukan ulang.",
  },
  saved: {
    approve: "Keputusan dicatat.",
    reject: "Pengajuan ditolak.",
    return: "Dikembalikan untuk koreksi.",
  },
  review: "Biaya yang diajukan",
  back: "Kembali ke pengajuan",
  view: "Lihat pengajuan",
  reason: "Alasan",
  reasonRequired: "Isi alasan keputusan.",
  saving: "Mencatat keputusan…",
  reload: "Muat ulang review",
  conflict: "Data yang ditinjau telah berubah. Muat ulang sebelum memberi keputusan.",
  unconfirmed:
    "Keputusan belum terkonfirmasi. Coba ulang operasi yang sama sebelum membuat keputusan lain.",
  retry: "Coba ulang operasi",
  operation: "ID operasi",
  duplicate:
    "Bukti ini memiliki indikasi duplikat saat ini. Periksa bukti dan jelaskan alasan persetujuan.",
  acknowledgement: "Saya telah memeriksa indikasi duplikat dan bukti pendukung.",
  acknowledgementRequired: "Konfirmasikan bahwa indikasi duplikat telah diperiksa.",
};
export const expenseDecisionMessages = (locale: Locale) => (locale === "id" ? id : en);
